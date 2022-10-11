package com.vpnbeast.android.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import androidx.annotation.NonNull;
import com.vpnbeast.android.R;
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.FormatFlagsConversionMismatchException;
import java.util.Locale;
import java.util.UnknownFormatConversionException;

public class LogItem implements Parcelable {

    private Object[] args;
    private String message;
    private int resourceId;
    private VpnStatus.LogLevel logLevel;
    private long logTime = System.currentTimeMillis();
    private int verbosityLevel = -1;

    public LogItem(VpnStatus.LogLevel level, int verblevel, String message) {
        this.message = message;
        this.logLevel = level;
        verbosityLevel = verblevel;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeArray(args);
        dest.writeString(message);
        dest.writeInt(resourceId);
        dest.writeInt(logLevel.getInt());
        dest.writeInt(verbosityLevel);
        dest.writeLong(logTime);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof LogItem))
            return obj.equals(this);

        LogItem other = (LogItem) obj;
        return Arrays.equals(args, other.args) &&
                ((other.message == null && message == other.message) ||
                        message.equals(other.message)) &&
                resourceId == other.resourceId &&
                ((logLevel == null && other.logLevel == logLevel) ||
                        other.logLevel.equals(logLevel)) &&
                verbosityLevel == other.verbosityLevel &&
                logTime == other.logTime;
    }

    byte[] getMarshalledBytes() throws UnsupportedEncodingException {
        ByteBuffer bb = ByteBuffer.allocate(16384);
        bb.put((byte) 0x0);
        bb.putLong(logTime);
        bb.putInt(verbosityLevel);
        bb.putInt(logLevel.getInt());
        bb.putInt(resourceId);
        if (message == null || message.length() == 0) {
            bb.putInt(0);
        } else {
            marshallString(message, bb);
        }
        if (args == null || args.length == 0) {
            bb.putInt(0);
        } else {
            bb.putInt(args.length);
            for (Object o : args) {
                if (o instanceof String) {
                    bb.putChar('s');
                    marshallString((String) o, bb);
                } else if (o instanceof Integer) {
                    bb.putChar('i');
                    bb.putInt((Integer) o);
                } else if (o instanceof Float) {
                    bb.putChar('f');
                    bb.putFloat((Float) o);
                } else if (o instanceof Double) {
                    bb.putChar('d');
                    bb.putDouble((Double) o);
                } else if (o instanceof Long) {
                    bb.putChar('l');
                    bb.putLong((Long) o);
                } else if (o == null) {
                    bb.putChar('0');
                } else {
                    bb.putChar('s');
                    marshallString(o.toString(), bb);
                }
            }
        }

        int pos = bb.position();
        bb.rewind();
        return Arrays.copyOf(bb.array(), pos);
    }

    private void marshallString(String str, ByteBuffer bb) throws UnsupportedEncodingException {
        byte[] utf8bytes = str.getBytes("UTF-8");
        bb.putInt(utf8bytes.length);
        bb.put(utf8bytes);
    }

    public LogItem(Parcel in) {
        args = in.readArray(Object.class.getClassLoader());
        message = in.readString();
        resourceId = in.readInt();
        logLevel = VpnStatus.LogLevel.getEnumByValue(in.readInt());
        verbosityLevel = in.readInt();
        logTime = in.readLong();
    }

    public static final Creator<LogItem> CREATOR = new Creator<LogItem>() {

        public LogItem createFromParcel(Parcel in) {
            return new LogItem(in);
        }

        public LogItem[] newArray(int size) {
            return new LogItem[size];
        }

    };

    public LogItem(VpnStatus.LogLevel logLevel, int resourceId, Object... args) {
        this.resourceId = resourceId;
        this.args = args;
        this.logLevel = logLevel;
    }

    public LogItem(VpnStatus.LogLevel logLevel, String msg) {
        this.logLevel = logLevel;
        message = msg;
    }

    public LogItem(VpnStatus.LogLevel logLevel, int resourceId) {
        this.resourceId = resourceId;
        this.logLevel = logLevel;
    }

    public String getString(Context c) {
        try {
            if (message != null) {
                return message;
            } else {
                if (c != null) {
                    if (resourceId == R.string.mobile_info)
                        return getMobileInfoString(c);
                    if (args == null)
                        return c.getString(resourceId);
                    else
                        return c.getString(resourceId, args);
                } else {
                    String str = String.format(Locale.ENGLISH, "Log (no context) resid %d", resourceId);
                    if (args != null)
                        str += join(args);

                    return str;
                }
            }
        } catch (UnknownFormatConversionException e) {
            if (c != null)
                throw new UnknownFormatConversionException(e.getLocalizedMessage() + getString(null));
            else
                throw e;
        } catch (FormatFlagsConversionMismatchException e) {
            if (c != null)
                throw new FormatFlagsConversionMismatchException(e.getLocalizedMessage() + getString(null), e.getConversion());
            else
                throw e;
        }
    }

    private String join(Object[] tokens) {
        StringBuilder sb = new StringBuilder();
        boolean firstTime = true;
        for (Object token : tokens) {
            if (firstTime) {
                firstTime = false;
            } else {
                sb.append("|");
            }
            sb.append(token);
        }
        return sb.toString();
    }

    @NonNull
    @Override
    public String toString() {
        return getString(null);
    }

    @SuppressLint("StringFormatMatches")
    private String getMobileInfoString(Context c) {
        c.getPackageManager();
        String apksign = "error getting package signature";
        String version = "error getting version";
        try {
            @SuppressLint("PackageManagerGetSignatures") Signature raw = c.getPackageManager().getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNATURES).signatures[0];
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(raw.toByteArray()));
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] der = cert.getEncoded();
            md.update(der);
            byte[] digest = md.digest();

            if (Arrays.equals(digest, VpnStatus.OFFICIAL_KEY))
                apksign = c.getString(R.string.official_build);
            else if (Arrays.equals(digest, VpnStatus.OFFICIAL_DEBUG_KEY))
                apksign = c.getString(R.string.debug_build);
            else if (Arrays.equals(digest, VpnStatus.AMAZON_KEY))
                apksign = "amazon version";
            else if (Arrays.equals(digest, VpnStatus.FDROID_KEY))
                apksign = "F-Droid built and signed version";
            else
                apksign = c.getString(R.string.built_by, cert.getSubjectX500Principal().getName());

            PackageInfo packageinfo = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            version = packageinfo.versionName;

        } catch (PackageManager.NameNotFoundException | CertificateException |
                NoSuchAlgorithmException e) {
            Log.w("LogItem", "getMobileInfoString: ", e);
        }

        Object[] argsCopy = Arrays.copyOf(args, args.length);
        argsCopy[argsCopy.length - 1] = apksign;
        argsCopy[argsCopy.length - 2] = version;
        return c.getString(R.string.mobile_info, argsCopy);
    }

    long getLogTime() {
        return logTime;
    }

}
