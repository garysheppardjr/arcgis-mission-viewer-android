# ArcGIS Mission Viewer Android demo

The ArcGIS Mission Viewer Android demo shows one way to access ArcGIS Mission data from a custom Android app using ArcGIS Runtime.

# Getting started

Login to your ArcGIS Enterprise instance. [Add an app as a Portal item](https://enterprise.arcgis.com/en/portal/latest/administer/windows/add-items.htm#ESRI_SECTION1_0D1B620254F745AE84F394289F8AF44B) and register it for OAuth 2.0. Set a redirect URL. The app ID and redirect URL must match values in this repository's `strings.xml` file.

- The redirect URL should be `[oauth_redirect_uri_scheme]://[oauth_redirect_uri_host]`. For example, in `strings.xml`, if the value of `oauth_redirect_uri_scheme` is `foo` and the value of `oauth_redirect_uri_host` is `bar`, the redirect URL in your registered app should be `foo://bar`.
- The client ID must equal the value of `oauth_client_id` in `strings.xml`. Either you can edit `strings.xml` to match your registered app's client ID (this is the easier option), or you can [change your registered app's client ID](https://developers.arcgis.com/rest/enterprise-administration/portal/change-app-id.htm) (this is a little harder but allows you to use the same client ID for your app in various ArcGIS Enterprise instances).

Go to the [ArcGIS for Developers dashboard](https://developers.arcgis.com/dashboard/), login, create an API key if you don't have one already, copy it, and paste it into your global `gradle.properties` (`$HOME/.gradle/gradle.properties` or `%USERPROFILE%\.gradle\gradle.properties`).

This app works in an x86 emulator and on a 32-bit or 64-bit Android device.

# Issues and contributions

Feel free to submit issues and pull requests, realizing that this is an unofficial demo app that may or may not be currently maintained.

# License

Apache License 2.0. See LICENSE file for details.
