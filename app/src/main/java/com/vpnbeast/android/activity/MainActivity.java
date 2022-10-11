package com.vpnbeast.android.activity;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import com.vpnbeast.android.R;
import com.vpnbeast.android.fragment.ServerSelectFragment;
import com.vpnbeast.android.model.entity.Server;
import com.vpnbeast.android.model.entity.User;
import com.vpnbeast.android.model.enums.AppConstants;
import com.vpnbeast.android.receiver.RefreshReceiver;
import com.vpnbeast.android.receiver.ServerReceiver;
import com.vpnbeast.android.service.ServerService;
import com.vpnbeast.android.util.NetworkUtil;
import com.vpnbeast.android.util.PermissionUtil;
import com.vpnbeast.android.util.PreferencesUtil;
import com.vpnbeast.android.util.ViewUtil;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;

public class MainActivity extends AppCompatActivity {

    private ScheduledExecutorService scheduledServerServiceExecutor;
    private ProgressBar progressBar;
    private ServerReceiver serverReceiver;
    private RefreshReceiver refreshReceiver;
    // private LocationReceiver locationReceiver;
    private IntentFilter serverIntentFilter;
    private IntentFilter refreshIntentFilter;
    // private IntentFilter locationIntentFilter;
    private RelativeLayout relativeLayout;
    private TextView txtCountry;
    private TextView txtProto;
    private TextView txtIp;
    private TextView txtPort;
    private Button btnConnect;
    private User user;
    private Server server;
    private String[] requiredPermissions = new String[] {Manifest.permission.INTERNET,
            Manifest.permission.READ_EXTERNAL_STORAGE};

    @Getter
    @Setter
    private FragmentTransaction fragmentTransaction;
    private ServerSelectFragment serverSelectFragment;

    /*@Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_ASK_PERMISSIONS) {
            for (int index = permissions.length - 1; index >= 0; --index) {
                if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                    // exit the app if one permission is not granted
                    Toast.makeText(this, "Required permission '" + permissions[index]
                            + "' not granted, exiting", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
            }
        }
    }*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (scheduledServerServiceExecutor.isShutdown())
            initServerScheduler();

        registerReceiver(serverReceiver, serverIntentFilter);
        progressBar.setVisibility(View.INVISIBLE);
    }

    @Override
    protected void onPause() {
        scheduledServerServiceExecutor.shutdown();
        unregisterReceiver(serverReceiver);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // scheduledRefreshServiceExecutor.shutdown();
        unregisterReceiver(refreshReceiver);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (fragmentTransaction != null && !fragmentTransaction.isEmpty()) {
            getSupportFragmentManager().beginTransaction().remove(serverSelectFragment).commit();
            relativeLayout.setVisibility(View.VISIBLE);
            fragmentTransaction = null;
        } else {
            ViewUtil.showMultiButtonAlertDialog(this, getString(R.string.text_confirm_logout_title),
                    getString(R.string.text_confirm_logout_msg), getString(android.R.string.yes),
                    (dialog, which) -> {
                        PreferencesUtil.storeServer(this, null, AppConstants.SERVER.toString());
                        PreferencesUtil.storeUser(this, null, AppConstants.USER.toString());
                        super.onBackPressed();
                    }, getString(android.R.string.no), null);
        }
    }

    private void init() {
        checkStateOnCreate();

        Button btnSelect = this.findViewById(R.id.btnSelectMain);
        txtCountry = this.findViewById(R.id.txtCountryMain);
        txtProto = this.findViewById(R.id.txtProtoMain);
        txtIp = this.findViewById(R.id.txtIpMain);
        txtPort = this.findViewById(R.id.txtPortMain);
        relativeLayout = this.findViewById(R.id.activity_main);
        progressBar = this.findViewById(R.id.progressBarMain);
        btnConnect = this.findViewById(R.id.btnConnect);
        user = PreferencesUtil.getUser(this, AppConstants.USER.toString());
        /*if (!LocationUtil.isLocationPermitted(LoginActivity.this))
            LocationUtil.showPermissionDialog(LoginActivity.this);*/

