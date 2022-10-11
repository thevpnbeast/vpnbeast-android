package com.vpnbeast.android.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.vpnbeast.android.model.entity.Server;
import com.vpnbeast.android.model.entity.User;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PreferencesUtil {

    private static final Gson gson = new Gson();

    public static SharedPreferences getDefaultSharedPreferences(Context c) {
        return c.getSharedPreferences(c.getPackageName() + "_preferences",
                Context.MODE_MULTI_PROCESS | Context.MODE_PRIVATE);
    }

    public static void storeUser(Context context, User user, String key) {
        SharedPreferences preferences = PreferencesUtil.getDefaultSharedPreferences(context.getApplicationContext());
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(key, convertUserToString(user));
        editor.apply();
    }

    public static void storeServer(Context context, Server server, String key) {
        SharedPreferences preferences = PreferencesUtil.getDefaultSharedPreferences(context.getApplicationContext());
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(key, convertServerToString(server));
        editor.apply();
    }

    public static User getUser(Context context, String key) {
        SharedPreferences preferences = PreferencesUtil.getDefaultSharedPreferences(context);
        String jsonString = preferences.getString(key, null);
        return convertStringToUser(jsonString);
    }

    public static Server getServer(Context context, String key) {
        SharedPreferences preferences = PreferencesUtil.getDefaultSharedPreferences(context);
        String jsonString = preferences.getString(key, null);
        return convertStringToServer(jsonString);
    }

    private static String convertUserToString(User user) {
        return gson.toJson(user);
    }

    private static String convertServerToString(Server server) {
        return gson.toJson(server);
    }

    private static User convertStringToUser(String jsonString) {
        return gson.fromJson(jsonString, User.class);
    }

    private static Server convertStringToServer(String jsonString) {
        return gson.fromJson(jsonString, Server.class);
    }

}