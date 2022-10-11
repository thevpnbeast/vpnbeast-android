package com.vpnbeast.android.core;

import android.content.Context;
import android.os.Build;
import android.os.Message;
import android.util.Log;
import com.vpnbeast.android.R;
import com.vpnbeast.android.model.enums.PauseReason;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.Locale;
import de.blinkt.openvpn.core.NativeUtils;

public class VpnStatus {

    private static final String TAG;
    private static LinkedList<LogItem> logBufferList;
    private static LinkedList<LogListener> logListenerList;
    private static LinkedList<StateListener> stateListenerList;
    private static LinkedList<ByteCountListener> byteCountListenerList;
    private static String lastStateString;
    private static String lastState;
    private static int lastStateResId;
    private static long[] lastByteCount;
    private static String lastConnectedServerUuid;
    public static final int BYTE_COUNT_INTERVAL;
    private static LogFileHandler logFileHandler;
    public static ConnectionStatus lastLevel;

    static boolean readFileLog;
    static final Object READ_FILE_LOCK;
    static final int MAX_LOG_ENTRIES;
    static final byte[] OFFICIAL_KEY;
    static final byte[] OFFICIAL_DEBUG_KEY;
    static final byte[] AMAZON_KEY;
    static final byte[] FDROID_KEY;

    static {
        TAG = "VpnStatus";
        BYTE_COUNT_INTERVAL = 2;
        READ_FILE_LOCK = new Object();
        MAX_LOG_ENTRIES = 1000;
        OFFICIAL_KEY = new byte[]{-58, -42, -44, -106, 90, -88, -87, -88, -52, -124, 84, 117,
                66, 79, -112, -111, -46, 86, -37, 109};
        OFFICIAL_DEBUG_KEY = new byte[]{-99, -69, 45, 71, 114, -116, 82, 66, -99, -122, 50,
                -70, -56, -111, 98, -35, -65, 105, 82, 43};
        AMAZON_KEY = new byte[]{-116, -115, -118, -89, -116, -112, 120, 55, 79, -8, -119, -23,
                106, -114, -85, -56, -4, 105, 26, -57};
        FDROID_KEY = new byte[]{-92, 111, -42, -46, 123, -96, -60, 79, -27, -31, 49, 103, 11,
                -54, -68, -27, 17, 2, 121, 104};

        readFileLog = false;
        lastLevel = ConnectionStatus.LEVEL_NOT_CONNECTED;
        logBufferList = new LinkedList<>();
        logListenerList = new LinkedList<>();
        stateListenerList = new LinkedList<>();
        byteCountListenerList = new LinkedList<>();
        lastStateString = "";
        lastState = "NOPROCESS";
        lastStateResId = R.string.state_noprocess;
        lastByteCount = new long[]{0, 0, 0, 0};
        logInformation();
    }

    private static void logException(LogLevel ll, String context, Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        LogItem li;
        if (context != null) {
            li = new LogItem(ll, R.string.unhandled_exception_context, e.getMessage(), sw.toString(),
                    context);
        } else {
            li = new LogItem(ll, R.string.unhandled_exception, e.getMessage(), sw.toString());
        }
        newLogItem(li);
    }

    static void logException(Exception e) {
        logException(LogLevel.ERROR, null, e);
    }

    public static void logException(String context, Exception e) {
        logException(LogLevel.ERROR, context, e);
    }

    public static boolean isVPNActive() {
        return lastLevel != ConnectionStatus.LEVEL_AUTH_FAILED && lastLevel != ConnectionStatus.LEVEL_NOT_CONNECTED;
    }

    static String getLastCleanLogMessage(Context c) {
        String message = lastStateString;
        if (lastLevel == ConnectionStatus.LEVEL_CONNECTED) {
            String[] parts = lastStateString.split(",");
                /*
                   (a) the integer unix date/time,
                   (b) the state name,
                   0 (c) optional descriptive string (used mostly on RECONNECTING
                    and EXITING to show the reason for the disconnect),

                    1 (d) optional TUN/TAP local IPv4 address
                   2 (e) optional address of remote server,
                   3 (f) optional port of remote server,
                   4 (g) optional local address,
                   5 (h) optional local port, and
                   6 (i) optional TUN/TAP local IPv6 address.
*/
            // Return only the assigned IP addresses in the UI
            if (parts.length >= 7)
                message = String.format(Locale.US, "%s %s", parts[1], parts[6]);
        }

        while (message.endsWith(","))
            message = message.substring(0, message.length() - 1);

        String status = lastState;
        if (status.equals("NOPROCESS"))
            return message;

        if (lastStateResId == R.string.state_waitconnectretry) {
            return c.getString(R.string.state_waitconnectretry, lastStateString);
        }

        String prefix = c.getString(lastStateResId);
        if (lastStateResId == R.string.state_unknown)
            message = status + message;

        if (message.length() > 0)
            prefix += ": ";

        return prefix + message;
    }

