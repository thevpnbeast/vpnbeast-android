package com.vpnbeast.android.service;

import android.content.Intent;
import android.os.HandlerThread;
import android.util.Log;
import com.vpnbeast.android.BuildConfig;
import com.vpnbeast.android.model.enums.AppConstants;
import com.vpnbeast.android.model.enums.EmailType;
import com.vpnbeast.android.util.ServiceUtil;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Objects;

public class VerificationService extends BaseService {

    private static final String TAG = "VerificationService";

    @Override
    public void onCreate() {
        super.onCreate();
        handlerThread = new HandlerThread("VerificationService.HandlerThread");
        handlerThread.start();
        serviceHandler = new ServiceHandler(handlerThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        serviceHandler.post(() -> {
            String action = intent.getAction();
            String email = intent.getStringExtra(AppConstants.EMAIL.toString());
            boolean doResetPassword = intent.getBooleanExtra(AppConstants.DO_RESET_PASSWORD.toString(),
                    false);
            EmailType emailType = (EmailType) intent.getSerializableExtra(AppConstants.EMAIL_TYPE.toString());
            if (action != null && action.equals(AppConstants.DO_VERIFY.toString())) {
                responseIntent = new Intent(AppConstants.DO_VERIFY.toString());
                doVerify(email, intent.getIntExtra(AppConstants.VERIFICATION_CODE.toString(),
                        111111), emailType, doResetPassword);
                stopService();
            } else if (action != null && action.equals(AppConstants.DO_RESEND_VERIFICATION_CODE.toString())) {
                responseIntent = new Intent(AppConstants.DO_RESEND_VERIFICATION_CODE.toString());
                doResendVerificationCode(intent.getStringExtra(AppConstants.EMAIL.toString()),
                        emailType, doResetPassword);
                stopService();
            }
        });
        return START_STICKY;
    }

    private void doResendVerificationCode(String email, EmailType emailType, boolean doResetPassword) {
        try {
            JSONObject requestObject = new JSONObject();
            requestObject.put("email", email);
            requestObject.put("emailType", emailType);

            JSONObject responseObject = ServiceUtil.makeRequest(BuildConfig.DOMAIN + BuildConfig.DO_RESEND_VERIFICATION_CODE_URI,
                    BuildConfig.DO_RESEND_VERIFICATION_CODE_METHOD, null, requestObject);
            int responseCode = Objects.requireNonNull(responseObject).getInt(AppConstants.RESPONSE_CODE
                    .toString());

            if (responseCode == 200)
                responseIntent.putExtra(AppConstants.RESPONSE_MSG.toString(), "OK");
            else
                responseIntent.putExtra(AppConstants.RESPONSE_MSG.toString(),
                        responseObject.getString("errorMessage"));

            responseIntent.putExtra(AppConstants.RESPONSE_CODE.toString(), responseCode);
            responseIntent.putExtra(AppConstants.EMAIL.toString(), email);
            responseIntent.putExtra(AppConstants.EMAIL_TYPE.toString(), emailType);
            responseIntent.putExtra(AppConstants.DO_RESET_PASSWORD.toString(), doResetPassword);
        } catch (NullPointerException | JSONException e) {
            Log.e(TAG, "doVerify: ", e);
        }
    }

    private void doVerify(String email, Integer verificationCode, EmailType emailType,
                          boolean startResetPasswordService) {
        try {
            JSONObject requestObject = new JSONObject();
            requestObject.put("email", email);
            requestObject.put("verificationCode", verificationCode);

            JSONObject responseObject = ServiceUtil.makeRequest(BuildConfig.DOMAIN + BuildConfig.DO_VERIFY_URI,
                    BuildConfig.DO_VERIFY_METHOD, null, requestObject);
            int responseCode = Objects.requireNonNull(responseObject).getInt(AppConstants.RESPONSE_CODE
                    .toString());

            if (responseCode == 200)
                responseIntent.putExtra(AppConstants.RESPONSE_MSG.toString(), "OK");
            else
                responseIntent.putExtra(AppConstants.RESPONSE_MSG.toString(),
                        responseObject.getString("errorMessage"));

            responseIntent.putExtra(AppConstants.RESPONSE_CODE.toString(), responseCode);
            responseIntent.putExtra(AppConstants.EMAIL.toString(), email);
            responseIntent.putExtra(AppConstants.EMAIL_TYPE.toString(), emailType);
            responseIntent.putExtra(AppConstants.VERIFICATION_CODE.toString(), verificationCode);
            responseIntent.putExtra(AppConstants.DO_RESET_PASSWORD.toString(), startResetPasswordService);
        } catch (NullPointerException | JSONException e) {
            Log.e(TAG, "doVerify: ", e);
        }
    }

}