package com.mooshim.mooshimeter.common;

import android.util.Log;

import java.util.Arrays;
import java.util.DuplicateFormatFlagsException;

/**
 * Created by First on 3/26/2016.
 */
public class MeterReading {
    private static String TAG="MeterReading";

    public float value;
    public int n_digits;
    public float max;
    public String units;

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
    }

    ////////////////////////////////
    // Convenience functions
    ////////////////////////////////

    public String toString() {
        if(max==0) {
            return units;
        }

        final String prefixes[] = new String[]{"n","?","m","","k","M","G"};
        int prefix_i = 3;
        float lval = value;
        int high = (int)Math.log10(max);

        if(Math.abs(lval) > 1.2*max) {
            return "OUT OF RANGE";
        }

        while(high > 3) {
            prefix_i++;
            high -= 3;
            lval /= 1000;
        }
        while(high <= 0) {
            prefix_i--;
            high += 3;
            lval *= 1000;
        }

        // TODO: Prefixes for units.  This will fail for wrong values of digits
        boolean neg = lval<0;
        int left  = high;
        int right = n_digits - high;
        String formatstring = String.format("%s%%0%d.%df",neg?"":" ", left+right+(neg?1:0), right); // To live is to suffer
        String retval;
        try {
            retval = String.format(formatstring, lval);
        } catch ( java.util.UnknownFormatConversionException e ) {
            // Something went wrong with the string formatting, provide a default and log the error
            Log.e(TAG, "BAD FORMAT STRING");
            Log.e(TAG, formatstring);
            retval = "what";
        } catch(DuplicateFormatFlagsException e) {
            Log.e(TAG, "DUPLICATE FLAG");
            Log.e(TAG, formatstring);
            retval = "what";
        }
        //Truncate
        retval += prefixes[prefix_i];
        retval += units;
        return retval;
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
