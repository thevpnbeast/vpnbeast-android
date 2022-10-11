package com.vpnbeast.android;

import android.app.Application;
import com.vpnbeast.android.core.StatusListener;
import com.vpnbeast.android.util.PRNGUtil;

public class VpnBeastApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // TODO: Remove below line and class PRNGUtil if app can run on all the APIS
        // PRNGUtil.apply();
        StatusListener statusListener = new StatusListener();
        statusListener.init(this);
    }

}