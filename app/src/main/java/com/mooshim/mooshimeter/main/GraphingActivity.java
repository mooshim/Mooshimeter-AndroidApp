package com.mooshim.mooshimeter.main;

import android.bluetooth.BluetoothGatt;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.common.GraphingActivityInterface;
import com.mooshim.mooshimeter.common.MooshimeterDelegate;
import com.mooshim.mooshimeter.common.MooshimeterDevice;
import com.mooshim.mooshimeter.common.MooshimeterDeviceBase;
import com.mooshim.mooshimeter.common.Util;

import java.util.ArrayList;
import java.util.List;

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
    int maxNumberOfPoints = 1024;
    int maxNumberOfPointsOnScreen = 256;
    List<TextView> yAxisTitles = new ArrayList<>();
    Axis xAxis;
    boolean manualAxisScaling = false;
    boolean lockedRight = true;

    private MooshimeterDeviceBase mMeter = null;
    private double time_start;

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

        (findViewById(R.id.refresh)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });

        (findViewById(R.id.clear)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Start clearing values from the background thread
                int size = mChart.getLineChartData().getLines().size();
                for (int i = 0; i < size; ++i) {
                    clearPoints(i);
                }
            }
        });

        (findViewById(R.id.lock_right)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lockedRight = !lockedRight;
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
        lineChartData.setAxisYLeft(new Axis().setName("Axis Y").setHasLines(true));
        mChart.setLineChartData(lineChartData);

        Intent intent = getIntent();
        mMeter = getDeviceWithAddress(intent.getStringExtra("addr"));

        addStream("CH1");
        addStream("CH2");
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
        mMeter.setBufferMode(0,false);
        mMeter.setBufferMode(1,false);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        transitionToActivity(mMeter,DeviceActivity.class);
    }

    /////////////////////////
    // GraphingActivityInterface methods
    /////////////////////////

    @Override
    public void refresh() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //Force chart to draw current data again
                mChart.setLineChartData(mChart.getLineChartData());
            }
        });
    }

    @Override
    public void setNPointOnScreen(int maxPoints) {
        maxNumberOfPointsOnScreen = maxPoints;
    }

    @Override
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
        addPoints(series_n,l);
    }

    @Override
    public void addPoints(final int series_n, final List<PointValue> new_values) {
        final LineChartData lineChartData = mChart.getLineChartData();
        try {
            //Set new data on the graph
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ArrayList<Line> lines = new ArrayList<>(lineChartData.getLines());
                    final Line line = lines.get(series_n);
                    //Get list of previous points on the line
                    final List<PointValue> values = line.getValues();
                    values.addAll(new_values);
                    while(values.size()>maxNumberOfPoints) {
                        values.remove(0);
                        //values.removeAll(values.subList(0, values.size() - maxNumberOfPoints));
                    }
                    mChart.setLineChartData(lineChartData);
                    setViewport();
                }
            });
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
            Log.e(TAG, "No series found at index " + series_n);
        }
    }

    @Override
    public void clearPoints(final int series_n) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                LineChartData lineChartData = mChart.getLineChartData();
                try {
                    //Get line for which points are to be cleared
                    Line line = lineChartData.getLines().get(series_n);
                    //Set empty list of points replacing old values list
                    line.setValues(new ArrayList<PointValue>(0));
                    //Set new data on the graph
                    mChart.setLineChartData(lineChartData);
                } catch (IndexOutOfBoundsException e) {
                    e.printStackTrace();
                    Log.e(TAG, "No series found at index " + series_n);
                }
            }
        });
    }

    @Override
    public void setXAxisTitle(String title) {
        xAxis.setName(title);
    }

    @Override
    public void setYAxisTitle(int series_n, String title) {
        try {
            yAxisTitles.get(series_n).setText(title);
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
            Log.e(TAG, "No series found at index " + series_n);
        }
    }

    private void setViewport() {
        Viewport maximumViewport = new Viewport();
        Viewport currentViewport = new Viewport(mChart.getCurrentViewport());

        LineChartData lineChartData = mChart.getLineChartData();

        List<Line> lines = lineChartData.getLines();
        final Line line = lines.get(0);
        final List<PointValue> values = line.getValues();
        final List<PointValue> pointsInView;

        // Compute maximum viewport

        float min = values.get(0).getY();
        float max = values.get(0).getY();
        for (PointValue p : values) {
            float ty = (float) p.getY();
            if (ty < min) {
                min = ty;
            }
            if (ty > max) {
                max = ty;
            }
        }
        maximumViewport.left = values.get(0).getX();
        maximumViewport.right = values.get(values.size()-1).getX();
        maximumViewport.top = max;
        maximumViewport.bottom = min;

        mChart.setMaximumViewport(maximumViewport);

        // Grab only the most recent N points and base the current viewport off of them

        if (values.size() >= maxNumberOfPointsOnScreen) {
            pointsInView = values.subList(values.size() - maxNumberOfPointsOnScreen, values.size());
        } else {
            pointsInView = values;
        }

        if (!manualAxisScaling && lockedRight) {
            // Figure out the max and min of the points in view
            min = pointsInView.get(0).getY();
            max = pointsInView.get(0).getY();

            for(PointValue p:pointsInView) {
                float ty = (float)p.getY();
                if(ty<min) {
                    min = ty;
                }
                if(ty>max) {
                    max = ty;
                }
            }
            currentViewport.top = max;
            currentViewport.bottom = min;
        }

        if(lockedRight) {
            currentViewport.left  = pointsInView.get(0).getX();
            currentViewport.right = pointsInView.get(pointsInView.size()-1).getX();
        }

        mChart.setCurrentViewport(currentViewport);
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
        float dt = (float) (timestamp_utc-time_start);
        addPoint(channel,dt,val);
    }

    @Override
    public void onBufferReceived(double timestamp_utc, int channel, float dt, float[] val) {
        double t = (timestamp_utc - dt*val.length)-time_start;
        List<PointValue> l = new ArrayList<>(val.length);
        for(float v:val) {
            l.add(new PointValue((float)t,v));
            t+=dt;
        }
        addPoints(channel,l);
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
}