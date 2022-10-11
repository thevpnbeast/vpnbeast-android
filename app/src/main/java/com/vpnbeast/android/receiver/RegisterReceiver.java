package com.vpnbeast.android.receiver;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import com.vpnbeast.android.R;
import com.vpnbeast.android.model.enums.AppConstants;
import com.vpnbeast.android.model.enums.EmailType;
import com.vpnbeast.android.util.ViewUtil;
import java.util.Objects;

public class RegisterReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        ((Activity) context).findViewById(R.id.progressBar).setVisibility(View.INVISIBLE);
        final String action = Objects.requireNonNull(intent.getAction());
        if (action.equals(AppConstants.DO_REGISTER.toString()) && intent.getStringExtra(AppConstants
                .USER_NAME.toString()) != null) {

            final String msg = intent.getStringExtra(AppConstants.RESPONSE_MSG.toString());
            final String email = intent.getStringExtra(AppConstants.EMAIL.toString());
            int code = intent.getIntExtra(AppConstants.RESPONSE_CODE.toString(), 400);
            if (code == 201) {
                ViewUtil.showVerificationDialog(context, context.getString(R.string.text_verify_email_title),
                        String.format(context.getString(R.string.text_verify_email_msg), email), email,
                        EmailType.VALIDATE_EMAIL, false);
            } else {
                ViewUtil.showSingleButtonAlertDialog(context, context.getString(R.string.error), msg,
                        null);
            }
        } else if (action.equals(AppConstants.DO_REGISTER.toString()) && intent.getStringExtra(AppConstants
                .USER_NAME.toString()) == null) {
            ViewUtil.showSingleButtonAlertDialog(context, context.getResources().getString(R.string.error),
                    context.getResources().getString(R.string.err_unknown_msg), null);
        }
    }

}