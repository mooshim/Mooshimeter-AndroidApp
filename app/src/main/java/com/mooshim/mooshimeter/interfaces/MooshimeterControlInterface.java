package com.mooshim.mooshimeter.interfaces;
import com.mooshim.mooshimeter.common.MeterReading;
import com.mooshim.mooshimeter.devices.MooshimeterDeviceBase;

import java.util.List;

/**
 * Created by First on 2/26/2016.
 */
public interface MooshimeterControlInterface {

    enum Channel {
        CH1,
        CH2,
        MATH,
    }

    void addDelegate(final MooshimeterDelegate d);
    void removeDelegate();

    ////////////////////////////////
    // Convenience functions
    ////////////////////////////////

    boolean isInOADMode();

    //////////////////////////////////////
    // Autoranging
    //////////////////////////////////////

    boolean bumpRange(Channel c, boolean expand);

    // Return true if settings changed
    boolean applyAutorange();

    //////////////////////////////////////
    // Interacting with the Mooshimeter itself
    //////////////////////////////////////

    void setName(String name);
    String getName();

    void pause();
    void oneShot();
    void stream();

    void enterShippingMode();

    int getPCBVersion();

    double getUTCTime();
    void setTime(double utc_time);

    MeterReading getOffset(Channel c);
    void setOffset(Channel c, float offset);

    int getSampleRateHz();
    int setSampleRateIndex(int i);
    List<String> getSampleRateList();

    int getBufferDepth();
    int setBufferDepthIndex(int i);
    List<String> getBufferDepthList();

    void setBufferMode(Channel c, boolean on);

    boolean getLoggingOn();
    void setLoggingOn(boolean on);
    int getLoggingStatus();
    String getLoggingStatusMessage();
    void setLoggingInterval(int ms);
    int getLoggingIntervalMS();

    MeterReading getValue(Channel c);

    String       getRangeLabel(Channel c);
    int          setRange(Channel c,MooshimeterDeviceBase.RangeDescriptor rd);
    List<String> getRangeList (Channel c);

    String getInputLabel(final Channel c);
    int setInput(Channel c, MooshimeterDeviceBase.InputDescriptor descriptor);
    List<MooshimeterDeviceBase.InputDescriptor> getInputList(Channel c);
    MooshimeterDeviceBase.InputDescriptor getSelectedDescriptor(Channel c);
}
