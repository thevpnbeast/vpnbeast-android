package com.vpnbeast.android.core;

import com.vpnbeast.android.model.enums.PauseReason;

public interface OpenVPNManagement {

    void reconnect();

    void pause(PauseReason reason);

    void resume();

    /**
     * @param replaceConnection True if the VPN is connected by a new connection.
     * @return true if there was a process that has been send a stop signal
     */
    boolean stopVPN(boolean replaceConnection);

    /*
     * Rebind the interface
     */
    void networkChange(boolean sameNetwork);

    void setPauseCallback(PausedStateCallback callback);

}