package com.mooshim.mooshimeter.interfaces;

import com.mooshim.mooshimeter.common.MeterReading;
import com.mooshim.mooshimeter.devices.MooshimeterDeviceBase;

/**
 * Created by First on 2/26/2016.
 */
public interface MooshimeterDelegate {
    void onDisconnect();

    void onRssiReceived(int rssi);

    void onBatteryVoltageReceived(final float voltage);

    void onSampleReceived(final double timestamp_utc, final MooshimeterControlInterface.Channel c, final MeterReading val);

    void onBufferReceived(final double timestamp_utc, final MooshimeterControlInterface.Channel c, final float dt, final float[] val);

    void onSampleRateChanged(final int i, final int sample_rate_hz);

    void onBufferDepthChanged(final int i, final int buffer_depth);

    void onLoggingStatusChanged(boolean on, int new_state, String message);

    void onRangeChange(final MooshimeterControlInterface.Channel c, final MooshimeterDeviceBase.RangeDescriptor new_range);

    void onInputChange(final MooshimeterControlInterface.Channel c, final MooshimeterDeviceBase.InputDescriptor descriptor);

    void onOffsetChange(final MooshimeterControlInterface.Channel c, final MeterReading offset);
}
