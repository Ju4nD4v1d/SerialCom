package com.jortegat.usbserialandroid.usbhostserialcom;

import java.io.InputStream;

public class SerialInputStream extends InputStream {
    private int timeout = 0;

    private final byte[] buffer;
    private int pointer;
    private int bufferSize;

    private final UsbSerialInterface device;

    SerialInputStream(UsbSerialInterface device) {
        this.device = device;
        int maxBufferSize = 16 * 1024;
        this.buffer = new byte[maxBufferSize];
        this.pointer = 0;
        this.bufferSize = -1;
    }

    @Override
    public int read() {
        int value = checkFromBuffer();
        if (value >= 0)
            return value;

        int ret = device.syncRead(buffer, timeout);
        if (ret >= 0) {
            bufferSize = ret;
            return buffer[pointer++] & 0xff;
        } else {
            return -1;
        }
    }

    @Override
    public int read(byte[] b) {
        return device.syncRead(b, timeout);
    }

    @Override
    public int available() {
        if (bufferSize > 0)
            return bufferSize - pointer;
        else
            return 0;
    }

    private int checkFromBuffer() {
        if (bufferSize > 0 && pointer < bufferSize) {
            return buffer[pointer++] & 0xff;
        } else {
            pointer = 0;
            bufferSize = -1;
            return -1;
        }
    }
}
