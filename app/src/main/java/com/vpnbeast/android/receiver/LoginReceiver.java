package com.vpnbeast.android.receiver;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import com.vpnbeast.android.R;
import com.vpnbeast.android.activity.MainActivity;
import com.vpnbeast.android.model.entity.User;
import com.vpnbeast.android.model.enums.AppConstants;
import com.vpnbeast.android.model.enums.EmailType;
import com.vpnbeast.android.util.PreferencesUtil;
import com.vpnbeast.android.util.ServiceUtil;
import com.vpnbeast.android.util.ViewUtil;
import java.util.Objects;

public class LoginReceiver extends BroadcastReceiver {

    private static final String TAG = "LoginReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        ((Activity) context).findViewById(R.id.progressBar).setVisibility(View.INVISIBLE);
        final String action = Objects.requireNonNull(intent.getAction());
        if (action.equals(AppConstants.DO_LOGIN.toString())) {
            final String msg = intent.getStringExtra(AppConstants.RESPONSE_MSG.toString());
            int code = intent.getIntExtra(AppConstants.RESPONSE_CODE.toString(), 404);
            if (code == 200) {
                final User user = intent.getParcelableExtra(AppConstants.USER.toString());
                PreferencesUtil.storeUser(context.getApplicationContext(), user, AppConstants.USER.toString());
                if (!Objects.requireNonNull(user).isEmailVerified()) {
                    ((Activity) context).findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
                    ServiceUtil.startVerificationService(context, AppConstants.DO_RESEND_VERIFICATION_CODE.toString(),
                            user.getEmail(), null, EmailType.VALIDATE_EMAIL, false);
                } else {
                    Intent mainIntent = new Intent(context, MainActivity.class);
                    context.startActivity(mainIntent);
                }
            } else {
                ViewUtil.showSingleButtonAlertDialog(context, context.getString(R.string.error),
                        msg, null);
            }
        }
    }

}