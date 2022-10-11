package com.vpnbeast.android.model.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class DataPoint {

    private long timestamp;
    private long data;

}