package com.vpnbeast.android.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.os.Build;
import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.O)
public class NotificationUtil extends ContextWrapper {

    public static final String ANDROID_CHANNEL_ID = "com.vpnbeast.android";
    public static final String ANDROID_CHANNEL_NAME = "ANDROID_CHANNEL";

    private NotificationManager notificationManager;

    public NotificationUtil(Context base) {
        super(base);
        createChannels();
    }

    // We should create notification channels for Android Oreo and above
    public void createChannels() {
        // create android channel
        NotificationChannel androidChannel = new NotificationChannel(ANDROID_CHANNEL_ID,
                ANDROID_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        // Sets whether notifications posted to this channel should display notification lights
        androidChannel.enableLights(true);
        // Sets whether notification posted to this channel should vibrate.
        androidChannel.enableVibration(true);
        // Sets the notification light color for notifications posted to this channel
        androidChannel.setLightColor(Color.GREEN);
        // Sets whether notifications posted to this channel appear on the lockscreen or not
        androidChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        getManager().createNotificationChannel(androidChannel);
    }

    private NotificationManager getManager() {
        if (notificationManager == null)
            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        return notificationManager;
    }

    public Notification.Builder getAndroidChannelNotification() {
        return new Notification.Builder(getApplicationContext(), ANDROID_CHANNEL_ID);
    }
}