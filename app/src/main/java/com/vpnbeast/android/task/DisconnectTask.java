package com.vpnbeast.android.task;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import com.vpnbeast.android.R;
import com.vpnbeast.android.activity.StatusActivity;
import com.vpnbeast.android.core.ConnectionStatus;
import com.vpnbeast.android.core.ServerManager;
import com.vpnbeast.android.core.VpnStatus;
import com.vpnbeast.android.model.entity.Server;
import com.vpnbeast.android.model.enums.AppConstants;
import java.lang.ref.WeakReference;

public class DisconnectTask extends AsyncTask<Void, Void, Integer> {

    private static final String TAG = "DisconnectTask";
    private WeakReference<StatusActivity> activityReference;

    public DisconnectTask(StatusActivity context) {
        activityReference = new WeakReference<>(context);
    }

    @Override
    protected void onPreExecute() {
        StatusActivity statusActivity = activityReference.get();
        if (statusActivity.isUserManagedDisconnection()) {
            statusActivity.getProgressDialog().setTitle(R.string.state_disconnecting);
            statusActivity.getProgressDialog().setMessage(statusActivity.getString(R.string.state_msg_disconnecting));
            statusActivity.getProgressDialog().setCancelable(false);
            statusActivity.getProgressDialog().show();
        }
    }

    @Override
    protected Integer doInBackground(Void... params) {
        StatusActivity statusActivity = activityReference.get();
        ServerManager.setConnectedServerDisconnected(statusActivity);
        if (statusActivity.getServiceInternal() != null) {
            try {
                statusActivity.getServiceInternal().stopVPN(false);
                VpnStatus.lastLevel = ConnectionStatus.LEVEL_NOT_CONNECTED;
            } catch (RemoteException e) {
                Log.e(TAG, "doInBackground: ", e);
            }
        }
        return 0;
    }

    @Override
    protected void onPostExecute(Integer integer) {
        StatusActivity statusActivity = activityReference.get();
        if (statusActivity.isUserManagedDisconnection()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(statusActivity);
            builder.setTitle(statusActivity.getString(R.string.state_disconnected));
            String dialogMessage = statusActivity.getResources().getString(R.string.state_msg_disconnected);
            ForegroundColorSpan foregroundColorSpan = new ForegroundColorSpan(statusActivity
                .getResources().getColor(R.color.colorAccent));
            SpannableStringBuilder ssBuilder = new SpannableStringBuilder(dialogMessage);
            ssBuilder.setSpan(
                foregroundColorSpan,
                0,
                dialogMessage.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            builder.setMessage(ssBuilder);
            statusActivity.getProgressDialog().dismiss();
            builder.setPositiveButton(android.R.string.ok, (dialog, which) -> disconnect());
            builder.setNegativeButton(R.string.text_reconnect, (dialogInterface, i) -> reconnect());
            builder.setCancelable(false);
            builder.show();
        }
    }

    private void disconnect() {
        StatusActivity statusActivity = activityReference.get();
        statusActivity.getMainIntent().putExtra(AppConstants.DISCONNECT_VPN.toString(), true);
        VpnStatus.removeStateListener(statusActivity);
        VpnStatus.removeByteCountListener(statusActivity);
        statusActivity.setResult(Activity.RESULT_CANCELED);
        statusActivity.finish();
    }

    private void reconnect() {
        StatusActivity statusActivity = activityReference.get();
        Intent intent = new Intent();
        Server server = (Server) statusActivity.getIntent().getSerializableExtra(AppConstants.SERVER.toString());
        intent.putExtra(AppConstants.SERVER.toString(), server);
        statusActivity.setResult(Activity.RESULT_FIRST_USER, intent);
        statusActivity.finish();
    }

}
