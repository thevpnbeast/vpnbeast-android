package com.vpnbeast.android.service;

import android.content.Intent;
import android.os.HandlerThread;
import android.util.Log;
import com.vpnbeast.android.BuildConfig;
import com.vpnbeast.android.model.enums.AppConstants;
import com.vpnbeast.android.util.ServiceUtil;
import org.json.JSONArray;
import org.json.JSONException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;

public class ServerService extends BaseService {

    private static final String TAG = "ServerService";

    @Override
    public void onCreate() {
        super.onCreate();
        handlerThread = new HandlerThread("ServerService.HandlerThread");
        handlerThread.start();
        serviceHandler = new ServiceHandler(handlerThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        serviceHandler.post(() -> {
            String action = Objects.requireNonNull(intent.getAction());
            if (action.equals(AppConstants.GET_ALL_SERVERS.toString())) {
                responseIntent = new Intent(AppConstants.GET_ALL_SERVERS.toString());
                getAllServers(intent.getStringExtra(AppConstants.ACCESS_TOKEN.toString()));
            }
            stopService();
        });
        return START_STICKY;
    }

    private void getAllServers(String authToken) {
        int responseCode = 500;
        try {
            URL url = new URL(BuildConfig.DOMAIN + BuildConfig.GET_ALL_SERVERS_URI);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(BuildConfig.GET_ALL_SERVERS_METHOD);
            conn.setRequestProperty("Content-Type", BuildConfig.CONTENT_TYPE);
            conn.setRequestProperty("Authorization", "Bearer " + authToken);

            InputStream inputStream;
            responseCode = conn.getResponseCode();

            if (responseCode == 200)
                inputStream = new BufferedInputStream(conn.getInputStream());
            else
                inputStream = new BufferedInputStream(conn.getErrorStream());

            JSONArray responseArray = new JSONArray(ServiceUtil.convertStreamToString(inputStream));
            responseIntent.putExtra(AppConstants.ALL_SERVERS.toString(), responseArray.toString());
            responseIntent.putExtra(AppConstants.TOKEN_EXPIRED.toString(), false);
        } catch (IOException | JSONException e) {
            Log.w(TAG, "getAllServers: ", e);
            if (responseCode == 401)
                responseIntent.putExtra(AppConstants.TOKEN_EXPIRED.toString(), true);
        }
    }

}