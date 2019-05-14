package com.jortegat.usbserialandroid.usbhostserialcom;

abstract class AbstractWorkerThread extends Thread {
    private volatile boolean keep = true;
    private volatile Thread workingThread;

    void stopThread() {
        keep = false;
        if (this.workingThread != null) {
            this.workingThread.interrupt();
        }
    }

    public final void run() {
        if (!this.keep) {
            return;
        }
        this.workingThread = Thread.currentThread();
        while (this.keep && (!this.workingThread.isInterrupted())) {
            doRun();
        }
    }

    abstract void doRun();
}
