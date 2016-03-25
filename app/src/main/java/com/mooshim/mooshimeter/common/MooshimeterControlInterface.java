package com.mooshim.mooshimeter.common;

import java.util.List;

/**
 * Created by First on 2/26/2016.
 */
interface MooshimeterControlInterface {

    void setDelegate(final MooshimeterDelegate d);
    void removeDelegate();

    ////////////////////////////////
    // Convenience functions
    ////////////////////////////////

    boolean isInOADMode();

    //////////////////////////////////////
    // Autoranging
    //////////////////////////////////////

    boolean bumpRange(int channel, boolean expand);

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

    float getOffset(int c);
    void setOffset(int c, float offset);

    int getSampleRateHz();
    int setSampleRateIndex(int i);
    List<String> getSampleRateList();

    int getBufferDepth();
    int setBufferDepthIndex(int i);
    List<String> getBufferDepthList();

    void setBufferMode(int c, boolean on);

    boolean getLoggingOn();
    void setLoggingOn(boolean on);
    int getLoggingStatus();
    String getLoggingStatusMessage();
    void setLoggingInterval(int ms);
    int getLoggingIntervalMS();

    float getValue(int c);
    String formatValueLabel(int c, float value);

    float getPower();

    String       getRangeLabel(int c);
    int          setRangeIndex(int c,int r);
    List<String> getRangeList (int c);

    String getInputLabel(final int c);
    int getInputIndex(int c);
    int setInputIndex(int c, int mapping);
    List<String> getInputList(int c);
}