        initServerScheduler();
        initReceivers();
        registerReceivers();
        btnSelect.setOnClickListener(v -> {
            fragmentTransaction = getSupportFragmentManager().beginTransaction();
            serverSelectFragment = new ServerSelectFragment();
            fragmentTransaction.replace(android.R.id.content, serverSelectFragment);
            fragmentTransaction.commit();
        });

        btnConnect.setOnClickListener(v -> {
            if (server != null)
                startVPN(server);
            else
                ViewUtil.showSingleButtonAlertDialog(MainActivity.this, "Error",
                        "You should select a VPN server first!", null);
        });
    }

    private void startServerService() {
        Intent intent = new Intent(this, ServerService.class);
        intent.setAction(AppConstants.GET_ALL_SERVERS.toString());
        intent.putExtra(AppConstants.ACCESS_TOKEN.toString(), user.getAccessToken());
        startService(intent);
    }

    /*private void startRefreshService() {
        user = PreferencesUtil.getUser(this, AppConstants.USER.toString());
        Intent intent = new Intent(this, RefreshService.class);
        intent.setAction(AppConstants.DO_REFRESH.toString());
        intent.putExtra(AppConstants.REFRESH_TOKEN.toString(), user.getRefreshToken());
        startService(intent);
    }*/

    /*private void startLocationService() {
        Intent i = new Intent(this, LocationService.class);
        i.setAction(AppConstants.GET_LOCATION.toString());
        Log.i(TAG, "startLocationService: creating LocationService...");
        startService(i);
    }*/

    private void initReceivers() {
        serverReceiver = new ServerReceiver();
        serverIntentFilter = new IntentFilter(AppConstants.GET_ALL_SERVERS.toString());
        refreshReceiver = new RefreshReceiver(this);
        refreshIntentFilter = new IntentFilter(AppConstants.DO_REFRESH.toString());
        /*locationReceiver = new LocationReceiver();
        locationIntentFilter = new IntentFilter(AppConstants.GET_LOCATION.toString());*/
    }

    private void registerReceivers() {
        registerReceiver(serverReceiver, serverIntentFilter);
        registerReceiver(refreshReceiver, refreshIntentFilter);
        //registerReceiver(locationReceiver, locationIntentFilter);
    }

    public void updateViews() {
        if (getIntent().getSerializableExtra(AppConstants.SERVER.toString()) != null) {
            server = (Server) getIntent().getSerializableExtra(AppConstants.SERVER.toString());
            txtCountry.setText(Objects.requireNonNull(server).getCountryLong());
            txtIp.setText(Objects.requireNonNull(server.getIp()));
            txtProto.setText(Objects.requireNonNull(server.getProto().toUpperCase()));
            txtPort.setText(Objects.requireNonNull(String.valueOf(server.getPort())));
            btnConnect.setBackground(getResources().getDrawable(R.drawable.button_selector_green));
            btnConnect.setTextColor(getResources().getColor(R.color.colorBlack));
        }
    }

    private void initServerScheduler() {
        // scheduledServerServiceExecutor = Executors.newScheduledThreadPool(5);
        scheduledServerServiceExecutor = Executors.newSingleThreadScheduledExecutor();
        scheduledServerServiceExecutor.scheduleAtFixedRate(this::startServerService,
                0, 70, TimeUnit.SECONDS);
    }

    private void startVPN(Server server) {
        Intent intent = new Intent(this, LaunchVPN.class);
        intent.putExtra(AppConstants.SERVER.toString(), server);
        intent.setAction(Intent.ACTION_MAIN);
        startActivity(intent);
    }

    private void checkStateOnCreate() {
        NetworkUtil.checkNetworkAvailability(this);
        if (requiredPermissions.length != 0)
            PermissionUtil.checkPermissions(this, requiredPermissions);
    }

}
