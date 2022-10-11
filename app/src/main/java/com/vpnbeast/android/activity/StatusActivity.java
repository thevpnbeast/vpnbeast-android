package com.vpnbeast.android.activity;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.vpnbeast.android.R;
import com.vpnbeast.android.core.ByteCountListener;
import com.vpnbeast.android.core.ConnectionStatus;
import com.vpnbeast.android.core.IOpenVPNServiceInternal;
import com.vpnbeast.android.core.StateListener;
import com.vpnbeast.android.core.VpnStatus;
import com.vpnbeast.android.model.entity.Server;
import com.vpnbeast.android.model.entity.User;
import com.vpnbeast.android.model.enums.AppConstants;
import com.vpnbeast.android.task.ConnectTask;
import com.vpnbeast.android.task.DisconnectTask;
import com.vpnbeast.android.util.PreferencesUtil;
import com.vpnbeast.android.util.ViewUtil;
import java.util.Locale;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StatusActivity extends AppCompatActivity implements StateListener, ByteCountListener {

    public static final String TAG = "StatusActivity";

    private long hours;
    private long minutes;
    private long seconds;
    private boolean isBytesDisplayed;
    private ProgressDialog progressDialog;
    private String lastStatus;
    private long connectTime;
    private TextView txtIp;
    private TextView txtPort;
    private TextView txtServerName;
    private TextView txtDuration;
    private TextView txtProto;
    private TextView txtBytesIn;
    private TextView txtBytesOut;
    private TextView txtStatus;
    private Button btnDisconnect;
    private Server server;
    private User user;
    private AsyncTask<Void, Void, Integer> connectionChecker;
    private Intent mainIntent;
    private Runnable runnable;
    private SharedPreferences sharedPrefs;
    private IOpenVPNServiceInternal serviceInternal;
    private boolean isBound;
    private ServiceConnection serviceConnection;
    private String state;
    private String logMessage;
    private int localizedResId;
    private ConnectionStatus level;
    private boolean userManagedDisconnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status);
        init();
        new ConnectTask(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        runnable.run();
    }

    @Override
    protected void onDestroy() {
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
            if (!userManagedDisconnection)
                new DisconnectTask(StatusActivity.this).execute();
        }
        super.onDestroy();
    }

    private void init() {
        userManagedDisconnection = false;
        hours = 0;
        minutes = 0;
        seconds = 0;
        isBytesDisplayed = false;
        isBound = false;
        server = (Server) getIntent().getSerializableExtra(AppConstants.SERVER.toString());
        user = PreferencesUtil.getUser(this, AppConstants.USER.toString());
        sharedPrefs = PreferencesUtil.getDefaultSharedPreferences(StatusActivity.this);
        progressDialog = new ProgressDialog(StatusActivity.this);
        progressDialog.setProgressStyle(R.style.VpnbeastProgressBar);
        btnDisconnect = this.findViewById(R.id.btnDisconnect);
        txtServerName = this.findViewById(R.id.txtServerNameStatus);
        txtIp = this.findViewById(R.id.txtIpStatus);
        txtPort = this.findViewById(R.id.txtPortStatus);
        txtProto = this.findViewById(R.id.txtProtoStatus);
        txtStatus = this.findViewById(R.id.txtStatusStatus);
        txtDuration = this.findViewById(R.id.txtDurationStatus);
        txtBytesIn = this.findViewById(R.id.txtBytesInStatus);
        txtBytesOut = this.findViewById(R.id.txtBytesOutStatus);
        txtServerName.setText(server.getHostname());
        txtIp.setText(server.getIp());
        txtPort.setText(String.valueOf(server.getPort()));
        txtProto.setText(server.getProto().toUpperCase());
        txtStatus.setText(getString(R.string.state_connecting));
        btnDisconnect.setText(getString(R.string.state_connecting));
        txtBytesIn.setText(getString(R.string.text_null));
        txtBytesOut.setText(getString(R.string.text_null));
        txtDuration.setText(getString(R.string.text_initial_timer));
        mainIntent = new Intent(this, MainActivity.class);
        btnDisconnect.setOnClickListener(v -> ViewUtil.showMultiButtonAlertDialog(this,
                getString(R.string.text_disconnect_title), getString(R.string.text_disconnect_msg),
                getString(android.R.string.ok), (dialog, which) -> {
                    userManagedDisconnection = true;
                    new DisconnectTask(StatusActivity.this).execute();
                }, getString(android.R.string.cancel), null));

        runnable = () -> serviceConnection = new ServiceConnection() {

            @Override
            public void onServiceConnected(ComponentName className,
                                           IBinder service) {
                isBound = true;
                serviceInternal = IOpenVPNServiceInternal.Stub.asInterface(service);
            }

            // https://stackoverflow.com/questions/17713453/onservicedisconnected-not-called-after-calling-service-stopself
            @Override
            public void onServiceDisconnected(ComponentName arg0) {
                Log.i(TAG, "onServiceDisconnected: service disconnected!");
                isBound = false;
                serviceInternal = null;
            }
        };
    }

    private String humanReadableByteCount(long bytes, boolean mbit) {
        if (mbit)
            bytes = bytes * 8;
        int unit = mbit ? 1000 : 1024;
        if (bytes < unit)
            return bytes + (mbit ? " bit" : " B");
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (mbit ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + "";
        if (mbit)
            return String.format(Locale.getDefault(), "%.1f %sbit", bytes / Math.pow(unit, exp), pre);
        else
            return String.format(Locale.getDefault(), "%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    @Override
    public void onBackPressed() {
        ViewUtil.showSingleButtonAlertDialog(this, getString(R.string.state_connected),
                getString(R.string.state_already_connected_msg), null);
    }

    @Override
    public void updateByteCount(long in, long out, long diffIn, long diffOut) {
        final String byteIn = humanReadableByteCount(in, false);
        final String sumByteIn = humanReadableByteCount(diffIn / VpnStatus.BYTE_COUNT_INTERVAL, true);
        final String byteOut = humanReadableByteCount(out, false);
        final String sumByteOut = humanReadableByteCount(diffOut / VpnStatus.BYTE_COUNT_INTERVAL, true);
        if (VpnStatus.lastLevel == ConnectionStatus.LEVEL_CONNECTED) {
            StatusActivity.this.runOnUiThread(() -> updateByteTexts(byteIn, sumByteIn, byteOut, sumByteOut));
        }
    }

    private void updateByteTexts(String in, String ins, String out, String outs) {
        txtBytesIn.setText(String.format(getString(R.string.bytes_total), ins, in));
        txtBytesOut.setText(String.format(getString(R.string.bytes_total), outs, out));
    }

    @Override
    public void updateState(String state, String logMessage, int localizedResId, ConnectionStatus level) {
        this.state = state;
        this.logMessage = logMessage;
        this.localizedResId = localizedResId;
        this.level = level;
    }

    @Override
    public void setConnectedVPN(String uuid) {
        // No need to override that method here
    }

}
