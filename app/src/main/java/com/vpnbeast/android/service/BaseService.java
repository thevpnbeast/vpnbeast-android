package com.vpnbeast.android.service;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.NonNull;

public abstract class BaseService extends Service {

    HandlerThread handlerThread;
    ServiceHandler serviceHandler;
    Intent responseIntent;

    @Override
    public void onDestroy() {
        handlerThread.quit();
    }

    // Binding is another way to communicate between service and activity
    // Not needed here, local broadcasts will be used instead
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void stopService() {
        sendBroadcast(responseIntent);
        stopSelf();
    }

    public static final class ServiceHandler extends Handler {

        ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message message) {
            // This is a base class, so we should not handle the message here
        }
    }

}