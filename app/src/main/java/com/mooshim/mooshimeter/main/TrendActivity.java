package com.mooshim.mooshimeter.main;

import android.app.Activity;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.OrientationEventListener;

import com.jjoe64.graphview.GraphView;
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
    private final LineGraphSeries[] dataPoints = new LineGraphSeries[2];
    private double start_time;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trend);
        mGraph = (GraphView) findViewById(R.id.graph);
        LineGraphSeries<DataPoint> series = new LineGraphSeries<DataPoint>(new DataPoint[] {
                new DataPoint(0, 1),
                new DataPoint(1, 5),
                new DataPoint(2, 3),
                new DataPoint(3, 2),
                new DataPoint(4, 6)
        });
        mGraph.addSeries(series);
        orientation_listener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_UI) {
            @Override
            public void onOrientationChanged(int i) {
                if((i > 170 && i < 190) || (i>0 && i < 10)) {
                    // FIXME: I know there should be a better way to do this.
                    Log.i(null, "PORTRAIT!");
                    setResult(RESULT_OK);
                    finish();
                    orientation_listener.disable();
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

        mGraph.removeAllSeries();
        dataPoints[0] = new LineGraphSeries();
        dataPoints[1] = new LineGraphSeries();
        mGraph.addSeries(dataPoints[0]);
        mGraph.addSeries(dataPoints[1]);

        start_time = milliTime();

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
        orientation_listener.enable();
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
                        dataPoints[0].appendData(new DataPoint(new_time,val),true,100);
                        lsb_int = mMeter.meter_sample.reading_lsb[1];
                        val = mMeter.lsbToNativeUnits(lsb_int, 1);
                        dataPoints[1].appendData(new DataPoint(new_time,val),true,100);
                    }
                });
            }
        });
    }

    @Override
    protected void onPause() {
        // Log.d(TAG, "onPause");
        super.onPause();
        mMeter.meter_settings.target_meter_state = MooshimeterDevice.METER_PAUSED;
        mMeter.sendMeterSettings(new Block() {
            @Override
            public void run() {
                Log.d(null,"Paused");
            }
        });
        mMeter.close();
        orientation_listener.disable();
    }
}
