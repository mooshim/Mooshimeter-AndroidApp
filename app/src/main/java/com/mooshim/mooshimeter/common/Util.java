/*
 * Copyright (c) Mooshim Engineering LLC 2015.
 *
 * This file is part of Mooshimeter-AndroidApp.
 *
 * Foobar is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Foobar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with Mooshimeter-AndroidApp.  If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.mooshim.mooshimeter.common;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Base64;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.mooshim.mooshimeter.interfaces.NotifyHandler;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/**
 * Created by JWhong on 9/26/2015.
 */
public class Util {

    private Util() {}

    private final static String TAG = "UTIL";
    private static Map<Looper,Handler> mHandlerMap = new HashMap<>();

    private static Context mRootContext;
    private static TextToSpeech speaker;
    public static FirmwareFile download_fw;
    public static FirmwareFile bundled_fw;

    public static void init(Context context) {
        mRootContext = context;
        TextToSpeech.OnInitListener speaker_init_listener = new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int i) {
                if(i != TextToSpeech.ERROR) {
                    try {
                        speaker.setLanguage(Locale.US);
                    } catch(IllegalArgumentException e) {
                        // On Samsung Galaxy S6 running Android 6.0, speaker.setLanguage crashes with IllegalArgumentException!
                        // We catch it here though.  It's out of our hands.  Let the chips fall where they may.
                    }
                }
            }
        };
        speaker = new TextToSpeech(context,speaker_init_listener);
        bundled_fw = FirmwareFile.FirmwareFileFromPath("Mooshimeter.bin");
        Util.dispatch(new Runnable() {
            @Override
            public void run() {
                FirmwareFile tmp = FirmwareFile.FirmwareFileFromURL("https://moosh.im/s/f/mooshimeter-firmware-beta.bin");
                if(tmp != null) {
                    Log.d(TAG,"Successfully downloaded newer firmware file!");
                    download_fw = tmp;
                }
            }
        });
    }

    public static FirmwareFile getBundledFW() {
        return bundled_fw;
    }

    public static FirmwareFile getDownloadFW() {
        return download_fw;
    }

    public static FirmwareFile getLatestFirmware() {
        if(download_fw!=null && download_fw.getVersion()>bundled_fw.getVersion()) {
            return download_fw;
        }
        return bundled_fw;
    }

    static boolean inMainThread() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }

    public static void checkNotOnMainThread() {
        if(inMainThread()) {
            Log.e(TAG, "We're in the main thread, but we should not be!");
            Exception e = new Exception();
            e.printStackTrace();
        }
    }

    private static Dispatcher bg_dispatcher = new Dispatcher("bg_thread"); // bg=Background
    private static Dispatcher cb_dispatcher = new Dispatcher("cb_thread"); // cb=Callback

    public static void dispatch(Runnable r) {
        bg_dispatcher.dispatch(r);
    }

    public static void dispatchCb(Runnable r) {
        cb_dispatcher.dispatch(r);
    }
    public static boolean onCBThread() {
        return cb_dispatcher.isCallingThread();
    }

    private static Handler getHandlerForPresentActivity() {
        Handler rval = mHandlerMap.get(Looper.getMainLooper());
        if(rval == null) {
            rval = new Handler(Looper.getMainLooper());
        }
        return rval;
    }

    public static void postToMain(Runnable r) {
        getHandlerForPresentActivity().post(r);
    }
    public static void postDelayed(Runnable r, int ms) {
        getHandlerForPresentActivity().postDelayed(r,ms);
    }
    public static void cancelDelayedCB(Runnable r) {
        getHandlerForPresentActivity().removeCallbacks(r);
    }

    public static void setText(final TextView v,final CharSequence s) {
        CharSequence cached = v.getText();
        if(     cached != null
                && !cached.equals(s)) {
            Util.postToMain(new Runnable() {
                @Override
                public void run() {
                    v.setText(s);
                }
            });
        }
    }

    public static void blockOnAlertBox(final Context context, final String title, final String message) {
        checkNotOnMainThread();
        final Semaphore sem = new Semaphore(0);

        Runnable r = new Runnable() {
            @Override
            public void run() {
                AlertDialog alertDialog = new AlertDialog.Builder(context).create();
                alertDialog.setTitle(title);
                alertDialog.setMessage(message);
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                                      new DialogInterface.OnClickListener() {
                                          public void onClick(DialogInterface dialog, int which) {
                                              dialog.dismiss();
                                              sem.release();
                                          }
                                      });
                // In some circumstances, we can end up posting a dialog box with an activity that is
                // finishing, which will cause a:
                // """
                // Fatal Exception: android.view.WindowManager$BadTokenException
                // Unable to add window -- token android.os.BinderProxy@4fb78e4 is not valid; is your activity running?
                // """
                if(!((Activity)context).isFinishing()) {
                    alertDialog.show();
                } else {
                    sem.release();
                }
            }
        };

        postToMain(r);

        // block until the dialog to be dismissed
        try {
            sem.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static int offerChoiceDialog(final Context context, final String title, final String message, final String[] buttons) {
        checkNotOnMainThread();
        final Semaphore sem = new Semaphore(0);
        final int[] response = new int[1]; // Capture the user's decision

        if(buttons.length>3) {
            Log.e(TAG,"Can't have more than 3 choices!");
            new Exception().printStackTrace();
        }

        Runnable r = new Runnable() {
            @Override
            public void run() {
                AlertDialog alertDialog = new AlertDialog.Builder(context).create();
                alertDialog.setTitle(title);
                alertDialog.setMessage(message);
                for(int i = 0; i < buttons.length; i++) {
                    // i-3 is because BUTTON_NEUTRAL, BUTTON_POSITIVE and BUTTON_NEGATIVE
                    // are -3,-2,-1, respectively
                    alertDialog.setButton(i-3, buttons[i],
                          new DialogInterface.OnClickListener() {
                              public void onClick(DialogInterface dialog, int which) {
                                  response[0] = which+3;
                                  dialog.dismiss();
                                  sem.release();
                              }
                          });
                }
                if(!((Activity)context).isFinishing()) {
                    alertDialog.show();
                }
            }
        };

        postToMain(r);

        // block until the dialog to be dismissed
        try {
            sem.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return response[0];
    }

    public static String offerStringInputBox(final Context context, final String title) {
        checkNotOnMainThread();
        final Semaphore sem = new Semaphore(0);
        final String[] response = new String[1]; // Capture the user's decision

        Runnable r = new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                final EditText input = new EditText(context);
                builder.setTitle(title);
                builder.setView(input);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        response[0] = input.getText().toString();
                        sem.release();
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        response[0] = null;
                        dialog.cancel();
                        sem.release();
                    }
                });
                builder.show();
            }
        };
        postToMain(r);

        // block until the dialog to be dismissed
        try {
            sem.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return response[0];
    }

    public static boolean offerYesNoDialog(final Context context, final String title, final String message) {
        final String[] choices = {"Yes","No"};
        return 0 == offerChoiceDialog(context,title,message,choices);
    }

    public static void blockUntilRunOnMainThread(final Runnable r) {
        final Exception context = new Exception();
        if (inMainThread()) {
            r.run();
        } else {
            final Semaphore sem = new Semaphore(0);
            postToMain(new Runnable() {
                @Override
                public void run() {
                    try {
                        r.run();
                    } catch(Exception e) {
                        Log.e(TAG, "Exception in callback dispatched from:");
                        context.printStackTrace();
                        Log.e(TAG, "Exception details:" + e.getMessage());
                        e.printStackTrace();
                        // forward the exception
                        throw e;
                    } finally {
                        sem.release();
                    }
                }
            });
            try {
                sem.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static UUID uuidFromBytes(byte[] in) {
        ByteBuffer bb = ByteBuffer.wrap(in);
        long high = bb.getLong();
        long low = bb.getLong();
        return new UUID(high, low);
    }

    private static byte[] uuidToBytes(final UUID arg) {
        final byte[] s = arg.toString().getBytes();
        byte[] rval = new byte[16];
        for(int i = 0; i < 16; i++){ rval[i]=0; }
        // We expect 16 bytes, but UUID strings are reverse order from byte arrays
        int i = 31;
        for(byte b:s) {
            if( b >= 0x30 && b < 0x3A ) {        // Numbers 0-9
                b -= 0x30;
            } else if( b >= 0x41 && b < 0x47 ) { // Capital letters A-F
                b -= 0x41;
                b += 10;
            } else if( b >= 0x61 && b < 0x67 ) { // Lower letters a-f
                b -= 0x61;
                b += 10;
            } else { // Unrecognized symbol, probably a dash
                continue;
            }
            // Is this the top or bottom nibble?
            b <<= (i%2 == 0)?0:4;
            rval[i/2] |= b;
            i--;
        }
        return rval;
    }

    public static double getUTCTime() {
        return ((double)System.currentTimeMillis())/1000;
    }

    public static double getNanoTime() {
        return ((double)System.nanoTime())/1000000000;
    }

    public static Context getRootContext() {
        return mRootContext;
    }

    public static PopupMenu generatePopupMenuWithOptions(final Context context, final List<String> options,final View anchor,final NotifyHandler cb,final Runnable on_dismiss) {
        final PopupMenu rval = new PopupMenu(context,anchor);
        int i = 0;
        for(String s:options) {
            rval.getMenu().add(0,i,i,s);
            rval.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {
                    final int choice = menuItem.getItemId();
                    rval.dismiss();
                    Util.dispatch(new Runnable() {
                        @Override
                        public void run() {
                            cb.onReceived(getUTCTime(), choice);
                        }
                    });
                    return false;
                }
            });
            i++;
        }
        if(on_dismiss!=null) {
            rval.setOnDismissListener(new PopupMenu.OnDismissListener() {
                @Override
                public void onDismiss(PopupMenu popupMenu) {
                    on_dismiss.run();
                }
            });
        }
        rval.show();
        return rval;
    }
    public static void delay(int ms) {
        if(ms==0) {
            return;
        }
        final Semaphore s = new Semaphore(0);
        postDelayed(new Runnable() {
            @Override
            public void run() {
                s.release();
            }
        }, ms);
        try {
            s.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    public static boolean isSpeaking() {
        return speaker.isSpeaking();
    }
    public static void speak(String speech) {
        speaker.speak(speech, TextToSpeech.QUEUE_FLUSH, null);
    }

    public static List<String> stringifyCollection(Collection<?> objectCollection) {
        List<String> rval = new ArrayList<>(objectCollection.size());
        for(Object o:objectCollection) {
            rval.add(o.toString());
        }
        return rval;
    }

    public static void logNullMeterEvent(String addr) {
        // For unknown reasons, getDeviceWithAddress sometimes returns null
        // This should be impossible.  But to prevent it crashing the app
        Answers.getInstance().logCustom(new CustomEvent("ReceivedNullMeter")
                                        .putCustomAttribute("StackTrace", Log.getStackTraceString(new Exception()))
                                        .putCustomAttribute("MeterAddress", addr));
    }

    public static class TemperatureUnitsHelper {
        private TemperatureUnitsHelper() {}
        public static float absK2C(float K) {
            return (float) (K-273.15);
        }
        public static float relK2C(float K) {
            return K;
        }
        public static float absK2F(float K) {
            return (float) ((K - 273.15)* 1.8000 + 32.00);
        }
        public static float absC2F(float C) {
            return (float) ((C)* 1.8000 + 32.00);
        }
        public static float relK2F(float C) {
            return (float) ((C)* 1.8000);
        }
    }
    //////////////////
    // Functions for dealing with persistent global preferences
    //////////////////
    public static class preference_keys {
        private preference_keys() {}
        public static final String
        USE_FAHRENHEIT= "USE_FAHRENHEIT",
        BROADCAST_INTENTS= "BROADCAST_INTENTS";
    }

    public static SharedPreferences getSharedPreferences(String name) {
        return mRootContext.getSharedPreferences(name,Context.MODE_PRIVATE);
    }
    public static SharedPreferences getSharedPreferences() {
        return getSharedPreferences("mooshimeter-global");
    }
    public static boolean hasPreference(String key) {
        return getSharedPreferences().contains(key);
    }
    public static boolean getPreferenceBoolean(String key) {
        return getSharedPreferences().getBoolean(key, false);
    }
    public static byte[] getPreferenceByteArray(String key) {
        String intermediate = getSharedPreferences().getString(key,null);
        if(intermediate==null) {
            return null;
        }
        return  Base64.decode(intermediate, Base64.DEFAULT);
    }
    public static void setPreference(String key, boolean val) {
        SharedPreferences sp = getSharedPreferences();
        SharedPreferences.Editor e = sp.edit();
        e.putBoolean(key,val);
        e.commit();
    }
    public static void setPreference(String key, byte[] val) {
        SharedPreferences sp = getSharedPreferences();
        SharedPreferences.Editor e = sp.edit();
        String saveThis = Base64.encodeToString(val, Base64.DEFAULT);
        e.putString(key,saveThis);
        e.commit();
    }
}
