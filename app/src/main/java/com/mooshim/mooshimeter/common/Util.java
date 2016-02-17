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

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by JWhong on 9/26/2015.
 */
public class Util {

    private final static String TAG = "UTIL";

    private static Context mContext = null;
    private static Handler mHandler = null;
    private static ProgressDialog[] mProgressDialogContainer = new ProgressDialog[1];

    public static void init(Context context) {
        mContext = context;
        loadFile();
        mHandler = new Handler(mContext.getMainLooper());
    }

    // Singleton resources for accessing the bundled firmware image
    private static final int FILE_BUFFER_SIZE = 0x40000;
    public static final int OAD_BLOCK_SIZE = 16;
    public static final int OAD_BUFFER_SIZE = 2 + OAD_BLOCK_SIZE;
    private static final byte[] mFileBuffer = new byte[FILE_BUFFER_SIZE];
    private static int mFirmwareVersion;

    public static int getBundledFirmwareVersion() {
        return mFirmwareVersion;
    }

    public static byte[] getFileBuffer() {
        return mFileBuffer;
    }

    public static byte[] getFileBlock(short bnum) {
        final byte rval[] = new byte[OAD_BLOCK_SIZE];
        System.arraycopy(mFileBuffer, bnum*OAD_BLOCK_SIZE, rval, 0, OAD_BLOCK_SIZE);
        return rval;
    }

    private static void loadFile() {
        InputStream stream;
        try {
            // Read the file raw into a buffer
            stream = mContext.getAssets().open("Mooshimeter.bin");
            stream.read(mFileBuffer, 0, mFileBuffer.length);
            stream.close();

            ByteBuffer b = ByteBuffer.wrap(mFileBuffer);
            b.order(ByteOrder.LITTLE_ENDIAN);
            b.getShort(); // Skip crc0
            b.getShort(); // Skip crc1
            b.getShort(); // Skip user version (unused)
            b.getShort(); // Skip len
            mFirmwareVersion = b.getInt();
        } catch (IOException e) {
            // Handle exceptions here
            Log.e(TAG, "Failed to unpack the firmware asset");
        }
    }

    // Worker thread
    private static final class NamedThreadFactory implements ThreadFactory {
        private String name;
        public NamedThreadFactory(String n) {
            name=n;
        }
        public Thread newThread(Runnable r) {
            return new Thread(r, name);
        }
    }
    // TODO don't repeat yourself
    private static final BlockingQueue<Runnable> worker_tasks = new LinkedBlockingQueue<Runnable>();
    private static final ExecutorService worker = new ThreadPoolExecutor(
            1,  // Number of worker threads to run
            1,  // Maximum number of worker threads to run
            1,  // Timeout
            TimeUnit.SECONDS, // Timeout units
            worker_tasks, // Queue of runnables
            new NamedThreadFactory("main_background") // Thread factory to generate named thread for easy debug
    );

    private static final BlockingQueue<Runnable> cb_worker_tasks = new LinkedBlockingQueue<Runnable>();
    private static final ExecutorService cb_worker = new ThreadPoolExecutor(
            1,  // Number of worker threads to run
            1,  // Maximum number of worker threads to run
            1,  // Timeout
            TimeUnit.SECONDS, // Timeout units
            cb_worker_tasks, // Queue of runnables
            new NamedThreadFactory("cb_background") // Thread factory to generate named thread for easy debug
    );
    static boolean inMainThread() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }

    public static void checkOnMainThread() {
        if(!inMainThread()) {
            Log.e(TAG, "We're not in the main thread, but we should be!");
            Exception e = new Exception();
            e.printStackTrace();
        }
    }

    public static void checkNotOnMainThread() {
        if(inMainThread()) {
            Log.e(TAG, "We're in the main thread, but we should not be!");
            Exception e = new Exception();
            e.printStackTrace();
        }
    }

    public static void dispatch(Runnable r) {
        worker.execute(r);
    }

    public static void dispatch_cb(Runnable r) {
        cb_worker.execute(r);
    }

    public static void postDelayed(Runnable r, int ms) {
        mHandler.postDelayed(r, ms);
    }
    public static void cancel(Runnable r) {
        mHandler.removeCallbacks(r);
    }

    public static void displayProgressBar(final Context context, final String title, final String message ) {
        if(mProgressDialogContainer[0] != null) {
            Log.e(TAG,"Trying to display a progress bar with one already up!");
            mProgressDialogContainer[0].dismiss();
        }
        Runnable r = new Runnable() {
            @Override
            public void run() {
                mProgressDialogContainer[0] = new ProgressDialog(context);
                mProgressDialogContainer[0].setTitle(title);
                mProgressDialogContainer[0].setMessage(message);
                mProgressDialogContainer[0].setIndeterminate(false);
                mProgressDialogContainer[0].setMax(100);
                mProgressDialogContainer[0].show();
            }
        };
        if(inMainThread()) {
            r.run();
        } else {
            mHandler.post(r);
        }
    }

    public static void setProgress(final int percent) {
        if(mProgressDialogContainer[0] == null) {
            Log.e(TAG, "Trying to set progress on a nonexistant bar!");
            return;
        }
        Runnable r = new Runnable() {
            @Override
            public void run() {
                mProgressDialogContainer[0].setProgress(percent);
            }
        };
        if(inMainThread()) {
            r.run();
        } else {
            mHandler.post(r);
        }
        return;
    }

    public static void dismissProgress() {
        if(mProgressDialogContainer[0] == null) {
            Log.e(TAG, "Trying to set progress on a nonexistant bar!");
            return;
        }
        Runnable r = new Runnable() {
            @Override
            public void run() {
                mProgressDialogContainer[0].dismiss();
                mProgressDialogContainer[0] = null;
            }
        };
        if(inMainThread()) {
            r.run();
        } else {
            mHandler.post(r);
        }
        return;
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
                alertDialog.show();
            }
        };

        mHandler.post(r);

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
                    alertDialog.setButton(i-3, buttons[i],
                          new DialogInterface.OnClickListener() {
                              public void onClick(DialogInterface dialog, int which) {
                                  response[0] = which+3;
                                  dialog.dismiss();
                                  sem.release();
                              }
                          });
                }
                alertDialog.show();
            }
        };

        mHandler.post(r);

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
        if(inMainThread()) {
            r.run();
        } else {
            final Semaphore sem = new Semaphore(0);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    r.run();
                    sem.release();
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
        return mContext;
    }

    public static PopupMenu generatePopupMenuWithOptions(final Context context, final List<String> options,final View anchor,final NotifyHandler cb,final Runnable on_dismiss) {
        final PopupMenu rval = new PopupMenu(context,anchor);
        int i = 0;
        for(String s:options) {
            rval.getMenu().add(0,i,i,s);
            rval.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {
                    int choice = menuItem.getItemId();
                    rval.dismiss();
                    cb.onReceived(getUTCTime(), choice);
                    return false;
                }
            });
            i++;
        }
        rval.setOnDismissListener(new PopupMenu.OnDismissListener() {
            @Override
            public void onDismiss(PopupMenu popupMenu) {
                on_dismiss.run();
            }
        });
        rval.show();
        return rval;
    }
}