    static void setConnectedServerUuid(String uuid) {
        lastConnectedServerUuid = uuid;
        for (StateListener sl: stateListenerList)
            sl.setConnectedVPN(uuid);
    }

    static String getLastConnectedServerUuid() {
        return lastConnectedServerUuid;
    }

    public enum LogLevel {
        INFO(2),
        ERROR(-2),
        WARNING(1),
        VERBOSE(3),
        DEBUG(4);

        protected int mValue;

        LogLevel(int value) {
            mValue = value;
        }

        public int getInt() {
            return mValue;
        }

        public static LogLevel getEnumByValue(int value) {
            switch (value) {
                case 2:
                    return INFO;
                case -2:
                    return ERROR;
                case 1:
                    return WARNING;
                case 3:
                    return VERBOSE;
                case 4:
                    return DEBUG;
                default:
                    return null;
            }
        }
    }

    public static synchronized void logMessage(LogLevel level, String prefix, String message) {
        newLogItem(new LogItem(level, prefix + message));
    }

    public static synchronized void clearLog() {
        logBufferList.clear();
        logInformation();

        if (logFileHandler != null)
            logFileHandler.sendEmptyMessage(LogFileHandler.TRIM_LOG_FILE);
    }

    private static void logInformation() {
        String nativeAPI;
        try {
            nativeAPI = NativeUtils.getNativeAPI();
        } catch (UnsatisfiedLinkError ignore) {
            nativeAPI = "error";
        }

        logInfo(R.string.mobile_info, Build.MODEL, Build.BOARD, Build.BRAND, Build.VERSION.SDK_INT,
                nativeAPI, Build.VERSION.RELEASE, Build.ID, Build.FINGERPRINT, "", "");
    }

    static synchronized void addLogListener(LogListener ll) {
        logListenerList.add(ll);
    }

    static synchronized void removeLogListener(LogListener ll) {
        logListenerList.remove(ll);
    }

    public static synchronized void addByteCountListener(ByteCountListener bcl) {
        bcl.updateByteCount(lastByteCount[0], lastByteCount[1], lastByteCount[2], lastByteCount[3]);
        byteCountListenerList.add(bcl);
    }

    public static synchronized void removeByteCountListener(ByteCountListener bcl) {
        byteCountListenerList.remove(bcl);
    }


    public static synchronized void addStateListener(StateListener sl) {
        if (!stateListenerList.contains(sl)) {
            stateListenerList.add(sl);
            if (lastState != null)
                sl.updateState(lastState, lastStateString, lastStateResId, lastLevel);
        }
    }

    private static int getLocalizedState(String state) {
        switch (state) {
            case "CONNECTING":
                return R.string.state_connecting;
            case "WAIT":
                return R.string.state_wait;
            case "AUTH":
                return R.string.state_auth;
            case "GET_CONFIG":
                return R.string.state_get_config;
            case "ASSIGN_IP":
                return R.string.state_assign_ip;
            case "ADD_ROUTES":
                return R.string.state_add_routes;
            case "CONNECTED":
                return R.string.state_connected;
            case "DISCONNECTED":
                return R.string.state_disconnected;
            case "RECONNECTING":
                return R.string.state_reconnecting;
            case "EXITING":
                return R.string.state_exiting;
            case "RESOLVE":
                return R.string.state_resolve;
            case "TCP_CONNECT":
                return R.string.state_tcp_connect;
            default:
                return R.string.state_unknown;
        }
    }

    static void updateStatePause(PauseReason pauseReason) {
        switch (pauseReason) {
            case NO_NETWORK:
                VpnStatus.updateStateString("NONETWORK", "", R.string.state_nonetwork,
                        ConnectionStatus.LEVEL_NO_NETWORK);
                break;
            case SCREEN_OFF:
                VpnStatus.updateStateString("SCREENOFF", "", R.string.state_screenoff,
                        ConnectionStatus.LEVEL_VPN_PAUSED);
                break;
            case USER_PAUSE:
                VpnStatus.updateStateString("USERPAUSE", "", R.string.state_userpause,
                        ConnectionStatus.LEVEL_VPN_PAUSED);
                break;
        }
    }

    private static ConnectionStatus getLevel(String state) {
        String[] noReplyYet = {"CONNECTING", "WAIT", "RECONNECTING", "RESOLVE", "TCP_CONNECT"};
        String[] reply = {"AUTH", "GET_CONFIG", "ASSIGN_IP", "ADD_ROUTES"};
        String[] connected = {"CONNECTED"};
        String[] notConnected = {"DISCONNECTED", "EXITING"};

        for (String x : noReplyYet)
            if (state.equals(x))
                return ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET;

        for (String x : reply)
            if (state.equals(x))
                return ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED;

        for (String x : connected)
            if (state.equals(x))
                return ConnectionStatus.LEVEL_CONNECTED;

        for (String x : notConnected)
            if (state.equals(x))
                return ConnectionStatus.LEVEL_NOT_CONNECTED;

        return ConnectionStatus.UNKNOWN_LEVEL;
    }

