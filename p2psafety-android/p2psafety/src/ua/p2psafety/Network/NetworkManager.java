package ua.p2psafety.Network;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.util.Log;

import com.facebook.Session;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.AbstractHttpMessage;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;

import ua.p2psafety.Event;
import ua.p2psafety.SosManager;
import ua.p2psafety.User;
import ua.p2psafety.data.Prefs;
import ua.p2psafety.roles.Role;
import ua.p2psafety.util.Logs;
import ua.p2psafety.util.Utils;

import static ua.p2psafety.util.Utils.errorDialog;

public class NetworkManager {
    public static final int SITE = 0;
    public static final int FACEBOOK = 1;

    private static final String SERVER_URL = "http://p2psafety.staging.42cc.co";
    public static Logs LOGS;

    private static final int CODE_SUCCESS = 201;

    private static HttpClient httpClient;
    private static ExecutorService executor = Executors.newSingleThreadExecutor();
    private static ObjectMapper mapper = new ObjectMapper();

    public static void init(Context context) {
        HttpParams httpParams = new BasicHttpParams();
        HttpProtocolParams.setVersion(httpParams, HttpVersion.HTTP_1_1);
        HttpConnectionParams.setConnectionTimeout(httpParams, 0);
        HttpConnectionParams.setSoTimeout(httpParams, 0);

        // https
        HostnameVerifier hostnameVerifier = SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;
        SchemeRegistry schReg = new SchemeRegistry();
        SSLSocketFactory socketFactory = SSLSocketFactory.getSocketFactory();
        socketFactory.setHostnameVerifier((X509HostnameVerifier) hostnameVerifier);
        schReg.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        schReg.register(new Scheme("https", socketFactory, 443));
        ClientConnectionManager conMgr = new ThreadSafeClientConnManager(httpParams, schReg);

        httpClient = new DefaultHttpClient(conMgr, httpParams);
        HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier);

        LOGS = new Logs(context);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();

