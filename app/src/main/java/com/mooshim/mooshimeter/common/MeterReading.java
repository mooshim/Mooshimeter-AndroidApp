package com.mooshim.mooshimeter.common;

import android.util.Log;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.DuplicateFormatFlagsException;

/**
 * Created by First on 3/26/2016.
 */
public class MeterReading {
    private static String TAG="MeterReading";

    public float value;
    public String units;

    private int n_digits;
    private float max;

    private DecimalFormat format;
    private float format_mult;
    private int format_prefix;

    public MeterReading() {
        this(0,0,0,"");
    }
    public MeterReading(float value) {
        this(value,0,0,"");
    }
    public MeterReading(float value, int n_digits, float max, String units) {
        this.value    = value;
        this.n_digits=n_digits;
        this.max    = max;
        this.units  = units;

        if(max == 0) {
            // Formatting code will break if max is 0
            max = 1;
        }

        int high = (int)Math.log10(max);
        format_mult = 1;
        format_prefix = 3;

        while(high > 3) {
            format_prefix++;
            high -= 3;
            format_mult /= 1000;
        }
        while(high <= 0) {
            format_prefix--;
            high += 3;
            format_mult *= 1000;
        }

        StringBuilder fstring = new StringBuilder();
        for(int i = 0; i < high; i++) {
            fstring.append("0");
        }
        fstring.append(".");
        for(int i = 0; i < n_digits-high; i++) {
            fstring.append("0");
        }
        format = new DecimalFormat(fstring.toString());
    }

    ////////////////////////////////
    // Convenience functions
    ////////////////////////////////

    public float getMax() {
        return max;
    }

    public String toString() {
        if(max==0) {
            return units;
        }

        final String prefixes[] = new String[]{"n","\u03bc","m","","k","M","G"};
        float lval = value;
        if(Math.abs(lval) > 1.2*max) {
            return "OUT OF RANGE";
        }
        StringBuilder retval = new StringBuilder();
        if(lval>=0) {
            retval.append(" "); // Space for neg sign
        }
        retval.append(format.format(lval*format_mult));
        retval.append(prefixes[format_prefix]);
        retval.append(units);
        return retval.toString();
    }

    public static MeterReading mult(MeterReading m0, MeterReading m1) {
        MeterReading rval = new MeterReading(m0.value*m1.value,
                                (m0.n_digits+m1.n_digits)/2,
                                m0.max*m1.max,
                                m0.units+m1.units
                                );
        char[] tmp = rval.units.toCharArray();
        Arrays.sort(tmp);
        rval.units = String.valueOf(tmp);
        if(rval.units.equals("AV")) {
            rval.units = "W";
        }
        return rval;
    }
}
