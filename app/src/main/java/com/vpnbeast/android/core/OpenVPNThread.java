package com.vpnbeast.android.core;

import android.util.Log;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;

public class OpenVPNThread implements Runnable {

    private static final String TAG = "OpenVPNThread";
    private static final String DUMP_PATH_STRING = "Dump path: ";

    private String[] argvArray;
    private Process process;
    private String nativeDir;
    private OpenVPNService openvpnService;
    private String dumpPath;

    OpenVPNThread(OpenVPNService openvpnService, String[] argvArray, String nativeDir) {
        this.argvArray = argvArray;
        this.nativeDir = nativeDir;
        this.openvpnService = openvpnService;
    }

    private void stopProcess() {
        process.destroy();
    }

    @Override
    public void run() {
        try {
            startOpenVPNThreadArgs(argvArray);
        } catch (Exception e) {
            Log.e(TAG, "OpenVPNThread Got " + e.toString());
        } finally {
            try {
                if (process != null)
                    process.waitFor();
            } catch (InterruptedException | IllegalThreadStateException ie) {
                Log.e(TAG, "run: ", ie);
            }

            if (dumpPath != null) {
                try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(dumpPath + ".log"))) {
                    SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMAN);
                    for (LogItem li : VpnStatus.getLogBufferList()) {
                        String time = timeFormat.format(new Date(li.getLogTime()));
                        bufferedWriter.write(time + " " + li.getString(openvpnService) + "\n");
                    }
                } catch (IOException e) {
                    Log.e(TAG, "run: ", e);
                }
            }

            openvpnService.processDied();
            Log.i(TAG, "Exiting");
        }
    }

    private void startOpenVPNThreadArgs(String[] argv) {
        LinkedList<String> argvList = new LinkedList<>();
        Collections.addAll(argvList, argv);
        ProcessBuilder pb = new ProcessBuilder(argvList);
        String lbPath = genLibraryPath(argv, pb);
        pb.environment().put("LD_LIBRARY_PATH", lbPath);
        pb.redirectErrorStream(true);
        try {
            process = pb.start();
            // Close the output, since we don't need it
            process.getOutputStream().close();
            InputStream in = process.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            while (true) {
                String logLine = br.readLine();
                Log.i(TAG, "startOpenVPNThreadArgs: logline = " + logLine);

                if (logLine == null)
                    return;

                if (logLine.startsWith(DUMP_PATH_STRING)) {
                    dumpPath = logLine.substring(DUMP_PATH_STRING.length());
                    Log.i(TAG, "startOpenVPNThreadArgs: dumpPath = " + dumpPath);
                }

                if (Thread.interrupted()) {
                    throw new InterruptedException("OpenVpn process was killed form java code");
                }
            }
        } catch (InterruptedException | IOException e) {
            Log.e(TAG, "startOpenVPNThreadArgs: ", e);
            stopProcess();
        }
    }

    private String genLibraryPath(String[] argv, ProcessBuilder pb) {
        String libPath = argv[0].replaceFirst("/cache/.*$", "/lib");

        String lbpath = pb.environment().get("LD_LIBRARY_PATH");
        if (lbpath == null)
            lbpath = libPath;
        else
            lbpath = libPath + ":" + lbpath;

        if (!libPath.equals(nativeDir))
            lbpath = nativeDir + ":" + lbpath;

        Log.i(TAG, "genLibraryPath: lbpath = " + lbpath);
        return lbpath;
    }

}