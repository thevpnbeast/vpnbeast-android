package com.vpnbeast.android.receiver;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import com.vpnbeast.android.R;
import com.vpnbeast.android.activity.LoginActivity;
import com.vpnbeast.android.model.enums.AppConstants;
import com.vpnbeast.android.model.enums.EmailType;
import com.vpnbeast.android.util.ViewUtil;
import java.util.Objects;

public class VerificationReceiver extends BroadcastReceiver {

    // TODO: Reduce cognitive complexity
    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = Objects.requireNonNull(intent.getAction());
        final String msg = intent.getStringExtra(AppConstants.RESPONSE_MSG.toString());
        final String email = intent.getStringExtra(AppConstants.EMAIL.toString());
        final int responseCode = intent.getIntExtra(AppConstants.RESPONSE_CODE.toString(), 404);
        final boolean doResetPassword = intent.getBooleanExtra(AppConstants.DO_RESET_PASSWORD.toString(), false);
        final int verificationCode = intent.getIntExtra(AppConstants.VERIFICATION_CODE.toString(), 111111);
        final EmailType emailType = (EmailType) intent.getSerializableExtra(AppConstants.EMAIL_TYPE.toString());
        ((Activity) context).findViewById(R.id.progressBar).setVisibility(View.INVISIBLE);
        if (action.equals(AppConstants.DO_VERIFY.toString())) {
            if (responseCode == 200) {
                if (doResetPassword)
                    ViewUtil.showSingleButtonAlertDialog(context, context.getString(R.string.text_email_verification_success_title),
                            String.format(context.getString(R.string.text_email_verification_success_msg),
                                    email), (dialog, which) -> ViewUtil.showProvidePasswordDialog(context,
                                    context.getString(R.string.text_provide_password_title),
                                    context.getString(R.string.text_provide_password_msg), verificationCode, email));
                else
                    ViewUtil.showSingleButtonAlertDialog(context, context.getString(R.string.text_email_verification_success_title),
                            String.format(context.getString(R.string.text_email_verification_success_msg), email),
                            (dialog, which) -> {
                                if (!(context instanceof LoginActivity))
                                    ((Activity) context).finish();
                            });
            } else {
                ViewUtil.showSingleButtonAlertDialog(context, "Error", msg, (dialog, which) ->
                        ViewUtil.showVerificationDialog(context, context.getString(R.string.text_verify_email_title),
                                String.format(context.getString(R.string.text_verify_email_msg), email),
                                email, emailType, doResetPassword));
            }
        } else if (action.equals(AppConstants.DO_RESEND_VERIFICATION_CODE.toString())) {
            if (responseCode == 200) {
                ViewUtil.showSingleButtonAlertDialog(context, context.getString(R.string.text_resend_verification_code_title),
                        String.format(context.getString(R.string.text_resend_verification_code_msg), email),
                        (dialog, which) -> ViewUtil.showVerificationDialog(context,
                                context.getString(R.string.text_verify_email_title),
                                String.format(context.getString(R.string.text_verify_email_msg), email),
                                email, emailType, doResetPassword));
            } else {
                if (doResetPassword)
                    ViewUtil.showSingleButtonAlertDialog(context, context.getString(R.string.error), msg,
                            (dialog, which) -> ViewUtil.showProvideEmailDialog(context,
                                    context.getString(R.string.text_provide_email_title),
                                    context.getString(R.string.text_provide_email_msg)));
                else
                    ViewUtil.showSingleButtonAlertDialog(context, context.getString(R.string.error),
                            context.getString(R.string.err_unknown_msg), (dialog, which) ->
                                    ViewUtil.showVerificationDialog(context, context.getString(R.string.text_verify_email_title),
                                            String.format(context.getString(R.string.text_verify_email_msg), email),
                                            email, emailType, false));
            }
        }
    }

}