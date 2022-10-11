package com.vpnbeast.android.core;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.vpnbeast.android.model.entity.UpdateMessage;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;

public class OpenVPNStatusService extends Service implements LogListener, ByteCountListener, StateListener {

    private static final String TAG = "OpenVPNStatusService";
    private static final int SEND_NEW_LOG_ITEM = 100;
    private static final int SEND_NEW_STATE = 101;
    private static final int SEND_NEW_BYTE_COUNT = 102;
    private static final int SEND_NEW_CONNECTED_VPN = 103;

    private OpenVPNStatusHandler statusHandler;
    private RemoteCallbackList<IStatusCallbacks> callbackList;
    private UpdateMessage lastUpdateMessage;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind: binded!");
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        init();
    }

    private void init() {
        statusHandler = new OpenVPNStatusHandler();
        statusHandler.setService(this);
        callbackList = new RemoteCallbackList<>();
        VpnStatus.addLogListener(this);
        VpnStatus.addByteCountListener(this);
        VpnStatus.addStateListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        VpnStatus.removeLogListener(this);
        VpnStatus.removeByteCountListener(this);
        VpnStatus.removeStateListener(this);
        callbackList.kill();
    }

    private IServiceStatus.Stub binder = new IServiceStatus.Stub() {
        @Override
        public ParcelFileDescriptor registerStatusCallback(IStatusCallbacks cb) throws RemoteException {
            final LogItem[] logBuffer = VpnStatus.getLogBufferList();

            if (lastUpdateMessage != null)
                sendUpdate(cb, lastUpdateMessage);

            callbackList.register(cb);
            try {
                final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                new Thread("pushLogs") {
                    @Override
                    public void run() {
                        DataOutputStream fd = new DataOutputStream(new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]));
                        try {
                            synchronized (VpnStatus.READ_FILE_LOCK) {
                                if (!VpnStatus.readFileLog)
                                    VpnStatus.READ_FILE_LOCK.wait();
                            }

                            for (LogItem logItem : logBuffer) {
                                byte[] bytes = logItem.getMarshalledBytes();
                                fd.writeShort(bytes.length);
                                fd.write(bytes);
                            }

                            fd.writeShort(0x7fff);
                        } catch (InterruptedException | IOException e) {
                            Log.e(TAG, "run: ", e);
                        } finally {
                            try {
                                fd.close();
                            } catch (IOException e) {
                                Log.e(TAG, "run: ", e);
                            }
                        }
                    }
                }.start();
                return pipe[0];
            } catch (IOException e) {
                Log.e(TAG, "registerStatusCallback: ", e);
                return null;
            }
        }

        @Override
        public void unregisterStatusCallback(IStatusCallbacks cb) {
            callbackList.unregister(cb);
        }

        @Override
        public String getLastConnectedVPN() {
            return VpnStatus.getLastConnectedServerUuid();
        }

    };

    @Override
    public void newLog(LogItem logItem) {
        Message msg = statusHandler.obtainMessage(SEND_NEW_LOG_ITEM, logItem);
        msg.sendToTarget();
    }

    @Override
    public void updateByteCount(long in, long out, long diffIn, long diffOut) {
        Message msg = statusHandler.obtainMessage(SEND_NEW_BYTE_COUNT, Pair.create(in, out));
        msg.sendToTarget();
    }

    @Override
    public void updateState(String state, String logMessage, int localizedResId, ConnectionStatus level) {
        lastUpdateMessage = UpdateMessage.builder()
                .state(state)
                .logMessage(logMessage)
                .resId(localizedResId)
                .level(level)
                .build();
        Message msg = statusHandler.obtainMessage(SEND_NEW_STATE, lastUpdateMessage);
        msg.sendToTarget();
    }

    @Override
    public void setConnectedVPN(String uuid) {
        Message msg = statusHandler.obtainMessage(SEND_NEW_CONNECTED_VPN, uuid);
        Log.i(TAG, "setConnectedVPN: obtained message = " + msg);
        msg.sendToTarget();
    }

    private static class OpenVPNStatusHandler extends Handler {
        WeakReference<OpenVPNStatusService> service = null;

        private void setService(OpenVPNStatusService statusService) {
            service = new WeakReference<>(statusService);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            RemoteCallbackList<IStatusCallbacks> callbacks;
            if (service == null || service.get() == null)
                return;
            callbacks = service.get().callbackList;
            // Broadcast to all clients the new value.
            final int N = callbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    IStatusCallbacks broadcastItem = callbacks.getBroadcastItem(i);
                    switch (msg.what) {
                        case SEND_NEW_LOG_ITEM:
                            Log.i(TAG, "handleMessage: inside SEND_NEW_LOGITEM case");
                            broadcastItem.newLogItem((LogItem) msg.obj);
                            break;
                        case SEND_NEW_BYTE_COUNT:
                            Log.i(TAG, "handleMessage: inside SEND_NEW_BYTECOUNT case");
                            Pair<Long, Long> inout = (Pair<Long, Long>) msg.obj;
                            broadcastItem.updateByteCount(inout.first, inout.second);
                            break;
                        case SEND_NEW_STATE:
                            Log.i(TAG, "handleMessage: inside SEND_NEW_STATE case");
                            service.get().sendUpdate(broadcastItem, (UpdateMessage) msg.obj);
                            break;
                        case SEND_NEW_CONNECTED_VPN:
                            Log.i(TAG, "handleMessage: inside SEND_NEW_CONNECTED_VPN case");
                            Log.i(TAG, "handleMessage: uuid = " + msg.obj);
                            broadcastItem.connectedVPN((String) msg.obj);
                            break;
                        default:
                            Log.w(TAG, "handleMessage: unknown message to handle!");
                            break;
                    }
                } catch (RemoteException e) {
                    // The RemoteCallbackList will take care of removing
                    // the dead object for us.
                }
            }
            callbacks.finishBroadcast();
        }
    }

    private void sendUpdate(IStatusCallbacks broadcastItem, UpdateMessage um) throws RemoteException {
        broadcastItem.updateStateString(um.getState(), um.getLogMessage(), um.getResId(), um.getLevel());
    }

}