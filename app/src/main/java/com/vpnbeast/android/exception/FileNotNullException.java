package com.vpnbeast.android.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class FileNotNullException extends RuntimeException {

    private final transient ExceptionInfo exceptionInfo;

}