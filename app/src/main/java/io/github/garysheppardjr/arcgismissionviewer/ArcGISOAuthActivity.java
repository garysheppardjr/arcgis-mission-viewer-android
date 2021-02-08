package io.github.garysheppardjr.arcgismissionviewer;

import androidx.appcompat.app.AppCompatActivity;

import com.esri.arcgisruntime.security.AuthenticationManager;
import com.esri.arcgisruntime.security.DefaultAuthenticationChallengeHandler;
import com.esri.arcgisruntime.security.OAuthConfiguration;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

abstract class ArcGISOAuthActivity extends AppCompatActivity {

    protected String setupOAuth(String portalUrl) throws MalformedURLException {
        try {
            URI uri = new URI(portalUrl);
            if (null == uri.getScheme()) {
                throw new URISyntaxException(portalUrl, "no scheme");
            }
        } catch (URISyntaxException ex) {
            // Maybe the user didn't add https://
            portalUrl = "https://" + portalUrl;
        }
        OAuthConfiguration oAuthConfiguration = new OAuthConfiguration(
                portalUrl,
                getString(R.string.oauth_client_id),
                getString(R.string.oauth_redirect_uri_scheme) + "://" + getString(R.string.oauth_redirect_uri_host)
        );
        DefaultAuthenticationChallengeHandler defaultAuthenticationChallengeHandler = new DefaultAuthenticationChallengeHandler(this);
        AuthenticationManager.setAuthenticationChallengeHandler(defaultAuthenticationChallengeHandler);
        AuthenticationManager.addOAuthConfiguration(oAuthConfiguration);
        return portalUrl;
    }

}
