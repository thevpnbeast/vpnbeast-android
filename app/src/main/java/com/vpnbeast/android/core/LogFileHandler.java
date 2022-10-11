package com.vpnbeast.android.core;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import com.vpnbeast.android.exception.ExceptionInfo;
import com.vpnbeast.android.exception.FileNotNullException;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Date;

// TODO: Understand the need for Log file related stuff
public class LogFileHandler extends Handler {

    static final int TRIM_LOG_FILE;
    private static final int FLUSH_TO_DISK;
    private static final int LOG_INIT;
    static final int LOG_MESSAGE;
    private static final int MAGIC_BYTE;
    private OutputStream logFile;
    private static final String TAG;
    private static final String LOGFILE_NAME;

    public LogFileHandler(Looper looper) {
        super(looper);
    }

    static {
        TAG = "LogFileHandler";
        LOGFILE_NAME = "logcache.dat";
        MAGIC_BYTE = 0x55;
        LOG_MESSAGE = 103;
        LOG_INIT = 102;
        FLUSH_TO_DISK = 101;
        TRIM_LOG_FILE = 100;
    }

    @Override
    public void handleMessage(Message msg) {
        try {
            if (msg.what == LOG_INIT) {
                if (logFile != null)
                    throw new FileNotNullException(ExceptionInfo.builder()
                            .errorMessage("logFile not null")
                            .timestamp(new Date(System.currentTimeMillis()))
                            .build());
                Log.i(TAG, "handleMessage: inside");
                readLogCache((File) msg.obj);
                openLogFile((File) msg.obj);
            } else if (msg.what == LOG_MESSAGE && msg.obj instanceof LogItem) {
                // Ignore log messages if not yet initialized
                if (logFile == null)
                    return;
                writeLogItemToDisk((LogItem) msg.obj);
            } else if (msg.what == TRIM_LOG_FILE) {
                trimLogFile();
                for (LogItem li : VpnStatus.getLogBufferList())
                    writeLogItemToDisk(li);
            } else if (msg.what == FLUSH_TO_DISK) {
                flushToDisk();
            }
        } catch (IOException | BufferOverflowException e) {
            Log.e(TAG, "handleMessage: ", e);
        }
    }

    private void flushToDisk() throws IOException {
        logFile.flush();
    }

    private void trimLogFile() {
        try {
            logFile.flush();
            ((FileOutputStream) logFile).getChannel().truncate(0);
        } catch (IOException e) {
            Log.e(TAG, "trimLogFile: ", e);
        }
    }

    private void writeLogItemToDisk(LogItem li) throws IOException {
        byte[] liBytes = li.getMarshalledBytes();
        writeEscapedBytes(liBytes);
    }

    private void writeEscapedBytes(byte[] bytes) throws IOException {
        int magic = 0;
        for (byte b : bytes)
            if (b == MAGIC_BYTE || b == MAGIC_BYTE + 1)
                magic++;

        byte[] eBytes = new byte[bytes.length + magic];
        int i = 0;
        for (byte b : bytes) {
            if (b == MAGIC_BYTE || b == MAGIC_BYTE + 1) {
                eBytes[i++] = (byte) (MAGIC_BYTE + 1);
                eBytes[i++] = (byte) (b - MAGIC_BYTE);
            } else {
                eBytes[i++] = b;
            }
        }

        byte[] lenBytes = ByteBuffer.allocate(4).putInt(bytes.length).array();
        synchronized (VpnStatus.READ_FILE_LOCK) {
            logFile.write(MAGIC_BYTE);
            logFile.write(lenBytes);
            logFile.write(eBytes);
        }
    }

    private void openLogFile(File cacheDir) throws FileNotFoundException {
        File logfile = new File(cacheDir, LOGFILE_NAME);
        logFile = new FileOutputStream(logfile);
    }

    private void readLogCache(File cacheDir) {
        try {
            File logfile = new File(cacheDir, LOGFILE_NAME);

            if (!logfile.exists() || !logfile.canRead())
                return;

            readCacheContents(new FileInputStream(logfile));
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "readLogCache: ", e);
        } finally {
            synchronized (VpnStatus.READ_FILE_LOCK) {
                VpnStatus.readFileLog = true;
                VpnStatus.READ_FILE_LOCK.notifyAll();
            }
        }
    }

    private void readCacheContents(InputStream in) throws IOException {
        BufferedInputStream bufferedInputStream = new BufferedInputStream(in);

        byte[] buf = new byte[16384];
        int read = bufferedInputStream.read(buf, 0, 5);
        int itemsRead = 0;

        readLoop:
        while (read >= 5) {
            int skipped = 0;
            while (buf[skipped] != MAGIC_BYTE) {
                skipped++;
                if ((bufferedInputStream.read(buf, skipped + 4, 1) != 1) || skipped + 10 > buf.length) {
                    break readLoop;
                }
            }
            int len = ByteBuffer.wrap(buf, skipped + 1, 4).asIntBuffer().get();
            int pos = 0;
            byte[] buf2 = new byte[buf.length];

            while (pos < len) {
                byte b = (byte) bufferedInputStream.read();
                if (b == MAGIC_BYTE) {
                    read = bufferedInputStream.read(buf, 1, 4) + 1;
                    continue readLoop;
                } else if (b == MAGIC_BYTE + 1) {
                    b = (byte) bufferedInputStream.read();
                    if (b == 0)
                        b = (byte) MAGIC_BYTE;
                    else if (b == 1)
                        b = (byte) (MAGIC_BYTE + 1);
                    else {
                        read = bufferedInputStream.read(buf, 1, 4) + 1;
                        continue readLoop;
                    }
                }
                buf2[pos++] = b;
            }

            read = bufferedInputStream.read(buf, 0, 5);
            itemsRead++;
            if (itemsRead > 2 * VpnStatus.MAX_LOG_ENTRIES) {
                read = 0;
            }

        }
    }
}