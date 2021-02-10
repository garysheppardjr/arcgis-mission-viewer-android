package io.github.garysheppardjr.layers;

import android.util.Log;

import com.esri.arcgisruntime.geometry.Geometry;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.symbology.Renderer;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Connects to a stream service, gets its WebSocket URL, connects via WebSocket,
 * and emits graphics.
 */
public class StreamServiceListener extends WebSocketListener {

    private static final String TAG = StreamServiceListener.class.getSimpleName();

    /**
     * Used to pass new stream features back to a client.
     */
    public static abstract class StreamServiceCallback {

        /**
         * Called when the stream service's renderer is read.
         *
         * @param renderer the stream service's default renderer.
         */
        protected abstract void rendererAvailable(Renderer renderer);

        /**
         * Handles a new stream feature.
         *
         * @param newFeature a graphic representing a stream feature with a track ID
         *                   that the associated listener has not seen before.
         */
        protected abstract void newStreamFeature(Graphic newFeature);

    }

    private final String streamServiceUrl;
    private final StreamServiceCallback streamServiceCallback;
    private final Map<String, Graphic> trackIdToGraphic = new HashMap<>();
    private final CountDownLatch stopLatch = new CountDownLatch(1);

    private String trackIdFieldName;
    private String token = null;
    private JSONObject webSocketConnectionInfo;
    private JSONObject spatialReference;
    private WebSocket webSocket;
    private StringBuilder multiTextBuffer;

    /**
     * Instantiates but does not start a new stream service listener. After
     * calling the constructor, call start() to start it.
     *
     * @param streamServiceUrl      the stream service URL, e.g.
     *                              https://realtimegis.esri.com:6443/arcgis/rest/services/Helicopter/StreamServer
     *                              . This should be a valid non-null stream service URL; otherwise, the
     *                              start() method will probably throw an exception.
     * @param streamServiceCallback a callback object. It can be null, but then
     *                              this listener won't be able to give you anything useful.
     * @see #start()
     */
    public StreamServiceListener(String streamServiceUrl, String token, StreamServiceCallback streamServiceCallback) {
        this.streamServiceUrl = streamServiceUrl;
        this.token = token;
        this.streamServiceCallback = streamServiceCallback;
    }

