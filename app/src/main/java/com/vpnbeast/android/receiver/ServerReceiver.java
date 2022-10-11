package com.vpnbeast.android.receiver;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import com.vpnbeast.android.R;
import com.vpnbeast.android.model.entity.User;
import com.vpnbeast.android.model.enums.AppConstants;
import com.vpnbeast.android.service.RefreshService;
import com.vpnbeast.android.util.PersistenceUtil;
import com.vpnbeast.android.util.PreferencesUtil;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.Objects;

public class ServerReceiver extends BroadcastReceiver {

    private static final String TAG = "ServerReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (intent.getBooleanExtra(AppConstants.TOKEN_EXPIRED.toString(), false)) {
                startRefreshService(context);
            } else {
                ((Activity) context).findViewById(R.id.progressBarMain).setVisibility(View.INVISIBLE);
                final String action = Objects.requireNonNull(intent.getAction());
                if (action.equals(AppConstants.GET_ALL_SERVERS.toString())) {
                    JSONArray responseArray = new JSONArray(intent.getStringExtra(AppConstants.ALL_SERVERS.toString()));
                    PersistenceUtil.writeAllServers(context, responseArray);
                }
            }
        } catch (JSONException | NullPointerException e) {
            Log.e(TAG, "onReceive: catched Exception!", e);
        }
    }

    private void startRefreshService(Context context) {
        User user = PreferencesUtil.getUser(context, AppConstants.USER.toString());
        Intent intent = new Intent(context, RefreshService.class);
        intent.setAction(AppConstants.DO_REFRESH.toString());
        intent.putExtra(AppConstants.START_SERVER_SERVICE.toString(), true);
        intent.putExtra(AppConstants.REFRESH_TOKEN.toString(), user.getRefreshToken());
        context.startService(intent);
    }

}