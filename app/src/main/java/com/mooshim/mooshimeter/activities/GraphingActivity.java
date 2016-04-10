package com.mooshim.mooshimeter.activities;

import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;

import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.interfaces.GraphingActivityInterface;
import com.mooshim.mooshimeter.common.MeterReading;
import com.mooshim.mooshimeter.interfaces.MooshimeterControlInterface;
import com.mooshim.mooshimeter.interfaces.MooshimeterDelegate;
import com.mooshim.mooshimeter.devices.MooshimeterDeviceBase;
import com.mooshim.mooshimeter.interfaces.NotifyHandler;
import com.mooshim.mooshimeter.common.Util;

import java.util.ArrayList;
import java.util.List;

import lecho.lib.hellocharts.formatter.SimpleAxisValueFormatter;
import lecho.lib.hellocharts.gesture.ContainerScrollType;
import lecho.lib.hellocharts.gesture.ZoomType;
import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.model.Viewport;
import lecho.lib.hellocharts.view.LineChartView;

public class GraphingActivity extends MyActivity implements GraphingActivityInterface, MooshimeterDelegate {

    ///////////////////////
    // STATICS
    ///////////////////////

    private static final String TAG = GraphingActivity.class.getSimpleName();

    private static int[] mColorList = {
            0xFFFF0000, // R
            0xFF0000FF, // B
            0xFF00FF00, // G
            0xFF008888, // G+B
            0xFF880088, // R+B
            0xFF888800, // R+G
    };

    private enum ChDispModes {
        OFF,
        LOCKED,
        MANUAL,
        AUTO,
    }

    static int[] sampleOptions = {50,100,200,400,800};

    ///////////////////////
    // WIDGETS
    ///////////////////////

    Button[] modeButtons = new Button[]{null,null};
    Button sampleButton;
    Button playButton;
    ImageButton scrollLockButton;

    LineChartView mChart;
    Axis xAxis, yAxisLeft, yAxisRight;

    ///////////////////////
    // BEHAVIOR CONTROL
    ///////////////////////

    final int maxNumberOfPoints = 10000;
    int maxNumberOfPointsOnScreen = 32;
    ChDispModes[] dispModes = new ChDispModes[]{ChDispModes.AUTO, ChDispModes.AUTO};;
    boolean scrollLockOn = true;
    boolean xyModeOn = false;
    boolean bufferModeOn = false;
    boolean playing = true;

    private MooshimeterDeviceBase mMeter;
    private double time_start;

    ///////////////////////
    // HELPER VARS
    ///////////////////////

    //Create lists for storing actual value of points that are visible on the screen for both axises
    VisibleVsBackupHelper leftAxisValues;
    VisibleVsBackupHelper rightAxisValues;
    VisibleVsBackupHelper[] axisValueHelpers;
    LinearTransform rightToLeftHelper = new LinearTransform();
    Viewport[] viewportStash = new Viewport[]{new Viewport(),new Viewport()};

    ///////////////////////
    // HELPER CLASSES
    ///////////////////////

