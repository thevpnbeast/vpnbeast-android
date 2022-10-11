package com.vpnbeast.android.service;

import android.content.Intent;
import android.os.HandlerThread;
import android.util.Log;
import com.vpnbeast.android.BuildConfig;
import com.vpnbeast.android.model.enums.AppConstants;
import com.vpnbeast.android.util.ServiceUtil;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Objects;

public class RefreshService extends BaseService {

    private static final String TAG = "RefreshService";

    @Override
    public void onCreate() {
        super.onCreate();
        handlerThread = new HandlerThread("RefreshService.HandlerThread");
        handlerThread.start();
        serviceHandler = new ServiceHandler(handlerThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        serviceHandler.post(() -> {
            String action = intent.getAction();
            if (action != null && action.equals(AppConstants.DO_REFRESH.toString())) {
                responseIntent = new Intent(AppConstants.DO_REFRESH.toString());
                doRefresh(intent.getStringExtra(AppConstants.REFRESH_TOKEN.toString()), intent
                        .getBooleanExtra(AppConstants.START_SERVER_SERVICE.toString(), false));
                stopService();
            }
        });
        return START_STICKY;
    }

    private void doRefresh(final String refreshToken, final boolean shouldStartServerService) {
        try {
            JSONObject responseObject = ServiceUtil.makeRequest(BuildConfig.DOMAIN + BuildConfig.DO_REFRESH_URI,
                    BuildConfig.DO_REFRESH_METHOD, refreshToken, null);
            int responseCode = Objects.requireNonNull(responseObject).getInt(AppConstants.RESPONSE_CODE
                    .toString());
            if (responseCode == 200) {
                responseIntent.putExtra(AppConstants.RESPONSE_CODE.toString(), responseCode);
                responseIntent.putExtra(AppConstants.RESPONSE_MSG.toString(), "OK");
                responseIntent.putExtra(AppConstants.ACCESS_TOKEN.toString(),
                        responseObject.getString("accessToken"));
                responseIntent.putExtra(AppConstants.REFRESH_TOKEN.toString(),
                        responseObject.getString("refreshToken"));
                responseIntent.putExtra(AppConstants.ACCESS_TOKEN_EXPIRES_AT.toString(),
                        responseObject.getString("accessTokenExpiresAt"));
                responseIntent.putExtra(AppConstants.REFRESH_TOKEN_EXPIRES_AT.toString(),
                        responseObject.getString("refreshTokenExpiresAt"));
                if (shouldStartServerService) {
                    Log.i(TAG, "doRefresh: starting ServerService because boolean startServerService is true!");
                    startServerService(responseObject.getString("accessToken"));
                }
            } else {
                responseIntent.putExtra(AppConstants.RESPONSE_CODE.toString(), responseCode);
                responseIntent.putExtra(AppConstants.RESPONSE_MSG.toString(),
                        responseObject.getString("errorMessage"));
            }
        } catch (JSONException e) {
            Log.e(TAG, "doRefresh: catched Exception!", e);
        }
    }

    private void startServerService(String accessToken) {
        Intent intent = new Intent(this, ServerService.class);
        intent.setAction(AppConstants.GET_ALL_SERVERS.toString());
        intent.putExtra(AppConstants.ACCESS_TOKEN.toString(), accessToken);
        startService(intent);
    }

}