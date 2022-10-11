// IOpenVPNServiceInternal.aidl
package com.vpnbeast.android.core;

// Declare any non-default types here with import statements

interface IOpenVPNServiceInternal {

    boolean protect(int fd);

    void userPause(boolean b);

    /**
     * @param replaceConnection True if the VPN is connected by a new connection.
     * @return true if there was a process that has been send a stop signal
     */
    boolean stopVPN(boolean replaceConnection);
}
