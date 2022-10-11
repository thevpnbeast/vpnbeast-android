package com.vpnbeast.android.core;

import android.util.Log;
import com.vpnbeast.android.model.entity.Server;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

class ProxyListener {

    private static final String TAG = "ProxyListener";

    private ProxyListener() {

    }

    static SocketAddress detectProxy(Server server) {
        try {
            URL url = new URL(String.format("https://%s:%s", server.getIp(), server.getPort()));
            Proxy proxy = getFirstProxy(url);
            if(proxy == null)
                return null;
            SocketAddress addr = proxy.address();
            if (addr instanceof InetSocketAddress) {
                return addr;
            }
        } catch (MalformedURLException | URISyntaxException a) {
            Log.e(TAG, "detectProxy: ", a);
        }
        return null;
    }

    private static Proxy getFirstProxy(URL url) throws URISyntaxException {
        System.setProperty("java.net.useSystemProxies", "true");
        List<Proxy> proxyList = ProxySelector.getDefault().select(url.toURI());
        if (proxyList != null) {
            for (Proxy proxy: proxyList) {
                SocketAddress addr = proxy.address();
                if (addr != null)
                    return proxy;
            }
        }
        return null;
    }
}