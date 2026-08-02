package com.auto.serialport;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Recreated from decompiling CarInfo.apk (com.auto.serialport.SerialPort) so
 * we can call the exact same native library (libserialport.so, extracted
 * from that APK and bundled under jniLibs/armeabi-v7a). Must keep this
 * fully-qualified class name identical to the original -- JNI resolves
 * native methods by a symbol name baked from the package+class path.
 */
public class SerialPort {
    private FileInputStream mFileInputStream;
    private FileOutputStream mFileOutputStream;
    private FileDescriptor mFd;

    private static native FileDescriptor open(String path, int baudrate);

    public native void close();

    public boolean openSerialPort(File device, int baudrate) {
        FileDescriptor fd = open(device.getAbsolutePath(), baudrate);
        this.mFd = fd;
        if (fd == null) {
            return false;
        }
        this.mFileInputStream = new FileInputStream(fd);
        this.mFileOutputStream = new FileOutputStream(fd);
        return true;
    }

    public InputStream getInputStream() {
        return mFileInputStream;
    }

    public OutputStream getOutputStream() {
        return mFileOutputStream;
    }

    static {
        System.loadLibrary("serialport");
    }
}
