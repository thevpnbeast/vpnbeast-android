package com.vpnbeast.android.core;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutManager;
import android.net.ConnectivityManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.RequiresApi;
import com.vpnbeast.android.R;
import com.vpnbeast.android.activity.StatusActivity;
import com.vpnbeast.android.model.entity.CIDR;
import com.vpnbeast.android.model.entity.IPAddress;
import com.vpnbeast.android.model.entity.Server;
import com.vpnbeast.android.model.enums.AppConstants;
import com.vpnbeast.android.util.NotificationUtil;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import de.blinkt.openvpn.core.NativeUtils;

public class OpenVPNService extends VpnService implements StateListener, Handler.Callback,
        ByteCountListener, IOpenVPNServiceInternal {

    private static final String TAG = "OpenVPNService";
    private static final int OPENVPN_STATUS = 1;
    private final Object processLock = new Object();

    private boolean notificationsAlwaysVisible;
    private NetworkSpace routesIPv4;
    private NetworkSpace routesIPv6;
    private Thread processThread;
    private DeviceStateReceiver deviceStateReceiver;
    private boolean displayByteCount;
    private int mtu;
    private IPAddress localIP;
    private String localIPv6;
    private long connectTime;
    private OpenVPNManagement vpnManagement;
    private String lastTunCfg;
    private String remoteGW;
    private List<String> dnsList;
    private Server server;
    private String domainName;
    private NotificationManager notificationManager;

    private final IBinder mBinder = new IOpenVPNServiceInternal.Stub() {

        @Override
        public boolean protect(int fd) {
            return OpenVPNService.this.protect(fd);
        }

        @Override
        public void userPause(boolean shouldBePaused) {
            OpenVPNService.this.userPause(shouldBePaused);
        }

        @Override
        public boolean stopVPN(boolean replaceConnection) {
            return OpenVPNService.this.stopVPN(replaceConnection);
        }
    };

    private void init() {
        dnsList = new ArrayList<>();
        domainName = null;
        localIP = null;
        localIPv6 = null;
        displayByteCount = false;
        processThread = null;
        routesIPv4 = new NetworkSpace();
        routesIPv6 = new NetworkSpace();
        notificationsAlwaysVisible = false;
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    // From: http://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java
    public static String humanReadableByteCount(long bytes, boolean mbit) {
        if (mbit)
            bytes = bytes * 8;
        int unit = mbit ? 1000 : 1024;
        if (bytes < unit)
            return bytes + (mbit ? " bit" : " B");

        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (mbit ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (mbit ? "" : "");
        if (mbit)
            return String.format(Locale.getDefault(), "%.1f %sbit", bytes / Math.pow(unit, exp), pre);
        else
            return String.format(Locale.getDefault(), "%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    @Override
    public IBinder onBind(Intent intent) {
        String action = intent.getAction();
        if (action != null && action.equals(AppConstants.START_SERVICE.toString()))
            return mBinder;
        else
            return super.onBind(intent);
    }

    @Override
    public void onRevoke() {
        Log.e(TAG, "onRevoke: on revoke");
        VpnStatus.logError(R.string.permission_revoked);
        vpnManagement.stopVPN(false);
        endVpnService();
    }

    // Similar to revoke but do not try to stop process
    public void processDied() {
        Log.i(TAG, "processDied: ");
        endVpnService();
    }

    private void endVpnService() {
        synchronized (processLock) {
            processThread = null;
        }
        Log.i(TAG, "endVpnService: ending vpn service");
        VpnStatus.removeByteCountListener(this);
        unregisterDeviceStateReceiver();
        ServerManager.setConnectedServerDisconnected(this);
        stopForeground(!notificationsAlwaysVisible);
        notificationManager.cancel(OPENVPN_STATUS);
        stopSelf();
        VpnStatus.removeStateListener(this);
    }

    private void showNotification(final String msg, String tickerText, long when) {
        int icon = R.drawable.vpn26;
        Notification.Builder nbuilder = new Notification.Builder(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationUtil notificationUtil = new NotificationUtil(this);
            nbuilder = notificationUtil.getAndroidChannelNotification();
        }

        if (server != null)
            nbuilder.setContentTitle(getString(R.string.notifcation_title, server.getHostname()));
        else
            nbuilder.setContentTitle(getString(R.string.notifcation_title_notconnect));

        nbuilder.setContentText(msg);
        nbuilder.setOnlyAlertOnce(true);
        nbuilder.setOngoing(true);
        nbuilder.setSmallIcon(icon);
        nbuilder.setContentIntent(getStatusPendingIntent());

        if (when != 0)
            nbuilder.setWhen(when);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            lpNotificationExtras(nbuilder);

        if (tickerText != null && !tickerText.equals(""))
            nbuilder.setTicker(tickerText);

        Notification notification = nbuilder.getNotification();
        notificationManager.notify(OPENVPN_STATUS, notification);
        startForeground(OPENVPN_STATUS, notification);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void lpNotificationExtras(Notification.Builder nbuilder) {
        nbuilder.setCategory(Notification.CATEGORY_SERVICE);
        nbuilder.setLocalOnly(true);

    }

    private PendingIntent getStatusPendingIntent() {
        Intent intent = new Intent(this, StatusActivity.class);
        return PendingIntent.getActivity(this, 0, intent, 0);
    }

    synchronized void registerDeviceStateReceiver(OpenVPNManagement magnagement) {
        // Registers BroadcastReceiver to track network connection changes.
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        deviceStateReceiver = new DeviceStateReceiver(magnagement);

        // Fetch initial network state
        deviceStateReceiver.networkStateChange(this);

        registerReceiver(deviceStateReceiver, filter);
        VpnStatus.addByteCountListener(deviceStateReceiver);
        Log.i(TAG, "registerDeviceStateReceiver: registered");
    }

    synchronized void unregisterDeviceStateReceiver() {
        if (deviceStateReceiver != null)
            try {
                VpnStatus.removeByteCountListener(deviceStateReceiver);
                this.unregisterReceiver(deviceStateReceiver);
            } catch (IllegalArgumentException iae) {
                Log.e(TAG, "unregisterDeviceStateReceiver: ", iae);
            }
        deviceStateReceiver = null;
    }

    public void userPause(boolean shouldBePaused) {
        if (deviceStateReceiver != null)
            deviceStateReceiver.userPause(shouldBePaused);
    }

    @Override
    public boolean stopVPN(boolean replaceConnection) {
        if (getManagement() != null)
            return getManagement().stopVPN(replaceConnection);
        else
            return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        init();
        if (intent != null && intent.getBooleanExtra(AppConstants.NOTIFICATION_ALWAYS_VISIBLE.toString(), false))
            notificationsAlwaysVisible = true;

        VpnStatus.addStateListener(this);
        VpnStatus.addByteCountListener(this);

        if (intent != null && AppConstants.PAUSE_VPN.toString().equals(intent.getAction())) {
            if (deviceStateReceiver != null)
                deviceStateReceiver.userPause(true);
            return START_NOT_STICKY;
        }

        if (intent != null && AppConstants.RESUME_VPN.toString().equals(intent.getAction())) {
            if (deviceStateReceiver != null)
                deviceStateReceiver.userPause(false);
            return START_NOT_STICKY;
        }

        if (intent != null && AppConstants.START_SERVICE.toString().equals(intent.getAction()))
            return START_NOT_STICKY;
        if (intent != null && AppConstants.START_SERVICE_STICKY.toString().equals(intent.getAction())) {
            return START_REDELIVER_INTENT;
        }

        if (intent != null) {
            server = (Server) intent.getSerializableExtra(AppConstants.SERVER.toString());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                updateShortCutUsage(server);
            }
        } else {
            server = ServerManager.getLastConnectedServer();
            Log.i(TAG, "onStartCommand: uuid of last connected vpn = " + server.getUuid());
            VpnStatus.logInfo(R.string.service_restarted);

            if (server == null)
                Log.d("OpenVPN", "Got no last connected profile on null intent. Stoping.");
        }

        /* start the OpenVPN process itself in a background thread */
        new Thread(this::startOpenVPN).start();

        ServerManager.setConnectedServer(this, server);
        VpnStatus.setConnectedServerUuid(server.getUuid());
        Log.i(TAG, "onStartCommand: last connected vpn uuid = " + ServerManager
                .getLastConnectedServer().getUuid());

        return START_STICKY;
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private void updateShortCutUsage(Server server) {
        if (server == null)
            return;
        ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
        Objects.requireNonNull(shortcutManager).reportShortcutUsed(server.getHostname());
    }

    private void startOpenVPN() {
        String nativeLibraryDirectory = getApplicationInfo().nativeLibraryDir;
        // Write OpenVPN binary
        String[] argv = VPNLaunchHelper.buildOpenvpnArgv(this);
        // Set a flag that we are starting a new VPN
        // Stop the previous session by interrupting the thread.
        stopOldOpenVPNProcess();
        // start a Thread that handles incoming messages of the managment socket
        OpenVPNManagementThread ovpnManagementThread = new OpenVPNManagementThread(server, this);
        if (ovpnManagementThread.openManagementInterface(this)) {
            Thread socketManagerThread = new Thread(ovpnManagementThread, "OpenVPNManagementThread");
            socketManagerThread.start();
            vpnManagement = ovpnManagementThread;
            Log.i(TAG, "startOpenVPN: starting socket thread");
            VpnStatus.logInfo("started Socket Thread");
        } else {
            Log.i(TAG, "startOpenVPN: !isOpenvpn3 else block");
            endVpnService();
            return;
        }

        Runnable processRunnable = new OpenVPNThread(this, argv, nativeLibraryDirectory);
        synchronized (processLock) {
            processThread = new Thread(processRunnable, "OpenVPNProcessThread");
            Log.i(TAG, "startOpenVPN: starting processThread");
            processThread.start();
        }

        new Handler(getMainLooper()).post(() -> {
            if (deviceStateReceiver != null)
                unregisterDeviceStateReceiver();

            registerDeviceStateReceiver(vpnManagement);
        });
    }

    private void stopOldOpenVPNProcess() {
        if (vpnManagement != null && vpnManagement.stopVPN(true)) {
            // an old was asked to exit, wait 1s
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "stopOldOpenVPNProcess: ", e);
            }
        }
        forceStopOpenVpnProcess();
    }

    public void forceStopOpenVpnProcess() {
        synchronized (processLock) {
            if (processThread != null) {
                processThread.interrupt();
                Thread.interrupted();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.e(TAG, "forceStopOpenVpnProcess: ", e);
                }
            }
        }
    }

    @Override
    public IBinder asBinder() {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        synchronized (processLock) {
            if (processThread != null) {
                vpnManagement.stopVPN(true);
            }
        }

        if (deviceStateReceiver != null) {
            this.unregisterReceiver(deviceStateReceiver);
        }
        // Just in case unregister for state
        VpnStatus.removeStateListener(this);
    }

    private String getTunConfigString() {
        // The format of the string is not important, only that
        // two identical configurations produce the same result
        String cfg = "TUNCFG UNQIUE STRING ips:";

        if (localIP != null)
            cfg += localIP.toString();
        if (localIPv6 != null)
            cfg += localIPv6;

        cfg += "routes: " + TextUtils.join("|", routesIPv4.getNetworks(true)) +
                TextUtils.join("|", routesIPv6.getNetworks(true));
        cfg += "excl. routes:" + TextUtils.join("|", routesIPv4.getNetworks(false)) +
                TextUtils.join("|", routesIPv6.getNetworks(false));
        cfg += "dns: " + TextUtils.join("|", dnsList);
        cfg += "domain: " + domainName;
        cfg += "mtu: " + mtu;
        Log.i(TAG, "getTunConfigString: cfg = " + cfg);
        return cfg;
    }

    public ParcelFileDescriptor openTun() {
        Builder builder = new Builder();
        VpnStatus.logInfo(R.string.last_openvpn_tun_config);

        if (localIP == null && localIPv6 == null) {
            VpnStatus.logError(getString(R.string.opentun_no_ipaddr));
            return null;
        }

        if (localIP != null) {
            addLocalNetworksToRoutes();
            try {
                builder.addAddress(localIP.getIp(), localIP.getLen());
            } catch (IllegalArgumentException iae) {
                Log.e(TAG, "openTun: " + getString(R.string.dns_add_error), iae);
                return null;
            }
        }

        if (localIPv6 != null) {
            String[] ipv6parts = localIPv6.split("/");
            try {
                builder.addAddress(ipv6parts[0], Integer.parseInt(ipv6parts[1]));
            } catch (IllegalArgumentException iae) {
                Log.e(TAG, "openTun: " + getString(R.string.ip_add_error), iae);
                return null;
            }
        }

        for (String dns : dnsList) {
            try {
                builder.addDnsServer(dns);
            } catch (IllegalArgumentException iae) {
                Log.e(TAG, "openTun: " + getString(R.string.dns_add_error), iae);
            }
        }

        String release = Build.VERSION.RELEASE;

        if ((Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT && !release.startsWith("4.4.3")
                && !release.startsWith("4.4.4") && !release.startsWith("4.4.5") && !release.startsWith("4.4.6"))
                && mtu < 1280) {
            VpnStatus.logInfo(String.format(Locale.US, "Forcing MTU to 1280 instead of %d to workaround " +
                    "Android Bug #70916", mtu));
            builder.setMtu(1280);
        } else {
            builder.setMtu(mtu);
        }

        Collection<CIDR> positiveIPv4Routes = routesIPv4.getPositiveIPList();
        Collection<CIDR> positiveIPv6Routes = routesIPv6.getPositiveIPList();
        if ("samsung".equals(Build.BRAND) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                !dnsList.isEmpty()) {
            // Check if the first DNS Server is in the VPN range
            try {
                CIDR dnsServer = new CIDR(new IPAddress(dnsList.get(0), 32), true);
                boolean dnsIncluded = false;
                for (CIDR net : positiveIPv4Routes) {
                    if (net.containsNet(dnsServer)) {
                        dnsIncluded = true;
                    }
                }
                if (!dnsIncluded) {
                    String samsungwarning = String.format("Warning Samsung Android 5.0+ devices ignore " +
                            "DNS servers outside the VPN range. To enable DNS resolution a route to your DNS " +
                            "Server (%s) has been added.", dnsList.get(0));
                    VpnStatus.logWarning(samsungwarning);
                    positiveIPv4Routes.add(dnsServer);
                }
            } catch (Exception e) {
                Log.e(TAG, "openTun: Error parsing DNS Server IP: " + dnsList.get(0), e);
            }
        }

        CIDR multicastRange = new CIDR(new IPAddress("224.0.0.0", 3), true);

        for (CIDR route : positiveIPv4Routes) {
            try {
                if (multicastRange.containsNet(route))
                    VpnStatus.logDebug(R.string.ignore_multicast_route, route.toString());
                else
                    builder.addRoute(route.getIPv4Address(), route.getNetworkMask());
            } catch (IllegalArgumentException ia) {
                Log.e(TAG, "openTun: " + getString(R.string.route_rejected), ia);
            }
        }

        for (CIDR route6 : positiveIPv6Routes) {
            try {
                builder.addRoute(route6.getIPv6Address(), route6.getNetworkMask());
            } catch (IllegalArgumentException ia) {
                Log.e(TAG, "openTun: " + getString(R.string.route_rejected) + " " + route6, ia);
            }
        }

        if (domainName != null)
            builder.addSearchDomain(domainName);

        VpnStatus.logInfo(R.string.local_ip_info, localIP.getIp(), localIP.getLen(), localIPv6, mtu);
        VpnStatus.logInfo(R.string.dns_server_info, TextUtils.join(", ", dnsList), domainName);
        VpnStatus.logInfo(R.string.routes_info_incl, TextUtils.join(", ", routesIPv4.getNetworks(true)),
                TextUtils.join(", ", routesIPv6.getNetworks(true)));
        VpnStatus.logInfo(R.string.routes_info_excl, TextUtils.join(", ", routesIPv4.getNetworks(false)),
                TextUtils.join(", ", routesIPv6.getNetworks(false)));
        VpnStatus.logDebug(R.string.routes_debug, TextUtils.join(", ", positiveIPv4Routes),
                TextUtils.join(", ", positiveIPv6Routes));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            setAllowedVpnPackages(builder);

        String session = server.getIp();

        if (localIP != null && localIPv6 != null)
            session = getString(R.string.session_ipv6string, session, localIP, localIPv6);

        else if (localIP != null)
            session = getString(R.string.session_ipv4string, session, localIP);

        builder.setSession(session);
        // No DNS Server, log a warning

        if (dnsList.isEmpty())
            VpnStatus.logInfo(R.string.warn_no_dns);

        lastTunCfg = getTunConfigString();
        // Reset information
        dnsList.clear();
        routesIPv4.clear();
        routesIPv6.clear();
        localIP = null;
        localIPv6 = null;
        domainName = null;
        builder.setConfigureIntent(getStatusPendingIntent());

        try {
            ParcelFileDescriptor tun = builder.establish();
            if (tun == null)
                throw new NullPointerException("Android establish() method returned null (Really broken " +
                        "network configuration?)");
            return tun;
        } catch (Exception e) {
            Log.e(TAG, "openTun: " + getString(R.string.tun_open_error), e);
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                Log.w(TAG, "openTun: " + getString(R.string.tun_error_helpful));
            }
            return null;
        }
    }

    private void addLocalNetworksToRoutes() {
        // Add local network interfaces
        String[] localRoutes = NativeUtils.getIfconfig();
        // The format of mLocalRoutes is kind of broken because I don't really like JNI
        for (int i = 0; i < localRoutes.length; i += 3) {
            String intf = localRoutes[i];
            String ipAddr = localRoutes[i + 1];
            String netMask = localRoutes[i + 2];

            if (intf == null || intf.equals("lo") ||
                    intf.startsWith("tun") || intf.startsWith("rmnet"))
                continue;

            if (ipAddr == null || netMask == null) {
                VpnStatus.logError("Local routes are broken?! (Report to author) " + TextUtils.join("|", localRoutes));
                continue;
            }

            if (ipAddr.equals(localIP.getIp()))
                continue;

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT && !server.isAllowLocalLAN()) {
                routesIPv4.addIPSplit(new IPAddress(ipAddr, netMask));

            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && server.isAllowLocalLAN())
                routesIPv4.addIP(new IPAddress(ipAddr, netMask), false);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setAllowedVpnPackages(Builder builder) {
        boolean atLeastOneAllowedApp = false;
        for (String pkg : server.getAllowedAppsVpn()) {
            try {
                if (server.isAllowedAppsVpnAreDisallowed()) {
                    builder.addDisallowedApplication(pkg);
                } else {
                    builder.addAllowedApplication(pkg);
                    atLeastOneAllowedApp = true;
                }
            } catch (PackageManager.NameNotFoundException e) {
                server.getAllowedAppsVpn().remove(pkg);
                Log.e(TAG, "setAllowedVpnPackages: " + getString(R.string.app_no_longer_exists), e);
            }
        }

        if (!server.isAllowedAppsVpnAreDisallowed() && !atLeastOneAllowedApp) {
            VpnStatus.logDebug(R.string.no_allowed_app, getPackageName());
            try {
                builder.addAllowedApplication(getPackageName());
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "setAllowedVpnPackages: ", e);
            }
        }

        if (server.isAllowedAppsVpnAreDisallowed()) {
            VpnStatus.logDebug(R.string.disallowed_vpn_apps_info, TextUtils.join(", ",
                    server.getAllowedAppsVpn()));
        } else {
            VpnStatus.logDebug(R.string.allowed_vpn_apps_info, TextUtils.join(", ",
                    server.getAllowedAppsVpn()));
        }
    }

    public void addDNS(String dns) {
        dnsList.add(dns);
    }

    public void setDomain(String domain) {
        if (domainName == null)
            domainName = domain;
    }

    /**
     * Route that is always included, used by the v3 core
     */
    public void addRoute(IPAddress route) {
        routesIPv4.addIP(route, true);
    }

    public void addRoute(String dest, String mask, String gateway, String device) {
        IPAddress route = new IPAddress(dest, mask);
        boolean include = isAndroidTunDevice(device);
        CIDR gatewayIP = new CIDR(new IPAddress(gateway, 32), false);

        if (localIP == null) {
            VpnStatus.logError("Local IP address unset and received. Neither pushed server " +
                    "config nor local config specifies an IP addresses. Opening tun device is most " +
                    "likely going to fail.");
            return;
        }

        CIDR localNet = new CIDR(localIP, true);

        if (localNet.containsNet(gatewayIP))
            include = true;

        if (gateway != null &&
                (gateway.equals("255.255.255.255") || gateway.equals(remoteGW)))
            include = true;

        if (route.getLen() == 32 && !mask.equals("255.255.255.255")) {
            VpnStatus.logWarning(R.string.route_not_cidr, dest, mask);
        }

        if (route.normalise())
            VpnStatus.logWarning(R.string.route_not_netip, dest, route.getLen(), route.getIp());

        routesIPv4.addIP(route, include);
    }

    public void addRoutev6(String network, String device) {
        String[] v6parts = network.split("/");
        boolean included = isAndroidTunDevice(device);
        try {
            Inet6Address ip = (Inet6Address) InetAddress.getAllByName(v6parts[0])[0];
            int mask = Integer.parseInt(v6parts[1]);
            routesIPv6.addIPv6(ip, mask, included);
        } catch (UnknownHostException e) {
            Log.e(TAG, "addRoutev6: ", e);
        }
    }

    private boolean isAndroidTunDevice(String device) {
        return device != null &&
                (device.startsWith("tun") || "(null)".equals(device) || "vpnservice-tun".equals(device));
    }

    public void setLocalIP(String local, String netmask, int mtu, String mode) {
        localIP = new IPAddress(local, netmask);
        this.mtu = mtu;
        remoteGW = null;
        long netMaskAsInt = IPAddress.getInt(netmask);
        if (localIP.getLen() == 32 && !netmask.equals("255.255.255.255")) {
            // get the netmask as IP
            int masklen;
            long mask;
            if ("net30".equals(mode)) {
                masklen = 30;
                mask = 0xfffffffc;
            } else {
                masklen = 31;
                mask = 0xfffffffe;
            }
            // Netmask is Ip address +/-1, assume net30/p2p with small net
            if ((netMaskAsInt & mask) == (localIP.getInt() & mask)) {
                localIP.setLen(masklen);
            } else {
                localIP.setLen(32);
                if (!"p2p".equals(mode))
                    VpnStatus.logWarning(R.string.ip_not_cidr, local, netmask, mode);
            }
        }
        if (("p2p".equals(mode) && localIP.getLen() < 32) || ("net30".equals(mode) && localIP.getLen() < 30)) {
            VpnStatus.logWarning(R.string.ip_looks_like_subnet, local, netmask, mode);
        }

        /* Workaround for Lollipop, it  does not route traffic to the VPNs own network mask */
        if (localIP.getLen() <= 31 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            IPAddress interfaceRoute = new IPAddress(localIP.getIp(), localIP.getLen());
            interfaceRoute.normalise();
            addRoute(interfaceRoute);
        }
        // Configurations are sometimes really broken...
        remoteGW = netmask;
    }

    public void setLocalIPv6(String ipv6addr) {
        localIPv6 = ipv6addr;
    }

    @Override
    public void updateState(String state, String logMessage, int resid, ConnectionStatus level) {
        doSendBroadcast(state, level);
        if (processThread == null && !notificationsAlwaysVisible)
            return;
        // Display byte count only after being connected
        if (level == ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT || level == ConnectionStatus.LEVEL_CANCELLED) {
            // The user is presented a dialog of some kind, no need to inform the user
            // with a notifcation
            return;
        } else if (level == ConnectionStatus.LEVEL_CONNECTED) {
            displayByteCount = true;
            connectTime = System.currentTimeMillis();
        } else {
            displayByteCount = false;
        }

        String msg = getString(resid);
        showNotification(VpnStatus.getLastCleanLogMessage(this),
                msg,0);
    }

    @Override
    public void setConnectedVPN(String uuid) {

    }

    private void doSendBroadcast(String state, ConnectionStatus level) {
        Intent statusIntent = new Intent();
        statusIntent.setAction(AppConstants.VPN_STATUS.toString());
        statusIntent.putExtra("status", level.toString());
        statusIntent.putExtra("detailstatus", state);
        sendBroadcast(statusIntent, Manifest.permission.ACCESS_NETWORK_STATE);
    }

    @Override
    public void updateByteCount(long in, long out, long diffIn, long diffOut) {
        if (displayByteCount) {
            String netstat = String.format(getString(R.string.statusline_bytecount),
                    humanReadableByteCount(in, false),
                    humanReadableByteCount(diffIn / VpnStatus.BYTE_COUNT_INTERVAL, true),
                    humanReadableByteCount(out, false),
                    humanReadableByteCount(diffOut / VpnStatus.BYTE_COUNT_INTERVAL, true));
            showNotification(netstat, null, connectTime);
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        Runnable r = msg.getCallback();
        if (r != null) {
            r.run();
            return true;
        } else {
            return false;
        }
    }

    public OpenVPNManagement getManagement() {
        return vpnManagement;
    }

    public String getTunReopenStatus() {
        String currentConfiguration = getTunConfigString();
        if (currentConfiguration.equals(lastTunCfg)) {
            return "NOACTION";
        } else {
            String release = Build.VERSION.RELEASE;
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT && !release.startsWith("4.4.3")
                    && !release.startsWith("4.4.4") && !release.startsWith("4.4.5") && !release.startsWith("4.4.6"))
                // There will be probably no 4.4.4 or 4.4.5 version, so don't waste effort to do parsing here
                return "OPEN_AFTER_CLOSE";
            else
                return "OPEN_BEFORE_CLOSE";
        }
    }

}