    /**
     * Tells the listener to read the stream service's JSON descriptor and connect
     * to the stream service via WebSocket.
     *
     * @throws IOException        if the stream service cannot be read, either because it
     *                            is down or because the URL provided to the constructor is incorrect.
     * @throws URISyntaxException if the stream service URL given to the
     *                            constructor is syntactically incorrect.
     * @throws JSONException      if the stream service does not return the kind of JSON that it ought to
     *                            return.
     */
    public void start() throws IOException, URISyntaxException, JSONException {
        JSONObject streamServiceJsonDescriptor = readStreamServiceJsonDescriptor();
        spatialReference = streamServiceJsonDescriptor.optJSONObject("spatialReference");
        trackIdFieldName = readTrackIdFieldName(streamServiceJsonDescriptor);
        if (null != streamServiceCallback) {
            Renderer renderer = readRenderer(streamServiceJsonDescriptor);
            if (null != renderer) {
                streamServiceCallback.rendererAvailable(renderer);
            }
        }
        webSocketConnectionInfo = readWebSocketConnectionInfo(streamServiceJsonDescriptor);
        if (webSocketConnectionInfo.has("urls")) {
            JSONArray urls = webSocketConnectionInfo.getJSONArray("urls");
            if (0 < urls.length()) {
                new Thread(() -> {
                    try {
                        OkHttpClient client = new OkHttpClient.Builder().build();
                        Request.Builder builder = new Request.Builder().url(urls.optString(0) + "/subscribe");
                        if (webSocketConnectionInfo.has("token")) {
                            builder.addHeader("Authorization", "Bearer " + webSocketConnectionInfo.optString("token"));
                        }
                        this.webSocket = client.newWebSocket(builder.build(), this);
                        stopLatch.await();
                    } catch (InterruptedException ex) {
                        Logger.getLogger(StreamServiceListener.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }).start();
            } else {
                throw new IOException("Stream service JSON descriptor's urls array is empty");
            }
        } else {
            throw new IOException("Stream service JSON descriptor does not contain urls");
        }

    }

    private JSONObject readStreamServiceJsonDescriptor() throws IOException, JSONException {
        OkHttpClient client = new OkHttpClient.Builder().build();
        Request.Builder builder = new Request.Builder()
                .url(streamServiceUrl + "?f=json");
        if (null != token) {
            builder.addHeader("Authorization", "Bearer " + token);
        }
        Request request = builder.build();
        Response response = client.newCall(request).execute();
        return new JSONObject(response.body().string());
    }

    private static String readTrackIdFieldName(JSONObject streamServiceJsonDescriptor) throws JSONException {
        String trackIdFieldName = null;
        if (streamServiceJsonDescriptor.has("timeInfo")) {
            JSONObject timeInfoObj = streamServiceJsonDescriptor.getJSONObject("timeInfo");
            if (timeInfoObj.has("trackIdField")) {
                trackIdFieldName = timeInfoObj.getString("trackIdField");
            }
        }
        return trackIdFieldName;
    }

    private static Renderer readRenderer(JSONObject streamServiceJsonDescriptor) throws JSONException {
        if (streamServiceJsonDescriptor.has("drawingInfo")) {
            JSONObject drawingInfo = streamServiceJsonDescriptor.getJSONObject("drawingInfo");
            if (drawingInfo.has("renderer")) {
                return Renderer.fromJson(drawingInfo.getJSONObject("renderer").toString());
            }
        }
        return null;
    }

    private static JSONObject readWebSocketConnectionInfo(JSONObject streamServiceJsonDescriptor) throws IOException, URISyntaxException, JSONException {
        if (streamServiceJsonDescriptor.has("streamUrls")) {
            JSONArray streamUrls = streamServiceJsonDescriptor.getJSONArray("streamUrls");
            if (0 < streamUrls.length()) {
                return streamUrls.getJSONObject(0);
            } else {
                throw new IOException("Stream service JSON descriptor's streamUrls array is empty");
            }
        } else {
            throw new IOException("Stream service JSON descriptor does not contain streamUrls");
        }
    }

    @Override
    public void onMessage(@NotNull WebSocket webSocket, @NotNull String json) {
        Log.d(TAG, "onMessage: " + json);
        if (null != streamServiceCallback) {
            if (null != multiTextBuffer) {
                multiTextBuffer.append(json);
                json = multiTextBuffer.toString();
            }
            try {
                JSONObject obj = new JSONObject(json);
                createOrUpdateGraphic(
                        obj.getJSONObject("geometry"),
                        new Gson().fromJson(
                                obj.getString("attributes"),
                                new TypeToken<HashMap<String, Object>>() {
                                }.getType()
                        )
                );
            } catch (JSONException ex) {
                Log.e(TAG, String.format("JSONException related to this string: %s", json), ex);
            }
        }
        multiTextBuffer = null;

        super.onMessage(webSocket, json);
    }

    private static Map<String, Object> jsonObjectToMap(JSONObject obj) throws JSONException {
        HashMap<String, Object> map = new HashMap<>();
        AtomicReference<JSONException> exHolder = new AtomicReference<>();
        obj.keys().forEachRemaining(key -> {
            try {
                map.put(key, obj.get(key));
            } catch (JSONException e) {
                exHolder.set(e);
            }
        });
        JSONException ex = exHolder.get();
        if (null != ex) {
            throw ex;
        } else {
            return map;
        }
    }

    private void createOrUpdateGraphic(JSONObject geometry, Map<String, Object> attributes) throws JSONException {
        if (!geometry.has("spatialReference")) {
            geometry.put("spatialReference", spatialReference);
        }
        createOrUpdateGraphic(Geometry.fromJson(geometry.toString()), attributes);
    }

    private void createOrUpdateGraphic(Geometry geometry, Map<String, Object> attributes) {
        Graphic graphic = null;
        String trackId = null;
        if (null != trackIdFieldName) {
            Object trackIdObj = attributes.get(trackIdFieldName);
            if (null != trackIdObj) {
                trackId = trackIdObj.toString();
                graphic = trackIdToGraphic.get(trackId);
            }
        }
        boolean newGraphic = false;
        if (null == graphic) {
            graphic = new Graphic();
            newGraphic = true;
            if (null != trackId) {
                trackIdToGraphic.put(trackId, graphic);
            }
        }
        graphic.getAttributes().putAll(attributes);
        graphic.setGeometry(geometry);
        if (newGraphic) {
            streamServiceCallback.newStreamFeature(graphic);
        }
    }

    @Override
    public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
        // TODO do something to notify the client code and/or the user
        Log.e(TAG, "onFailure: " + t.getLocalizedMessage(), t);
        super.onFailure(webSocket, t, response);
    }

    /**
     * Closes the WebSocket connection. You can start it again by calling start().
     */
    public void close() {
        webSocket.close(200, "OK");
        stopLatch.countDown();
    }

    @Override
    public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
        stopLatch.countDown();
        super.onClosed(webSocket, code, reason);
    }

}
