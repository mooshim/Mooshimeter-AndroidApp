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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
    private static final byte[] mFileBuffer = new byte[FILE_BUFFER_SIZE];
    private static int mFirmwareVersion;

    public static int getBundledFirmwareVersion() {
        return mFirmwareVersion;
    }

    public static byte[] getFileBuffer() {
        return mFileBuffer;
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
            b.getShort(); // Skip crc
            b.getShort(); // Skip user version (unused)
            b.getShort(); // Skip len
            mFirmwareVersion = b.getInt();
        } catch (IOException e) {
            // Handle exceptions here
            Log.e("UTIL", "Failed to unpack the firmware asset");
        }
    }

    // Worker thread
    private static final class NamedThreadFactory implements ThreadFactory {
        public Thread newThread(Runnable r) {
            return new Thread(r, "background_thread");
        }
    }
    private static final BlockingQueue<Runnable> worker_tasks = new LinkedBlockingQueue<Runnable>();
    private static final ExecutorService worker = new ThreadPoolExecutor(
            1,  // Number of worker threads to run
            1,  // Maximum number of worker threads to run
            1,  // Timeout
            TimeUnit.SECONDS, // Timeout units
            worker_tasks, // Queue of runnables
            new NamedThreadFactory() // Thread factory to generate named thread for easy debug
    );
    static boolean inMainThread() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }

    public static void checkOnMainThread() {
        if(!inMainThread()) {
            Log.e("UTIL", "We're not in the main thread, but we should be!");
            Exception e = new Exception();
            e.printStackTrace();
        }
    }

    public static void checkNotOnMainThread() {
        if(inMainThread()) {
            Log.e("UTIL", "We're in the main thread, but we should not be!");
            Exception e = new Exception();
            e.printStackTrace();
        }
    }

    public static void dispatch(Runnable r) {
        worker.execute(r);
    }

    public static void postDelayed(Runnable r, int ms) {
        mHandler.postDelayed(r, ms);
    }

    public static void displayProgressBar(final Context context, final String title, final String message ) {
        if(mProgressDialogContainer[0] != null) {
            Log.e("UTIL","Trying to display a progress bar with one already up!");
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
            Log.e("UTIL", "Trying to set progress on a nonexistant bar!");
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
            Log.e("UTIL", "Trying to set progress on a nonexistant bar!");
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
        return;
    }

    public static boolean offerYesNoDialog(final Context context, final String title, final String message) {
        checkNotOnMainThread();
        final Semaphore sem = new Semaphore(0);
        final boolean[] response = new boolean[1]; // Capture the user's decision

        Runnable r = new Runnable() {
            @Override
            public void run() {
                AlertDialog alertDialog = new AlertDialog.Builder(context).create();
                alertDialog.setTitle(title);
                alertDialog.setMessage(message);
                alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Yes",
                                      new DialogInterface.OnClickListener() {
                                          public void onClick(DialogInterface dialog, int which) {
                                              response[0] = true;
                                              dialog.dismiss();
                                              sem.release();
                                          }
                                      });
                alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "No",
                                      new DialogInterface.OnClickListener() {
                                          public void onClick(DialogInterface dialog, int which) {
                                              response[0] = false;
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
        return response[0];
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
}
