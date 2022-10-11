package com.vpnbeast.android.exception;

import java.util.Date;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExceptionInfo {

    private final String errorMessage;
    private final Boolean status;
    private final Date timestamp;

}