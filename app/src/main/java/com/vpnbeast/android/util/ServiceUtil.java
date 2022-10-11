package com.vpnbeast.android.util;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.vpnbeast.android.BuildConfig;
import com.vpnbeast.android.model.enums.AppConstants;
import com.vpnbeast.android.model.enums.EmailType;
import com.vpnbeast.android.service.ResetPasswordService;
import com.vpnbeast.android.service.VerificationService;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ServiceUtil {

    private static final String TAG = "ServiceUtil";

    public static JSONObject makeRequest(String urlString, String method, String token,
                                         JSONObject requestObject) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);

            if (token != null)
                conn.setRequestProperty("Authorization", "Bearer " + token);

            if (!method.equals("GET")) {
                conn.setDoOutput(true);
                conn.setDoInput(true);
                conn.setRequestProperty("Content-Type", BuildConfig.CONTENT_TYPE);
                DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                os.writeBytes(requestObject.toString());

                os.flush();
                os.close();
            }

            // read the response
            InputStream inputStream;
            JSONObject responseObject;
            int responseCode = conn.getResponseCode();
            if (responseCode == 200 || responseCode == 201)
                inputStream = new BufferedInputStream(conn.getInputStream());
            else
                inputStream = new BufferedInputStream(conn.getErrorStream());
            responseObject = new JSONObject(convertStreamToString(inputStream));
            responseObject.put(AppConstants.RESPONSE_CODE.toString(), responseCode);
            return responseObject;
        } catch (IOException | JSONException e) {
            Log.e(TAG, "makeRequest: ", e);
        }
        return null;
    }

    public static String convertStreamToString(InputStream stream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            Log.e(TAG, "convertStreamToString: ", e);
        } finally {
            try {
                stream.close();
            } catch (IOException e) {
                Log.e(TAG, "convertStreamToString: ", e);
            }
        }
        return sb.toString();
    }

    public static void startVerificationService(Context context, String action, String email,
                                                Integer verificationCode, EmailType emailType,
                                                Boolean doResetPassword) {
        Intent intent = new Intent(context, VerificationService.class);
        intent.setAction(action);
        intent.putExtra(AppConstants.EMAIL.toString(), email);
        intent.putExtra(AppConstants.VERIFICATION_CODE.toString(), verificationCode);
        intent.putExtra(AppConstants.EMAIL_TYPE.toString(), emailType);
        intent.putExtra(AppConstants.DO_RESET_PASSWORD.toString(), doResetPassword);
        context.startService(intent);
    }

    static void startResetPasswordService(Context context, String action, String email, Integer verificationCode,
                                          String password) {
        Intent intent = new Intent(context, ResetPasswordService.class);
        intent.setAction(action);
        intent.putExtra(AppConstants.EMAIL.toString(), email);
        intent.putExtra(AppConstants.VERIFICATION_CODE.toString(), verificationCode);
        intent.putExtra(AppConstants.USER_PASS.toString(), password);
        context.startService(intent);
    }

}