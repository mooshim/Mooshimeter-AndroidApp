package com.mooshim.mooshimeter.devices;

import com.mooshim.mooshimeter.common.Chooser;
import com.mooshim.mooshimeter.common.LogFile;
import com.mooshim.mooshimeter.common.MeterReading;
import com.mooshim.mooshimeter.common.Util;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimulatedMooshimeterDevice extends MooshimeterDeviceBase{

    MeterReading[] offsets = {new MeterReading(0),new MeterReading(0)};
    boolean streaming = false;
    boolean buffermode = false;
    boolean mLoggingOn = false;
    Chooser<Integer> mSampleRate = new Chooser<>();
    Chooser<Integer> mBufferDepth = new Chooser<>();
    Chooser<InputDescriptor> mInputDescriptors = new Chooser<>();
    Chooser<RangeDescriptor> mRangeDescriptors = new Chooser<>();
    int mLoggingInterval = 10;

    SimulatedMooshimeterDevice mSelfRef = this;

    Map<Integer,LogFile> mLogs = new HashMap<>();

    public SimulatedMooshimeterDevice(PeripheralWrapper wrap) {
        super(wrap);
        mSampleRate.add(125);
        mSampleRate.add(250);
        mSampleRate.add(500);
        mSampleRate.add(1000);
        mSampleRate.add(2000);
        mSampleRate.add(4000);
        mSampleRate.add(8000);

        mBufferDepth.add(32);
        mBufferDepth.add(64);
        mBufferDepth.add(128);
        mBufferDepth.add(256);

        mInputDescriptors.add(new InputDescriptor("Voltage","V"));
        mInputDescriptors.add(new InputDescriptor("Current","A"));

        mRangeDescriptors.add(new RangeDescriptor("10F",10));
        mRangeDescriptors.add(new RangeDescriptor("100F",100));
        mRangeDescriptors.add(new RangeDescriptor("1000F",1000));
    }

    @Override
    float getEnob(Channel c) {
        return 16;
    }

    @Override
    float getMaxRangeForChannel(Channel c) {
        return 100;
    }

    @Override
    public boolean bumpRange(Channel c, boolean expand) {
        return false;
    }

    @Override
    public boolean applyAutorange() {
        return false;
    }

    @Override
    public void setName(String name) {
    }

    @Override
    public String getName() {
        return "SIMULATED";
    }

    @Override
    public void pause() {
        streaming = false;
    }

    @Override
    public void oneShot() {
        streaming = false;
        streamer.run();
    }

    @Override
    public void stream() {
        streaming = true;
        streamer.run();
    }

    private Runnable streamer = new Runnable() {
        @Override
        public void run() {
            delegate.onSampleReceived(getUTCTime(),Channel.CH1,new MeterReading((float)Math.sin(Util.getUTCTime()/10),5,100,"F"));
            delegate.onSampleReceived(getUTCTime(),Channel.CH2,new MeterReading((float)Math.sin(Util.getUTCTime()/10),5,100,"F"));
            if(streaming) {
                Util.postDelayed(streamer, 500);
            }
        }
    };

    @Override
    public void reboot() {
        Util.postDelayed(new Runnable() {
            @Override
            public void run() {
                delegate.onDisconnect();
            }
        },1000);
    }

    @Override
    public void enterShippingMode() {
        Util.postDelayed(new Runnable() {
            @Override
            public void run() {
                delegate.onDisconnect();
            }
        },1000);
    }

    @Override
    public int getPCBVersion() {
        return 8;
    }

    @Override
    public double getUTCTime() {
        return System.currentTimeMillis()/1000;
    }

    @Override
    public void setTime(double utc_time) {}

    @Override
    public MeterReading getOffset(Channel c) {
        return new MeterReading(0);
    }

    @Override
    public void setOffset(final Channel c, final float offset) {
        offsets[c.ordinal()] = new MeterReading(offset);
        Util.dispatchCb(new Runnable() {
            @Override
            public void run() {
                delegate.onOffsetChange(c,offsets[c.ordinal()]);
            }
        });
    }

    @Override
    public int getSampleRateHz() {
        return mSampleRate.getChosen();
    }

    @Override
    public int setSampleRateIndex(int i) {
        return mSampleRate.choose(i);
    }

    @Override
    public List<String> getSampleRateList() {
        return mSampleRate.getChoiceNames();
    }

    @Override
    public int getBufferDepth() {
        return mBufferDepth.getChosen();
    }

    @Override
    public int setBufferDepthIndex(int i) {
        return mBufferDepth.chooseIndex((Integer)i);
    }

    @Override
    public List<String> getBufferDepthList() {
        return mBufferDepth.getChoiceNames();
    }

    @Override
    public void setBufferMode(Channel c, boolean on) {
        buffermode = on;
    }

    @Override
    public boolean getLoggingOn() {
        return mLoggingOn;
    }

    @Override
    public void setLoggingOn(final boolean on) {
        mLoggingOn = on;
        Util.dispatchCb(new Runnable() {
            @Override
            public void run() {
                delegate.onLoggingStatusChanged(on,0,"OK");
            }
        });
    }

    @Override
    public int getLoggingStatus() {
        return 0;
    }

    @Override
    public String getLoggingStatusMessage() {
        return "Simulated OK";
    }

    @Override
    public void setLoggingInterval(int ms) {
        mLoggingInterval = ms;
    }

    @Override
    public int getLoggingIntervalMS() {
        return mLoggingInterval;
    }

    @Override
    public MeterReading getValue(Channel c) {
        return new MeterReading(0);
    }

    @Override
    public String getRangeLabel(Channel c) {
        return mRangeDescriptors.getChosen().name;
    }

    @Override
    public int setRange(final Channel c, final RangeDescriptor rd) {
        mRangeDescriptors.choose(rd);
        Util.dispatchCb(new Runnable() {
            @Override
            public void run() {
                delegate.onRangeChange(c,rd);
            }
        });
        return 0;
    }

    @Override
    public List<String> getRangeList(Channel c) {
        return mRangeDescriptors.getChoiceNames();
    }

    @Override
    public String getInputLabel(Channel c) {
        return mInputDescriptors.getChosen().name;
    }

    @Override
    public int setInput(final Channel c, final InputDescriptor descriptor) {
        mInputDescriptors.choose(descriptor);
        Util.dispatchCb(new Runnable() {
            @Override
            public void run() {
                delegate.onInputChange(c, descriptor);
            }
        });
        return 0;
    }

    @Override
    public List<InputDescriptor> getInputList(Channel c) {
        return mInputDescriptors.getChoices();
    }

    @Override
    public InputDescriptor getSelectedDescriptor(Channel c) {
        return mInputDescriptors.getChosen();
    }

    private class LogInfoProducer implements Runnable{
        public int i = 0;
        @Override
        public void run() {
            LogFile logFile = new LogFile();
            logFile.mMeter = mSelfRef;
            logFile.mIndex = i;
            logFile.mBytes = 5000;
            logFile.mEndTime = (System.currentTimeMillis()/1000)-(3600*(10-i));
            mLogs.put(i,logFile);
            delegate.onLogInfoReceived(logFile);
            if(i<10) {
                i++;
                Util.postDelayed(this, 500);
            }
        }
    }

    @Override
    public void pollLogInfo() {
        Util.postDelayed(new LogInfoProducer(),1000);
    }

    private class LogDataProducer implements Runnable{
        LogFile mLogFile;
        int mBytesRemaining;
        public LogDataProducer(LogFile file) {
            mLogFile = file;
            mBytesRemaining = mLogFile.mBytes;
        }
        @Override
        public void run() {
            String testline = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.\n";
            try {
                mLogFile.mData.write(testline.getBytes());
                mBytesRemaining -= testline.length();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(mBytesRemaining<=0) {
                delegate.onLogFileReceived(mLogFile);
            } else {
                // Synthesize 1kb/s
                int ms_delay = testline.length();
                Util.postDelayed(this, ms_delay);
            }
        }
    }

    @Override
    public void downloadLog(LogFile log) {
        Util.postDelayed(new LogDataProducer(log),100);
    }

    @Override
    public LogFile getLogInfo(int index) {
        return mLogs.get(index);
    }

    private boolean mIsConnected = false;
    private boolean mIsDiscovered= false;
    private boolean mIsInitialized=false;

    @Override
    public int connect() {
        mIsConnected = true;
        return 0;
    }
    @Override
    public int disconnect() {
        mIsConnected = false;
        Util.dispatchCb(new Runnable() {
            @Override
            public void run() {
                delegate.onDisconnect();
            }
        });
        return 0;
    }
    @Override
    public int discover() {
        return 0;
    }
    @Override
    public int initialize(){
        return 0;
    }
    @Override
    public boolean isConnected() {
        return mIsConnected;
    }
    @Override
    public boolean isConnecting() {
        return false;
    }
    @Override
    public boolean isDisconnected() {
        return !mIsConnected;
    }
    @Override
    public int getRSSI() {
        return -50;
    }
    @Override
    public void setRSSI(int rssi) {}
    @Override
    public String getAddress() {
        return "FAKEADDR";
    }
    @Override
    public boolean isInOADMode() {
        return false;
    }
}
