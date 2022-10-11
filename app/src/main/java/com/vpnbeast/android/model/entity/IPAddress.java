package com.vpnbeast.android.model.entity;

import androidx.annotation.NonNull;
import java.util.Locale;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IPAddress {

    String ip;
    int len;

    public IPAddress(String ip, String mask) {
        this.ip = ip;
        long netmask = getInt(mask);

        // Add 33. bit to ensure the loop terminates
        netmask += 1L << 32;

        int lenZeros = 0;
        while ((netmask & 0x1) == 0) {
            lenZeros++;
            netmask = netmask >> 1;
        }
        // Check if rest of netmask is only 1s
        if (netmask != (0x1ffffffffL >> lenZeros)) {
            // Asume no CIDR, set /32
            len = 32;
        } else {
            len = 32 - lenZeros;
        }
    }

    public IPAddress(String ip, int len) {
        this.ip = ip;
        this.len = len;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%s/%d", ip, len);
    }

    public boolean normalise() {
        long oldIp = getInt(this.ip);
        long newIp = oldIp & (0xffffffffL << (32 - len));
        if (newIp != oldIp) {
            this.ip = getNormalizedString(newIp);
            return true;
        } else {
            return false;
        }
    }

    private String getNormalizedString(long newip) {
        return String.format(Locale.getDefault(), "%d.%d.%d.%d", (newip & 0xff000000) >> 24,
                (newip & 0xff0000) >> 16, (newip & 0xff00) >> 8, newip & 0xff);
    }

    public static long getInt(String ipAddr) {
        String[] ipt = ipAddr.split("\\.");
        long ip = 0;
        ip += Long.parseLong(ipt[0]) << 24;
        ip += Integer.parseInt(ipt[1]) << 16;
        ip += Integer.parseInt(ipt[2]) << 8;
        ip += Integer.parseInt(ipt[3]);
        return ip;
    }

    public long getInt() {
        return getInt(ip);
    }
}
