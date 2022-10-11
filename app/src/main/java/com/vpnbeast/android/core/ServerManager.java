package com.vpnbeast.android.core;

import android.content.Context;
import com.vpnbeast.android.model.entity.Server;
import com.vpnbeast.android.model.enums.AppConstants;
import com.vpnbeast.android.util.PreferencesUtil;
import java.util.Date;

public class ServerManager {

    private static Server lastConnectedServer = null;

    private ServerManager() {

    }

    public static void updateLRU(Context c, Server server) {
        server.setLastUsedAt(new Date(System.currentTimeMillis()));
        PreferencesUtil.storeServer(c, server, AppConstants.SERVER.toString());
    }

    static Server getLastConnectedServer() {
        return lastConnectedServer;
    }

    public static void setConnectedServer(Context c, Server server) {
        PreferencesUtil.storeServer(c, server, AppConstants.LAST_CONNECTED_SERVER.toString());
        lastConnectedServer = server;
    }

    public static void setConnectedServerDisconnected(Context c) {
        PreferencesUtil.storeServer(c, null, AppConstants.LAST_CONNECTED_SERVER.toString());
        lastConnectedServer = null;
    }

}