    private static class LinearTransform {
        public float scale  = 1;
        public float offset = 0;
        public float apply(float in) {
            return in*scale+offset;
        }
        public float invert(float in) {
            return (in-offset)/scale;
        }
        public void calcFromViewports(Viewport from, Viewport to) {
            final float fromDiff = from.top-from.bottom;
            final float toDiff   = to.top-to.bottom;
            if (toDiff != 0 && fromDiff != 0) {
                scale = toDiff / fromDiff;
            } else {
                scale = 1;
            }
            offset = to.bottom-from.bottom*scale;
        }
    }
    private static class PointListHelper {
        // visible_list is bound to a line in mChart
        private final List<PointValue> list;
        public Viewport bounding_vp;
        private PointListHelper() {
            this(new ArrayList<PointValue>());
        }
        private PointListHelper(List<PointValue> bound_list) {
            this.list = bound_list;
            bounding_vp = new Viewport();
            recalcMinMax();
        }
        public void __add(PointValue p) {
            minMaxProcessPoint(p);
            list.add(p);
        }
        public void add(PointValue p) {
            __add(p);
        }
        public void add(List<PointValue> plist) {
            for(PointValue p:plist) {
                __add(p);
            }
        }
        private void minMaxProcessPoint(PointValue p) {
            float x = p.getX();
            float y = p.getY();
            if(x> bounding_vp.right) {
                bounding_vp.right = x;
            }
            if(x< bounding_vp.left) {
                bounding_vp.left = x;
            }
            if(y> bounding_vp.top) {
                bounding_vp.top = y;
            }
            if(y< bounding_vp.bottom) {
                bounding_vp.bottom = y;
            }
        }
        private void recalcMinMax() {
            bounding_vp.left  =  Float.MAX_VALUE;
            bounding_vp.right = -Float.MAX_VALUE;
            bounding_vp.bottom=  Float.MAX_VALUE;
            bounding_vp.top   = -Float.MAX_VALUE;
            for(PointValue p:list) {
                minMaxProcessPoint(p);
            }
        }
        void pop(int n_pop) {
            boolean recalc_flag = false;
            for(int i = 0; i < n_pop; i++) {
                final PointValue p = list.remove(0);
                float x = p.getX();
                float y = p.getY();
                if( x >= bounding_vp.right || x <= bounding_vp.left || y >= bounding_vp.top || y <= bounding_vp.bottom) {
                    recalc_flag = true;
                }
            }
            if(recalc_flag) {
                recalcMinMax();
            }
        }
        public Viewport getViewport() {
            return new Viewport(bounding_vp);
        }
        public int size() {
            return list.size();
        }
        public void clear() {
            list.clear();
            recalcMinMax();
        }
        public void copyTo(PointListHelper target) {
            target.add(list);
        }
        public List<PointValue> getList() {
            return list;
        }
    }
    private class VisibleVsBackupHelper {
        private PointListHelper backing;
        private PointListHelper visible;
        private VisibleVsBackupHelper() {
            visible = new PointListHelper();
            backing = new PointListHelper();
        }
        public void addPoint(PointValue p) {
            backing.add(p);
            visible.add(p);
            int visible_max = scrollLockOn ?maxNumberOfPointsOnScreen:maxNumberOfPoints;
            if(visible.size()>visible_max) {
                visible.pop(visible.size()-visible_max);
            }
            if(backing.size()>maxNumberOfPoints) {
                backing.pop(backing.size()-maxNumberOfPoints);
            }
        }
        public void addPoints(List<PointValue> l) {
            for(PointValue p:l) {
                addPoint(p);
            }
        }
        public void expandVisibleToAll() {
            visible.clear();
            backing.copyTo(visible);
        }
        public Viewport getViewport() {
            return visible.getViewport();
        }
        public List<PointValue> getVisible() {
            return visible.getList();
        }
        public void clear() {visible.clear();backing.clear();}
    }

