package com.mooshim.mooshimeter.common;

import android.content.Context;
import android.content.Intent;

/**
 * Created by Workroom on 7/6/2016.   added for broadcast intent
 */
public class BroadcastIntentData {
    public static void broadcastMeterReading(MeterReading val) {
        broadcastMeterReading(val,"SAMPLE_INTENT");
    }
    public static void broadcastMeterReading(MeterReading val, String intent_name) {
        /* broadcast the intent.  any and all receivers on the android device will receive the intent if
          the receiver has an action filter of     com.mooshim.mooshimeter.SAMPLE_INTENT
         Change MeterReading val so broadcast receivers can more easily use the data in key:value pair format.
        */
        //  Build the intent and broadcast it
        Intent intent = new Intent();
        intent.setAction("com.mooshim.mooshimeter."+intent_name);
        intent.putExtra("units",val.units); // key, value pair
        intent.putExtra("value",val.value);
        try {
            Util.getRootContext().sendBroadcast(intent);  // context must come from an activity or MyApplication
        }
        catch (Exception e) {
            // placeholder
        }
    }
}