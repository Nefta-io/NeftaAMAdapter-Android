package com.nefta.sdk;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executor;

import javax.net.ssl.HttpsURLConnection;

public class RestHelper {

    private static final int _defaultConnectTimeoutMs = 10 * 1000;
    private static final int _defaultReadTimeoutMs = 20 * 1000;

    public static int _connectTimeoutMs = _defaultConnectTimeoutMs;
    public static int _readTimeoutMs = _defaultReadTimeoutMs;

    public interface IOnResponse {
        void Invoke(String responseReader, Placement placement, int responseCode);
    }

    private final Context _context;
    private final Executor _executor;
    private final ConnectivityManager _connectivityManager;

    public RestHelper(Context context, Executor executor) {
        _context = context;
        _executor = executor;
        _connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public String IsNetworkAvailable() {
        if (Settings.System.getInt(_context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0) {
            return "airplane";
        }
        if (_connectivityManager != null) {
            NetworkInfo activeNetworkInfo = _connectivityManager.getActiveNetworkInfo();
            if (activeNetworkInfo == null) {
                return "no network";
            }
            if (!activeNetworkInfo.isConnectedOrConnecting()) {
                return "network not available";
            }
        }
        return null;
    }

    public void MakeGetRequest(String url) {
        _executor.execute(new Runnable() {
            public void run() {
                HttpURLConnection connection = null;
                try {
                    URL request = new URL(url);
                    connection = (HttpURLConnection) request.openConnection();
                    connection.setRequestMethod("GET");
                    SetHeaders(connection);

                    int responseCode = connection.getResponseCode();
                    NeftaPlugin.NLogI("GET success "+ url +": "+ responseCode);
                } catch (Exception e) {
                    NeftaPlugin.NLogW("GET error "+ url +": "+ e.getMessage());
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    public void LoadVastWrapperTag(String url, Placement placement, IOnResponse onResponse) {
        HttpURLConnection connection = null;
        String responseString = null;
        int responseCode = 0;
        String error = null;
        try {
            URL request = new URL(url);
            connection = (HttpURLConnection) request.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(_connectTimeoutMs);
            connection.setReadTimeout(_readTimeoutMs);

            NeftaPlugin.NLogD("GET "+ url);

            responseCode = connection.getResponseCode();
            if (responseCode == HttpsURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    responseBuilder.append(line);
                }
                responseString = responseBuilder.toString();
                bufferedReader.close();
                inputStream.close();
            } else {
                error = connection.getResponseMessage();
            }
        } catch (IOException e) {
            error = e.getMessage();
        }
        if (connection != null) {
            connection.disconnect();
        }

        if (error == null) {
            NeftaPlugin.NLogD("GET success "+ responseCode +": "+ responseString);
        } else {
            NeftaPlugin.NLogW("GET error "+ url + ": "+ responseCode +": "+ error);
        }

        if (onResponse != null) {
            onResponse.Invoke(responseString, placement, responseCode);
        }
    }

    public void MakePostRequest(String url, byte[] body, IOnResponse onResponse) {
        int responseCode = 0;
        String error;
        HttpURLConnection connection = null;
        try {
            URL request = new URL(url);
            connection = (HttpURLConnection) request.openConnection();
            connection.setRequestMethod("POST");
            SetHeaders(connection);
            connection.setDoOutput(true);

            OutputStream requestStream = connection.getOutputStream();

            requestStream.write(body, 0, body.length);

            responseCode = connection.getResponseCode();
            error = connection.getResponseMessage();
        } catch (IOException e) {
            error = e.getMessage();
        }
        if (connection != null) {
            connection.disconnect();
        }

        if (responseCode == HttpsURLConnection.HTTP_OK) {
            NeftaPlugin.NLogD("POST success "+ url +": "+ responseCode +": "+ error);
        } else {
            NeftaPlugin.NLogW("POST error "+ url +": "+ responseCode +": "+ error);
        }

        onResponse.Invoke(null, null, responseCode);
    }

    public void MakePostRequest(String url, JSONObject requestObject, Placement placement, IOnResponse onResponse) {
        _executor.execute(new Runnable() {
            public void run() {
                HttpURLConnection connection = null;
                String responseString = null;
                int responseCode = 0;
                String error = null;
                try {
                    URL request = new URL(url);
                    connection = (HttpURLConnection) request.openConnection();
                    connection.setRequestMethod("POST");
                    SetHeaders(connection);
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(_connectTimeoutMs);
                    connection.setReadTimeout(_readTimeoutMs);

                    OutputStream requestStream = connection.getOutputStream();

                    String requestBody = requestObject.toString();
                    NeftaPlugin.NLogD("POST "+ url +": "+ requestBody);

                    final byte[] requestRaw = requestBody.getBytes("UTF-8");
                    requestStream.write(requestRaw, 0, requestRaw.length);

                    responseCode = connection.getResponseCode();
                    if (responseCode == HttpsURLConnection.HTTP_OK) {
                        InputStream inputStream = connection.getInputStream();
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                        StringBuilder responseBuilder = new StringBuilder();
                        String line;
                        while ((line = bufferedReader.readLine()) != null) {
                            responseBuilder.append(line);
                        }
                        responseString = responseBuilder.toString();
                        bufferedReader.close();
                        inputStream.close();
                    } else {
                        error = connection.getResponseMessage();
                    }
                } catch (IOException e) {
                    error = e.getMessage();
                }
                if (connection != null) {
                    connection.disconnect();
                }

                if (error == null) {
                    NeftaPlugin.NLogD("POST success "+ responseCode +": "+ responseString);
                } else {
                    NeftaPlugin.NLogW("POST error "+ url + ": "+ responseCode +": "+ error);
                }

                if (onResponse != null) {
                    onResponse.Invoke(responseString, placement, responseCode);
                }
            }
        });
    }

    private void SetHeaders(HttpURLConnection connection) {
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("nefta-sdk-version", NeftaPlugin.Version);
        connection.setRequestProperty("nefta-sdk-bundle", NeftaPlugin._instance._info._bundleId);
        connection.setRequestProperty("nefta-sdk-appid", NeftaPlugin._instance._info._appId);
        connection.setRequestProperty("nefta-sdk-platform", "Android");
    }
}
