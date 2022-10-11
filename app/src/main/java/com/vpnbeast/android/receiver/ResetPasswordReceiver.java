package com.vpnbeast.android.receiver;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import com.vpnbeast.android.R;
import com.vpnbeast.android.model.enums.AppConstants;
import com.vpnbeast.android.util.ViewUtil;
import java.util.Objects;

public class ResetPasswordReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = Objects.requireNonNull(intent.getAction());
        final String msg = intent.getStringExtra(AppConstants.RESPONSE_MSG.toString());
        final int code = intent.getIntExtra(AppConstants.RESPONSE_CODE.toString(), 404);
        ((Activity) context).findViewById(R.id.progressBar).setVisibility(View.INVISIBLE);
        if (action.equals(AppConstants.DO_RESET_PASSWORD.toString())) {
            if (code == 200) {
                ViewUtil.showSingleButtonAlertDialog(context, context.getString(R.string.text_reset_password_success_title),
                        context.getString(R.string.text_reset_password_success_msg), null);
            } else {
                ViewUtil.showSingleButtonAlertDialog(context, context.getString(R.string.error), msg,
                        null);
            }
        }
    }

}