    public static synchronized void removeStateListener(StateListener sl) {
        stateListenerList.remove(sl);
    }

    static synchronized LogItem[] getLogBufferList() {
        // The stoned way of java to return an array from a vector
        // brought to you by eclipse auto complete
        return logBufferList.toArray(new LogItem[logBufferList.size()]);
    }

    static void updateStateString(String state, String msg) {
        Log.i(TAG, "updateStateString: state = " + state + " msg = " + msg);
        int rid = getLocalizedState(state);
        ConnectionStatus level = getLevel(state);
        lastLevel = level;
        updateStateString(state, msg, rid, level);
    }

    static void logInfo(String message) {
        newLogItem(new LogItem(LogLevel.INFO, message));
    }

    public static void logDebug(String message) {
        newLogItem(new LogItem(LogLevel.DEBUG, message));
    }

    static void logInfo(int resourceId, Object... args) {
        newLogItem(new LogItem(LogLevel.INFO, resourceId, args));
    }

    static void logDebug(int resourceId, Object... args) {
        newLogItem(new LogItem(LogLevel.DEBUG, resourceId, args));
    }

    static void newLogItem(LogItem logItem) {
        newLogItem(logItem, false);
    }

    private static synchronized void newLogItem(LogItem logItem, boolean cachedLine) {
        if (cachedLine) {
            logBufferList.addFirst(logItem);
        } else {
            logBufferList.addLast(logItem);
            if (logFileHandler != null) {
                Message m = logFileHandler.obtainMessage(LogFileHandler.LOG_MESSAGE, logItem);
                logFileHandler.sendMessage(m);
            }
        }

        if (logBufferList.size() > MAX_LOG_ENTRIES + MAX_LOG_ENTRIES / 2) {
            while (logBufferList.size() > MAX_LOG_ENTRIES)
                logBufferList.removeFirst();
            if (logFileHandler != null)
                logFileHandler.sendMessage(logFileHandler.obtainMessage(LogFileHandler.TRIM_LOG_FILE));
        }

        for (LogListener ll : logListenerList) {
            ll.newLog(logItem);
        }
    }

    static void logError(String msg) {
        newLogItem(new LogItem(LogLevel.ERROR, msg));
    }

    static void logWarning(int resourceId, Object... args) {
        newLogItem(new LogItem(LogLevel.WARNING, resourceId, args));
    }

    static void logWarning(String msg) {
        newLogItem(new LogItem(LogLevel.WARNING, msg));
    }

    public static void logError(int resourceId) {
        newLogItem(new LogItem(LogLevel.ERROR, resourceId));
    }
    public static void logError(int resourceId, Object... args) {
        newLogItem(new LogItem(LogLevel.ERROR, resourceId, args));
    }

    public static void logMessageOpenVPN(LogLevel level, int ovpnlevel, String message) {
        newLogItem(new LogItem(level, ovpnlevel, message));

    }

    public static synchronized void updateByteCount(long in, long out) {
        long lastIn = lastByteCount[0];
        long lastOut = lastByteCount[1];
        long diffIn = lastByteCount[2] = Math.max(0, in - lastIn);
        long diffOut = lastByteCount[3] = Math.max(0, out - lastOut);


        lastByteCount = new long[]{in, out, diffIn, diffOut};
        for (ByteCountListener bcl : byteCountListenerList) {
            bcl.updateByteCount(in, out, diffIn, diffOut);
        }
    }

    public static synchronized void updateStateString(String state, String msg, int resid,
                                                      ConnectionStatus level) {
        // Workound for OpenVPN doing AUTH and wait and being connected
        // Simply ignore these state
        if (lastLevel == ConnectionStatus.LEVEL_CONNECTED &&
                (state.equals("WAIT") || state.equals("AUTH"))) {
            newLogItem(new LogItem((LogLevel.DEBUG), String.format("Ignoring OpenVPN Status in " +
                    "CONNECTED state (%s->%s): %s", state, level.toString(), msg)));
            return;
        }

        lastState = state;
        lastStateString = msg;
        lastStateResId = resid;
        lastLevel = level;

        for (StateListener sl : stateListenerList) {
            sl.updateState(state, msg, resid, level);
        }
        newLogItem(new LogItem((LogLevel.DEBUG), String.format("New OpenVPN Status (%s->%s): %s",state,level.toString(),msg)));
    }

}