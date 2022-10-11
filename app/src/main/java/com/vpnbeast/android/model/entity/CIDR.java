package com.vpnbeast.android.model.entity;

import androidx.annotation.NonNull;
import java.math.BigInteger;
import java.net.Inet6Address;
import java.util.Locale;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CIDR implements Comparable<CIDR> {

    private BigInteger netAddress;
    private int networkMask;
    private boolean included;
    private boolean isV4;
    private BigInteger firstAddress;
    private BigInteger lastAddress;

    public CIDR(IPAddress ip, boolean include) {
        included = include;
        netAddress = BigInteger.valueOf(ip.getInt());
        networkMask = ip.len;
        isV4 = true;
    }

    public CIDR(Inet6Address address, int mask, boolean include) {
        networkMask = mask;
        included = include;
        int s = 128;
        netAddress = BigInteger.ZERO;
        for (byte b : address.getAddress()) {
            s -= 8;
            netAddress = netAddress.add(BigInteger.valueOf((b & 0xFF)).shiftLeft(s));
        }
    }

    private CIDR(BigInteger baseAddress, int mask, boolean included, boolean isV4) {
        this.netAddress = baseAddress;
        this.networkMask = mask;
        this.included = included;
        this.isV4 = isV4;
    }

    public BigInteger getLastAddress() {
        if (lastAddress == null)
            lastAddress = getMaskedAddress(true);
        return lastAddress;
    }

    public BigInteger getFirstAddress() {
        if (firstAddress == null)
            firstAddress = getMaskedAddress(false);
        return firstAddress;
    }

    private BigInteger getMaskedAddress(boolean one) {
        BigInteger numAddress = netAddress;

        int numBits;
        if (isV4) {
            numBits = 32 - networkMask;
        } else {
            numBits = 128 - networkMask;
        }

        for (int i = 0; i < numBits; i++) {
            if (one)
                numAddress = numAddress.setBit(i);
            else
                numAddress = numAddress.clearBit(i);
        }
        return numAddress;
    }

    public CIDR[] split() {
        CIDR firstHalf = new CIDR(getFirstAddress(), networkMask + 1, included, isV4);
        CIDR secondHalf = new CIDR(firstHalf.getLastAddress().add(BigInteger.ONE),
                networkMask + 1, included, isV4);
        return new CIDR[]{firstHalf, secondHalf};
    }

    public String getIPv4Address() {
        long ip = netAddress.longValue();
        return String.format(Locale.US, "%d.%d.%d.%d", (ip >> 24) % 256, (ip >> 16) % 256, (ip >> 8) % 256, ip % 256);
    }

    public String getIPv6Address() {
        BigInteger r = netAddress;

        String ipv6str = null;
        boolean lastPart = true;

        while (r.compareTo(BigInteger.ZERO) > 0) {
            long part = r.mod(BigInteger.valueOf(0x10000)).longValue();
            if (ipv6str != null || part != 0) {
                if (ipv6str == null && !lastPart)
                    ipv6str = ":";
                if (lastPart)
                    ipv6str = String.format(Locale.US, "%x", part);
                else
                    ipv6str = String.format(Locale.US, "%x:%s", part, ipv6str);
            }
            r = r.shiftRight(16);
            lastPart = false;
        }
        if (ipv6str == null)
            return "::";
        return ipv6str;
    }

    public boolean containsNet(CIDR network) {
        BigInteger ourFirst = getFirstAddress();
        BigInteger ourLast = getLastAddress();
        BigInteger netFirst = network.getFirstAddress();
        BigInteger netLast = network.getLastAddress();
        boolean a = ourFirst.compareTo(netFirst) < 1;
        boolean b = ourLast.compareTo(netLast) > -1;
        return a && b;
    }

    @NonNull
    @Override
    public String toString() {
        return isV4 ? String.format(Locale.US, "%s/%d", getIPv4Address(), networkMask) :
                String.format(Locale.US, "%s/%d", getIPv6Address(), networkMask);
    }

    /**
     * sorts the networks with following criteria:
     * 1. compares first 1 of the network
     * 2. smaller networks are returned as smaller
     */
    @Override
    public int compareTo(@NonNull CIDR another) {
        int comp = getFirstAddress().compareTo(another.getFirstAddress());
        if (comp != 0)
            return comp;
        return Integer.compare(another.networkMask, networkMask);
    }

    /**
     * Warning ignores the included integer
     *
     * @param o the object to compare this instance with.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CIDR))
            return super.equals(o);

        CIDR on = (CIDR) o;
        return (networkMask == on.networkMask) && on.getFirstAddress().equals(getFirstAddress());
    }

}