package com.vpnbeast.android.core;

import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import androidx.annotation.NonNull;
import com.vpnbeast.android.R;
import com.vpnbeast.android.model.entity.Server;
import com.vpnbeast.android.model.enums.AppConstants;
import com.vpnbeast.android.model.enums.PauseReason;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import de.blinkt.openvpn.core.NativeUtils;

public class OpenVPNManagementThread implements Runnable, OpenVPNManagement {

    private static final String TAG = "OpenVPNManagementThread";
    private final List<OpenVPNManagementThread> activeThreadList = new ArrayList<>();
    private OpenVPNService openVpnService;
    private Handler resumeHandler;
    private LocalSocket localSocket;
    private Server server;
    private LinkedList<FileDescriptor> fdList = new LinkedList<>();
    private LocalServerSocket localServerSocket;
    private boolean waitingForRelease = false;
    private long lastHoldRelease = 0;
    private PauseReason lastPauseReason = PauseReason.NO_NETWORK;
    private PausedStateCallback pauseCallback;
    private boolean isShuttingDown;

    OpenVPNManagementThread(Server server, OpenVPNService openVpnService) {
        this.server = server;
        this.openVpnService = openVpnService;
        init();
    }

    private void init() {
        resumeHandler = new Handler(openVpnService.getMainLooper());
    }

    private Runnable mResumeHoldRunnable = () -> {
        if (shouldBeRunning()) {
            releaseHoldCmd();
        }
    };

    boolean openManagementInterface(@NonNull Context c) {
        // Could take a while to open connection
        int tries = 8;
        boolean succeeded = false;
        String socketName = (c.getCacheDir().getAbsolutePath() + "/" + "mgmtsocket");
        // The mServerSocketLocal is transferred to the LocalServerSocket, ignore warning
        localSocket = new LocalSocket();
        while (tries > 0 && !localSocket.isBound()) {
            try {
                localSocket.bind(new LocalSocketAddress(socketName,
                        LocalSocketAddress.Namespace.FILESYSTEM));
            } catch (IOException e) {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException interrupted) {
                    Log.e(TAG, "openManagementInterface: ", interrupted);
                }
            }
            tries--;
        }

        try {
            localServerSocket = new LocalServerSocket(localSocket.getFileDescriptor());
            succeeded = true;
        } catch (IOException e) {
            Log.e(TAG, "openManagementInterface: ", e);
        }

