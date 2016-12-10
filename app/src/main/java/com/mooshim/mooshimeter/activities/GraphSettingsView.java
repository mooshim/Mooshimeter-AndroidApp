package com.mooshim.mooshimeter.activities;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.common.Util;
import com.mooshim.mooshimeter.interfaces.NotifyHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by First on 5/24/2016.
 */
public class GraphSettingsView extends LinearLayout {

    public GraphingActivity mDelegate;
    private Context mContext;
    private LayoutInflater mLayoutInflater;
    private GraphSettingsView mInstance;

    private class Builder {
        public void add(String title,View widget) {
            View v = mLayoutInflater.inflate(R.layout.element_graph_pref,mInstance,false);
            TextView titleview = (TextView)v.findViewById(R.id.pref_title);
            titleview.setText(title);
            if(widget!=null) {
                widget.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                FrameLayout frame = (FrameLayout)v.findViewById(R.id.frame);
                frame.addView(widget);
            }
            v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            mInstance.addView(v);
        }
    }
    abstract class BooleanRunnable implements Runnable {
        boolean arg;
    }
    Switch makeSwitch(boolean checked, final BooleanRunnable cb) {
        Switch s = new Switch(mContext);
        s.setChecked(checked);
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                cb.arg = isChecked;
                cb.run();
            }
        });
        return s;
    }

    public GraphSettingsView(Context context, AttributeSet attributeSet) {
        // Should only be called from the editor
        this(context,(GraphingActivity)null);
    }

    public GraphSettingsView(Context context,GraphingActivity delegate) {
        super(context);

        if(delegate == null) {
            // Should only happen from the editor, where we need to preview stuff
            delegate = new GraphingActivity();
        }

        mContext = context;
        mDelegate = delegate;
        mInstance = this;
        mLayoutInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        setOrientation(LinearLayout.VERTICAL);

        setBackgroundColor(0xDDFFFFFF);

        Builder builder = new Builder();

        String[] option_array ={ GraphingActivity.ChDispModes.OFF.toString(),
                                 GraphingActivity.ChDispModes.MANUAL.toString(),
                                 GraphingActivity.ChDispModes.AUTO.toString()};
        final List<String> options = Arrays.asList(option_array);
        final Button c1b = new Button(mContext);
        c1b.setText(mDelegate.dispModes[0].toString());
        c1b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Util.generatePopupMenuWithOptions(mContext, options, mDelegate.mConfigButton, new NotifyHandler() {
                    @Override
                    public void onReceived(double timestamp_utc, Object payload) {
                        mDelegate.setDispModes(0, GraphingActivity.ChDispModes.values()[(Integer)payload]);
                        Util.postToMain(new Runnable() {
                            @Override
                            public void run() {
                                c1b.setText(mDelegate.dispModes[0].toString());
                            }
                        });
                    }
                },null);
            }
        });
        builder.add("CH1 Mode", c1b);
        final Button c2b = new Button(mContext);
        c2b.setText(mDelegate.dispModes[1].toString());
        c2b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Util.generatePopupMenuWithOptions(mContext, options, mDelegate.mConfigButton, new NotifyHandler() {
                    @Override
                    public void onReceived(double timestamp_utc, Object payload) {
                        mDelegate.setDispModes(1, GraphingActivity.ChDispModes.values()[(Integer)payload]);
                        Util.postToMain(new Runnable() {
                            @Override
                            public void run() {
                                c2b.setText(mDelegate.dispModes[1].toString());
                            }
                        });
                    }
                },null);
            }
        });
        builder.add("CH2 Mode", c2b);
        builder.add("Autoscroll", makeSwitch(
                mDelegate.autoScrollOn,
                new BooleanRunnable() {
                    @Override
                    public void run() {
                        mDelegate.setAutoScrollOn(arg);
                    }
        }));
        builder.add("XY Mode", makeSwitch(
                mDelegate.xyModeOn,
                new BooleanRunnable() {
                    @Override
                    public void run() {
                        mDelegate.setXyModeOn(arg);
                    }
                }));
        builder.add("Buffer Mode", makeSwitch(
                mDelegate.bufferModeOn,
                new BooleanRunnable() {
                    @Override
                    public void run() {
                        mDelegate.setBufferModeOn(arg);
                    }
                }));

        final int[] sampleOptions = {50,100,200,400,800};
        final List<String> option_strings = new ArrayList<>();
        for(int i:sampleOptions) {
            option_strings.add(Integer.toString(i));
        }
        final Button b = new Button(mContext);
        b.setText(String.format("%d",mDelegate.maxNumberOfPointsOnScreen));
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Util.generatePopupMenuWithOptions(mContext, option_strings, mDelegate.mConfigButton, new NotifyHandler() {
                    @Override
                    public void onReceived(double timestamp_utc, Object payload) {
                        mDelegate.maxNumberOfPointsOnScreen = sampleOptions[(Integer) payload];
                        b.setText(String.format("%d",mDelegate.maxNumberOfPointsOnScreen));
                    }
                }, new Runnable() {
                    @Override
                    public void run() {

                    }
                });
            }
        });
        builder.add("Points Onscreen", b);
    }
/*
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        //base.layout(changed, l, t, r, b);
        //super.onLayout(changed, l, t, r, b);
        mLinearLayout.layout(l,t,r,b);
        Log.e("", "Yes I should do something here");
    }*/
}
