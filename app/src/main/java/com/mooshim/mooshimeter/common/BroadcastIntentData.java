package com.mooshim.mooshimeter.common;

import android.content.Context;
import android.content.Intent;

/**
 * Created by Workroom on 7/6/2016.   added for broadcast intent
 */
public class BroadcastIntentData {
    private CooldownTimer intent_cooldown = new CooldownTimer(); // added for broadcast intent

    public void broadcastIntent(Context context, MeterReading val) {
        /* broadcast the intent.  any and all receivers on the android device will receive the intent if
          the receiver has an action filter of     com.mooshim.mooshimeter.CUSTOM_INTENT

         Change MeterReading val so broadcast receivers can more easily use the data in key:value pair format.
         The broadcast key is always "val". The units of volts, amps, ohms, watts, degs are not used for simplicity for user.
         The scale factor is applied so user will not have be concerned with the meter auto scaling.
        */
        String outValue = "";
        Float sendValue = 0.0f;
        String inVal = val.toString();
        Float scaleFactor = 1.0f;
        String stringNumber = "";

        if (intent_cooldown.expired) {
            if (inVal == null || inVal.equals("")) {
                return;        // do not sendBroadcast if empty
            }
            if (inVal.equals("OUT OF RANGE")) {
                sendValue = 1.0E09f;        // set a really large (1G) value which receiver can use in math filters
            } else {
                // Get the numbers and convert to float and apply scale factor
                for (char c : inVal.toCharArray()) {
                    switch (c) {
                        case 'm':
                            scaleFactor = 0.0001f;
                            break;
                        case 'k':
                            scaleFactor = 1000.0f;
                            break;
                        case 'M':
                            scaleFactor = 1000000.0f;
                            break;
                        case '0':
                        case '1':
                        case '2':
                        case '3':
                        case '4':
                        case '5':
                        case '6':
                        case '7':
                        case '8':
                        case '9':
                        case '-':
                        case '.':
                        case ',':
                            stringNumber = stringNumber + c;
                            break;
                        default:
                            break;
                    }
                }
                sendValue = scaleFactor * Float.parseFloat(stringNumber); // scaled float of the meterReading value
            }

            outValue = sendValue.toString();

            //  Build the intent and broadcast it
            Intent intent = new Intent();
            intent.setAction("com.mooshim.mooshimeter.CUSTOM_INTENT");
            intent.putExtra("val", outValue); // key, value pair
            //intent.putExtra("val", "3.14159"); // key, value pair for testing
            //Log.e("intent", "before sendbroadcast" + "  " + sendValue + "  " + outValue);

            try {
                context.sendBroadcast(intent);  // context must come from an activity or MyApplication
            }
            catch (Exception e) {
                // placeholder
            }


            intent_cooldown.fire(10000);   // limit broadcast of intents to every 10sec

        }

    }
}