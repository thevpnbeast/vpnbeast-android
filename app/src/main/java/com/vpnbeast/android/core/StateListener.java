package com.vpnbeast.android.core;

public interface StateListener {

    void updateState(String state, String logMessage, int localizedResId, ConnectionStatus level);
    void setConnectedVPN(String uuid);

}