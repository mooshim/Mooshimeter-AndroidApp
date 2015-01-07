package com.example.ti.ble.sensortag;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * Created by First on 1/5/2015.
 */
public class ChannelView extends ViewGroup {

    private
    // GUI Elements
    TextView value_label;
    Button units_button;
    Button display_set_button;
    Button input_set_button;
    Button auto_manual_button;
    Button range_button;

    // Button listeners
    private Button.OnClickListener displaySetButtonPress = new Button.OnClickListener() {
        public void onClick(View v) {
            // Perform action on click
            Log.i(null,"Click!");
        }
    };

    public ChannelView(Context context) {
        super(context);
    }

    // Create a new button with our consistent style
    private void makeButton(int x, int y, int w, int h, Button.OnClickListener listener) {
        Button b = new Button(this.getContext());
        b.layout(x, y, x + w, y + h);
        b.setOnClickListener(listener);
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(w, h);
        b.setText("Test");
        this.addView(b, lp);
        /*
        UIButton* b;
        b = [UIButton buttonWithType:UIButtonTypeSystem];
        b.userInteractionEnabled = YES;
        [b addTarget:self action:cb forControlEvents:UIControlEventTouchUpInside];
        [b.titleLabel setFont:[UIFont systemFontOfSize:24]];
        [b setTitle:@"T" forState:UIControlStateNormal];
        [b setTitleColor:[UIColor blackColor] forState:UIControlStateNormal];
        [[b layer] setBorderWidth:2];
        [[b layer] setBorderColor:[UIColor darkGrayColor].CGColor];
        b.frame = frame;
        [self addSubview:b];
        return b;
    }*/
    }