        if (LOGS != null)
            LOGS.close();
    }

    public static void createEvent(final Context context) {
        createEvent(context, new DeliverResultRunnable());
    }

    public static void createEvent(final Context context,
                                   final DeliverResultRunnable<Event> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final String TAG = "createEvent";
                try {
                    if (!Utils.isNetworkConnected(context, LOGS)) {
                        errorDialog(context, Utils.DIALOG_NO_CONNECTION);
                        throw new Exception();
                    }

                    HttpPost httpPost = new HttpPost(new StringBuilder().append(SERVER_URL)
                            .append("/api/v1/events/").toString());

                    addAuthHeader(context, httpPost);
                    addUserAgentHeader(context, httpPost);
                    httpPost.setHeader("Accept", "application/json");
                    httpPost.setHeader("Content-type", "application/json");

                    JSONObject json = new JSONObject();
                    StringEntity se = new StringEntity(json.toString());
                    httpPost.setEntity(se);

                    Log.i(TAG, "request: " + httpPost.getRequestLine().toString());
                    Log.i(TAG, "request entity: " + EntityUtils.toString(httpPost.getEntity()));

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpPost);
                    } catch (Exception e) {
                        NetworkManager.LOGS.error("Can't execute post request", e);
                        errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                        throw new Exception();
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        Map<String, Object> data = mapper.readValue(responseContent, Map.class);
                        Event event = JsonHelper.jsonToEvent(data);
                        data.clear();

                        postRunnable.setResult(event);
                    } else {
                        postRunnable.setResult(null);
                    }

                    executeRunnable(context, postRunnable);
                } catch (Exception e) {
                    NetworkManager.LOGS.error("Can't create event", e);
                    errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                    postRunnable.setResult(null);
                    executeRunnable(context, postRunnable);
                }
            }
        });
    }

    public static void updateEventWithAttachment(Context context, File file, boolean isAudio) {
        updateEventWithAttachment(context, file, isAudio, new DeliverResultRunnable());
    }

    public static void updateEventWithAttachment(final Context context,
                                   final File file, final boolean isAudio,
                                   final DeliverResultRunnable<Boolean> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final String TAG = "updateEvent";
                try {
                    if (!Utils.isNetworkConnected(context, LOGS)) {
                        errorDialog(context, Utils.DIALOG_NO_CONNECTION);
                        throw new Exception();
                    }

                    Event event = SosManager.getInstance(context).getEvent();

                    HttpPost httpPost = new HttpPost(new StringBuilder().append(SERVER_URL)
                            .append("/api/v1/eventupdates/").toString());

                    addAuthHeader(context, httpPost);
                    addUserAgentHeader(context, httpPost);

                    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                    builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
                    if (isAudio)
                        builder.addPart("audio", new FileBody(file));
                    else
                        builder.addPart("video", new FileBody(file));

                    builder.addTextBody("key", event.getKey(), ContentType.APPLICATION_JSON);

                    httpPost.setEntity(builder.build());

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpPost);
                    } catch (Exception e) {
                        NetworkManager.LOGS.error("Can't execute post request", e);
                        errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                        throw new Exception();
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        postRunnable.setResult(true);
                    } else {
                        postRunnable.setResult(false);
                    }

                    executeRunnable(context, postRunnable);
                } catch (Exception e) {
                    NetworkManager.LOGS.error("Can't update event with attachments", e);
                    postRunnable.setResult(false);
                    executeRunnable(context, postRunnable);
                    errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                }
            }
        });
    }

    public static void updateEvent(Context context, Map data) {
        updateEvent(context, data, new DeliverResultRunnable());
    }

    public static void updateEvent(final Context context,
                                   final Map data,
                                   final DeliverResultRunnable<Boolean> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final String TAG = "updateEvent";
                try {
                    if (!Utils.isNetworkConnected(context, LOGS)) {
                        errorDialog(context, Utils.DIALOG_NO_CONNECTION);
                        throw new Exception();
                    }

                    Event event = SosManager.getInstance(context).getEvent();

                    HttpPost httpPost = new HttpPost(new StringBuilder().append(SERVER_URL)
                            .append("/api/v1/eventupdates/").toString());

                    addAuthHeader(context, httpPost);
                    addUserAgentHeader(context, httpPost);
                    httpPost.setHeader("Accept", "application/json");
                    httpPost.setHeader("Content-type", "application/json");

                    JSONObject json = new JSONObject();
                    json.put("key", event.getKey());
                    json.put("text", data.get("text"));
                    try {
                        Location loc = (Location) data.get("loc");
                        JSONObject jsonLocation = new JSONObject();
                        jsonLocation.put("latitude",  loc.getLatitude());
                        jsonLocation.put("longitude", loc.getLongitude());
                        json.put("location", jsonLocation);
                    } catch (Exception e) {
                        // we have no location (GPS is off)
                    }

                    StringEntity se = new StringEntity(json.toString(), "UTF-8");
                    httpPost.setEntity(se);

                    Log.i(TAG, "request: " + httpPost.getRequestLine().toString());
                    Log.i(TAG, "request entity: " + EntityUtils.toString(httpPost.getEntity()));

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpPost);
                    } catch (Exception e) {
                        NetworkManager.LOGS.error("Can't execute post request", e);
                        errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                        throw new Exception();
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        postRunnable.setResult(true);
                    } else {
                        postRunnable.setResult(false);
                    }

                    executeRunnable(context, postRunnable);
                } catch (Exception e) {
                    NetworkManager.LOGS.error("Can't update event", e);
                    errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                    postRunnable.setResult(false);
                    executeRunnable(context, postRunnable);
                }
            }
        });
    }

    // TODO: make it work (now it returns code 401)
    public static void getEvents(final Context context,
                                 final DeliverResultRunnable<List<Event>> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final String TAG = "getEvents";
                try {
                    if (!Utils.isNetworkConnected(context, LOGS)) {
                        errorDialog(context, Utils.DIALOG_NO_CONNECTION);
                        throw new Exception();
                    }

                    HttpGet httpGet = new HttpGet(new StringBuilder().append(SERVER_URL)
                            .append("/api/v1/events/")
                            .append("?user=")
                            .append(SosManager.getInstance(context).getEvent().getUser().getId())
                            .toString());

                    addAuthHeader(context, httpGet);
                    addUserAgentHeader(context, httpGet);
                    httpGet.setHeader("Accept", "application/json");
                    //httpGet.setHeader("Content-type", "application/json");

                    Log.i(TAG, "request: " + httpGet.getRequestLine().toString());

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpGet);
                    } catch (Exception e) {
                        NetworkManager.LOGS.error("Can't execute get request", e);
                        errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                        return;
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        Map<String, Object> data = mapper.readValue(responseContent, Map.class);
                        Event event = JsonHelper.jsonToEvent(data);
                        data.clear();

                        postRunnable.setResult(null);
                    } else {
                        postRunnable.setResult(null);
                    }

                    executeRunnable(context, postRunnable);
                } catch (Exception e) {
                    NetworkManager.LOGS.error("Can't get events", e);
                    errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                    postRunnable.setResult(null);
                    executeRunnable(context, postRunnable);
                }
            }
        });
    }

    public static void getRoles(final Context context,
                                final DeliverResultRunnable<List<Role>> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final int CODE_SUCCESS = 200;
                final String TAG = "getRoles";
                try {
                    if (!Utils.isNetworkConnected(context, LOGS)) {
                        errorDialog(context, Utils.DIALOG_NO_CONNECTION);
                        throw  new Exception();
                    }

                    StringBuilder url = new StringBuilder()
                            .append(SERVER_URL).append("/api/v1/")
                            .append("roles/");

                    HttpGet httpGet = new HttpGet(url.toString());
                    addAuthHeader(context, httpGet);
                    addUserAgentHeader(context, httpGet);
                    httpGet.setHeader("Accept", "application/json");
                    httpGet.setHeader("Content-type", "application/json");

                    Log.i(TAG, "request: " + httpGet.getRequestLine().toString());

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpGet);
                    } catch (Exception e) {
                        NetworkManager.LOGS.error("Can't execute get request", e);
                        errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                        throw new Exception();
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        List<Role> result = JsonHelper.jsonResponseToRoles(responseContent);

                        postRunnable.setResult(result);
                    } else {
                        postRunnable.setResult(null);
                    }

                    executeRunnable(context, postRunnable);
                } catch (Exception e) {
                    NetworkManager.LOGS.error("Can't get roles", e);
                    errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                    postRunnable.setResult(null);
                    executeRunnable(context, postRunnable);
                }
            }
        });
    }

    public static void getUserRoles(final Context context, final User user,
                                final DeliverResultRunnable<List<String>> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final int CODE_SUCCESS = 200;
                final String TAG = "getUserRoles";
                try {
                    if (!Utils.isNetworkConnected(context, LOGS)) {
                        errorDialog(context, Utils.DIALOG_NO_CONNECTION);
                        throw new Exception();
                    }

                    StringBuilder url = new StringBuilder()
                            .append(SERVER_URL).append("/api/v1/")
                            .append("users/")
                            .append(SosManager.getInstance(context).getEvent().getUser().getId())
                            .append("/roles/");

                    HttpGet httpGet = new HttpGet(url.toString());
                    addAuthHeader(context, httpGet);
                    addUserAgentHeader(context, httpGet);
                    httpGet.setHeader("Accept", "application/json");
                    httpGet.setHeader("Content-type", "application/json");

                    Log.i(TAG, "request: " + httpGet.getRequestLine().toString());

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpGet);
                    } catch (Exception e) {
                        NetworkManager.LOGS.error("Can't execute get request", e);
                        errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                        throw  new Exception();
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        String[] roles = responseContent.substring(1, responseContent.length()-1)
                                                        .split(",");
                        List<String> result = Arrays.asList(roles);
                        Log.i(TAG, "result: " + String.valueOf(result));
                        postRunnable.setResult(result);
                    } else {
                        postRunnable.setResult(null);
                    }

                    executeRunnable(context, postRunnable);
                } catch (Exception e) {
                    NetworkManager.LOGS.error("Can't get roles", e);
                    errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                    postRunnable.setResult(null);
                    executeRunnable(context, postRunnable);
                }
            }
        });
    }

    public static void setRoles(final Context context, User user, final List<Role> roles,
                                   final DeliverResultRunnable<Boolean> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final String TAG = "setRoles";
                try {
                    if (!Utils.isNetworkConnected(context, LOGS)) {
                        errorDialog(context, Utils.DIALOG_NO_CONNECTION);
                        throw new Exception();
                    }

                    HttpPost httpPost = new HttpPost(new StringBuilder().append(SERVER_URL)
                            .append("/api/v1/users/")
                            .append(SosManager.getInstance(context).getEvent().getUser().getId())
                            .append("/roles/")
                            .toString());

                    addAuthHeader(context, httpPost);
                    addUserAgentHeader(context, httpPost);
                    httpPost.setHeader("Accept", "application/json");
                    httpPost.setHeader("Content-type", "application/json");

                    JSONArray arr = new JSONArray();
                    for (Role role : roles)
                        if (role.checked)
                            arr.put(role.id);

                    JSONObject json = new JSONObject();
                    json.put("role_ids", arr);

                    StringEntity se = new StringEntity(json.toString());
                    httpPost.setEntity(se);

                    Log.i(TAG, "request: " + httpPost.getRequestLine().toString());
                    Log.i(TAG, "request entity: " + EntityUtils.toString(httpPost.getEntity()));

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpPost);
                    } catch (Exception e) {
                        NetworkManager.LOGS.error("Can't execute post request", e);
                        errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                        throw new Exception();
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        Map<String, Object> data = mapper.readValue(responseContent, Map.class);
                        Event event = JsonHelper.jsonToEvent(data);
                        data.clear();

                        postRunnable.setResult(true);
                    } else {
                        postRunnable.setResult(false);
                    }

                    executeRunnable(context, postRunnable);
                } catch (Exception e) {
                    NetworkManager.LOGS.error("Can't create roles", e);
                    errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                    postRunnable.setResult(false);
                    executeRunnable(context, postRunnable);
                }
            }
        });
    }

    public static void loginAtServer(final Context context, String login, String password,
                                     final DeliverResultRunnable<Boolean> postRunnable) {
        Map credentials = new HashMap();
        credentials.put("username", login);
        credentials.put("password", password);
        credentials.put("provider", SITE);

        loginAtServer(context, credentials, postRunnable);
    }

    public static void loginAtServer(final Context context, String token, int provider,
                                     final DeliverResultRunnable<Boolean> postRunnable) {
        Map credentials = new HashMap();
        credentials.put("access_token", token);
        credentials.put("provider", provider);

        loginAtServer(context, credentials, postRunnable);
    }

    public static void loginAtServer(final Context context, final Map credentials,
                                final DeliverResultRunnable<Boolean> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final int CODE_SUCCESS = 200;
                final String TAG = "loginAtServer";
                try {
                    if (!Utils.isNetworkConnected(context, LOGS)) {
                        errorDialog(context, Utils.DIALOG_NO_CONNECTION);
                        throw new Exception();
                    }

                    StringBuilder url = new StringBuilder(SERVER_URL)
                            .append("/api/v1/auth/login/");

                    JSONObject json = new JSONObject();
                    int provider = (Integer) credentials.get("provider");
                    switch (provider) {
                        case SITE:
                            url = url.append("site/");
                            json.put("username", credentials.get("username"));
                            json.put("password", credentials.get("password"));
                            break;
                        case FACEBOOK:
                            url = url.append("facebook/");
                            json.put("access_token", credentials.get("access_token"));
                            break;
                    }
                    StringEntity se = new StringEntity(json.toString());

                    HttpPost httpPost = new HttpPost(url.toString());
                    httpPost.setEntity(se);
                    addUserAgentHeader(context, httpPost);
                    httpPost.setHeader("Accept", "application/json");
                    httpPost.setHeader("Content-type", "application/json");


                    Log.i(TAG, "request: " + httpPost.getRequestLine().toString());
                    Log.i(TAG, "request entity: " + EntityUtils.toString(httpPost.getEntity()));

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpPost);
                    } catch (Exception e) {
                        errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                        throw new Exception();
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        Map<String, Object> data = mapper.readValue(responseContent, Map.class);
                        String api_username = String.valueOf(data.get("username"));
                        String api_key = String.valueOf(data.get("key"));

                        saveAuthData(context, api_username, api_key);

                        postRunnable.setResult(true);
                    } else {
                        postRunnable.setUnsuccessful(responseCode);
                    }
                    postRunnable.run();
                } catch (Exception e) {
                    errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                    postRunnable.setUnsuccessful(0);
                    executeRunnable(context, postRunnable);
                }
            }
        });
    }

    public static class DeliverResultRunnable<Result> implements Runnable {

        private Result result;
        private boolean success = true;
        private int errorCode = -1;

        public void setResult(Result result) {
            this.result = result;
        }

        @Override
        public final void run() {
            if (success) {
                deliver(result);
            } else {
                onError(errorCode);
            }
        }

        public void setUnsuccessful(int errorCode) {
            this.success = false;
            this.errorCode = errorCode;
        }

        public void deliver(Result result) {}

        public void onError(int errorCode) {}
    }

    private static void executeRunnable(Context context, Runnable runnable) {
        if (runnable == null)
            return;
        else if (context instanceof Activity) {
            Activity activity = (Activity) context;
            activity.runOnUiThread(runnable);
        } else
            runnable.run();
    }

    private static AbstractHttpMessage addAuthHeader(Context context, AbstractHttpMessage request) {
        request.addHeader(new BasicHeader("Authorization", new StringBuilder().append("ApiKey ")
                .append(Prefs.getApiUsername(context)).append(":")
                .append(Prefs.getApiKey(context)).toString()));
        return request;
    }

    private static void saveAuthData(Context context, String api_username, String api_key) {
        Prefs.putApiUsername(context, api_username);
        Prefs.putApiKey(context, api_key);
    }

    public static void addUserAgentHeader(Context context, AbstractHttpMessage request) {
        String systemUserAgent = System.getProperty("http.agent");
        String customUserAgent = "";
        try {
            customUserAgent = new StringBuilder().append("p2psafety/")
                    // add app version
                    .append(context.getPackageManager()
                            .getPackageInfo(context.getPackageName(), 0).versionName).append(" ")
                            // split davlik machine version
                    .append(systemUserAgent.substring(systemUserAgent.indexOf('(', 0),
                            systemUserAgent.length())).toString();
            request.addHeader(new BasicHeader("User-Agent", customUserAgent));
            Log.i("getUserAgent", customUserAgent);
        } catch (PackageManager.NameNotFoundException e) {
            request.addHeader(new BasicHeader("User-Agent", systemUserAgent));
        }
    }
}
