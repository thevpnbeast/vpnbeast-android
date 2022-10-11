package com.vpnbeast.android.core;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import com.vpnbeast.android.model.enums.AppConstants;

public class StatusListener {

    private static final String TAG = "StatusListener";

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            IServiceStatus serviceStatus = IServiceStatus.Stub.asInterface(service);
            try {
                /* Check if this a local service ... */
                if (service.queryLocalInterface("com.vpnbeast.android.core.IServiceStatus") == null) {
                    // Not a local service
                    Log.i(TAG, "onServiceConnected: not a local service");
                    VpnStatus.setConnectedServerUuid(serviceStatus.getLastConnectedVPN());
                    serviceStatus.registerStatusCallback(mCallback);
                }

            } catch (RemoteException e) {
                Log.e(TAG, "onServiceConnected: ", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            // nothing to do
        }

    };


    public void init(Context c) {
        Intent intent = new Intent(c, OpenVPNStatusService.class);
        intent.setAction(AppConstants.START_SERVICE.toString());
        c.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }


    private IStatusCallbacks mCallback = new IStatusCallbacks.Stub() {

        @Override
        public void newLogItem(LogItem item) {
            VpnStatus.newLogItem(item);
        }

        @Override
        public void updateStateString(String state, String msg, int resId, ConnectionStatus level) {
            VpnStatus.updateStateString(state, msg, resId, level);
        }

        @Override
        public void updateByteCount(long inBytes, long outBytes) {
            VpnStatus.updateByteCount(inBytes, outBytes);
        }

        @Override
        public void connectedVPN(String uuid) {
            VpnStatus.setConnectedServerUuid(uuid);
        }
    };

}