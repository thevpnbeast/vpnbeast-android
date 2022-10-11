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

public class ResetPasswordService extends BaseService {

    private static final String TAG = "ResetPasswordService";

    @Override
    public void onCreate() {
        super.onCreate();
        handlerThread = new HandlerThread("ResetPasswordService.HandlerThread");
        handlerThread.start();
        serviceHandler = new ServiceHandler(handlerThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        serviceHandler.post(() -> {
            String action = intent.getAction();
            if (action != null && action.equals(AppConstants.DO_RESET_PASSWORD.toString())) {
                responseIntent = new Intent(AppConstants.DO_RESET_PASSWORD.toString());
                doResetPassword(intent.getStringExtra(AppConstants.EMAIL.toString()),
                        intent.getIntExtra(AppConstants.VERIFICATION_CODE.toString(), 111111),
                        intent.getStringExtra(AppConstants.USER_PASS.toString()));
                stopService();
            }
        });
        return START_STICKY;
    }

    private void doResetPassword(final String email, final Integer verificationCode, final String password) {
        try {
            JSONObject requestObject = new JSONObject();
            requestObject.put("email", email);
            requestObject.put("password", password);
            requestObject.put("verificationCode", verificationCode);

            JSONObject responseObject = ServiceUtil.makeRequest(BuildConfig.DOMAIN + BuildConfig.DO_RESET_PASSWORD_URI,
                    BuildConfig.DO_RESET_PASSWORD_METHOD, null, requestObject);
            int responseCode = Objects.requireNonNull(responseObject).getInt(AppConstants.RESPONSE_CODE
                    .toString());

            if (responseCode == 200)
                responseIntent.putExtra(AppConstants.RESPONSE_MSG.toString(), "OK");
            else
                responseIntent.putExtra(AppConstants.RESPONSE_MSG.toString(),
                        responseObject.getString("errorMessage"));

            responseIntent.putExtra(AppConstants.RESPONSE_CODE.toString(), responseCode);
            responseIntent.putExtra(AppConstants.EMAIL.toString(), email);
        } catch (NullPointerException | JSONException e) {
            Log.e(TAG, "doResetPassword: ", e);
        }
    }

}