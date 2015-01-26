package com.mooshim.mooshimeter.main;

import android.app.Activity;
import android.graphics.Color;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.OrientationEventListener;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

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

    ////////////////
    // GUI elements
    ////////////////
    private LinearLayout mSettingsView;
    private GraphView mGraph;
    private Button mTrendButton;
    private Button mCH1Button;
    private Button mCH2Button;
    private Button mXYButton;

    private OrientationEventListener orientation_listener;
    private MooshimeterDevice mMeter;
    private final LineGraphSeries[] dataSeries = new LineGraphSeries[2];
    private double start_time;
    private boolean cleaning_up = false;

    private boolean mBufferMode = false;
    private final boolean mCHXOn[] = {true,true};
    private boolean mXYMode = false;
    private boolean mPlaying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trend);
        mGraph        = (GraphView)    findViewById(R.id.graph);
        mSettingsView = (LinearLayout) findViewById(R.id.settings_view);
        mTrendButton  = (Button)       findViewById(R.id.trend_button);
        mCH1Button    = (Button)       findViewById(R.id.ch1_button);
        mCH2Button    = (Button)       findViewById(R.id.ch2_button);
        mXYButton     = (Button)       findViewById(R.id.xy_button);

        orientation_listener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_UI) {
            @Override
            public void onOrientationChanged(int i) {
                if((i > 170 && i < 190) || (i>0 && i < 10)) {
                    // FIXME: I know there should be a better way to do this.
                    if(!cleaning_up) {
                        cleaning_up = true;
                        orientation_listener.disable();
                        Log.i(null, "PORTRAIT!");
                        mMeter.meter_settings.target_meter_state = MooshimeterDevice.METER_PAUSED;
                        mMeter.sendMeterSettings(new Block() {
                            @Override
                            public void run() {
                                mMeter.close();
                                orientation_listener.disable();
                                setResult(RESULT_OK);
                                finish();
                            }
                        });
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
                    trendViewPlay();
                }
            });
        } else {
            trendViewPlay();
        }

        mGraph.getViewport().setXAxisBoundsManual(true);
        mGraph.getViewport().setYAxisBoundsManual(true);
        mGraph.setExplicitRefreshMode(true);
        final SecondScale ss = mGraph.getSecondScale();

        mGraph.setKeepScreenOn(true);
        mGraph.setBackgroundColor(Color.BLACK);
        GridLabelRenderer r = mGraph.getGridLabelRenderer();
        r.setGridColor(Color.GRAY);
        r.setHorizontalAxisTitle("Time [s]");
        r.setHorizontalAxisTitleColor(Color.WHITE);
        r.setHorizontalLabelsColor(Color.WHITE);
        r.setGridStyle(GridLabelRenderer.GridStyle.BOTH);
        r.setVerticalAxisTitle(String.format("%s [%s]", mMeter.getDescriptor(0), mMeter.getUnits(0)));
        r.setVerticalAxisTitleColor(Color.RED);
        r.setVerticalLabelsColor(Color.RED);

        ss.setVerticalAxisTitle(String.format("%s [%s]", mMeter.getDescriptor(1), mMeter.getUnits(1)));
        ss.setVerticalAxisTitleColor(Color.GREEN);
        r.setVerticalLabelsSecondScaleColor(Color.GREEN);

        r.setNumVerticalLabels(7);

        orientation_listener.enable();
    }

    private void resetViewBounds() {
        final Viewport vp = mGraph.getViewport();

        // Manually reset axis boundaries
        final double padding_factor = 0.20;
        final double min_x = dataSeries[0].getLowestValueX();
        final double max_x = dataSeries[0].getHighestValueX();

        // MaxX must be padded to leave room for the second Y axis
        vp.setMinX(Math.floor(min_x));
        vp.setMaxX(Math.ceil(max_x));

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

    private class MinMax {
        public double min;
        public double max;
    }

    private MinMax getMinMaxFromRange(double in_min, double in_max) {
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

    private void trendViewPlay() {
        mMeter.meter_settings.target_meter_state = MooshimeterDevice.METER_RUNNING;
        mMeter.meter_settings.calc_settings |= MooshimeterDevice.METER_CALC_SETTINGS_MEAN | MooshimeterDevice.METER_CALC_SETTINGS_MS;
        mMeter.meter_settings.calc_settings &=~MooshimeterDevice.METER_CALC_SETTINGS_ONESHOT;

        final SecondScale ss = mGraph.getSecondScale();
        dataSeries[0] = new LineGraphSeries();
        dataSeries[1] = new LineGraphSeries();
        dataSeries[0].setColor(Color.RED);
        dataSeries[0].setThickness(5);
        dataSeries[1].setColor(Color.GREEN);
        dataSeries[1].setThickness(5);
        mGraph.removeAllSeries();
        ss.removeAllSeries();
        mGraph.addSeries(dataSeries[0]);
        ss.addSeries(dataSeries[1]);

        start_time = milliTime();

        mMeter.enableMeterStreamSample(true, new Block() {
            @Override
            public void run() {
                Log.i(null,"Stream requested");
                mMeter.sendMeterSettings( new Block() {
                    @Override
                    public void run() {
                        mPlaying = true;
                    }
                });
            }
        }, new Block() {
            @Override
            public void run() {
                if(!mBufferMode) {
                    Log.i(null, "Sample received!");
                    double new_time = milliTime() - start_time;
                    double val;
                    int lsb_int;
                    for (int c = 0; c < 2; c++) {
                        if (mMeter.disp_ac[c]) {
                            lsb_int = (int) (Math.sqrt(mMeter.meter_sample.reading_ms[c]));
                        } else {
                            lsb_int = mMeter.meter_sample.reading_lsb[c];
                        }
                        val = mMeter.lsbToNativeUnits(lsb_int, c);
                        dataSeries[c].appendData(new DataPoint(new_time, val), false, 500);
                    }
                    resetViewBounds();
                    mGraph.forceRefresh(true, false);
                }
            }
        });
    }

    private void trendViewPause(final Block cb) {
        mMeter.meter_settings.target_meter_state = MooshimeterDevice.METER_PAUSED;
        mMeter.sendMeterSettings(new Block() {
            @Override
            public void run() {
                mMeter.enableMeterStreamSample(false,new Block() {
                    @Override
                    public void run() {
                        mPlaying = false;
                        cb.run();
                    }
                },null);
            }
        });
    }

    private void streamBuffer() {
        mMeter.getBuffer(new Block() {
            @Override
            public void run() {
                Log.d(null,"Received full buffer in trendview!");
                final SecondScale ss = mGraph.getSecondScale();
                dataSeries[0] = new LineGraphSeries();
                dataSeries[1] = new LineGraphSeries();
                dataSeries[0].setColor(Color.RED);
                dataSeries[0].setThickness(5);
                dataSeries[1].setColor(Color.GREEN);
                dataSeries[1].setThickness(5);
                mGraph.removeAllSeries();
                ss.removeAllSeries();
                mGraph.addSeries(dataSeries[0]);
                ss.addSeries(dataSeries[1]);

                final int buf_len = mMeter.getBufLen();
                double dt = 1./125;
                for(int i = 0; i < (mMeter.meter_settings.adc_settings & MooshimeterDevice.ADC_SETTINGS_SAMPLERATE_MASK); i++) {
                    dt /= 2;
                }
                double t = 0.0;
                for(int i= 0; i < buf_len; i++) {
                    dataSeries[0].appendData(new DataPoint(t,mMeter.buffers[0][i]),false,buf_len);
                    dataSeries[1].appendData(new DataPoint(t,mMeter.buffers[1][i]),false,buf_len);
                    t+=dt;
                }

                resetViewBounds();

                mGraph.forceRefresh(true,true);
            }
        });
    }

    @Override
    protected void onPause() {
        // Log.d(TAG, "onPause");
        super.onPause();
        getActionBar().show();
    }

    ///////////////
    // Button handlers
    ///////////////

    public void onSettingsButtonClick(View v) {
        Log.d(null, "Settings Click");
        int n;
        switch(mSettingsView.getVisibility()) {
            case View.INVISIBLE:
                n = View.VISIBLE;
                break;
            case View.VISIBLE:
                n = View.INVISIBLE;
                break;
            default:
                Log.e(null,"Invalid settings visibility");
                n = View.INVISIBLE;
                break;
        }
        mSettingsView.setVisibility(n);
    }
    public void onTrendButtonClick(View v) {
        Log.d(null, "TrendClick");
        mBufferMode ^= true;
        if(mBufferMode) {
            if(mPlaying) {
                trendViewPause(new Block() {
                    @Override
                    public void run() {
                        streamBuffer();
                        mTrendButton.setText("Buffer Mode");
                    }
                });
            } else {
                streamBuffer();
            }
        } else {
            mMeter.enableMeterStreamBuf(false, new Block() {
                @Override
                public void run() {
                    trendViewPlay();
                    mTrendButton.setText("Trend Mode");
                }
            });
        }
        mTrendButton.setText("Transition...");
    }
    public void onCH1ButtonClick(View v) {
        Log.d(null, "CH1 Click");
        mCHXOn[0] ^= true;
        if(mCHXOn[0]) {
            mCH1Button.setText("CH1: ON");
        } else {
            mCH1Button.setText("CH1: OFF");
        }
    }
    public void onCH2ButtonClick(View v) {
        Log.d(null, "CH2 Click");
        mCHXOn[1] ^= true;
        if(mCHXOn[1]) {
            mCH2Button.setText("CH2: ON");
        } else {
            mCH2Button.setText("CH2: OFF");
        }
    }
    public void onXYButtonClick(View v) {
        Log.d(null, "XY Click");
        mXYMode ^= true;
        if(mXYMode) {
            mXYButton.setText("XY Mode: ON");
        } else {
            mXYButton.setText("XY Mode: OFF");
        }
    }

}
