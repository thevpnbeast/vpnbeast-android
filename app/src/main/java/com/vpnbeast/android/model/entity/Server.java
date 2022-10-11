package com.vpnbeast.android.model.entity;

import androidx.annotation.NonNull;
import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class Server implements Serializable {

    private String uuid;
    private Long id;
    private String hostname;
    private String ip;
    private String proto;
    private int port;
    private String confData;
    private String countryLong;
    private Date createdAt;
    private Date updatedAt;
    private Date lastUsedAt;
    private int version;
    private boolean enabled;
    private boolean persistTun;
    private Long speed;
    private Long numVpnSessions;
    private Long ping;
    private HashSet<String> allowedAppsVpn;
    private boolean allowedAppsVpnAreDisallowed;
    private boolean allowLocalLAN;

    @NonNull
    @Override
    public String toString() {
        return ip + "\n" + port + "\n" + proto.toUpperCase() + "\n" + countryLong;
    }

}