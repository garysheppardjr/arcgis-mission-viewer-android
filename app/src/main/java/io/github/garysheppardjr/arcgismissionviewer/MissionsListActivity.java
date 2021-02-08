package io.github.garysheppardjr.arcgismissionviewer;

import android.os.Bundle;
import android.util.Log;

import androidx.fragment.app.FragmentTransaction;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.portal.Portal;
import com.esri.arcgisruntime.portal.PortalInfo;
import com.esri.arcgisruntime.security.OAuthTokenCredential;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class MissionsListActivity extends ArcGISOAuthActivity {

    private static final String TAG = MissionsListActivity.class.getSimpleName();

    /**
     * The name of the Portal URL extra that this activity uses.
     */
    public static final String EXTRA_PORTAL_URL = MissionsListActivity.class.getName() + ".PortalUrl";

    private static final int REQUEST_CODE_MISSIONACTIVITY = 1;

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

    /**
     * Result code that means the app could not get Portal info from the provided URL.
     */
    public static final int RESULT_COULD_NOT_GET_MISSIONS_LIST = 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_missions_list);

        if (null == savedInstanceState) {

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

                if (null == portalInfo) {
                    Log.w(TAG, String.format("Could not get Portal info from %s.", portalUrlFinal));
                    setResult(RESULT_NO_PORTAL_INFO);
                    finish();
                    return;
                } else {
                    String serversUrl = portal.getSharingUrl() + "/portals/" + portalInfo.getOrganizationId() + "/servers?f=json";
                    Log.d(TAG, "Servers URL: " + serversUrl);
                    ListenableFuture<String> serversFuture = portal.sendRequestAsync(serversUrl);
                    serversFuture.addDoneListener(() -> {
                        JSONArray servers = null;
                        try {
                            String serversResponse = serversFuture.get();
                            JSONObject serversResponseObj = new JSONObject(serversResponse);
                            servers = serversResponseObj.getJSONArray("servers");
                        } catch (ExecutionException | InterruptedException | JSONException e) {
                            Log.w(TAG, e);
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
                            // Ask Mission Server for the missions
                            String missionsUrl = theMissionServerOptional.get().optString("url") + "/rest/missions?f=json";
                            RequestQueue requestQueue = Volley.newRequestQueue(this);
                            StringRequest req = new StringRequest(Request.Method.GET, missionsUrl, responseStr -> {
                                try {
                                    JSONObject response = new JSONObject(responseStr);
                                    Log.d(TAG, responseStr);
                                    JSONArray missions = response.getJSONArray("results");
                                    ArrayList<String> missionIds = new ArrayList<>();
                                    for (int i = 0; i < missions.length(); i++) {
                                        missionIds.add(missions.getJSONObject(i).getString("id"));
                                    }

                                    // Put the missions in the list
                                    // TODO perhaps save the list
                                    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                                    MissionsListFragment fragment = new MissionsListFragment(portalUrlFinal, missionIds);
                                    transaction.replace(R.id.sample_content_fragment, fragment);
                                    transaction.commit();
                                } catch (JSONException e) {
                                    Log.e(TAG, e.getLocalizedMessage(), e);
                                }

                            }, error -> {
                                Log.e(TAG, error.getLocalizedMessage(), error);
                            }) {
                                @Override
                                public Map<String, String> getHeaders() throws AuthFailureError {
                                    HashMap<String, String> headers = new HashMap<>(super.getHeaders());
                                    headers.put("Authorization", ((OAuthTokenCredential) portal.getCredential()).getAccessToken());
                                    return headers;
                                }
                            };
                            requestQueue.add(req);
                        }
                    });
                }

            });
            portal.loadAsync();
        }
    }

}