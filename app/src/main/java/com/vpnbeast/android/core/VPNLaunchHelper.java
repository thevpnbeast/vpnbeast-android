package com.vpnbeast.android.core;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Parcelable;
import android.util.Log;
import com.vpnbeast.android.R;
import com.vpnbeast.android.model.entity.Server;
import com.vpnbeast.android.model.enums.AppConstants;
import com.vpnbeast.android.util.PersistenceUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import de.blinkt.openvpn.core.NativeUtils;

// TODO: This class should be static or object? Investigate pros and cons!
public class VPNLaunchHelper {

    private static final String MINI_PIE_VPN;
    private static final String TAG;

    static {
        MINI_PIE_VPN = "pie_openvpn";
        TAG = VPNLaunchHelper.class.getName();
    }

    private VPNLaunchHelper() {

    }

    private static String writeMiniVPN(Context context) {
        String[] abis;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            abis = getSupportedABIsLollipop();
        else
            abis = new String[]{Build.CPU_ABI, Build.CPU_ABI2};
        String nativeAPI = NativeUtils.getNativeAPI();
        if (!nativeAPI.equals(abis[0])) {
            VpnStatus.logWarning(R.string.abi_mismatch, Arrays.toString(abis), nativeAPI);
            abis = new String[] {nativeAPI};
        }
        for (String abi: abis) {
            File vpnExecutable = new File(context.getCacheDir(), "c_" + getMiniVPNExecutableName() +
                    "." + abi);
            if ((vpnExecutable.exists() && vpnExecutable.canExecute()) || writeMiniVPNBinary(context,
                    abi, vpnExecutable)) {
                return vpnExecutable.getPath();
            }
        }
        return null;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static String[] getSupportedABIsLollipop() {
        return Build.SUPPORTED_ABIS;
    }

    private static String getMiniVPNExecutableName() {
        return MINI_PIE_VPN;
    }

    static String[] buildOpenvpnArgv(Context c) {
        ArrayList<String> args = new ArrayList<>();
        String binaryName = writeMiniVPN(c);

        if (binaryName==null) {
            VpnStatus.logError("Error writing minivpn binary");
            return new String[0];
        }

        args.add(binaryName);
        args.add("--config");
        args.add(PersistenceUtil.getServerConfPath(c));
        return args.toArray(new String[0]);
    }

    private static boolean writeMiniVPNBinary(Context context, String abi, File mvpnout) {
        try (InputStream inputStream = context.getAssets().open(getMiniVPNExecutableName() + "." + abi);
             OutputStream outputStream = new FileOutputStream(mvpnout)) {
            byte[] buf = new byte[4096];
            int lenread = inputStream.read(buf);
            while(lenread> 0) {
                outputStream.write(buf, 0, lenread);
                lenread = inputStream.read(buf);
            }

            if (!mvpnout.setExecutable(true, false)) {
                VpnStatus.logError("Failed to make OpenVPN executable");
                return false;
            }

            return true;
        } catch (IOException e) {
            VpnStatus.logException(e);
            Log.e(TAG, "writeMiniVPNBinary: ", e);
            return false;
        }
    }

    public static void startOpenVpn(Server server, Context context) {
        Intent intent = new Intent(context, OpenVPNService.class);
        intent.putExtra(AppConstants.SERVER.toString(), server);
        context.startService(intent);
    }

}