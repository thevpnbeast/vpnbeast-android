package com.vpnbeast.android.util;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import com.vpnbeast.android.R;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class NetworkUtil {

    private static boolean isNetworkAvailable(Context context) {
        final ConnectivityManager connectivityManager = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
        return Objects.requireNonNull(connectivityManager).getActiveNetworkInfo() != null &&
                Objects.requireNonNull(connectivityManager.getActiveNetworkInfo()).isConnected();
    }

    public static void checkNetworkAvailability(Context context) {
        if (!NetworkUtil.isNetworkAvailable(context))
            ViewUtil.showSingleButtonAlertDialog(context, context.getString(R.string.error),
                    context.getString(R.string.err_nonetwork_msg),
                    (dialog, which) -> ((Activity) context).finish());
    }

}