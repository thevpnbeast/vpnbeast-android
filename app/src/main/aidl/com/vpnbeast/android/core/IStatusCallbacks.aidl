// IStatusCallbacks.aidl
package com.vpnbeast.android.core;

import com.vpnbeast.android.core.LogItem;
import com.vpnbeast.android.core.ConnectionStatus;
// Declare any non-default types here with import statements

interface IStatusCallbacks {

    /**
     * Called when the service has a new status for you.
     */
    oneway void newLogItem(in LogItem item);

    oneway void updateStateString(in String state, in String msg, in int resid, in ConnectionStatus level);

    oneway void updateByteCount(long inBytes, long outBytes);

    oneway void connectedVPN(String uuid);

}