package com.vpnbeast.android.receiver;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.vpnbeast.android.R;
import com.vpnbeast.android.activity.MainActivity;
import com.vpnbeast.android.model.entity.User;
import com.vpnbeast.android.model.enums.AppConstants;
import com.vpnbeast.android.util.PreferencesUtil;
import com.vpnbeast.android.util.ViewUtil;
import java.lang.ref.WeakReference;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Objects;

public class RefreshReceiver extends BroadcastReceiver {

    private static final String TAG = "RefreshReceiver";

    private WeakReference<Activity> activityReference;

    public RefreshReceiver(Activity context) {
        activityReference = new WeakReference<>(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            Activity activity = activityReference.get();
            final String action = Objects.requireNonNull(intent.getAction());
            final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",
                    Locale.getDefault());
            if (action.equals(AppConstants.DO_REFRESH.toString())) {
                User user = PreferencesUtil.getUser(context.getApplicationContext(), AppConstants.USER.toString());
                int code = intent.getIntExtra(AppConstants.RESPONSE_CODE.toString(), 404);
                if (code == 200) {
                    user.setAccessToken(intent.getStringExtra(AppConstants.ACCESS_TOKEN.toString()));
                    user.setRefreshToken(intent.getStringExtra(AppConstants.REFRESH_TOKEN.toString()));
                    user.setAccessTokenExpiresAt(format.parse(Objects.requireNonNull(intent
                            .getStringExtra(AppConstants.ACCESS_TOKEN_EXPIRES_AT.toString()))));
                    user.setRefreshTokenExpiresAt(format.parse(Objects.requireNonNull(intent
                            .getStringExtra(AppConstants.REFRESH_TOKEN_EXPIRES_AT.toString()))));
                    PreferencesUtil.storeUser(context.getApplicationContext(), user, AppConstants.USER.toString());
                } else if (code == 401) {
                    user.setTokensExpired(true);
                    user.setAccessToken(null);
                    user.setAccessTokenExpiresAt(null);
                    user.setRefreshToken(null);
                    user.setRefreshTokenExpiresAt(null);
                    PreferencesUtil.storeUser(context.getApplicationContext(), user, AppConstants.USER.toString());
                    ViewUtil.showSingleButtonAlertDialog(context, context.getString(R.string.error),
                            context.getString(R.string.err_both_tokens_expired_msg), (dialog, which) -> {
                        if (activity instanceof MainActivity) {
                            activity.finish();
                        }
                    });
                }
            }
        } catch (ParseException e) {
            Log.e(TAG, "onReceive: ", e);
        }
    }
}
