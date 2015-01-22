package com.mooshim.mooshimeter.main;

import android.app.Activity;
import android.graphics.Color;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.OrientationEventListener;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.SecondScale;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.common.Block;
import com.mooshim.mooshimeter.common.MooshimeterDevice;

import java.util.Calendar;

public class TrendActivity extends Activity {

    private OrientationEventListener orientation_listener;
    private MooshimeterDevice mMeter;
    private GraphView mGraph;
    private final LineGraphSeries[] dataSeries = new LineGraphSeries[2];
    private double start_time;
    private boolean cleaning_up = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trend);
        mGraph = (GraphView) findViewById(R.id.graph);
        orientation_listener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_UI) {
            @Override
            public void onOrientationChanged(int i) {
                if((i > 170 && i < 190) || (i>0 && i < 10)) {
                    // FIXME: I know there should be a better way to do this.
                    if(!cleaning_up) {
                        cleaning_up = true;
                        Log.i(null, "PORTRAIT!");
                        setResult(RESULT_OK);
                        finish();
                        orientation_listener.disable();
                    }
                }
            }
        };
        orientation_listener.enable();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_trend, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private double milliTime() {
        Calendar cal = Calendar.getInstance();
        return (double)(cal.getTimeInMillis())/1000.;
    }

    @Override
    protected void onResume() {
        // Log.d(TAG, "onResume");
        super.onResume();

        getActionBar().hide();

        mMeter = MooshimeterDevice.getInstance();
        if(mMeter == null) {
            mMeter = MooshimeterDevice.Initialize(this, new Block() {
                @Override
                public void run() {
                    onMeterInitialized();
                }
            });
        } else {
            onMeterInitialized();
        }

        mGraph.removeAllSeries();
        mGraph.getViewport().setXAxisBoundsManual(true);
        mGraph.getViewport().setYAxisBoundsManual(true);
        final SecondScale ss = mGraph.getSecondScale();

        mGraph.setKeepScreenOn(true);
        mGraph.setBackgroundColor(Color.BLACK);
        GridLabelRenderer r = mGraph.getGridLabelRenderer();
        r.setGridColor(Color.GRAY);
        r.setHorizontalAxisTitle("Time");
        r.setHorizontalAxisTitleColor(Color.WHITE);
        r.setHorizontalLabelsColor(Color.WHITE);
        r.setGridStyle(GridLabelRenderer.GridStyle.BOTH);
        r.setVerticalAxisTitle(mMeter.getDescriptor(0));
        r.setVerticalAxisTitleColor(Color.RED);
        r.setVerticalLabelsColor(Color.RED);
        r.setNumVerticalLabels(7);
        // TODO: Second scale needs a title but I don't see a way to set it through GraphView API
        r.setVerticalLabelsSecondScaleColor(Color.GREEN);

        dataSeries[0] = new LineGraphSeries();
        dataSeries[1] = new LineGraphSeries();
        dataSeries[0].setColor(Color.RED);
        dataSeries[0].setThickness(5);
        dataSeries[1].setColor(Color.GREEN);
        dataSeries[1].setThickness(5);
        mGraph.addSeries(dataSeries[0]);
        ss.addSeries(dataSeries[1]);

        start_time = milliTime();

        orientation_listener.enable();
    }

    private class MinMax {
        public double min;
        public double max;
    }

    MinMax getMinMaxFromRange(double in_min, double in_max) {
        final double range = in_max - in_min;
        final double avg   = in_min+(range/2);
        double tick = 1e-6;
        boolean b = false;
        while( tick < range/7.f ) {
            if( b ){ tick *= 2.f; }
            else   { tick *= 5.f; }
            b ^= true;
        }
        final double center = tick * (int)((avg+tick/2)/tick);
        MinMax r = new MinMax();
        r.min = center-3*tick;
        r.max = center+3*tick;
        return r;
    }

    private void onMeterInitialized() {
        mMeter.meter_settings.target_meter_state = MooshimeterDevice.METER_RUNNING;
        mMeter.meter_settings.calc_settings |= 0x50;
        mMeter.sendMeterSettings(new Block() {
            @Override
            public void run() {
                Log.i(null,"Mode set");
                mMeter.enableMeterStreamSample(true, new Block() {
                    @Override
                    public void run() {
                        Log.i(null,"Stream requested");
                    }
                }, new Block() {
                    @Override
                    public void run() {
                        Log.i(null,"Sample received!");
                        double new_time = milliTime()-start_time;
                        double val;
                        int lsb_int;
                        // TODO: Need to refactor so that mMeter is being passed, not recreated
                        // By recreating we are losing settings
                        //if(ac) { lsb_int = (int)(Math.sqrt(mMeter.meter_sample.reading_ms[c])); }
                        //else   { lsb_int = mMeter.meter_sample.reading_lsb[c]; }
                        lsb_int = mMeter.meter_sample.reading_lsb[0];
                        val = mMeter.lsbToNativeUnits(lsb_int, 0);
                        dataSeries[0].appendData(new DataPoint(new_time,val),false,500);
                        lsb_int = mMeter.meter_sample.reading_lsb[1];
                        val = mMeter.lsbToNativeUnits(lsb_int, 1);
                        dataSeries[1].appendData(new DataPoint(new_time,val),false,500);

                        final Viewport vp = mGraph.getViewport();

                        // Manually reset axis boundaries
                        final double padding_factor = 0.15;
                        final double min_x = dataSeries[0].getLowestValueX();
                        final double max_x = dataSeries[0].getHighestValueX();
                        final double x_span = max_x - min_x;

                        // MaxX must be padded to leave room for the second Y axis
                        vp.setMinX(Math.floor(min_x));
                        vp.setMaxX(Math.ceil(max_x-padding_factor*x_span));

                        final double min_y1 = dataSeries[0].getLowestValueY();
                        final double max_y1 = dataSeries[0].getHighestValueY();
                        final MinMax m1 = getMinMaxFromRange(min_y1, max_y1);
                        vp.setMinY(m1.min);
                        vp.setMaxY(m1.max);


                        final double min_y2 = dataSeries[1].getLowestValueY();
                        final double max_y2 = dataSeries[1].getHighestValueY();
                        final MinMax m2 = getMinMaxFromRange(min_y2, max_y2);

                        mGraph.getSecondScale().setMinY(m2.min);
                        mGraph.getSecondScale().setMaxY(m2.max);
                    }
                });
            }
        });
    }

    @Override
    protected void onPause() {
        // Log.d(TAG, "onPause");
        super.onPause();
        getActionBar().show();
        mMeter.meter_settings.target_meter_state = MooshimeterDevice.METER_PAUSED;
        mMeter.sendMeterSettings(new Block() {
            @Override
            public void run() {
                Log.d(null, "Paused");
            }
        });
        mMeter.close();
        orientation_listener.disable();
    }
}
