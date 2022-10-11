package com.vpnbeast.android.core;

public interface ByteCountListener {

    void updateByteCount(long in, long out, long diffIn, long diffOut);

}