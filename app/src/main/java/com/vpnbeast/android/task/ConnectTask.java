package com.vpnbeast.android.task;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;
import com.vpnbeast.android.R;
import com.vpnbeast.android.activity.StatusActivity;
import com.vpnbeast.android.core.ConnectionStatus;
import com.vpnbeast.android.core.OpenVPNService;
import com.vpnbeast.android.core.ServerManager;
import com.vpnbeast.android.core.VpnStatus;
import com.vpnbeast.android.model.enums.AppConstants;
import com.vpnbeast.android.util.ViewUtil;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class ConnectTask extends AsyncTask<Void, Void, Integer> {

    private static final String TAG = "ConnectTask";

    private WeakReference<StatusActivity> activityReference;
    private int counter;

    public ConnectTask(StatusActivity context) {
        activityReference = new WeakReference<>(context);
        VpnStatus.addStateListener(activityReference.get());
        VpnStatus.addByteCountListener(activityReference.get());
        counter = 0;
    }

    @Override
    protected void onPreExecute() {
        StatusActivity statusActivity = activityReference.get();
        if (VpnStatus.lastLevel != ConnectionStatus.LEVEL_CONNECTED) {
            statusActivity.getProgressDialog().setTitle(R.string.state_connecting);
            statusActivity.getProgressDialog().setMessage(statusActivity.getString(R.string.state_msg_connecting));
            statusActivity.getProgressDialog().setButton(ProgressDialog.BUTTON_POSITIVE,
                    "cancel", ((dialog, which) -> {
                        statusActivity.stopService(new Intent(statusActivity, OpenVPNService.class));
                        stopConnecting(statusActivity.getString(R.string.err_connect_cancelled_title),
                        statusActivity.getString(R.string.err_connect_cancelled_msg));
                        this.cancel(true);
            }));
            statusActivity.getProgressDialog().setCancelable(false);
            statusActivity.getProgressDialog().show();
        }
    }

    @Override
    protected Integer doInBackground(Void... params) {
        StatusActivity statusActivity = activityReference.get();
        while (statusActivity.getLevel() != ConnectionStatus.LEVEL_CONNECTED) {
            try {
                Thread.sleep(1000);
                // TODO: Think a better way to do that
                counter++;

                if (statusActivity.getLevel() == ConnectionStatus.LEVEL_NOT_CONNECTED || counter > 15) {
                    stopConnecting(statusActivity.getString(R.string.err_connect_failed_title),
                            statusActivity.getString(R.string.err_connect_failed_unknown_msg));
                    this.cancel(true);
                    break;
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "doInBackground: ", e);
            }
        }
        return 0;
    }

    @Override
    protected void onPostExecute(Integer integer) {
        StatusActivity statusActivity = activityReference.get();
        ServerManager.setConnectedServer(statusActivity, statusActivity.getServer());
        statusActivity.getProgressDialog().dismiss();
        statusActivity.setConnectTime(System.currentTimeMillis());
        statusActivity.getTxtStatus().setText(statusActivity.getString(R.string.state_connected));
        statusActivity.getBtnDisconnect().setText(statusActivity.getString(R.string.disconnect));
        statusActivity.getBtnDisconnect().setBackground(statusActivity.getResources()
                .getDrawable(R.drawable.button_selector_red));
        statusActivity.getBtnDisconnect().setTextColor(statusActivity.getResources()
                .getColor(R.color.colorBlack));
        statusActivity.setBytesDisplayed(true);
        updateDuration();
        Intent intent = new Intent(statusActivity, OpenVPNService.class);
        intent.setAction(AppConstants.START_SERVICE.toString());
        statusActivity.bindService(intent, statusActivity.getServiceConnection(), Context.BIND_AUTO_CREATE);
        statusActivity.setBound(true);
    }

    private void updateDuration() {
        StatusActivity statusActivity = activityReference.get();
        if (VpnStatus.lastLevel == ConnectionStatus.LEVEL_CONNECTED) {
            SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    statusActivity.runOnUiThread(() -> {
                        statusActivity.setSeconds((System.currentTimeMillis() - statusActivity
                                .getConnectTime()) / 1000);
                        Calendar cal = Calendar.getInstance();
                        cal.set(Calendar.HOUR_OF_DAY, (int) statusActivity.getHours());
                        cal.set(Calendar.MINUTE, (int) statusActivity.getMinutes());
                        cal.set(Calendar.SECOND, (int) statusActivity.getSeconds());
                        Date date = cal.getTime();
                        statusActivity.getTxtDuration().setText(formatter.format(date));
                    });
                }
            }, 0, 1000);
        }
    }

    private void stopConnecting(String errorTitle, String errorMessage) {
        StatusActivity statusActivity = activityReference.get();
        statusActivity.stopService(new Intent(statusActivity, OpenVPNService.class));
        statusActivity.getMainIntent().putExtra(AppConstants.DISCONNECT_VPN.toString(), true);
        VpnStatus.removeStateListener(statusActivity);
        VpnStatus.removeByteCountListener(statusActivity);
        ServerManager.setConnectedServerDisconnected(statusActivity);
        statusActivity.getProgressDialog().dismiss();
        statusActivity.runOnUiThread(() -> ViewUtil.showSingleButtonAlertDialog(statusActivity,
                errorTitle, errorMessage, (dialog, which) -> {
            statusActivity.setResult(Activity.RESULT_CANCELED);
            statusActivity.finish();
        }));
    }

}
