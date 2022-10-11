package com.vpnbeast.android.model.entity;

import com.vpnbeast.android.core.ConnectionStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class UpdateMessage {

    private String state;
    private String logMessage;
    private ConnectionStatus level;
    private int resId;

}