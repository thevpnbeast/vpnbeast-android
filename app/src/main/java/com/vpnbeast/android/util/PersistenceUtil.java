package com.vpnbeast.android.util;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import com.vpnbeast.android.model.entity.Server;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PersistenceUtil {

    private static final String TAG = "PersistenceUtil";
    private static final String SELECTED_SERVER_FILENAME = "server.data";
    private static final String ALL_SERVERS_FILENAME = "servers.data";

    public static List<Server> readAllServers(Context context) {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(new File(context
                .getFilesDir(),"") + File.separator + ALL_SERVERS_FILENAME))) {
            boolean isCompleted = false;
            List<Server> serverList = new ArrayList<>();
            while (!isCompleted) {
                try {
                    Server server = (Server) in.readObject();
                    serverList.add(server);
                } catch (EOFException e) {
                    isCompleted = true;
                }
            }
            Log.i(TAG, "readAllServers: " + serverList.size() + " server found!");
            return serverList;
        } catch (IOException | ClassNotFoundException e) {
            Log.e(TAG, "readAllServers: catched Exception!", e);
            return new ArrayList<>();
        }
    }

    public static void writeAllServers(Context context, JSONArray responseArray) {
        clearFileContent(context, ALL_SERVERS_FILENAME);
        try (ObjectOutput out = new ObjectOutputStream(new FileOutputStream(new File(context
                .getFilesDir(),"") + File.separator + ALL_SERVERS_FILENAME))) {
            final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",
                    Locale.getDefault());
            for (int i = 0; i < responseArray.length(); i++) {
                JSONObject responseObject = (JSONObject) responseArray.get(i);
                // TODO: Let the backend return that values. We should not do anything on data.
                String confData = responseObject.getString("confData");
                confData += "\n\npreresolve";
                confData += "\nmanagement-query-proxy";
                confData += "\nmanagement /data/user/0/com.vpnbeast.android/cache/mgmtsocket unix";
                confData += "\nmanagement-client";
                confData += "\nmanagement-query-passwords";
                confData += "\nmanagement-hold";
                confData += "\n\nsetenv IV_GUI_VER \"com.vpnbeast.android 1.0\"";
                confData += "\nmachine-readable-output";
                confData += "\nallow-recursive-routing";
                confData += "\nifconfig-nowarn";
                Server server = Server.builder()
                        .id(responseObject.getLong("id"))
                        .uuid(responseObject.getString("uuid"))
                        .createdAt(format.parse(responseObject.getString("createdAt")))
                        .version(responseObject.getInt("version"))
                        .hostname(responseObject.getString("hostname"))
                        .ip(responseObject.getString("ip"))
                        .proto(responseObject.getString("proto"))
                        .countryLong(responseObject.getString("countryLong"))
                        .port(responseObject.getInt("port"))
                        .enabled(responseObject.getBoolean("enabled"))
                        .speed(responseObject.getLong("speed"))
                        .numVpnSessions(responseObject.getLong("numVpnSessions"))
                        .ping(responseObject.getLong("ping"))
                        .confData(confData)
                        .allowedAppsVpnAreDisallowed(true)
                        .allowedAppsVpn(new HashSet<>())
                        .persistTun(true)
                        .allowLocalLAN(Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
                        .build();

                out.writeObject(server);
            }
        } catch (IOException | JSONException | ParseException e) {
            Log.e(TAG, "writeAllServers: ", e);
        }
    }

    private static void clearFileContent(Context context, String filename) {
        try (FileOutputStream fos = context.openFileOutput(filename, Context.MODE_PRIVATE)) {
            fos.write(("").getBytes());
        } catch (IOException e) {
            Log.e(TAG, "writeSelectedServer: ", e);
        }
    }

    public static void writeSelectedServerConf(Context context, Server server) {
        clearFileContent(context, SELECTED_SERVER_FILENAME);
        try (FileOutputStream fos = context.openFileOutput(SELECTED_SERVER_FILENAME, Context.MODE_PRIVATE)) {
            fos.write(server.getConfData().getBytes());
        } catch (IOException e) {
            Log.e(TAG, "writeSelectedServer: ", e);
        }
    }

    public static String getServerConfPath(Context context) {
        return context.getFilesDir() + File.separator + SELECTED_SERVER_FILENAME;
    }

    public static String readSelectedServerConf(Context context) {
        try (FileInputStream fis = context.openFileInput(SELECTED_SERVER_FILENAME);
             InputStreamReader isr = new InputStreamReader(fis);
             BufferedReader bufferedReader = new BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null)
                sb.append(line).append("\n");
            return sb.toString();
        } catch (IOException e) {
            Log.e(TAG, "readAllServers: ", e);
            return null;
        }
    }

}