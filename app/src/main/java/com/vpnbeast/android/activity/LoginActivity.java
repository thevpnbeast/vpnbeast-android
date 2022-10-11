package com.vpnbeast.android.activity;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.vpnbeast.android.R;
import com.vpnbeast.android.model.enums.AppConstants;
import com.vpnbeast.android.receiver.LoginReceiver;
import com.vpnbeast.android.receiver.ResetPasswordReceiver;
import com.vpnbeast.android.receiver.VerificationReceiver;
import com.vpnbeast.android.service.LoginService;
import com.vpnbeast.android.util.NetworkUtil;
import com.vpnbeast.android.util.PermissionUtil;
import com.vpnbeast.android.util.PreferencesUtil;
import com.vpnbeast.android.util.ViewUtil;

public class LoginActivity extends AppCompatActivity {

    public static final int LOCATION_PERMISSION_REQUEST = 99;

    private EditText edtUsername;
    private EditText edtPassword;
    private CheckBox chkRemember;
    private ProgressBar progressBar;
    private LoginReceiver loginReceiver;
    private IntentFilter loginIntentFilter;
    private VerificationReceiver verificationReceiver;
    private IntentFilter verificationIntentFilter;
    private IntentFilter resendVerificationCodeIntentFilter;
    private ResetPasswordReceiver resetPasswordReceiver;
    private IntentFilter resetPasswordIntentFilter;
    private SharedPreferences sharedPreferences;
    private String[] requiredPermissions = new String[] {};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        init();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceivers();
        progressBar.setVisibility(View.INVISIBLE);
        if (sharedPreferences.getBoolean(AppConstants.USER_REMEMBER.toString(), false)) {
            edtUsername.setText(sharedPreferences.getString(AppConstants.USER_NAME.toString(), null));
            edtPassword.setText(sharedPreferences.getString(AppConstants.USER_PASS.toString(), null));
            chkRemember.setChecked(sharedPreferences.getBoolean(AppConstants.USER_REMEMBER.toString(), false));
        }
    }

    @Override
    protected void onPause() {
        unregisterReceivers();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        ViewUtil.showMultiButtonAlertDialog(this, getString(R.string.text_exit_title),
                getString(R.string.text_exit_msg), getString(android.R.string.ok),
                (dialog, which) -> LoginActivity.super.onBackPressed(),
                getString(android.R.string.cancel), null);
    }

    private void init() {
        checkStateOnCreate();

        edtUsername = this.findViewById(R.id.edtUsernameLogin);
        edtPassword = this.findViewById(R.id.edtPassLogin);
        chkRemember = this.findViewById(R.id.chkRememberLogin);
        chkRemember.setShadowLayer(1, 0, 1, getResources().getColor(R.color.colorAccent));
        progressBar = this.findViewById(R.id.progressBar);
        sharedPreferences = PreferencesUtil.getDefaultSharedPreferences(this);

        if (sharedPreferences.getBoolean(AppConstants.USER_REMEMBER.toString(), false)) {
            edtUsername.setText(sharedPreferences.getString(AppConstants.USER_NAME.toString(), null));
            edtPassword.setText(sharedPreferences.getString(AppConstants.USER_PASS.toString(), null));
            chkRemember.setChecked(sharedPreferences.getBoolean(AppConstants.USER_REMEMBER.toString(), false));
        }

        Intent intentRegister = new Intent(this, RegisterActivity.class);
        final TextView txtRegister = this.findViewById(R.id.txtRegister);
        txtRegister.setOnClickListener(v -> this.startActivity(intentRegister));

        final TextView txtResetPassword = this.findViewById(R.id.txtResetPassword);
        txtResetPassword.setOnClickListener(v -> ViewUtil.showProvideEmailDialog(this,
                getString(R.string.text_provide_email_title),
                getString(R.string.text_provide_email_msg)));

        final Button btnClear = this.findViewById(R.id.btnClearLogin);
        btnClear.setOnClickListener(v -> {
            edtUsername.setText("");
            edtPassword.setText("");
            chkRemember.setChecked(false);
        });

        final Button btnSubmit = this.findViewById(R.id.btnSubmitLogin);
        btnSubmit.setOnClickListener(v -> {
            progressBar.setVisibility(View.VISIBLE);
            final String username = edtUsername.getText().toString();
            final String password = edtPassword.getText().toString();
            if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(password)) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                if (chkRemember.isChecked()) {
                    editor.putString(AppConstants.USER_NAME.toString(), username);
                    editor.putString(AppConstants.USER_PASS.toString(), password);
                    editor.putBoolean(AppConstants.USER_REMEMBER.toString(), true);
                    editor.apply();
                } else {
                    editor.putString(AppConstants.USER_NAME.toString(), null);
                    editor.putString(AppConstants.USER_PASS.toString(), null);
                    editor.putBoolean(AppConstants.USER_REMEMBER.toString(), false);
                    editor.apply();
                }
                startLoginService(edtUsername.getText().toString(), edtPassword.getText().toString());
            }

            else {
                progressBar.setVisibility(View.INVISIBLE);
                ViewUtil.showSingleButtonAlertDialog(LoginActivity.this, getString(R.string.error),
                        getString(R.string.err_mandatory_fields), null);
            }
        });
        initReceivers();
    }

    private void startLoginService(String userName, String password) {
        Intent intent = new Intent(LoginActivity.this, LoginService.class);
        intent.setAction(AppConstants.DO_LOGIN.toString());
        intent.putExtra(AppConstants.USER_NAME.toString(), userName);
        intent.putExtra(AppConstants.USER_PASS.toString(), password);
        startService(intent);
    }

    private void initReceivers() {
        loginReceiver = new LoginReceiver();
        loginIntentFilter = new IntentFilter(AppConstants.DO_LOGIN.toString());

        verificationReceiver = new VerificationReceiver();
        verificationIntentFilter = new IntentFilter(AppConstants.DO_VERIFY.toString());
        resendVerificationCodeIntentFilter = new IntentFilter(AppConstants.DO_RESEND_VERIFICATION_CODE.toString());

        resetPasswordReceiver = new ResetPasswordReceiver();
        resetPasswordIntentFilter = new IntentFilter(AppConstants.DO_RESET_PASSWORD.toString());
    }

    private void registerReceivers() {
        registerReceiver(loginReceiver, loginIntentFilter);
        registerReceiver(resetPasswordReceiver, resetPasswordIntentFilter);
        registerReceiver(verificationReceiver, verificationIntentFilter);
        registerReceiver(verificationReceiver, resendVerificationCodeIntentFilter);
    }

    private void unregisterReceivers() {
        unregisterReceiver(loginReceiver);
        unregisterReceiver(resetPasswordReceiver);
        unregisterReceiver(verificationReceiver);
    }

    private void checkStateOnCreate() {
        NetworkUtil.checkNetworkAvailability(this);
        if (requiredPermissions.length != 0)
            PermissionUtil.checkPermissions(this, requiredPermissions);
    }

}