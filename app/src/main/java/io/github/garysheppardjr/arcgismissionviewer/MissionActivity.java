package io.github.garysheppardjr.arcgismissionviewer;

import android.os.Bundle;
import android.util.Log;

import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.portal.Portal;
import com.esri.arcgisruntime.portal.PortalInfo;
import com.esri.arcgisruntime.portal.PortalItem;
import com.esri.arcgisruntime.security.OAuthTokenCredential;
import com.esri.arcgisruntime.symbology.Renderer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.github.garysheppardjr.layers.StreamServiceListener;

public class MissionActivity extends ArcGISOAuthActivity {

    private static final String TAG = MissionActivity.class.getSimpleName();

    /**
     * The name of the mission ID extra that this activity uses.
     */
    public static final String EXTRA_MISSION_ID = MissionActivity.class.getName() + ".MissionId";

    /**
     * The name of the Portal URL extra that this activity uses.
     */
    public static final String EXTRA_PORTAL_URL = MissionActivity.class.getName() + ".PortalUrl";

    /**
     * Result code that means the provided ArcGIS Enterprise URL was malformed.
     */
    public static final int RESULT_BAD_PORTAL_URL = 1;

    /**
     * Result code that means the activity could not get a valid Mission Server from the ArcGIS
     * Enterprise deployment. This result could simply mean that the user has not logged in yet, or
     * it could represent a more significant problem.
     */
    public static final int RESULT_NO_MISSION_SERVER_FOUND = 2;

    /**
     * Result code that means the app could not get Portal info from the provided URL.
     */
    public static final int RESULT_NO_PORTAL_INFO = 3;

    private final GraphicsOverlay streamGraphicsOverlay = new GraphicsOverlay();

    private MapView mapView;
    private StreamServiceListener streamServiceListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mission);

        String portalUrl = getIntent().getStringExtra(EXTRA_PORTAL_URL);
        try {
            portalUrl = setupOAuth(portalUrl);
        } catch (MalformedURLException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
            setResult(RESULT_BAD_PORTAL_URL);
            finish();
            return;
        }
        final String portalUrlFinal = portalUrl;

        ArcGISRuntimeEnvironment.setApiKey(BuildConfig.API_KEY);

        Portal portal = new Portal(portalUrl);
        portal.addDoneLoadingListener(() -> {
            PortalInfo portalInfo = portal.getPortalInfo();
            JSONArray servers = null;
            if (null == portalInfo) {
                Log.w(TAG, String.format("Could not get Portal info from %s.", portalUrlFinal));
                setResult(RESULT_NO_PORTAL_INFO);
                finish();
                return;
            } else {
                try {
                    String serversUrl = portal.getSharingUrl() + "/portals/" + portalInfo.getOrganizationId() + "/servers?f=json";
                    Log.d(TAG, "Servers URL: " + serversUrl);
                    ListenableFuture<String> serversFuture = portal.postRequestAsync(serversUrl);
                    String serversResponse = serversFuture.get(5, TimeUnit.SECONDS);
                    JSONObject serversResponseObj = new JSONObject(serversResponse);
                    servers = serversResponseObj.getJSONArray("servers");
                } catch (JSONException | ExecutionException | InterruptedException | TimeoutException e) {
                    Log.w(TAG, e);
                }
            }
            if (null == servers) {
                servers = new JSONArray();
            }
            List<JSONObject> missionServers = new ArrayList<>();
            for (int i = 0; i < servers.length(); i++) {
                try {
                    JSONObject server = servers.getJSONObject(i);
                    if ("ARCGIS_MISSION_SERVER".equals(server.optString("serverType"))) {
                        missionServers.add(server);
                    }
                } catch (JSONException e) {
                    Log.w(TAG, e);
                }
            }
            Optional<JSONObject> theMissionServerOptional = missionServers.parallelStream()
                    .filter(missionServer -> "MissionServer".equals(missionServer.optString("serverFunction")))
                    .findFirst();
            if (!theMissionServerOptional.isPresent()) {
                setResult(RESULT_NO_MISSION_SERVER_FOUND);
                finish();
                return;
            } else {
                String missionId = getIntent().getStringExtra(EXTRA_MISSION_ID);
                PortalItem missionItem = new PortalItem(portal, missionId);
                try {
                    JSONObject missionItemData = new JSONObject(new Scanner(missionItem.fetchDataAsync().get()).useDelimiter("\\A").next());
                    JSONArray maps = missionItemData.getJSONArray("maps");
                    String webMapId = maps.getString(0);

                    PortalItem portalItem = new PortalItem(portal, webMapId);
                    ArcGISMap map = new ArcGISMap(portalItem);
                    mapView = findViewById(R.id.mapView);
                    mapView.setMap(map);
                    map.addDoneLoadingListener(new Runnable() {
                        @Override
                        public void run() {
                            map.removeDoneLoadingListener(this);
                            mapView.getGraphicsOverlays().add(streamGraphicsOverlay);
                        }
                    });

                    StreamServiceListener.StreamServiceCallback callback = new StreamServiceListener.StreamServiceCallback() {
                        @Override
                        protected void rendererAvailable(Renderer renderer) {
                            streamGraphicsOverlay.setRenderer(renderer);
                        }

                        @Override
                        protected void newStreamFeature(Graphic newFeature) {
                            streamGraphicsOverlay.getGraphics().add(newFeature);
                        }
                    };
                    streamServiceListener = new StreamServiceListener(
                            theMissionServerOptional.get().getString("url") + "/rest/services/" + missionId + "/tracks/StreamServer",
                            ((OAuthTokenCredential) portal.getCredential()).getAccessToken(),
                            callback
                    );
                    new Thread(() -> {
                        try {
                            streamServiceListener.start();
                        } catch (IOException | URISyntaxException | JSONException e) {
                            Log.e(TAG, "Could not connect to mission stream service: " + e.getLocalizedMessage(), e);
                        }
                    }).start();
                } catch (JSONException | ExecutionException | InterruptedException e) {
                    Log.e(TAG, "Could not get mission and web map: " + e.getLocalizedMessage(), e);
                }
            }
        });

        portal.loadAsync();
    }

    @Override
    protected void onPause() {
        if (null != mapView) {
            mapView.pause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (null != mapView) {
            mapView.resume();
        }
    }

    @Override
    protected void onDestroy() {
        streamServiceListener.close();
        if (null != mapView) {
            mapView.dispose();
        }
        super.onDestroy();
    }
}