package com.vpnbeast.android.service;

import android.content.Intent;
import android.os.HandlerThread;
import android.util.Log;
import com.vpnbeast.android.BuildConfig;
import com.vpnbeast.android.model.entity.User;
import com.vpnbeast.android.model.enums.AppConstants;
import com.vpnbeast.android.util.ServiceUtil;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Objects;

public class LoginService extends BaseService {

    private static final String TAG = "LoginService";

    @Override
    public void onCreate() {
        super.onCreate();
        handlerThread = new HandlerThread("LoginService.HandlerThread");
        handlerThread.start();
        serviceHandler = new ServiceHandler(handlerThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        serviceHandler.post(() -> {
            String action = intent.getAction();
            if (action != null && action.equals(AppConstants.DO_LOGIN.toString())) {
                responseIntent = new Intent(AppConstants.DO_LOGIN.toString());
                doLogin(intent.getStringExtra(AppConstants.USER_NAME.toString()),
                        intent.getStringExtra(AppConstants.USER_PASS.toString()));
                stopService();
            }
        });
        return START_STICKY;
    }

    private void doLogin(final String userName, final String password) {
        try {
            JSONObject requestObject = new JSONObject();
            requestObject.put("userName", userName);
            requestObject.put("password", password);

            JSONObject responseObject = ServiceUtil.makeRequest(BuildConfig.DOMAIN + BuildConfig.DO_LOGIN_URI,
                    BuildConfig.DO_LOGIN_METHOD, null, requestObject);
            int responseCode = Objects.requireNonNull(responseObject).getInt(AppConstants.RESPONSE_CODE
                    .toString());
            if (responseCode == 200) {
                final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",
                        Locale.getDefault());
                final User user = new User(responseObject.getString("uuid"), responseObject.getLong("id"),
                        responseObject.getString("userName"), responseObject.getString("email"),
                        responseObject.getBoolean("enabled"), responseObject.getBoolean("emailVerified"),
                        responseObject.getString("accessToken"), responseObject.getString("refreshToken"),
                        format.parse(responseObject.getString("accessTokenExpiresAt")),
                        format.parse(responseObject.getString("refreshTokenExpiresAt")), false);
                responseIntent.putExtra(AppConstants.RESPONSE_CODE.toString(), responseCode);
                responseIntent.putExtra(AppConstants.RESPONSE_MSG.toString(), "OK");
                responseIntent.putExtra(AppConstants.USER.toString(), user);
            } else {
                responseIntent.putExtra(AppConstants.USER_NAME.toString(), userName);
                responseIntent.putExtra(AppConstants.RESPONSE_CODE.toString(), responseCode);
                responseIntent.putExtra(AppConstants.RESPONSE_MSG.toString(),
                        responseObject.getString("errorMessage"));
            }
        } catch (NullPointerException | JSONException | ParseException e) {
            Log.e(TAG, "doLogin: ", e);
        }
    }

}