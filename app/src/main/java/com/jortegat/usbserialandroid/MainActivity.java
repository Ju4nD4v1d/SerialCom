package com.jortegat.usbserialandroid;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.jortegat.usbserialandroid.usbhostserialcom.UsbSerialDevice;
import com.jortegat.usbserialandroid.usbhostserialcom.UsbSerialInterface;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {
    private UsbSerialDevice serial;
    private UsbManager manager;
    private UsbDevice device = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        manager = (UsbManager) getSystemService(Context.USB_SERVICE);
    }

    private void setupDevice() {
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();

        for (UsbDevice usbDevice : deviceList
                .values()) {
            device = deviceList.get(usbDevice.getDeviceName());
        }
    }

    private void setupSerial(UsbDevice device) {
        if (device != null) {
            serial = UsbSerialDevice.createUsbSerialDevice(device, manager.openDevice(device));
            serial.open();
            serial.setBaudRate(115200);
            serial.setDataBits(UsbSerialInterface.DATA_BITS_8);
            serial.setParity(UsbSerialInterface.PARITY_ODD);
            serial.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
            serial.read(mCallback);
        }
    }

    private UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {
        @Override
        public void onReceivedData(byte[] data) {
            ByteBuffer wrapped = ByteBuffer.wrap(data);
            try {
                Log.d("BARCODE", "DATA RECEIVED: " + new String(data, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        setupDevice();
        setupSerial(device);
    }

    @Override
    protected void onStop() {
        super.onStop();
        serial.close();
    }

}
