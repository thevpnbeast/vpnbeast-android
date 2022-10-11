package com.vpnbeast.android.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import com.vpnbeast.android.model.enums.AppConstants;

public class LocationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action != null && action.equals(AppConstants.GET_LOCATION.toString())) {
            Toast.makeText(context, "Latitude = " + intent.getDoubleExtra("latitude", 0) +
                            "\n" + "Longitude = " + intent.getDoubleExtra("longitude", 0),
                    Toast.LENGTH_SHORT).show();
        }
    }

}