    ///////////////////////
    // ACTIVITY OVERRIDES
    ///////////////////////

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_activity_graphing, menu);
        return super.onCreateOptionsMenu(menu);
    }

    private void toggleXYMode() {
        xyModeOn = !xyModeOn;
        if(xyModeOn) {
            setXAxisTitle(mMeter.getInputLabel(MooshimeterControlInterface.Channel.CH1));
            setYAxisTitle(0, mMeter.getInputLabel(MooshimeterControlInterface.Channel.CH2));
            setYAxisTitle(1, "NA");
        } else {
            setXAxisTitle("Time [s]");
            setYAxisTitle(0, mMeter.getInputLabel(MooshimeterControlInterface.Channel.CH1));
            setYAxisTitle(1, mMeter.getInputLabel(MooshimeterControlInterface.Channel.CH2));
        }
    }

    private void toggleBufferMode() {
        bufferModeOn = !bufferModeOn;
        leftAxisValues.clear();
        rightAxisValues.clear();
        mMeter.setBufferMode(MooshimeterControlInterface.Channel.CH1,bufferModeOn);
        mMeter.setBufferMode(MooshimeterControlInterface.Channel.CH2,bufferModeOn);
        refreshSampleButton();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_buffer_mode_toggle:
                toggleBufferMode();
                break;
            case R.id.action_xy_mode_toggle:
                toggleXYMode();
                break;
            case R.id.action_back:
                transitionToActivity(mMeter, ScanActivity.class);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graphing);
        mChart = (LineChartView) findViewById(R.id.lineChart);
        mChart.setViewportCalculationEnabled(false);
        mChart.setMaxZoom((float) 1e6);

        Intent intent = getIntent();
        mMeter = (MooshimeterDeviceBase)getDeviceWithAddress(intent.getStringExtra("addr"));

        scrollLockButton = (ImageButton)findViewById(R.id.lock_right);
        modeButtons[0] = (Button) findViewById(R.id.mode0);
        modeButtons[1] = (Button) findViewById(R.id.mode1);
        sampleButton = (Button) findViewById(R.id.n_samples);
        playButton = (Button) findViewById(R.id.play_button);

        refreshModeButton(0);
        refreshModeButton(1);
        refreshPlayButton();
        refreshScrollLockButton();
        refreshSampleButton();

        mChart.setInteractive(true);
        mChart.setZoomType(ZoomType.VERTICAL);
        mChart.setContainerScrollEnabled(true, ContainerScrollType.HORIZONTAL);

        LineChartData lineChartData = new LineChartData(new ArrayList<Line>());
        xAxis = new Axis().setName("Time [s]").setHasLines(true);
        xAxis.setTextColor(0xFF000000);
        xAxis.setHasTiltedLabels(true);
        lineChartData.setAxisXBottom(xAxis);

        yAxisLeft = new Axis().setName(mMeter.getInputLabel(MooshimeterControlInterface.Channel.CH1)).setHasLines(true);
        yAxisLeft.setLineColor(mColorList[0]);
        yAxisLeft.setTextColor(mColorList[0]);
        yAxisLeft.setHasTiltedLabels(true);
        yAxisRight = new Axis().setName(mMeter.getInputLabel(MooshimeterControlInterface.Channel.CH2)).setHasLines(true);
        yAxisRight.setLineColor(mColorList[1]);
        yAxisRight.setTextColor(mColorList[1]);
        yAxisRight.setHasTiltedLabels(true);
        yAxisRight.setFormatter(new RightAxisFormatter());

        lineChartData.setAxisYLeft(yAxisLeft);
        lineChartData.setAxisYRight(yAxisRight);
        mChart.setLineChartData(lineChartData);

        addStream("CH1");
        addStream("CH2");

        leftAxisValues  = new VisibleVsBackupHelper();
        rightAxisValues = new VisibleVsBackupHelper();
        axisValueHelpers = new VisibleVsBackupHelper[]{leftAxisValues,rightAxisValues};

        time_start = Util.getNanoTime();
    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Util.dispatch(new Runnable() {
            @Override
            public void run() {
                if(bufferModeOn) {
                    toggleBufferMode();
                }
                mMeter.pause();
                mMeter.removeDelegate();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if(mMeter==null) {
            onBackPressed();
        }

        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
        ActionBar actionBar = getActionBar();
        actionBar.hide();

        final MooshimeterDelegate d = this;

        Util.dispatch(new Runnable() {
            @Override
            public void run() {
                mMeter.addDelegate(d);
                if(bufferModeOn) {
                    toggleBufferMode();
                }
                if(playing) {
                    mMeter.stream();
                    Log.i(TAG, "Stream requested");
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        transitionToActivity(mMeter, DeviceActivity.class);
    }

    /////////////////////////
    // Widget Handlers+Refreshers
    /////////////////////////

    public void refreshModeButton(int i) {
        final Button b = modeButtons[i];
        final String s;
        switch(dispModes[i]) {
            case OFF:
                s = "OFF "+Integer.toString(i+1);
                break;
            case LOCKED:
                s = "LOCK "+Integer.toString(i+1);
                break;
            case MANUAL:
                s = "MAN "+Integer.toString(i+1);
                break;
            case AUTO:
                s = "AUTO "+Integer.toString(i+1);
                break;
            default:
                s="WHAT";
                break;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                b.setText(s);
            }
        });
    }
    public void onClickModeButton(int i) {
        int other = (i+1)%2;
        switch(dispModes[i]) {
            case OFF:
                dispModes[i] = ChDispModes.AUTO;
                break;
            case LOCKED:
                dispModes[i] = ChDispModes.MANUAL;
                if(dispModes[other]== ChDispModes.MANUAL) {
                    dispModes[other] = ChDispModes.LOCKED;
                }
                break;
            case MANUAL:
                dispModes[i] = ChDispModes.OFF;
                if(dispModes[other]== ChDispModes.LOCKED) {
                    dispModes[other] = ChDispModes.MANUAL;
                }
                break;
            case AUTO:
                dispModes[i] = ChDispModes.MANUAL;
                if(dispModes[other]== ChDispModes.MANUAL) {
                    dispModes[other] = ChDispModes.LOCKED;
                }
                break;
        }
        refreshModeButton(0);
        refreshModeButton(1);
    }
    public void onClickMode0Button(View v) { onClickModeButton(0); }
    public void onClickMode1Button(View v) { onClickModeButton(1); }
    public void refreshPlayButton() {
        final String s;
        if(playing) {
            s="PAUSE";
        } else {
            s="PLAY";
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                playButton.setText(s);
            }
        });
    }
    public void onClickPlayButton(View v) {
        playing = !playing;
        if(playing) {
            mMeter.stream();
            if(!scrollLockOn && !bufferModeOn) {
                onClickScrollLockButton(null);
            }
        } else {
            mMeter.pause();
            if(scrollLockOn && !bufferModeOn) {
                onClickScrollLockButton(null);
            }
        }
        refreshPlayButton();
    }
    public void refreshSampleButton() {
        final String s;
        s = Integer.toString(maxNumberOfPointsOnScreen)+"pt";
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                sampleButton.setText(s);
            }
        });
    }
    public void onClickSampleButton(View v) {
        final List<String> option_strings = new ArrayList<>();
        final Context context = this;
        for(int i:sampleOptions) {
            option_strings.add(Integer.toString(i));
        }
        sampleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Util.generatePopupMenuWithOptions(context, option_strings, sampleButton, new NotifyHandler() {
                    @Override
                    public void onReceived(double timestamp_utc, Object payload) {
                        maxNumberOfPointsOnScreen = sampleOptions[(Integer) payload];
                        calcViewport();
                        refresh();
                        refreshSampleButton();
                    }
                }, new Runnable() {
                    @Override
                    public void run() {
                        refreshSampleButton();
                    }
                });
            }
        });
    }
    public void refreshScrollLockButton() {
        final int alpha = scrollLockOn?50:25;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                scrollLockButton.setImageAlpha(alpha);
            }
        });
    }
    public void onClickScrollLockButton(View v) {
        scrollLockOn = !scrollLockOn;
        if (!scrollLockOn) {
            axisValueHelpers[0].expandVisibleToAll();
            axisValueHelpers[1].expandVisibleToAll();
            calcViewport();
            refresh();
        }
        refreshScrollLockButton();
    }

    /////////////////////////
    // GraphingActivityInterface methods
    /////////////////////////

    @Override
    public void refresh() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                List<Line> lines = mChart.getLineChartData().getLines();
                if(xyModeOn) {
                    List<PointValue> l0 = leftAxisValues.getVisible();
                    List<PointValue> l1 = rightAxisValues.getVisible();
                    List<PointValue> xy = new ArrayList<PointValue>(l0.size());
                    for(int i=0; i<Math.min(l0.size(), l1.size()); i++) {
                        xy.add(new PointValue(l0.get(i).getY(),l1.get(i).getY()));
                    }
                    lines.get(0).setValues(xy);
                    // Right axis should show no values
                    lines.get(1).setValues(new ArrayList<PointValue>());
                } else {
                    // Feed in empty dataset if we don't want to display
                    if(dispModes[0]==ChDispModes.OFF) {
                        lines.get(0).setValues(new ArrayList<PointValue>());
                    } else {
                        lines.get(0).setValues(leftAxisValues.getVisible());
                    }
                    if(dispModes[1]==ChDispModes.OFF) {
                        lines.get(1).setValues(new ArrayList<PointValue>());
                    } else {
                        // For the right axis, we must transform
                        List<PointValue> rline = lines.get(1).getValues();
                        rline.clear();
                        for (PointValue p : rightAxisValues.getVisible()) {
                            rline.add(new PointValue(p.getX(), rightToLeftHelper.apply(p.getY())));
                        }
                    }
                }

                //Force chart to draw current data again
                mChart.setLineChartData(mChart.getLineChartData());
            }
        });
    }

    public int addStream(final String title) {
        //Create new line with some default values
        Line line = new Line();
        line.setHasLines(true);
        line.setHasPoints(false);
        List<Line> lines = new ArrayList<>(mChart.getLineChartData().getLines());
        lines.add(line);
        mChart.getLineChartData().setLines(lines);
        int rval = lines.size() - 1;
        line.setColor(mColorList[rval]);
        return rval;
    }

    @Override
    public void addPoint(final int series_n, final float x, final float y) {
        PointValue v = new PointValue(x,y);
        List<PointValue> l = new ArrayList<>();
        l.add(v);
        addPoints(series_n, l);
    }

    @Override
    public void addPoints(final int series_n, final List<PointValue> new_values) {
        try {
            //Set new data on the graph
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    axisValueHelpers[series_n].addPoints(new_values);
                }
            });
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
            Log.e(TAG, "No series found at index " + series_n);
        }
    }

    @Override
    public void setXAxisTitle(String title) {
        xAxis.setName(title);
    }

    @Override
    public void setYAxisTitle(int series_n, String title) {
        if (series_n == 0) {
            yAxisLeft.setName(title);
        } else if(series_n == 1) {
            yAxisRight.setName(title);
        }
    }

    private Viewport calcMaxViewport() {
        // If a channel's auto mode is locked, don't let it drive the maximum viewport at all.
        if(dispModes[0]== ChDispModes.LOCKED) {
            return axisValueHelpers[1].getViewport();
        }
        if(dispModes[1]== ChDispModes.LOCKED) {
            return axisValueHelpers[0].getViewport();
        }
        Viewport lv = axisValueHelpers[0].getViewport();
        Viewport rv = axisValueHelpers[1].getViewport();
        // rv is in its own units
        // Because of HelloChart's annoying system, we need to transform to put it in terms of the left scale
        rv.top = rightToLeftHelper.apply(rv.top);
        rv.bottom = rightToLeftHelper.apply(rv.bottom);
        // Determine max of the two viewports (based on data)
        Viewport maxView = new Viewport();

        maxView.top    = Math.max(lv.top, rv.top);
        maxView.bottom = Math.min(lv.bottom, rv.bottom);
        maxView.right  = Math.max(lv.right, rv.right);
        maxView.left   = Math.min(lv.left, rv.left);
        return maxView;
    }

    private void calcViewport() {
        Viewport lv;
        Viewport rv;
        if(xyModeOn) {
            // xy mode is easier
            lv = axisValueHelpers[0].getViewport();
            rv = axisValueHelpers[1].getViewport();
            rv.left  = lv.bottom;
            rv.right = lv.top;
            mChart.setCurrentViewport(rv);
            mChart.setMaximumViewport(rv);
            return;
        }
        switch(dispModes[0]) {
            case AUTO:
                lv = axisValueHelpers[0].getViewport();
                break;
            case LOCKED:
                lv = viewportStash[0];
                break;
            case MANUAL:
            case OFF:
            default:
                lv = mChart.getCurrentViewport();
                break;

        }
        switch(dispModes[1]) {
            case AUTO:
                rv = axisValueHelpers[1].getViewport();
                break;
            case LOCKED:
                rv = viewportStash[1];
                break;
            case MANUAL:
            case OFF:
            default:
                rv = mChart.getCurrentViewport();
                rv.top = rightToLeftHelper.invert(rv.top);
                rv.bottom = rightToLeftHelper.invert(rv.bottom);
                break;
        }
        rightToLeftHelper.calcFromViewports(rv, lv);

        viewportStash[0] = new Viewport(lv);
        viewportStash[1] = new Viewport(rv);

        // rv is in its own units
        // Because of HelloChart's annoying system, we need to transform to put it in terms of the left scale
        rv.top = rightToLeftHelper.apply(rv.top);
        rv.bottom = rightToLeftHelper.apply(rv.bottom);

        Viewport maxView = calcMaxViewport();
        mChart.setMaximumViewport(maxView);

        Viewport currentView = mChart.getCurrentViewport();
        currentView.top    = lv.top;
        currentView.bottom = lv.bottom;
        if(scrollLockOn) {
            currentView.left = maxView.left;
            currentView.right = maxView.right;
        }
        mChart.setCurrentViewport(currentView);
    }

    /////////////////////////
    // MooshimeterDelegate methods
    /////////////////////////

    @Override
    public void onInit() {

    }

    @Override
    public void onDisconnect() {
        transitionToActivity(mMeter,ScanActivity.class);
    }

    @Override
    public void onRssiReceived(int rssi) {}

    @Override
    public void onBatteryVoltageReceived(float voltage) {

    }
    @Override
    public void onSampleReceived(double timestamp_utc, final MooshimeterControlInterface.Channel c, MeterReading val) {
        if(bufferModeOn || !playing) {
            Log.d(TAG,"Received a trailing sample");
            return;
        }
        if(c== MooshimeterControlInterface.Channel.MATH) {
            // Ignore for now
            return;
        }
        float dt = (float) (timestamp_utc - time_start);
        addPoint(c.ordinal(), dt, val.value);
        if(scrollLockOn) {
            calcViewport();
            refresh();
        }
    }
    double buf0_t = 0;
    @Override
    public void onBufferReceived(double timestamp_utc, final MooshimeterControlInterface.Channel c, float dt, float[] val) {
        if(!bufferModeOn || !playing) {
            Log.d(TAG,"Received a trailing buffer");
            return;
        }
        // There is some awkwardness here because the timestamps correspond
        // to when the buffers were received, not when they were taken.
        // We know for a fact the sampling was simultaneous, so we'll ignore channel1's
        // timestamp and just use ch0.
        if(c == MooshimeterControlInterface.Channel.CH1) {
            buf0_t = (timestamp_utc - dt*val.length)-time_start;
        }
        double t = buf0_t;
        List<PointValue> l = new ArrayList<>(val.length);
        for(float v:val) {
            l.add(new PointValue((float)t,v));
            t += dt;
        }
        addPoints(c.ordinal(), l);
        if(c== MooshimeterControlInterface.Channel.CH2) {
            // This is the second buffer
            maxNumberOfPointsOnScreen = val.length;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    refreshSampleButton();
                    calcViewport();
                    refresh();
                }
            });
        }
    }
    @Override
    public void onSampleRateChanged(int i, int sample_rate_hz) {}
    @Override
    public void onBufferDepthChanged(int i, int buffer_depth) {}
    @Override
    public void onLoggingStatusChanged(boolean on, int new_state, String message) {}
    @Override
    public void onRangeChange(final MooshimeterControlInterface.Channel c, MooshimeterDeviceBase.RangeDescriptor new_range) {}
    @Override
    public void onInputChange(final MooshimeterControlInterface.Channel c, MooshimeterDeviceBase.InputDescriptor descriptor) {}
    @Override
    public void onOffsetChange(MooshimeterControlInterface.Channel c, MeterReading offset) {

    }
    private class RightAxisFormatter extends SimpleAxisValueFormatter {
        @Override
        public int formatValueForAutoGeneratedAxis(char[] formattedValue, float value, int autoDecimalDigits) {
            //Scale back to the original value so that it can be shown on
            float scaledValue = rightToLeftHelper.invert(value);
            // WARNING: Due to a but in HelloCharts FloatUtil, trying to display too many digits causes
            // values to cap at INT_MAX.
            // Try to display 6 sigfigs
            int left_digits = (int)Math.log10((double)scaledValue);
            int right_digits = -1*(left_digits-6);
            right_digits = right_digits<0?0:right_digits;
            return super.formatValueForAutoGeneratedAxis(formattedValue, scaledValue, right_digits);
        }
    }
}