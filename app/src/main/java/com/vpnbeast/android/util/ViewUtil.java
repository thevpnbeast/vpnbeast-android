package com.vpnbeast.android.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import com.vpnbeast.android.R;
import com.vpnbeast.android.model.enums.AppConstants;
import com.vpnbeast.android.model.enums.EmailType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@SuppressLint("InflateParams")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ViewUtil {

    public static void showSingleButtonAlertDialog(Context context, String title, String errorMessage,
                                                   DialogInterface.OnClickListener onClickListener) {
        if (!((Activity) context).isFinishing()) {
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
            alertDialog.setTitle(title);
            alertDialog.setMessage(errorMessage);

            if (onClickListener == null)
                alertDialog.setPositiveButton(android.R.string.ok, (dialog, which) -> {

                });
            else
                alertDialog.setPositiveButton(android.R.string.ok, onClickListener);

            alertDialog.setCancelable(false);
            alertDialog.show();
        }
    }

    public static void showMultiButtonAlertDialog(Context context, String title, String errorMessage,
                                                  String positiveButtonText, DialogInterface.OnClickListener positiveButtonListener,
                                                  String negativeButtonText, DialogInterface.OnClickListener negativeButtonListener) {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
        alertDialog.setTitle(title);
        alertDialog.setMessage(errorMessage);

        if (positiveButtonListener == null)
            alertDialog.setPositiveButton(android.R.string.ok, (dialog, which) -> {

            });
        else
            alertDialog.setPositiveButton(positiveButtonText, positiveButtonListener);

        if (negativeButtonListener == null)
            alertDialog.setNegativeButton(negativeButtonText, (dialog, which) -> {

            });
        else
            alertDialog.setNegativeButton(negativeButtonText, negativeButtonListener);

        alertDialog.setCancelable(false);
        alertDialog.show();
    }

    public static void showProvidePasswordDialog(Context context, String title, String message,
                                                 Integer verificationCode, String email) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setCancelable(false);
        final View passwordDialog = ((Activity) context).getLayoutInflater().inflate(R.layout.dialog_password,
                null);
        builder.setView(passwordDialog);
        AlertDialog dialog = builder.create();
        dialog.show();

        EditText edtPassword = dialog.findViewById(R.id.edtPasswordPasswordDialog);
        EditText edtConfirmPassword = dialog.findViewById(R.id.edtConfirmPasswordPasswordDialog);

        Button btnConfirm = dialog.findViewById(R.id.btnConfirmPasswordDialog);
        btnConfirm.setOnClickListener(v -> {
            ((Activity) context).findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
            dialog.dismiss();
            if (!TextUtils.isEmpty(edtPassword.getText().toString())
                    && !TextUtils.isEmpty(edtConfirmPassword.getText().toString())
                    && edtPassword.getText().toString().equals(edtConfirmPassword.getText().toString())) {
                dialog.dismiss();
                ServiceUtil.startResetPasswordService(context, AppConstants.DO_RESET_PASSWORD.toString(),
                        email, verificationCode, edtPassword.getText().toString());
            } else if (!TextUtils.isEmpty(edtPassword.getText().toString())
                    && !TextUtils.isEmpty(edtConfirmPassword.getText().toString())
                    && !edtPassword.getText().toString().equals(edtConfirmPassword.getText().toString())) {
                ((Activity) context).findViewById(R.id.progressBar).setVisibility(View.INVISIBLE);
                ViewUtil.showSingleButtonAlertDialog(context, "Error",
                        "Passwords do not match, please check passwords!", (dialog1, which) ->
                                showProvidePasswordDialog(context, context.getString(R.string.text_provide_password_title),
                                context.getString(R.string.text_provide_password_msg), verificationCode, email));
            } else if (TextUtils.isEmpty(edtPassword.getText().toString())
                    || TextUtils.isEmpty(edtConfirmPassword.getText().toString())) {
                ((Activity) context).findViewById(R.id.progressBar).setVisibility(View.INVISIBLE);
                ViewUtil.showSingleButtonAlertDialog(context, "Error",
                        "All fields are mandatory!", (dialog1, which) ->
                                showProvidePasswordDialog(context, context.getString(R.string.text_provide_password_title),
                                        context.getString(R.string.text_provide_password_msg), verificationCode, email));
            }
        });

        Button btnClear = dialog.findViewById(R.id.btnClearPasswordDialog);
        btnClear.setOnClickListener(v -> {
            edtPassword.setText("");
            edtConfirmPassword.setText("");
        });
    }

    public static void showProvideEmailDialog(Context context, String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setCancelable(true);
        final View emailDialog = ((Activity) context).getLayoutInflater().inflate(R.layout.dialog_email,
                null);
        builder.setView(emailDialog);
        AlertDialog dialog = builder.create();
        dialog.show();

        EditText edtEmail = dialog.findViewById(R.id.edtEmailDialog);

        Button btnConfirm = dialog.findViewById(R.id.btnConfirmEmail);
        btnConfirm.setOnClickListener(v -> {
            ((Activity) context).findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
            dialog.dismiss();
            if (!TextUtils.isEmpty(edtEmail.getText().toString())) {
                dialog.dismiss();
                ServiceUtil.startVerificationService(context, AppConstants.DO_RESEND_VERIFICATION_CODE.toString(),
                        edtEmail.getText().toString(), null, EmailType.RESET_PASSWORD, true);
            } else {
                ((Activity) context).findViewById(R.id.progressBar).setVisibility(View.INVISIBLE);
                showSingleButtonAlertDialog(context, context.getString(R.string.error),
                        context.getString(R.string.err_null_email_msg),
                        (dialog1, which) -> showProvideEmailDialog(context, context.getString(R.string.text_provide_email_title),
                                context.getString(R.string.text_provide_email_msg)));
            }
        });

        Button btnClear = dialog.findViewById(R.id.btnClearEmail);
        btnClear.setOnClickListener(v -> edtEmail.setText(""));
    }

    public static void showVerificationDialog(Context context, String title, String message,
                                              String email, EmailType emailType,
                                              Boolean startResetPasswordService) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setCancelable(false);
        final View verificationDialog = ((Activity) context).getLayoutInflater().inflate(R.layout.dialog_verification,
                null);
        builder.setView(verificationDialog);
        AlertDialog dialog = builder.create();
        dialog.show();
        Button btnConfirm = dialog.findViewById(R.id.btnConfirmVerification);
        btnConfirm.setOnClickListener(v -> {
            ((Activity) context).findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
            EditText edtCode = dialog.findViewById(R.id.edtCodeVerification);
            dialog.dismiss();
            if (!TextUtils.isEmpty(edtCode.getText().toString())) {
                final int verificationCode = Integer.parseInt(edtCode.getText().toString());
                ServiceUtil.startVerificationService(context, AppConstants.DO_VERIFY.toString(),
                        email, verificationCode, emailType, startResetPasswordService);
            } else {
                ((Activity) context).findViewById(R.id.progressBar).setVisibility(View.INVISIBLE);
                showSingleButtonAlertDialog(context, context.getString(R.string.error),
                        context.getString(R.string.err_null_verification_code_msg),
                        (dialog1, which) -> showVerificationDialog(context, context.getString(R.string.text_verify_email_title),
                                String.format(context.getString(R.string.text_verify_email_msg), email),
                                email, emailType, startResetPasswordService));
            }
        });
        Button btnResend = dialog.findViewById(R.id.btnResendVerification);
        btnResend.setOnClickListener(v -> {
            dialog.dismiss();
            ((Activity) context).findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
            ServiceUtil.startVerificationService(context, AppConstants.DO_RESEND_VERIFICATION_CODE.toString(),
                    email, null, emailType, startResetPasswordService);
        });
    }

}