    @Override
    protected void onLayout(boolean b, int i, int i2, int i3, int i4) {
        int w = (i3-i )/4;
        int h = (i4-i2)/6;

        makeButton(0,0,w,h,displaySetButtonPress);
        makeButton(w,0,w,h,displaySetButtonPress);
        makeButton(w,h,w,h,displaySetButtonPress);
    }

/*
    -(instancetype)initWithFrame:(CGRect)frame ch:(NSInteger)ch{
        UILabel* l;
        self = [super initWithFrame:frame];
        self.userInteractionEnabled = YES;
        self->channel = ch;

        float h = frame.size.height/4;
        float w = frame.size.width/6;

        #define cg(nx,ny,nw,nh) CGRectMake(nx*w,ny*h,nw*w,nh*h)
        #define mb(nx,ny,nw,nh,s) [self makeButton:cg(nx,ny,nw,nh) cb:@selector(s)]

        self.display_set_button = mb(0,0,4,1,display_set_button_press);
        self.input_set_button   = mb(4,0,2,1,input_set_button_press);
        self.auto_manual_button = mb(0,3,1,1,auto_manual_button_press);
        self.range_button       = mb(1,3,2,1,range_button_press);
        self.units_button       = mb(3,3,3,1,units_button_press);

        l = [[UILabel alloc] initWithFrame:cg(0,1,6,2)];
        l.textColor = [UIColor blackColor];
        l.textAlignment = NSTextAlignmentCenter;
        l.font = [UIFont fontWithName:@"Courier New" size:65];
        l.text = @"0.00000";
        [self addSubview:l];
        self.value_label = l;

        #undef cg
        #undef mb

        [[self layer] setBorderWidth:5];
        [[self layer] setBorderColor:[UIColor blackColor].CGColor];
        [self refreshAllControls];
        return self;
    }

    -(UIButton*)makeButton:(CGRect)frame cb:(SEL)cb {
        UIButton* b;
        b = [UIButton buttonWithType:UIButtonTypeSystem];
        b.userInteractionEnabled = YES;
        [b addTarget:self action:cb forControlEvents:UIControlEventTouchUpInside];
        [b.titleLabel setFont:[UIFont systemFontOfSize:24]];
        [b setTitle:@"T" forState:UIControlStateNormal];
        [b setTitleColor:[UIColor blackColor] forState:UIControlStateNormal];
        [[b layer] setBorderWidth:2];
        [[b layer] setBorderColor:[UIColor darkGrayColor].CGColor];
        b.frame = frame;
        [self addSubview:b];
        return b;
    }


    -(void)auto_manual_button_press{
        BOOL* b = &g_meter->disp_settings.auto_range[self->channel-1];
        *b = !*b;
        [self refreshAllControls];
    }

    -(void)auto_manual_button_refresh {
        BOOL* b = &g_meter->disp_settings.auto_range[self->channel-1];
        [MeterViewController style_auto_button:self.auto_manual_button on:*b];
    }

    -(void)display_set_button_press {
        // If on normal electrode input, toggle between AC and DC display
        // If reading CH3, cycle from VauxDC->VauxAC->Resistance->Diode
        // If reading temp, do nothing
        uint8 setting = [g_meter getChannelSetting:self->channel] & METER_CH_SETTINGS_INPUT_MASK;
        BOOL* const ac_setting = &g_meter->disp_settings.ac_display[self->channel-1];
        uint8* const ch3_mode  = &g_meter->disp_settings.ch3_mode;
        uint8* const measure_setting  = &g_meter->meter_settings.rw.measure_settings;
        switch(setting) {
            case 0x00:
                // Electrode input
                *ac_setting = !*ac_setting;
                break;
            case 0x04:
                // Temp input
                break;
            case 0x09:
                switch(*ch3_mode) {
                case CH3_VOLTAGE:
                    *ac_setting = !*ac_setting;
                    if(!*ac_setting) (*ch3_mode)++;
                    break;
                case CH3_RESISTANCE:
                    (*ch3_mode)++;
                    break;
                case CH3_DIODE:
                    (*ch3_mode) = CH3_VOLTAGE;
                    break;
            }
            switch(*ch3_mode) {
                case CH3_VOLTAGE:
                    *measure_setting &=~METER_MEASURE_SETTINGS_ISRC_ON;
                    break;
                case CH3_RESISTANCE:
                    *measure_setting |= METER_MEASURE_SETTINGS_ISRC_ON;
                    break;
                case CH3_DIODE:
                    *measure_setting |= METER_MEASURE_SETTINGS_ISRC_ON;
                    break;
            }
            break;
        }
        [g_meter sendMeterSettings:^(NSError *error) {
            [self refreshAllControls];
        }];

    }

    -(void)display_set_button_refresh {
        [self.display_set_button setTitle:[g_meter getDescriptor:self->channel] forState:UIControlStateNormal];
    }

    -(void)input_set_button_press {
        uint8 setting       = [g_meter getChannelSetting:self->channel];
        uint8 other_setting = [g_meter getChannelSetting:self->channel==1?2:1];
        switch(setting & METER_CH_SETTINGS_INPUT_MASK) {
            case 0x00:
                // Electrode input: Advance to CH3 unless the other channel is already on CH3
                if((other_setting & METER_CH_SETTINGS_INPUT_MASK) == 0x09 ) {
                    setting &= ~METER_CH_SETTINGS_INPUT_MASK;
                    setting |= 0x04;
                } else {
                    setting &= ~METER_CH_SETTINGS_INPUT_MASK;
                    setting |= 0x09;
                }
                break;
            case 0x09:
                // CH3 input
                setting &= ~METER_CH_SETTINGS_INPUT_MASK;
                setting |= 0x04;
                break;
            case 0x04:
                // Temp input
                setting &= ~METER_CH_SETTINGS_INPUT_MASK;
                setting |= 0x00;
                break;
        }
        [g_meter setChannelSetting:self->channel set:setting];
        [g_meter sendMeterSettings:^(NSError *error) {
            [self refreshAllControls];
        }];

    }

    -(void)input_set_button_refresh {
        [self.input_set_button setTitle:[g_meter getInputLabel:self->channel] forState:UIControlStateNormal];
    }

    -(void)units_button_press {
        BOOL* b = &g_meter->disp_settings.raw_hex[self->channel-1];
        *b=!*b;
        [self refreshAllControls];
    }

    -(void)units_button_refresh {
        NSString* unit_str;
        if(!g_meter->disp_settings.raw_hex[self->channel-1]) {
            SignificantDigits digits = [g_meter getSigDigits:self->channel];
            const NSString* prefixes[] = {@"μ",@"m",@"",@"k",@"M"};
            uint8 prefix_i = 2;
            //TODO: Unify prefix handling.
            while(digits.high > 4) {
                digits.high -= 3;
                prefix_i++;
            }
            while(digits.high <=0) {
                digits.high += 3;
                prefix_i--;
            }
            unit_str = [NSString stringWithFormat:@"%@%@",prefixes[prefix_i],[g_meter getUnits:self->channel]];
        } else {
            unit_str = @"RAW";
        }
        [self.units_button setTitle:unit_str forState:UIControlStateNormal];
    }

    -(uint8)pga_cycle:(uint8)chx_set {
        uint8 tmp;
        tmp = chx_set & METER_CH_SETTINGS_PGA_MASK;
        tmp >>=4;
        switch(tmp) {
            case 1:
                tmp=4;
                break;
            case 4:
                tmp=6;
                break;
            case 6:
            default:
                tmp=1;
                break;
        }
        tmp <<= 4;
        chx_set &=~METER_CH_SETTINGS_PGA_MASK;
        chx_set |= tmp;
        return chx_set;
    }

    -(void)range_button_press {
        uint8 channel_setting = [g_meter getChannelSetting:self->channel];
        uint8* const adc_setting = &g_meter->meter_settings.rw.adc_settings;
        uint8* const ch3_mode  = &g_meter->disp_settings.ch3_mode;
        uint8 tmp;

        if(g_meter->disp_settings.auto_range[self->channel-1]) {
            return;
        }

        switch(channel_setting & METER_CH_SETTINGS_INPUT_MASK) {
            case 0x00:
                // Electrode input
                switch(self->channel) {
                    case 1:
                        // We are measuring current.  We can boost PGA, but that's all.
                        channel_setting = [self pga_cycle:channel_setting];
                        break;
                    case 2:
                        // Switch the ADC GPIO to activate dividers
                        tmp = (*adc_setting & ADC_SETTINGS_GPIO_MASK)>>4;
                        tmp++;
                        tmp %= 3;
                        tmp<<=4;
                        *adc_setting &= ~ADC_SETTINGS_GPIO_MASK;
                        *adc_setting |= tmp;
                        channel_setting &=~METER_CH_SETTINGS_PGA_MASK;
                        channel_setting |= 0x10;
                        break;
                }
                break;
            case 0x04:
                // Temp input
                break;
            case 0x09:
                switch(*ch3_mode) {
                case CH3_VOLTAGE:
                    channel_setting = [self pga_cycle:channel_setting];
                    break;
                case CH3_RESISTANCE:
                case CH3_DIODE:
                    channel_setting = [self pga_cycle:channel_setting];
                    tmp = channel_setting & METER_CH_SETTINGS_PGA_MASK;
                    tmp >>=4;
                    if(tmp == 1) {
                        // Change the current source setting
                        g_meter->meter_settings.rw.measure_settings ^= METER_MEASURE_SETTINGS_ISRC_LVL;
                    }
                    break;
            }
            break;
        }
        [g_meter setChannelSetting:self->channel set:channel_setting];
        [g_meter sendMeterSettings:^(NSError *error) {
            [self refreshAllControls];
        }];
    }

    -(void)range_button_refresh {
        // How many different ranges do we want to support?
        // Supporting a range for every single PGA gain seems mighty excessive.

        uint8 channel_setting = [g_meter getChannelSetting:self->channel];
        uint8 measure_setting = g_meter->meter_settings.rw.measure_settings;
        uint8* const adc_setting = &g_meter->meter_settings.rw.adc_settings;
        uint8* const ch3_mode  = &g_meter->disp_settings.ch3_mode;
        NSString* lval;

        switch(channel_setting & METER_CH_SETTINGS_INPUT_MASK) {
            case 0x00:
                // Electrode input
                switch(self->channel) {
                    case 1:
                        switch(channel_setting&METER_CH_SETTINGS_PGA_MASK) {
                            case 0x10:
                                lval = @"10A";
                                break;
                            case 0x40:
                                lval = @"2.5A";
                                break;
                            case 0x60:
                                lval = @"1A";
                                break;
                        }
                        break;
                    case 2:
                        switch(*adc_setting & ADC_SETTINGS_GPIO_MASK) {
                        case 0x00:
                            lval = @"1.2V";
                            break;
                        case 0x10:
                            lval = @"60V";
                            break;
                        case 0x20:
                            lval = @"600V";
                            break;
                    }
                    break;
                }
                break;
            case 0x04:
                // Temp input
                lval = @"60C";
                break;
            case 0x09:
                switch(*ch3_mode) {
                case CH3_VOLTAGE:
                case CH3_DIODE:
                    switch(channel_setting&METER_CH_SETTINGS_PGA_MASK) {
                        case 0x10:
                            lval = @"1.2V";
                            break;
                        case 0x40:
                            lval = @"300mV";
                            break;
                        case 0x60:
                            lval = @"100mV";
                            break;
                    }
                    break;
                case CH3_RESISTANCE:
                    switch((channel_setting&METER_CH_SETTINGS_PGA_MASK) | (measure_setting&METER_MEASURE_SETTINGS_ISRC_LVL)) {
                        case 0x12:
                            lval = @"10kΩ";
                            break;
                        case 0x42:
                            lval = @"2.5kΩ";
                            break;
                        case 0x62:
                            lval = @"1kΩ";
                            break;
                        case 0x10:
                            lval = @"10MΩ";
                            break;
                        case 0x40:
                            lval = @"2.5MΩ";
                            break;
                        case 0x60:
                            lval = @"1MΩ";
                            break;
                    }
                    break;
            }
            break;
        }
        [self.range_button setTitle:lval forState:UIControlStateNormal];
        if(g_meter->disp_settings.auto_range[self->channel-1]) {
            [self.range_button setBackgroundColor:[UIColor lightGrayColor]];
        } else {
            [self.range_button setBackgroundColor:[UIColor whiteColor]];
        }
    }

    -(void)value_label_refresh {
        const int c = self->channel;
        const BOOL ac = g_meter->disp_settings.ac_display[c-1];
        double val;
        int lsb_int;
        switch(channel) {
            case 1:
                if(ac) { lsb_int = (int)(sqrt(g_meter->meter_sample.ch1_ms)); }
                else   { lsb_int = [MooshimeterDevice to_int32:g_meter->meter_sample.ch1_reading_lsb]; }
                break;
            case 2:
                if(ac) { lsb_int = (int)(sqrt(g_meter->meter_sample.ch2_ms)); }
                else   { lsb_int = [MooshimeterDevice to_int32:g_meter->meter_sample.ch2_reading_lsb]; }
                break;
        }

        if(g_meter->disp_settings.raw_hex[c-1]) {
            lsb_int &= 0x00FFFFFF;
            self.value_label.text = [NSString stringWithFormat:@"%06X", lsb_int];
        } else {
            // If at the edge of your range, say overload
            // Remember the bounds are asymmetrical
            const int32 upper_limit_lsb =  1.3*(1<<22);
            const int32 lower_limit_lsb = -0.9*(1<<22);

            if(   lsb_int > upper_limit_lsb
                    || lsb_int < lower_limit_lsb ) {
                self.value_label.text = @"OVERLOAD";
            } else {
                val = [g_meter lsbToNativeUnits:lsb_int ch:c];
                self.value_label.text = [MeterViewController formatReading:val digits:[g_meter getSigDigits:c] ];
            }
        }
    }

    -(void)refreshAllControls {
        [self display_set_button_refresh];
        [self input_set_button_refresh];
        [self auto_manual_button_refresh];
        [self units_button_refresh];
        [self range_button_refresh];
        //[self value_label_refresh];
    }*/

}
