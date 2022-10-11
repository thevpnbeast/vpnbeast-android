package com.vpnbeast.android.model.enums;

import androidx.annotation.NonNull;

public enum AppConstants {

    CHARSET("UTF-8"),

    USER_NAME("user_name"),
    USER_REMEMBER("user_remember"),
    EMAIL("email"),
    EMAIL_TYPE("email_type"),
    USER_PASS("user_pass"),
    VERIFICATION_CODE("verification_code"),

    RESPONSE_CODE("response_code"),
    RESPONSE_MSG("response_msg"),

    TOKEN_EXPIRED("token_expired"),

    DO_LOGIN("do_login"),
    DO_REGISTER("do_register"),
    DO_REFRESH("do_refresh"),
    DO_VERIFY("do_verify"),
    DO_RESEND_VERIFICATION_CODE("do_resend_verification_code"),
    DO_RESET_PASSWORD("do_reset_password"),

    GET_LOCATION("get_location"),
    GET_ALL_SERVERS("get_all_servers"),
    START_SERVER_SERVICE("start_server_service"),

    VPN_STATUS("vpn_status"),
    DISCONNECT_VPN("disconnect_vpn"),
    START_SERVICE("start_service"),
    START_SERVICE_STICKY("start_service_sticky"),
    NOTIFICATION_ALWAYS_VISIBLE("notification_always_visible"),
    PAUSE_VPN("pause_vpn"),
    RESUME_VPN("resume_vpn"),

    USER("user"),
    SERVER("server"),
    LAST_CONNECTED_SERVER("last_connected_server"),
    ALL_SERVERS("all_servers"),
    ACCESS_TOKEN("access_token"),
    ACCESS_TOKEN_EXPIRES_AT("access_token_expires_at"),
    REFRESH_TOKEN("refresh_token"),
    REFRESH_TOKEN_EXPIRES_AT("access_token_expires_at");

    private final String value;

    AppConstants(final String value) {
        this.value = value;
    }

    @NonNull
    @Override
    public String toString() {
        return value;
    }

}