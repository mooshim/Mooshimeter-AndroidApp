package com.mooshim.mooshimeter.common;

/**
 * Created by First on 2/26/2016.
 */
public interface MooshimeterDelegate {
    void onInit();

    void onDisconnect();

    void onRssiReceived(int rssi);

    void onBatteryVoltageReceived(final float voltage);

    void onSampleReceived(final double timestamp_utc, final int channel, final float val);

    void onBufferReceived(final double timestamp_utc, final int channel, final float dt, final float val[]);

    void onSampleRateChanged(final int i, final int sample_rate_hz);

    void onBufferDepthChanged(final int i, final int buffer_depth);

    void onLoggingStatusChanged(boolean on, int new_state, String message);

    void onRangeChange(final int c, final int i, final MooshimeterDeviceBase.RangeDescriptor new_range);

    void onInputChange(final int c, final int i, final MooshimeterDeviceBase.InputDescriptor descriptor);

    void onRealPowerCalculated(final double timestamp_utc, final float val);

    void onOffsetChange(final int c, final float offset);
}
