package com.jortegat.usbserialandroid.usbhostserialcom;

import java.io.EOFException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import okio.Buffer;

class SerialBuffer {
    static final int DEFAULT_READ_BUFFER_SIZE = 16 * 1024;
    private static final int MAX_BULK_BUFFER = 16 * 1024;
    private ByteBuffer readBuffer;

    private final SynchronizedBuffer writeBuffer;
    private byte[] readBufferCompatible; // Read buffer for android < 4.2

    SerialBuffer(boolean version) {
        writeBuffer = new SynchronizedBuffer();
        if (version) {
            readBuffer = ByteBuffer.allocate(DEFAULT_READ_BUFFER_SIZE);

        } else {
            readBufferCompatible = new byte[DEFAULT_READ_BUFFER_SIZE];
        }
    }

    ByteBuffer getReadBuffer() {
        synchronized (this) {
            return readBuffer;
        }
    }

    byte[] getDataReceived() {
        synchronized (this) {
            byte[] dst = new byte[readBuffer.position()];
            readBuffer.position(0);
            readBuffer.get(dst, 0, dst.length);
            return dst;
        }
    }

    public void clearReadBuffer() {
        synchronized (this) {
            readBuffer.clear();
        }
    }

    public byte[] getWriteBuffer() {
        return writeBuffer.get();
    }

    public void putWriteBuffer(byte[] data) {
        writeBuffer.put(data);
    }


    public byte[] getBufferCompatible() {
        return readBufferCompatible;
    }

    public byte[] getDataReceivedCompatible(int numberBytes) {
        return Arrays.copyOfRange(readBufferCompatible, 0, numberBytes);
    }

    private class SynchronizedBuffer {
        private Buffer buffer;

        SynchronizedBuffer() {
            this.buffer = new Buffer();
        }

        synchronized void put(byte[] src) {
            if (src == null || src.length == 0) return;

            buffer.write(src);
            notify();
        }

        synchronized byte[] get() {
            if (buffer.size() == 0) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
            }
            byte[] dst;
            if (buffer.size() <= MAX_BULK_BUFFER) {
                dst = buffer.readByteArray();
            } else {
                try {
                    dst = buffer.readByteArray(MAX_BULK_BUFFER);
                } catch (EOFException e) {
                    e.printStackTrace();
                    return new byte[0];
                }
            }
            return dst;
        }
    }

}
