package com.vpnbeast.android.activity;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import com.vpnbeast.android.R;
import com.vpnbeast.android.model.enums.AppConstants;
import com.vpnbeast.android.receiver.RegisterReceiver;
import com.vpnbeast.android.receiver.VerificationReceiver;
import com.vpnbeast.android.service.RegisterService;
import com.vpnbeast.android.util.ViewUtil;
import lombok.Getter;
import lombok.Setter;

public class RegisterActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private RegisterReceiver registerReceiver;
    private VerificationReceiver verificationReceiver;
    private IntentFilter registerIntentFilter;
    private IntentFilter verifyIntentFilter;
    private IntentFilter resendVerificationCodeIntentFilter;

    @Getter
    @Setter
    private FragmentTransaction fragmentTransaction;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        init();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceivers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceivers();
        progressBar.setVisibility(View.INVISIBLE);
    }

    private void init() {
        EditText edtUsername = this.findViewById(R.id.edtUsernameRegister);
        EditText edtPassword = this.findViewById(R.id.edtPassRegister);
        EditText edtValidatePassword = this.findViewById(R.id.edtValidatePassRegister);
        EditText edtEmail = this.findViewById(R.id.edtEmailRegister);
        progressBar = this.findViewById(R.id.progressBar);

        final Button btnClear = this.findViewById(R.id.btnCleanRegister);
        final Button btnSubmit = this.findViewById(R.id.btnSubmitRegister);

        btnClear.setOnClickListener(v -> {
            edtUsername.setText("");
            edtPassword.setText("");
            edtValidatePassword.setText("");
            edtEmail.setText("");
        });

        btnSubmit.setOnClickListener(v -> {
            progressBar.setVisibility(View.VISIBLE);
            final String username = edtUsername.getText().toString();
            final String password = edtPassword.getText().toString();
            final String password2 = edtValidatePassword.getText().toString();
            final String email = edtEmail.getText().toString();
            if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(password)
                    && !TextUtils.isEmpty(password2) && !TextUtils.isEmpty(email)) {
                if (password.equals(password2)) {
                    startRegisterService(username, password, email);
                } else {
                    ViewUtil.showSingleButtonAlertDialog(this, getString(R.string.error),
                            getString(R.string.err_passwords_mismatch), null);
                    progressBar.setVisibility(View.INVISIBLE);
                }
            } else {
                progressBar.setVisibility(View.INVISIBLE);
                ViewUtil.showSingleButtonAlertDialog(this, getString(R.string.error),
                        getString(R.string.err_mandatory_fields), null);
            }
        });

        initReceivers();
    }

    private void startRegisterService(String userName, String password, String email) {
        Intent intent = new Intent(this, RegisterService.class);
        intent.setAction(AppConstants.DO_REGISTER.toString());
        intent.putExtra(AppConstants.USER_NAME.toString(), userName);
        intent.putExtra(AppConstants.USER_PASS.toString(), password);
        intent.putExtra(AppConstants.EMAIL.toString(), email);
        startService(intent);
    }

    private void initReceivers() {
        registerReceiver = new RegisterReceiver();
        registerIntentFilter = new IntentFilter(AppConstants.DO_REGISTER.toString());

        verificationReceiver = new VerificationReceiver();
        verifyIntentFilter = new IntentFilter(AppConstants.DO_VERIFY.toString());
        resendVerificationCodeIntentFilter = new IntentFilter(AppConstants.DO_RESEND_VERIFICATION_CODE.toString());
    }

    private void registerReceivers() {
        registerReceiver(registerReceiver, registerIntentFilter);
        registerReceiver(verificationReceiver, verifyIntentFilter);
        registerReceiver(verificationReceiver, resendVerificationCodeIntentFilter);
    }

    private void unregisterReceivers() {
        unregisterReceiver(registerReceiver);
        unregisterReceiver(verificationReceiver);
    }

}