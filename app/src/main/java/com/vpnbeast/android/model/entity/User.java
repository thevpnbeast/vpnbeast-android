package com.vpnbeast.android.model.entity;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class User implements Parcelable {

    private String uuid;
    private Long id;
    private String userName;
    private String email;
    private boolean enabled;
    private boolean emailVerified;
    private String accessToken;
    private String refreshToken;
    private Date accessTokenExpiresAt;
    private Date refreshTokenExpiresAt;
    private boolean tokensExpired;

    public User(String uuid, Long id, String userName, String email, boolean enabled, boolean emailVerified,
                String accessToken, String refreshToken, Date accessTokenExpiresAt, Date refreshTokenExpiresAt,
                boolean tokensExpired) {
        this.uuid = uuid;
        this.id = id;
        this.userName = userName;
        this.email = email;
        this.enabled = enabled;
        this.emailVerified = emailVerified;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.refreshTokenExpiresAt = refreshTokenExpiresAt;
        this.tokensExpired = tokensExpired;
    }

    public User(Parcel in) {
        uuid = in.readString();
        id = in.readLong();
        userName = in.readString();
        email = in.readString();
        enabled = in.readInt() == 1;
        emailVerified = in.readInt() == 1;
        accessToken = in.readString();
        refreshToken = in.readString();
        accessTokenExpiresAt = (Date) in.readValue(Date.class.getClassLoader());
        refreshTokenExpiresAt = (Date) in.readValue(Date.class.getClassLoader());
        tokensExpired = in.readInt() == 1;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(uuid);
        dest.writeLong(id);
        dest.writeString(userName);
        dest.writeString(email);
        dest.writeInt(enabled ? 1 : 0);
        dest.writeInt(emailVerified ? 1 : 0);
        dest.writeString(accessToken);
        dest.writeString(refreshToken);
        dest.writeValue(accessTokenExpiresAt);
        dest.writeValue(refreshTokenExpiresAt);
        dest.writeInt(tokensExpired ? 1 : 0);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<User> CREATOR = new Creator<User>() {
        @Override
        public User createFromParcel(Parcel in) {
            return new User(in);
        }

        @Override
        public User[] newArray(int size) {
            return new User[size];
        }
    };

}