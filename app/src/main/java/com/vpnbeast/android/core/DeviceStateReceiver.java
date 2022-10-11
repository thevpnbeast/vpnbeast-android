package com.vpnbeast.android.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.util.Log;
import com.vpnbeast.android.model.entity.DataPoint;
import com.vpnbeast.android.model.enums.ConnectionState;
import com.vpnbeast.android.model.enums.PauseReason;
import com.vpnbeast.android.util.PreferencesUtil;
import java.util.LinkedList;

public class DeviceStateReceiver extends BroadcastReceiver implements ByteCountListener, PausedStateCallback {

    // Window time in s
    private static final int TRAFFIC_WINDOW = 60;
    // Data traffic limit in bytes
    private static final int TRAFFIC_LIMIT = 64 * 1024;
    // Time to wait after network disconnect to pause the VPN
    private static final int DISCONNECT_WAIT = 20;

    private final Handler disconnectHandler;
    private OpenVPNManagement openvpnManagement;
    private LinkedList<DataPoint> trafficData = new LinkedList<>();
    private ConnectionState network = ConnectionState.DISCONNECTED;
    private ConnectionState screen = ConnectionState.SHOULD_BE_CONNECTED;
    private ConnectionState userPause = ConnectionState.SHOULD_BE_CONNECTED;
    private NetworkInfo lastConnectedNetwork;

    private Runnable delayDisconnectRunnable = new Runnable() {
        @Override
        public void run() {
            if ((network != ConnectionState.PENDING_DISCONNECT))
                return;

            network = ConnectionState.DISCONNECTED;

            if (screen == ConnectionState.PENDING_DISCONNECT)
                screen = ConnectionState.DISCONNECTED;

            openvpnManagement.pause(getPauseReason());
        }
    };

    @Override
    public boolean shouldBeRunning() {
        return shouldBeConnected();
    }

    @Override
    public void updateByteCount(long in, long out, long diffIn, long diffOut) {
        if (screen != ConnectionState.PENDING_DISCONNECT)
            return;

        long total = diffIn + diffOut;
        trafficData.add(new DataPoint(System.currentTimeMillis(), total));

        while (trafficData.getFirst().getTimestamp() <= (System.currentTimeMillis() - TRAFFIC_WINDOW * 1000)) {
            trafficData.removeFirst();
        }

        long windowtraffic = 0;
        for (DataPoint dp : trafficData)
            windowtraffic += dp.getData();

        if (windowtraffic < TRAFFIC_LIMIT) {
            screen = ConnectionState.DISCONNECTED;
            openvpnManagement.pause(getPauseReason());
        }
    }

    public void userPause(boolean pause) {
        if (pause) {
            userPause = ConnectionState.DISCONNECTED;
            // Check if we should disconnect
            openvpnManagement.pause(getPauseReason());
        } else {
            boolean wereConnected = shouldBeConnected();
            userPause = ConnectionState.SHOULD_BE_CONNECTED;
            if (shouldBeConnected() && !wereConnected)
                openvpnManagement.resume();
            else
                // Update the reason why we currently paused
                openvpnManagement.pause(getPauseReason());
        }
    }

