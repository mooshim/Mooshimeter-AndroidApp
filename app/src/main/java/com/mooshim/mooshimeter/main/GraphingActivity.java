package com.mooshim.mooshimeter.main;

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

import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.common.GraphingActivityInterface;
import com.mooshim.mooshimeter.common.MooshimeterDelegate;
import com.mooshim.mooshimeter.common.MooshimeterDevice;
import com.mooshim.mooshimeter.common.MooshimeterDeviceBase;
import com.mooshim.mooshimeter.common.NotifyHandler;
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

    Button[] chModeButtons = new Button[]{null,null};
    Button sampleButton;
    int[] sampleOptions = {50,100,200,400,800};
    Button playButton;

    LineChartView mChart;
    final int maxNumberOfPoints = 10000;
    int maxNumberOfPointsOnScreen = 32;
    Axis xAxis, yAxisLeft, yAxisRight;
    ChDispModes[] dispModes = new ChDispModes[]{ChDispModes.AUTO, ChDispModes.AUTO};;
    boolean scrollLockOn = true;
    boolean xyModeOn = false;
    boolean playing = true;

    //Create lists for storing actual value of points that are visible on the screen for both axises
    VisibleVsBackupHelper leftAxisValues;
    VisibleVsBackupHelper rightAxisValues;
    VisibleVsBackupHelper[] axisValueHelpers;
    LinearTransform rightToLeftHelper = new LinearTransform();
    Viewport[] viewportStash = new Viewport[]{new Viewport(),new Viewport()};

    private MooshimeterDeviceBase mMeter;
    private double time_start;

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
        void pop() {
            final PointValue p = list.remove(0);
            float x = p.getX();
            float y = p.getY();
            if( x >= bounding_vp.right || x <= bounding_vp.left || y >= bounding_vp.top || y >= bounding_vp.bottom) {
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
            while(visible.size()>visible_max) {
                visible.pop();
            }
            while(backing.size()>maxNumberOfPoints) {
                backing.pop();
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
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_activity_graphing, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_buffer_mode_toggle:
                mMeter.setBufferMode(0,true);
                mMeter.setBufferMode(1,true);
                break;
            case R.id.action_back:
                onDisconnect();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void refreshModeButton(int i) {
        final Button b = chModeButtons[i];
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
    private void processScalingButtonClick(int i) {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graphing);
        mChart = (LineChartView) findViewById(R.id.lineChart);
        mChart.setViewportCalculationEnabled(false);
        mChart.setMaxZoom((float) 1000.0);

        Intent intent = getIntent();
        mMeter = getDeviceWithAddress(intent.getStringExtra("addr"));

        (findViewById(R.id.lock_right)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scrollLockOn = !scrollLockOn;
                if (!scrollLockOn) {
                    axisValueHelpers[0].expandVisibleToAll();
                    axisValueHelpers[1].expandVisibleToAll();
                }
            }
        });

        chModeButtons[0] = (Button) findViewById(R.id.scaling0);
        chModeButtons[0].setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                processScalingButtonClick(0);
            }
        });
        chModeButtons[1] = (Button) findViewById(R.id.scaling1);
        chModeButtons[1].setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                processScalingButtonClick(1);
            }
        });
        refreshModeButton(0);
        refreshModeButton(1);

        sampleButton = (Button) findViewById(R.id.n_samples);
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
                    }
                }, null);
            }
        });

        playButton = (Button) findViewById(R.id.play_button);
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playing = !playing;
                if(playing) {
                    mMeter.stream();
                } else {
                    mMeter.pause();
                }
            }
        });


        mChart.setInteractive(true);
        mChart.setZoomType(ZoomType.VERTICAL);
        mChart.setContainerScrollEnabled(true, ContainerScrollType.HORIZONTAL);

        LineChartData lineChartData = new LineChartData(new ArrayList<Line>());
        xAxis = new Axis().setName("Time [s]").setHasLines(true);
        lineChartData.setAxisXBottom(xAxis);

        yAxisLeft = new Axis().setName(mMeter.getInputLabel(0)).setHasLines(true);
        yAxisLeft.setLineColor(mColorList[0]);
        yAxisLeft.setTextColor(mColorList[0]);
        yAxisLeft.setHasTiltedLabels(true);
        yAxisRight = new Axis().setName(mMeter.getInputLabel(1)).setHasLines(true);
        yAxisRight.setLineColor(mColorList[1]);
        yAxisRight.setTextColor(mColorList[1]);
        yAxisRight.setHasTiltedLabels(true);
        yAxisRight.setFormatter(new ValueFormatter());

        lineChartData.setAxisYLeft(yAxisLeft);
        lineChartData.setAxisYRight(yAxisRight);
        mChart.setLineChartData(lineChartData);

        addStream("CH1");
        addStream("CH2");
        
        leftAxisValues  = new VisibleVsBackupHelper();
        rightAxisValues = new VisibleVsBackupHelper();
        axisValueHelpers = new VisibleVsBackupHelper[]{leftAxisValues,rightAxisValues};
    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Util.dispatch(new Runnable() {
            @Override
            public void run() {
                mMeter.pause();
                mMeter.removeDelegate();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
        ActionBar actionBar = getActionBar();
        actionBar.hide();

        final MooshimeterDelegate d = this;
        time_start = Util.getNanoTime();

        Util.dispatch(new Runnable() {
            @Override
            public void run() {
                mMeter.setDelegate(d);
                mMeter.stream();
                Log.i(TAG, "Stream requested");
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Util.dispatch(new Runnable() {
            @Override
            public void run() {
                mMeter.setBufferMode(0, false);
                mMeter.setBufferMode(1,false);
            }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        transitionToActivity(mMeter, DeviceActivity.class);
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
                    calcViewport();
                    refresh();
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

        // Since we're adjusting the data actually being displayed on screen, we should not need to mess
        // with the current viewport
        // Grab only the most recent N points and base the current viewport off of them
        //mChart.setCurrentViewport(currentViewport);
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
    public void onBatteryVoltageReceived(float voltage) {

    }

    @Override
    public void onSampleReceived(double timestamp_utc, int channel, float val) {
        float dt = (float) (timestamp_utc - time_start);
        addPoint(channel, dt, val);
    }

    @Override
    public void onBufferReceived(double timestamp_utc, int channel, float dt, float[] val) {
        double t = (timestamp_utc - dt*val.length)-time_start;
        List<PointValue> l = new ArrayList<>(val.length);
        for(float v:val) {
            l.add(new PointValue((float)t,v));
            t+= dt;
        }
        addPoints(channel, l);
    }

    @Override
    public void onSampleRateChanged(int i, int sample_rate_hz) {

    }

    @Override
    public void onBufferDepthChanged(int i, int buffer_depth) {

    }

    @Override
    public void onLoggingStatusChanged(boolean on, int new_state, String message) {

    }

    @Override
    public void onRangeChange(int c, int i, MooshimeterDevice.RangeDescriptor new_range) {

    }

    @Override
    public void onInputChange(int c, int i, MooshimeterDevice.InputDescriptor descriptor) {

    }

    @Override
    public void onRealPowerCalculated(double timestamp_utc, float val) {

    }

    @Override
    public void onOffsetChange(int c, float offset) {

    }

    private class ValueFormatter extends SimpleAxisValueFormatter {
        @Override
        public int formatValueForAutoGeneratedAxis(char[] formattedValue, float value, int autoDecimalDigits) {
            //Scale back to the original value so that it can be shown on
            float scaledValue = rightToLeftHelper.invert(value);
            return super.formatValueForAutoGeneratedAxis(formattedValue, scaledValue, autoDecimalDigits);
        }
    }
}