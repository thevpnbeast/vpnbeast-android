package com.vpnbeast.android.activity;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.util.Log;
import com.vpnbeast.android.R;
import com.vpnbeast.android.core.ConnectionStatus;
import com.vpnbeast.android.core.ServerManager;
import com.vpnbeast.android.core.VPNLaunchHelper;
import com.vpnbeast.android.core.VpnStatus;
import com.vpnbeast.android.model.entity.Server;
import com.vpnbeast.android.model.enums.AppConstants;
import java.io.IOException;

public class LaunchVPN extends Activity {

    private static final int START_VPN_PROFILE = 70;
    private static final int START_STATUS_ACTIVITY = 80;

    private Server server;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        server = (Server) getIntent().getSerializableExtra(AppConstants.SERVER.toString());
        startVpnFromIntent();
    }

    void startStatusActivity() {
        Intent startLW = new Intent(this, StatusActivity.class);
        startLW.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startLW.putExtra(AppConstants.SERVER.toString(), server);
        startActivityForResult(startLW, START_STATUS_ACTIVITY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == START_VPN_PROFILE) {
            if (resultCode == Activity.RESULT_OK) {
                ServerManager.updateLRU(this, server);
                VPNLaunchHelper.startOpenVpn(server, this);
                startStatusActivity();
            } else if (resultCode == Activity.RESULT_CANCELED) {
                VpnStatus.updateStateString("USER_VPN_PERMISSION_CANCELLED", "",
                        R.string.state_user_vpn_permission_cancelled, ConnectionStatus.LEVEL_NOT_CONNECTED);
                LaunchVPN.this.finish();
            }
        } else if (requestCode == START_STATUS_ACTIVITY) {
            if (resultCode == Activity.RESULT_FIRST_USER)
                startVpnFromIntent();
            else {
                VpnStatus.updateStateString("USER_VPN_PERMISSION_CANCELLED", "",
                        R.string.state_user_vpn_permission_cancelled, ConnectionStatus.LEVEL_NOT_CONNECTED);
                LaunchVPN.this.finish();
            }
        }
    }

    private void startVpnFromIntent() {
        final Intent intent = getIntent();
        final String action = intent.getAction();
        if (Intent.ACTION_MAIN.equals(action)) {
            launchVPN();
        }
    }

    private void launchVPN() {
        Intent intent = VpnService.prepare(getApplicationContext());
        executeSuCmd();
        if (intent != null) {
            VpnStatus.updateStateString("USER_VPN_PERMISSION", "",
                    R.string.state_user_vpn_permission, ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT);
            try {
                startActivityForResult(intent, START_VPN_PROFILE);
            } catch (ActivityNotFoundException ane) {
                VpnStatus.logError(R.string.no_vpn_support_image);
            }
        } else {
            onActivityResult(START_VPN_PROFILE, Activity.RESULT_OK, null);
        }
    }

    private void executeSuCmd() {
        try {
            ProcessBuilder pb = new ProcessBuilder("su", "-c", "chown system /dev/tun");
            Process p = pb.start();
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            VpnStatus.logException("SU command", e);
        }
    }

}