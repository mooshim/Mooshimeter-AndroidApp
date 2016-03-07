package com.mooshim.mooshimeter.main;

import android.bluetooth.BluetoothGatt;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
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
import java.util.Random;

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

    LineChartView mChart;
    int totalPoints, maxNumberOfPoints = 256;
    List<TextView> yAxisTitles = new ArrayList<>();
    Axis xAxis;
    boolean manualAxisScaling;
    float minX, maxX, maxY, minY;

    private MooshimeterDeviceBase mMeter = null;
    private int disconnect_handle = -1;
    private double time_start;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graphing);
        mChart = (LineChartView) findViewById(R.id.lineChart);
        (findViewById(R.id.refresh)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });

        (findViewById(R.id.add)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Start adding values from the background thread
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        int index = addStream("Line");
                        for (int i = 0; i < 100; i++) {
                            //Add random values
                            addPoint(index, i, (float) (100 * Math.random()));
                            try {
                                //Set update value to 1 second
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }).start();
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
        xAxis = new Axis().setName("Axis X");
        lineChartData.setAxisXBottom(xAxis);
        lineChartData.setAxisYLeft(new Axis().setName("Axis Y"));
        mChart.setLineChartData(lineChartData);

        Intent intent = getIntent();
        mMeter = getDeviceWithAddress(intent.getStringExtra("addr"));
        disconnect_handle = mMeter.addConnectionStateCB(BluetoothGatt.STATE_DISCONNECTED, new Runnable() {
            @Override
            public void run() {
                mMeter.cancelConnectionStateCB(disconnect_handle);
                transitionToActivity(mMeter, ScanActivity.class);
            }
        });

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
        time_start = Util.getUTCTime();

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
        maxNumberOfPoints = maxPoints;
    }

    @Override
    public int addStream(final String title) {
        //Create new line with some default values
        Line line = new Line();
        line.setHasLines(true);
        line.setHasPoints(false);
        Random random = new Random();
        final int argb = Color.rgb(random.nextInt(256),
                random.nextInt(256), random.nextInt(256));
        line.setColor(argb);
        List<Line> lines = new ArrayList<>(mChart.getLineChartData().getLines());
        lines.add(line);
        mChart.getLineChartData().setLines(lines);
        return lines.size() - 1;
    }

    @Override
    public void addPoint(final int series_n, final float x, final float y) {
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
                    values.add(new PointValue(x,y));
                    mChart.setLineChartData(lineChartData);
                    totalPoints++;
                    setViewport(x, y);
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
    public void setNAxes(int n_axes) {
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

    private void setViewport(float x, float y) {
        if (x < minX) {
            minX = x;
        }

        if (x > maxX) {
            maxX = x;
        }

        if (y > maxY) {
            maxY = y;
        }

        if (y < minY) {
            minY = y;
        }

        Viewport maximumViewport;
        Viewport currentViewport;

        currentViewport =  new Viewport(mChart.getCurrentViewport());
        LineChartData lineChartData = mChart.getLineChartData();
        List<Line> lines = lineChartData.getLines();
        final Line line = lines.get(0);
        final List<PointValue> values = line.getValues();
        if(values.size()>=maxNumberOfPoints) {
            currentViewport.left = values.get(values.size() - maxNumberOfPoints).getX();
        } else {
            currentViewport.left = values.get(0).getX();
        }
        maximumViewport =  mChart.getMaximumViewport();

        if (manualAxisScaling) {
            mChart.setViewportCalculationEnabled(false);

            //currentViewport = new Viewport(mChart.getCurrentViewport());
            //currentViewport.right = maxX;
            //currentViewport.left = minX;
            // Assume
            //maximumViewport = new Viewport(mChart.getMaximumViewport());
            //maximumViewport.right = maxX;
            //maximumViewport.left = 0;
            //maximumViewport.top = maxY;
            //maximumViewport.bottom = minY;
        } else {
            //maximumViewport =  mChart.getMaximumViewport();
            currentViewport.top = maximumViewport.top;
            currentViewport.bottom = maximumViewport.bottom;
        }

        mChart.setCurrentViewport(currentViewport);
        mChart.setMaximumViewport(maximumViewport);
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
    public void onSampleReceived(int channel, float val) {
        float dt = (float) (Util.getUTCTime()-time_start);
        addPoint(channel,dt,val);
    }

    @Override
    public void onBufferReceived(int channel, float dt, float[] val) {

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
    public void onRealPowerCalculated(float val) {

    }
}