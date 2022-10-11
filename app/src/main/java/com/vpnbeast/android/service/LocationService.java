package com.vpnbeast.android.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.HandlerThread;
import com.vpnbeast.android.model.enums.AppConstants;

public class LocationService extends BaseService implements LocationListener {

    protected LocationManager locationManager;
    private Context context;

    public void onCreate() {
        super.onCreate();
        handlerThread = new HandlerThread("LocationService.HandlerThread");
        handlerThread.start();
        context = getApplicationContext();
        serviceHandler = new ServiceHandler(handlerThread.getLooper());
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        serviceHandler.post(() -> {
            String action = intent.getAction();
            if (action != null && action.equals(AppConstants.GET_LOCATION.toString())) {
                responseIntent = new Intent(AppConstants.GET_LOCATION.toString());
                getLocation();
            }
        });
        return START_STICKY;
    }

    @SuppressLint("MissingPermission")
    private void getLocation() {
        locationManager = (LocationManager) context.getSystemService(LOCATION_SERVICE);

        if (locationManager != null) {
            Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

            if (location != null) {
                responseIntent.putExtra("latitude", location.getLatitude());
                responseIntent.putExtra("longitude", location.getLongitude());
            }
        }
        stopService();
    }

    @Override
    public void onLocationChanged(Location location) {
        // Nothing to do
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {
        // Nothing to do
    }

    @Override
    public void onProviderEnabled(String s) {
        // Nothing to do
    }

    @Override
    public void onProviderDisabled(String s) {
        // Nothing to do
    }

}