    public DeviceStateReceiver(OpenVPNManagement management) {
        super();
        openvpnManagement = management;
        openvpnManagement.setPauseCallback(this);
        disconnectHandler = new Handler();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = PreferencesUtil.getDefaultSharedPreferences(context);
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
            networkStateChange(context);
        } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
            boolean screenOffPause = prefs.getBoolean("screenoff", false);
            if (screenOffPause) {
                if (ServerManager.getLastConnectedServer() != null && !ServerManager
                        .getLastConnectedServer().isPersistTun())
                    screen = ConnectionState.PENDING_DISCONNECT;
                fillTrafficData();
                if (network == ConnectionState.DISCONNECTED || userPause == ConnectionState.DISCONNECTED)
                    screen = ConnectionState.DISCONNECTED;
            }
        } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
            // Network was disabled because screen off
            boolean connected = shouldBeConnected();
            screen = ConnectionState.SHOULD_BE_CONNECTED;
            /* We should connect now, cancel any outstanding disconnect timer */
            disconnectHandler.removeCallbacks(delayDisconnectRunnable);
            /* should be connected has changed because the screen is on now, connect the VPN */
            if (shouldBeConnected() != connected)
                openvpnManagement.resume();
            else if (!shouldBeConnected())
                /*Update the reason why we are still paused */
                openvpnManagement.pause(getPauseReason());
        }
    }

    private void fillTrafficData() {
        trafficData.add(new DataPoint(System.currentTimeMillis(), TRAFFIC_LIMIT));
    }

    public static boolean equalsObj(Object a, Object b) {
        return (a == null) ? (b == null) : a.equals(b);
    }

    public void networkStateChange(Context context) {
        NetworkInfo networkInfo = getCurrentNetworkInfo(context);
        SharedPreferences prefs = PreferencesUtil.getDefaultSharedPreferences(context);
        boolean sendUsr1 = prefs.getBoolean("netchangereconnect", true);
        Log.i("DeviceStateReceiver", "networkStateChange: network = " + network);
        if (networkInfo != null && networkInfo.getState() == NetworkInfo.State.CONNECTED) {
            boolean pendingDisconnect = (network == ConnectionState.PENDING_DISCONNECT);
            network = ConnectionState.SHOULD_BE_CONNECTED;
            boolean sameNetwork = lastConnectedNetwork != null
                    && lastConnectedNetwork.getType() == networkInfo.getType()
                    && equalsObj(lastConnectedNetwork.getExtraInfo(), networkInfo.getExtraInfo());
            Log.i(DeviceStateReceiver.class.getName(), "networkStateChange: sameNetwork = " + sameNetwork);

            /* Same network, connection still 'established' */
            if (pendingDisconnect && sameNetwork) {
                disconnectHandler.removeCallbacks(delayDisconnectRunnable);
                // Reprotect the sockets just be sure
                openvpnManagement.networkChange(true);
            } else {
                /* Different network or connection not established anymore */
                if (screen == ConnectionState.PENDING_DISCONNECT)
                    screen = ConnectionState.DISCONNECTED;
                if (shouldBeConnected()) {
                    Log.i(DeviceStateReceiver.class.getName(), "networkStateChange: shouldBeConnected = " + shouldBeConnected());
                    disconnectHandler.removeCallbacks(delayDisconnectRunnable);
                    if (pendingDisconnect || !sameNetwork)
                        openvpnManagement.networkChange(sameNetwork);
                    else
                        openvpnManagement.resume();
                }
                lastConnectedNetwork = networkInfo;
            }
        } else if (networkInfo == null) {
            // Not connected, stop openvpn, set last connected network to no network
            Log.i(DeviceStateReceiver.class.getName(), "networkStateChange: networkInfo is null!");
            if (sendUsr1) {
                network = ConnectionState.PENDING_DISCONNECT;
                disconnectHandler.postDelayed(delayDisconnectRunnable, DISCONNECT_WAIT * 1000);
            }
        }
    }

    private boolean shouldBeConnected() {
        return (screen == ConnectionState.SHOULD_BE_CONNECTED && userPause == ConnectionState.SHOULD_BE_CONNECTED &&
                network == ConnectionState.SHOULD_BE_CONNECTED);
    }

    private PauseReason getPauseReason() {
        if (userPause == ConnectionState.DISCONNECTED)
            return PauseReason.USER_PAUSE;

        if (screen == ConnectionState.DISCONNECTED)
            return PauseReason.SCREEN_OFF;

        if (network == ConnectionState.DISCONNECTED)
            return PauseReason.NO_NETWORK;

        return PauseReason.USER_PAUSE;
    }

    private NetworkInfo getCurrentNetworkInfo(Context context) {
        ConnectivityManager con = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
        return con != null ? con.getActiveNetworkInfo() : null;
    }

}