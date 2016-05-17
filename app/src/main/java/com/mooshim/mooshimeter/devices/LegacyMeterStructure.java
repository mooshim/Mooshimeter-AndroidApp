package com.mooshim.mooshimeter.devices;

import android.util.Log;

import com.mooshim.mooshimeter.interfaces.NotifyHandler;

import java.nio.BufferUnderflowException;
import java.util.UUID;

/**
 * Created by First on 3/18/2016.
 */
public abstract class LegacyMeterStructure {
    /**
     * Requests a read of the structure from the Mooshimeter, unpacks the response in to the
     * member variables.  Unpacking and packing are implemented by the subclass.
     * This function is asynchronous - the function returns before the structure is updated -
     * you must wait for cb to be called.
     */
    private static String TAG = "LegacyStruct";
    private PeripheralWrapper mPwrap;
    //public LegacyMeterStructure() {
    //    Log.e(TAG,"CAN'T INITIALIZE WITHOUT PWRAP");
    //}
    public LegacyMeterStructure(PeripheralWrapper pwrap) {
        mPwrap=pwrap;
    }
    public void update() {
        unpack(mPwrap.req(getUUID()));
    }
    public void unpack(byte[] in) {
        if(in==null) {
            Log.e(TAG, "Can't unpack a null buffer!");
            Log.e(TAG, Log.getStackTraceString(new Exception()));
            return;
        }
        try {
            unpackInner(in);
        }
        catch(BufferUnderflowException e){
            Log.e(TAG,"Received incorrect pack length while unpacking!");
            Log.e(TAG, Log.getStackTraceString(new Exception()));
        }
    }
    /**
     * Sends the struct to the Mooshimeter, unpacks the response in to the
     * member variables.  Unpacking and packing are implemented by the subclass.
     * This function is asynchronous - the function returns before the structure is updated -
     * you must wait for cb to be called.
     */
    public int send() {
        return mPwrap.send(getUUID(), pack());
    }

    /**
     * Tells you whether notifications are enabled for this characteristic
     * @return boolean Is it enabled or aint it
     */
    public boolean isNotificationEnabled() {
        return mPwrap.isNotificationEnabled(getUUID());
    }

    /**
     * Enable or disable notifications on this field and set the callbacks.
     * @param enable        If true, enable the notification.  If false, disable.
     * @param on_notify     When a notify event is received, this is called.
     */
    public int enableNotify(boolean enable, final NotifyHandler on_notify) {
        return mPwrap.enableNotify(getUUID(), enable, new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                byte[] bytes = (byte[])payload;
                boolean success = true;
                try {
                    unpack(bytes);
                } catch (BufferUnderflowException e) {
                    success = false;
                    Log.e(TAG, "Received incorrect pack length while unpacking!");
                    Log.e(TAG, Log.getStackTraceString(new Exception()));
                } finally {
                    if (success && on_notify != null) {
                        on_notify.onReceived(timestamp_utc, bytes);
                    }
                }
            }
        });
    }

    /**
     * Serialize the instance members
     * @return  A byte[] suitable for transmission as a BLE payload
     */
    public abstract byte[] pack();

    /**
     * Interpret a BLE payload and set the instance members
     * @param in A byte[] received as a BLE payload
     */
    public abstract void unpackInner(byte[] in);

    /**
     *
     * @return The UUID of this structure
     */
    public abstract UUID getUUID();
}
