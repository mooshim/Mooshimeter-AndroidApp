package com.mooshim.mooshimeter.common;

/**
 * Created by First on 2/12/2016.
 */
public abstract class NotifyHandler {
    public abstract void onReceived(double timestamp_utc, Object payload);
}
