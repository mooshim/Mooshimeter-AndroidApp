package com.mooshim.mooshimeter.main;

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
            0xFF00FF00, // G
            0xFF0000FF, // B
            0xFF008888, // G+B
            0xFF880088, // R+B
            0xFF888800, // R+G
    };

    LineChartView mChart;
    final int maxNumberOfPoints = 10000;
    int maxNumberOfPointsOnScreen = 256;
    Axis xAxis, yAxisLeft, yAxisRight;
    boolean manualAxisScaling = false;
    boolean lockedRight = true;
    //Create lists for storing actual value of points that are visible on the screen for both axises
    VisibleVsBackupHelper leftAxisValues;
    VisibleVsBackupHelper rightAxisValues;
    VisibleVsBackupHelper[] helpers;
    LinearTransform rightToLeftHelper = new LinearTransform();
    final static LinearTransform unityHelper = new LinearTransform();

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
        public Viewport vp;
        private PointListHelper() {
            this(new ArrayList<PointValue>());
        }
        private PointListHelper(List<PointValue> bound_list) {
            this.list = bound_list;
            vp = new Viewport();
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
            if(x>vp.right) {
                vp.right = x;
            }
            if(x<vp.left) {
                vp.left = x;
            }
            if(y>vp.top) {
                vp.top = y;
            }
            if(y<vp.bottom) {
                vp.bottom = y;
            }
        }
        private void recalcMinMax() {
            vp.left  =  Float.MAX_VALUE;
            vp.right = -Float.MAX_VALUE;
            vp.bottom=  Float.MAX_VALUE;
            vp.top   = -Float.MAX_VALUE;
            for(PointValue p:list) {
                minMaxProcessPoint(p);
            }
        }
        void pop() {
            final PointValue p = list.remove(0);
            float x = p.getX();
            float y = p.getY();
            if( x >= vp.right || x <= vp.left || y >= vp.top || y >= vp.bottom) {
                recalcMinMax();
            }
        }
        public Viewport getViewport() {
            return new Viewport(vp);
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
            int visible_max = lockedRight?maxNumberOfPointsOnScreen:maxNumberOfPoints;
            while(visible.size()>visible_max) {
                visible.pop();
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graphing);
        mChart = (LineChartView) findViewById(R.id.lineChart);
        mChart.setViewportCalculationEnabled(false);
        mChart.setMaxZoom((float) 1000.0);

        (findViewById(R.id.lock_right)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lockedRight = !lockedRight;
                if(!lockedRight) {
                    helpers[0].expandVisibleToAll();
                    helpers[1].expandVisibleToAll();
                }
            }
        });

        final Button scalingButton = (Button) findViewById(R.id.scaling);
        scalingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                manualAxisScaling = !manualAxisScaling;
                if (manualAxisScaling)
                    scalingButton.setText("Auto Scaling");
                else
                    scalingButton.setText("Manual Scaling");
            }
        });
        mChart.setInteractive(true);
        mChart.setZoomType(ZoomType.HORIZONTAL_AND_VERTICAL);
        mChart.setContainerScrollEnabled(true, ContainerScrollType.HORIZONTAL);

        LineChartData lineChartData = new LineChartData(new ArrayList<Line>());
        xAxis = new Axis().setName("Axis X").setHasLines(true);
        lineChartData.setAxisXBottom(xAxis);
        yAxisLeft = new Axis().setName("Left Y").setHasLines(true);
        yAxisRight = new Axis().setName("Right Y").setHasLines(true).setFormatter(new ValueFormatter());
        lineChartData.setAxisYLeft(yAxisLeft);
        lineChartData.setAxisYRight(yAxisRight);
        mChart.setLineChartData(lineChartData);

        Intent intent = getIntent();
        mMeter = getDeviceWithAddress(intent.getStringExtra("addr"));

        addStream("CH1");
        addStream("CH2");

        ArrayList<Line> lines = new ArrayList<>(lineChartData.getLines());

        leftAxisValues  = new VisibleVsBackupHelper();
        rightAxisValues = new VisibleVsBackupHelper();
        helpers = new VisibleVsBackupHelper[]{leftAxisValues,rightAxisValues};
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
                lines.get(0).setValues(leftAxisValues.getVisible());
                // For the right axis, we must transform
                List<PointValue> rline = lines.get(1).getValues();
                rline.clear();
                for(PointValue p:rightAxisValues.getVisible()) {
                    rline.add(new PointValue(p.getX(),rightToLeftHelper.apply(p.getY())));
                }
                //Force chart to draw current data again
                mChart.setLineChartData(mChart.getLineChartData());
            }
        });
    }

    @Override
    public void setNPointOnScreen(int maxPoints) {
        maxNumberOfPointsOnScreen = maxPoints;
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

    void calculateScale() {
        rightToLeftHelper.calcFromViewports(rightAxisValues.getViewport(), leftAxisValues.getViewport());
    }

    @Override
    public void addPoints(final int series_n, final List<PointValue> new_values) {
        try {
            //Set new data on the graph
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    helpers[series_n].addPoints(new_values);
                    calculateScale();
                    setViewport();
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

    private void setViewport() {
        Viewport lv = leftAxisValues.getViewport();
        Viewport rv = rightAxisValues.getViewport();
        // rv is in its own units
        // Because of HelloChart's annoying system, we need to transform to put it in terms of the left scale
        rv.top = rightToLeftHelper.apply(rv.top);
        rv.bottom = rightToLeftHelper.apply(rv.bottom);
        // Determine max of the two viewports
        Viewport maxView = new Viewport();
        maxView.top    = Math.max(lv.top, rv.top);
        maxView.bottom = Math.min(lv.bottom, rv.bottom);
        maxView.right  = Math.max(lv.right, rv.right);
        maxView.left   = Math.min(lv.left, rv.left);
        mChart.setMaximumViewport(maxView);

        boolean touched = false;
        Viewport currentView = mChart.getCurrentViewport();

        if(!manualAxisScaling) {
            currentView.top = maxView.top;
            currentView.bottom = maxView.bottom;
            touched = true;
        }
        if(lockedRight) {
            currentView.left = maxView.left;
            currentView.right = maxView.right;
            touched = true;
        }
        if(touched) {
            mChart.setCurrentViewport(currentView);
        }

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