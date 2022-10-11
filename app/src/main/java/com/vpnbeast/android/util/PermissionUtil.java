package com.vpnbeast.android.util;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PermissionUtil {

    private static final int REQUEST_CODE_ASK_PERMISSIONS = 1;

    public static void checkPermissions(Context context, String[] requiredPermissions) {
        final List<String> missingPermissions = new ArrayList<>();

        for (final String permission : requiredPermissions) {
            final int result = ContextCompat.checkSelfPermission(context, permission);
            if (result != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if (!missingPermissions.isEmpty()) {
            final String[] permissions = missingPermissions.toArray(new String[0]);
            ActivityCompat.requestPermissions(((Activity) context), permissions, REQUEST_CODE_ASK_PERMISSIONS);
        } else {
            final int[] grantResults = new int[requiredPermissions.length];
            Arrays.fill(grantResults, PackageManager.PERMISSION_GRANTED);
            ((AppCompatActivity) context).onRequestPermissionsResult(REQUEST_CODE_ASK_PERMISSIONS,
                    requiredPermissions, grantResults);
        }
    }

}