        return succeeded;
    }

    private boolean managementCommand(String cmd) {
        try {
            if (localSocket != null) {
                localSocket.getOutputStream().write(cmd.getBytes());
                localSocket.getOutputStream().flush();
                return true;
            }
        } catch (IOException e) {
            Log.w(TAG, "managementCommand: ", e);
        }
        return false;
    }

    private FileDescriptor[] readFdsFromSocket() {
        try {
            return localSocket.getAncillaryFileDescriptors();
        } catch (IOException e) {
            VpnStatus.logException("Error reading fds from socket", e);
            return new FileDescriptor[]{};
        }
    }

    @Override
    public void run() {
        byte[] buffer = new byte[2048];
        //	localSocket.setSoTimeout(5); // Setting a timeout cannot be that bad
        StringBuilder pendingInput = new StringBuilder();
        synchronized (activeThreadList) {
            activeThreadList.add(this);
        }
        try {
            // Wait for a client to connect
            Log.i(TAG, "run: setting localSocket");
            localSocket = localServerSocket.accept();

            if (localSocket != null) {
                InputStream inputStream = localSocket.getInputStream();
                // Close the management socket after client connected
                localServerSocket.close();

                // Closing one of the two sockets also closes the other
                //mServerSocketLocal.close();
                Log.i(TAG, "run: isShuttingDown = " + isShuttingDown);
                while (!isShuttingDown) {
                    int numBytesRead = inputStream.read(buffer);
                    if (numBytesRead == -1)
                        return;

                    FileDescriptor[] fds = readFdsFromSocket();

                    if (fds != null)
                        Collections.addAll(fdList, fds);

                    String input = new String(buffer, 0, numBytesRead, AppConstants.CHARSET.toString());
                    pendingInput.append(input);
                    pendingInput = new StringBuilder(processInput(pendingInput.toString()));
                }
            }

            if (localSocket == null)
                Log.i(TAG, "run: local socket is still null");
            else
                Log.i(TAG, "run: local socket is still NOT null");

        } catch (IOException e) {
            Log.e(TAG, "run: ", e);
        }

        synchronized (activeThreadList) {
            activeThreadList.remove(this);
        }
    }


    private void protectFileDescriptor(FileDescriptor fd) {
        try {
            // TODO: Fix below problem somehow
            Method getInt = FileDescriptor.class.getDeclaredMethod("getInt$");
            int fdInt = (int) getInt.invoke(fd);

            if (!openVpnService.protect(fdInt))
                VpnStatus.logWarning("Could not protect VPN socket");

            NativeUtils.jniclose(fdInt);
        } catch (NoSuchMethodException | IllegalArgumentException | InvocationTargetException | IllegalAccessException | NullPointerException e) {
            Log.e(TAG, "protectFileDescriptor: Failed to retrieve fd from socket (" + fd + ")", e);
        }
    }

    private String processInput(String pendingInput) {
        while (pendingInput.contains("\n")) {
            String[] tokens = pendingInput.split("\\r?\\n", 2);
            Log.i(TAG, "processInput: inside. tokens = " + Arrays.toString(tokens));
            processCommand(tokens[0]);
            if (tokens.length == 1)
                pendingInput = "";
            else
                pendingInput = tokens[1];
        }
        return pendingInput;
    }


    private void processCommand(String command) {
        Log.i(TAG, "processCommand: command = " + command);
        if (command.startsWith(">") && command.contains(":")) {
            String[] parts = command.split(":", 2);
            String cmd = parts[0].substring(1);
            final String argument = parts[1];
            switch (cmd) {
                case "INFO":
                    return;
                case "PASSWORD":
                    break;
                case "HOLD":
                    handleHold();
                    break;
                case "NEED-OK":
                    processNeedCommand(argument);
                    break;
                case "BYTECOUNT":
                    processByteCount(argument);
                    break;
                case "STATE":
                    if (!isShuttingDown)
                        processState(argument);
                    break;
                case "PROXY":
                    processProxyCMD(argument);
                    break;
                case "LOG":
                    processLogMessage(argument);
                    break;
                case "RSA_SIGN":
                    Log.i(TAG, "processCommand: got RSA_SIGN, NOT IMPLEMENTED!");
                    break;
                default:
                    Log.w(TAG, "processCommand: MGMT: Got unrecognized command" + command);
                    break;
            }
        } else if (command.startsWith("SUCCESS:")) {
            Log.i(TAG, "processCommand: SUCCESS");
        } else if (command.startsWith("PROTECTFD: ")) {
            FileDescriptor fdToProtect = fdList.pollFirst();
            if (fdToProtect != null)
                protectFileDescriptor(fdToProtect);
        } else {
            Log.w(TAG, "processCommand: Got unrecognized line from managment" + command);
        }
    }


    private void processLogMessage(String argument) {
        String[] args = argument.split(",", 4);
        Log.i(TAG, "processLogMessage: args = " + Arrays.toString(args));
        // 0 unix time stamp
        // 1 log level N,I,E etc.
                /*
                  (b) zero or more message flags in a single string:
          I -- informational
          F -- fatal error
          N -- non-fatal error
          W -- warning
          D -- debug, and
                 */
        // 2 log message

        // TODO: Handle logging
        /*VpnStatus.LogLevel level;
        switch (args[1]) {
            case "I":
                level = VpnStatus.LogLevel.INFO;
                break;
            case "W":
                level = VpnStatus.LogLevel.WARNING;
                break;
            case "D":
                level = VpnStatus.LogLevel.VERBOSE;
                break;
            case "F":
                level = VpnStatus.LogLevel.ERROR;
                break;
            default:
                level = VpnStatus.LogLevel.INFO;
                break;
        }*/

    }

    private boolean shouldBeRunning() {
        if (pauseCallback == null)
            return false;
        else
            return pauseCallback.shouldBeRunning();
    }

    private void handleHold() {
        int waitTime = 0;
        if (shouldBeRunning()) {
            VpnStatus.updateStateString("CONNECTRETRY", String.valueOf(waitTime),
                    R.string.state_waitconnectretry, ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET);
            resumeHandler.postDelayed(mResumeHoldRunnable, waitTime * 1000);
        } else {
            waitingForRelease = true;
            VpnStatus.updateStatePause(lastPauseReason);
        }
    }

    private void releaseHoldCmd() {
        resumeHandler.removeCallbacks(mResumeHoldRunnable);
        if ((System.currentTimeMillis() - lastHoldRelease) < 5000) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Log.e(TAG, "releaseHoldCmd: ", e);
            }
        }
        waitingForRelease = false;
        lastHoldRelease = System.currentTimeMillis();
        managementCommand("hold release\n");
        managementCommand("bytecount " + VpnStatus.BYTE_COUNT_INTERVAL + "\n");
        managementCommand("state on\n");
    }

    private void releaseHold() {
        if (waitingForRelease)
            releaseHoldCmd();
    }

    private void processProxyCMD(String argument) {
        String[] args = argument.split(",", 3);
        SocketAddress proxyaddr = ProxyListener.detectProxy(server);

        if (args.length >= 2) {
            String proto = args[1];
            if (proto.equals("UDP")) {
                proxyaddr = null;
            }
        }

        if (proxyaddr instanceof InetSocketAddress) {
            InetSocketAddress isa = (InetSocketAddress) proxyaddr;
            VpnStatus.logInfo(R.string.using_proxy, isa.getHostName(), isa.getPort());
            String proxyCmd = String.format(Locale.ENGLISH, "proxy HTTP %s %d\n", isa.getHostName(), isa.getPort());
            managementCommand(proxyCmd);
        } else {
            managementCommand("proxy NONE\n");
        }
    }

    private void processState(String argument) {
        String[] args = argument.split(",", 3);
        String currentstate = args[1];
        Log.i(TAG, "processState: currentstate = " + currentstate);
        Log.i(TAG, "processState: args[2] = " + args[2]);
        if (args[2].equals(",,"))
            VpnStatus.updateStateString(currentstate, "");
        else
            VpnStatus.updateStateString(currentstate, args[2]);
    }

    private void processByteCount(String argument) {
        //   >BYTECOUNT:{BYTES_IN},{BYTES_OUT}
        int comma = argument.indexOf(',');
        long in = Long.parseLong(argument.substring(0, comma));
        long out = Long.parseLong(argument.substring(comma + 1));
        VpnStatus.updateByteCount(in, out);
    }

    private void processRouteCmd(String argument) {
        String[] routeparts = argument.split(" ");
        if (routeparts.length == 5) {
            openVpnService.addRoute(routeparts[0], routeparts[1], routeparts[2], routeparts[4]);
        } else if (routeparts.length >= 3) {
            openVpnService.addRoute(routeparts[0], routeparts[1], routeparts[2], null);
        } else {
            Log.w(TAG, "processRouteCmd: Unrecognized ROUTE cmd:" + Arrays.toString(routeparts) + " | " + argument);
        }
    }

    private void processRoute6Cmd(String argument) {
        String[] routeParts = argument.split(" ");
        openVpnService.addRoutev6(routeParts[0], routeParts[1]);
    }

    private void processProtectFdCmd() {
        FileDescriptor fdToProtect = fdList.pollFirst();
        protectFileDescriptor(fdToProtect);
    }

    private void processIfconfigCmd(String argument) {
        String[] ifconfigparts = argument.split(" ");
        int mtu = Integer.parseInt(ifconfigparts[2]);
        openVpnService.setLocalIP(ifconfigparts[0], ifconfigparts[1], mtu, ifconfigparts[3]);
    }

    private void processNeedCommand(String argument) {
        int p1 = argument.indexOf('\'');
        int p2 = argument.indexOf('\'', p1 + 1);
        String needed = argument.substring(p1 + 1, p2);
        String extra = argument.split(":", 2)[1];
        String status = "ok";
        Log.i(TAG, "processNeedCommand: needed = " + needed);
        Log.i(TAG, "processNeedCommand: extra = " + extra);
        switch (needed) {
            case "PROTECTFD":
                processProtectFdCmd();
                break;
            case "DNSSERVER":
            case "DNS6SERVER":
                openVpnService.addDNS(extra);
                break;
            case "DNSDOMAIN":
                openVpnService.setDomain(extra);
                break;
            case "ROUTE":
                processRouteCmd(extra);
                break;
            case "ROUTE6":
                processRoute6Cmd(extra);
                break;
            case "IFCONFIG":
                processIfconfigCmd(extra);
                break;
            case "IFCONFIG6":
                openVpnService.setLocalIPv6(extra);
                break;
            case "PERSIST_TUN_ACTION":
                // check if tun cfg stayed the same
                status = openVpnService.getTunReopenStatus();
                break;
            case "OPENTUN":
                if (sendTunFD(needed, extra))
                    return;
                else
                    status = "cancel";
                // This not nice or anything but setFileDescriptors accepts only FilDescriptor class :(
                break;
            default:
                Log.w(TAG, "processNeedCommand: Unknown needok command " + argument);
                return;
        }
        String cmd = String.format("needok '%s' %s\n", needed, status);
        managementCommand(cmd);
    }

    private boolean sendTunFD(String needed, String extra) {
        if (!extra.equals("tun"))
            return false;

        ParcelFileDescriptor pfd = openVpnService.openTun();
        if (pfd == null)
            return false;

        Method setInt;
        int fdInt = pfd.getFd();
        try {
            setInt = FileDescriptor.class.getDeclaredMethod("setInt$", int.class);
            FileDescriptor fdtosend = new FileDescriptor();
            setInt.invoke(fdtosend, fdInt);
            FileDescriptor[] fds = {fdtosend};
            localSocket.setFileDescriptorsForSend(fds);
            String cmd = String.format("needok '%s' %s\n", needed, "ok");
            managementCommand(cmd);
            // Set the FileDescriptor to null to stop this mad behavior
            localSocket.setFileDescriptorsForSend(null);
            pfd.close();
            return true;
        } catch (NoSuchMethodException | IllegalArgumentException | InvocationTargetException |
                IOException | IllegalAccessException exp) {
            Log.e(TAG, "sendTunFD: Could not send fd over socket", exp);
        }
        return false;
    }

    private boolean stopOpenVPN() {
        synchronized (activeThreadList) {
            Log.i(TAG, "stopOpenVPN: inside stopOpenVPN");
            boolean sendCMD = false;
            for (OpenVPNManagementThread mt : activeThreadList) {
                sendCMD = mt.managementCommand("signal SIGINT\n");
                try {
                    if (mt.localSocket != null)
                        mt.localSocket.close();
                } catch (IOException e) {
                    Log.e(TAG, "stopOpenVPN: ", e);
                }
            }
            return sendCMD;
        }
    }

    @Override
    public void networkChange(boolean isSameNetwork) {
        if (waitingForRelease) {
            releaseHold();
            return;
        }
        managementCommand("network-change\n");
    }

    @Override
    public void setPauseCallback(PausedStateCallback callback) {
        pauseCallback = callback;
    }

    private void signalUsr1() {
        resumeHandler.removeCallbacks(mResumeHoldRunnable);
        if (!waitingForRelease)
            managementCommand("signal SIGUSR1\n");
        else
            // If signalusr1 is called update the state string
            // if there is another for stopping
            VpnStatus.updateStatePause(lastPauseReason);
    }

    @Override
    public void reconnect() {
        signalUsr1();
        releaseHold();
    }

    @Override
    public void pause(PauseReason reason) {
        lastPauseReason = reason;
        signalUsr1();
    }


    @Override
    public void resume() {
        releaseHold();
        /* Reset the reason why we are disconnected */
        lastPauseReason = PauseReason.NO_NETWORK;
    }


    @Override
    public boolean stopVPN(boolean replaceConnection) {
        boolean stopSucceed = stopOpenVPN();
        if (stopSucceed)
            isShuttingDown = true;
        return stopSucceed;
    }
}
