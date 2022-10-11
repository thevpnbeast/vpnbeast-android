package com.vpnbeast.android.core;

import android.os.Build;
import com.vpnbeast.android.model.entity.CIDR;
import com.vpnbeast.android.model.entity.IPAddress;
import java.net.Inet6Address;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.TreeSet;

class NetworkSpace {

    private TreeSet<CIDR> cidrSet = new TreeSet<>();

    Collection<CIDR> getNetworks(boolean included) {
        List<CIDR> ips = new LinkedList<>();
        for (CIDR ip : cidrSet) {
            if (ip.isIncluded() == included)
                ips.add(ip);
        }
        return ips;
    }

    void clear() {
        cidrSet.clear();
    }

    void addIP(IPAddress cidrIp, boolean include) {
        cidrSet.add(new CIDR(cidrIp, include));
    }

    void addIPSplit(IPAddress cidrIp) {
        CIDR newIP = new CIDR(cidrIp, true);
        CIDR[] splitIps = newIP.split();
        cidrSet.addAll(Arrays.asList(splitIps));
    }

    void addIPv6(Inet6Address address, int mask, boolean included) {
        cidrSet.add(new CIDR(address, mask, included));
    }

    private TreeSet<CIDR> generateIPList() {
        PriorityQueue<CIDR> networks = new PriorityQueue<>(cidrSet);
        TreeSet<CIDR> ipsDone = new TreeSet<>();
        CIDR currentNet = networks.poll();

        if (currentNet == null)
            return ipsDone;

        while (currentNet != null) {
            // Check if it and the next of it are compatible
            CIDR nextNet = networks.poll();
            if (nextNet == null || currentNet.getLastAddress().compareTo(nextNet.getFirstAddress()) == -1) {
                // Everything good, no overlapping nothing to do
                ipsDone.add(currentNet);
                currentNet = nextNet;
            } else {
                // This network is smaller or equal to the next but has the same base address
                if (currentNet.getFirstAddress().equals(nextNet.getFirstAddress()) && currentNet.getNetworkMask() >= nextNet.getNetworkMask()) {
                    if (currentNet.isIncluded() == nextNet.isIncluded()) {
                        // Included in the next next and same type
                        // Simply forget our current network
                        currentNet = nextNet;
                    } else {
                        CIDR[] newNets = nextNet.split();

                        if (!networks.contains(newNets[1]))
                            networks.add(newNets[1]);

                        if (!newNets[0].getLastAddress().equals(currentNet.getLastAddress()) && !networks.contains(newNets[0]))
                            networks.add(newNets[0]);
                    }
                } else {
                    // This network is bigger than the next and last ip of current >= next
                    //noinspection StatementWithEmptyBody
                    if (currentNet.isIncluded() == nextNet.isIncluded()) {
                        // Next network is in included in our network with the same type,
                        // simply ignore the next and move on
                    } else {
                        // We need to split our network
                        CIDR[] newNets = currentNet.split();
                        if (newNets[1].getNetworkMask() == nextNet.getNetworkMask()) {
                            networks.add(nextNet);
                        } else {
                            // Add the smaller network first
                            networks.add(newNets[1]);
                            networks.add(nextNet);
                        }
                        currentNet = newNets[0];
                    }
                }
            }
        }
        return ipsDone;
    }

    Collection<CIDR> getPositiveIPList() {
        TreeSet<CIDR> ipsSorted = generateIPList();
        List<CIDR> ips = new LinkedList<>();

        for (CIDR ia : ipsSorted) {
            if (ia.isIncluded())
                ips.add(ia);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            // Include postive routes from the original set under < 4.4 since these might overrule the local
            // network but only if no smaller negative route exists
            for (CIDR origIp : cidrSet) {
                if (!origIp.isIncluded() || ipsSorted.contains(origIp))
                    continue;

                boolean skipIp = false;
                // If there is any smaller net that is excluded we may not add the positive route back
                for (CIDR calculatedIp : ipsSorted) {
                    if (!calculatedIp.isIncluded() && origIp.containsNet(calculatedIp)) {
                        skipIp = true;
                        break;
                    }
                }

                if (skipIp)
                    continue;

                ips.add(origIp);
            }
        }
        return ips;
    }

}