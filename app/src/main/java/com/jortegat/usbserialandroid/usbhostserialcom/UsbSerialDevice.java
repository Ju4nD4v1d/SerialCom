package com.jortegat.usbserialandroid.usbhostserialcom;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbRequest;

public abstract class UsbSerialDevice implements UsbSerialInterface {

    // Android version < 4.3 It is not going to be asynchronous read operations
    private static final boolean mr1Version = android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.JELLY_BEAN_MR1;
    final UsbDeviceConnection connection;

    static final int USB_TIMEOUT = 0;

    private SerialBuffer serialBuffer;

    private WorkerThread workerThread;
    private WriteThread writeThread;
    private ReadThread readThread;

    // Endpoints for synchronous read and write operations
    private UsbEndpoint inEndpoint;
    private UsbEndpoint outEndpoint;

    // InputStream and OutputStream (only for sync api)
    SerialInputStream inputStream;

    boolean asyncMode;
    boolean isOpen;

    UsbSerialDevice(UsbDeviceConnection connection) {
        this.connection = connection;
        this.asyncMode = true;
        serialBuffer = new SerialBuffer(mr1Version);
    }

    public static UsbSerialDevice createUsbSerialDevice(UsbDevice device, UsbDeviceConnection connection) {
        return new CDCSerialDevice(device, connection, -1);
    }

    @Override
    public abstract boolean open();

    @Override
    public void write(byte[] buffer) {
        if (asyncMode)
            serialBuffer.putWriteBuffer(buffer);
    }

    public int getInitialBaudRate() {
        return -1;
    }

    @Override
    public int read(UsbReadCallback mCallback) {
        if (!asyncMode)
            return -1;

        if (mr1Version) {
            if (workerThread != null) {
                workerThread.setCallback(mCallback);
                workerThread.getUsbRequest().queue(serialBuffer.getReadBuffer(), SerialBuffer.DEFAULT_READ_BUFFER_SIZE);
            }
        } else {
            readThread.setCallback(mCallback);
        }
        return 0;
    }


    @Override
    public abstract void close();

    @Override
    public abstract boolean syncOpen();

    @Override
    public abstract void syncClose();

    @Override
    public int syncWrite(byte[] buffer, int timeout) {
        if (!asyncMode) {
            if (buffer == null)
                return 0;

            return connection.bulkTransfer(outEndpoint, buffer, buffer.length, timeout);
        } else {
            return -1;
        }
    }

    @Override
    public int syncRead(byte[] buffer, int timeout) {
        if (asyncMode) {
            return -1;
        }

        if (buffer == null)
            return 0;

        return connection.bulkTransfer(inEndpoint, buffer, buffer.length, timeout);
    }

    // Serial port configuration
    @Override
    public abstract void setBaudRate(int baudRate);

    @Override
    public abstract void setDataBits(int dataBits);

    @Override
    public abstract void setStopBits(int stopBits);

    @Override
    public abstract void setParity(int parity);

    @Override
    public abstract void setFlowControl(int flowControl);

    @Override
    public abstract void setBreak(boolean state);

    /*
     * WorkerThread waits for request notifications from IN endpoint
     */
    protected class WorkerThread extends AbstractWorkerThread {

        private UsbReadCallback callback;
        private UsbRequest requestIN;

        @Override
        public void doRun() {
            UsbRequest request = connection.requestWait();
            if (request != null && request.getEndpoint().getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                    && request.getEndpoint().getDirection() == UsbConstants.USB_DIR_IN) {
                byte[] data = serialBuffer.getDataReceived();
                // Clear buffer, execute the callback
                serialBuffer.clearReadBuffer();
                onReceivedData(data);

                // Queue a new request
                requestIN.queue(serialBuffer.getReadBuffer(), SerialBuffer.DEFAULT_READ_BUFFER_SIZE);
            }
        }

        void setCallback(UsbReadCallback callback) {
            this.callback = callback;
        }

        void setUsbRequest(UsbRequest request) {
            this.requestIN = request;
        }

        UsbRequest getUsbRequest() {
            return requestIN;
        }

        private void onReceivedData(byte[] data) {
            if (callback != null)
                callback.onReceivedData(data);
        }
    }

    private class WriteThread extends AbstractWorkerThread {
        private UsbEndpoint outEndpoint;

        @Override
        public void doRun() {
            byte[] data = serialBuffer.getWriteBuffer();
            if (data.length > 0)
                connection.bulkTransfer(outEndpoint, data, data.length, USB_TIMEOUT);
        }

        void setUsbEndpoint(UsbEndpoint outEndpoint) {
            this.outEndpoint = outEndpoint;
        }
    }

    protected class ReadThread extends AbstractWorkerThread {

        private UsbReadCallback callback;
        private UsbEndpoint inEndpoint;

        void setCallback(UsbReadCallback callback) {
            this.callback = callback;
        }

        @Override
        public void doRun() {
            byte[] dataReceived;
            int numberBytes;
            if (inEndpoint != null)
                numberBytes = connection.bulkTransfer(inEndpoint, serialBuffer.getBufferCompatible(),
                        SerialBuffer.DEFAULT_READ_BUFFER_SIZE, 0);
            else
                numberBytes = 0;

            if (numberBytes > 0) {
                dataReceived = serialBuffer.getDataReceivedCompatible(numberBytes);
                onReceivedData(dataReceived);
            }
        }

        void setUsbEndpoint(UsbEndpoint inEndpoint) {
            this.inEndpoint = inEndpoint;
        }

        private void onReceivedData(byte[] data) {
            if (callback != null)
                callback.onReceivedData(data);
        }
    }

    void setSyncParams(UsbEndpoint inEndpoint, UsbEndpoint outEndpoint) {
        this.inEndpoint = inEndpoint;
        this.outEndpoint = outEndpoint;
    }

    void setThreadsParams(UsbRequest request, UsbEndpoint endpoint) {
        writeThread.setUsbEndpoint(endpoint);
        if (mr1Version) {
            workerThread.setUsbRequest(request);
        } else {
            readThread.setUsbEndpoint(request.getEndpoint());
        }
    }

    /*
     * Kill workingThread; This must be called when closing a device
     */
    void killWorkingThread() {
        if (mr1Version && workerThread != null) {
            workerThread.stopThread();
            workerThread = null;
        } else if (!mr1Version && readThread != null) {
            readThread.stopThread();
            readThread = null;
        }
    }

    /*
     * Restart workingThread if it has been killed before
     */
    void restartWorkingThread() {
        if (mr1Version && workerThread == null) {
            workerThread = new WorkerThread();
            workerThread.start();
            while (!workerThread.isAlive()) {
            } // Busy waiting
        } else if (!mr1Version && readThread == null) {
            readThread = new ReadThread();
            readThread.start();
            while (!readThread.isAlive()) {
            } // Busy waiting
        }
    }

    void killWriteThread() {
        if (writeThread != null) {
            writeThread.stopThread();
            writeThread = null;
        }
    }

    void restartWriteThread() {
        if (writeThread == null) {
            writeThread = new WriteThread();
            writeThread.start();
            while (!writeThread.isAlive()) {
            } // Busy waiting
        }
    }
}
