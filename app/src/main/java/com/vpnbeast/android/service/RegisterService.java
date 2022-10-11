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

public class RegisterService extends BaseService {

    private static final String TAG = "RegisterService";

    @Override
    public void onCreate() {
        super.onCreate();
        handlerThread = new HandlerThread("RegisterService.HandlerThread");
        handlerThread.start();
        serviceHandler = new ServiceHandler(handlerThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        serviceHandler.post(() -> {
            String action = Objects.requireNonNull(intent.getAction());
            if (action.equals(AppConstants.DO_REGISTER.toString())) {
                Log.i(TAG, "onStartCommand: inside");
                responseIntent = new Intent(AppConstants.DO_REGISTER.toString());
                doRegister(intent.getStringExtra(AppConstants.USER_NAME.toString()),
                        intent.getStringExtra(AppConstants.USER_PASS.toString()),
                        intent.getStringExtra(AppConstants.EMAIL.toString()));
                stopService();
            }
        });
        return START_STICKY;
    }

    private void doRegister(String userName, String password, String email) {
        try {
            JSONObject requestObject = new JSONObject();
            requestObject.put("userName", userName);
            requestObject.put("password", password);
            requestObject.put("email", email);

            JSONObject responseObject = ServiceUtil.makeRequest(BuildConfig.DOMAIN + BuildConfig.DO_REGISTER_URI,
                    BuildConfig.DO_REGISTER_METHOD, null, requestObject);
            final int responseCode = Objects.requireNonNull(responseObject).getInt(AppConstants.RESPONSE_CODE
                    .toString());
            responseIntent.putExtra(AppConstants.USER_NAME.toString(), userName);
            responseIntent.putExtra(AppConstants.RESPONSE_CODE.toString(), responseCode);
            responseIntent.putExtra(AppConstants.EMAIL.toString(), email);
            if (responseCode == 201)
                responseIntent.putExtra(AppConstants.RESPONSE_MSG.toString(), "OK");
            else
                responseIntent.putExtra(AppConstants.RESPONSE_MSG.toString(),
                        responseObject.getString("errorMessage"));
        } catch (NullPointerException | JSONException e) {
            Log.e(TAG, "doRegister: ", e);
        }
    }

}