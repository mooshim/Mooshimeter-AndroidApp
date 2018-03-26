/**************************************************************************************************
 Filename:       FwUpdateActivity.java
 Revised:        $Date: 2013-09-05 05:55:20 +0200 (to, 05 sep 2013) $
 Revision:       $Revision: 27614 $

 Copyright (c) 2013 - 2014 Texas Instruments Incorporated

 All rights reserved not granted herein.
 Limited License.

 Texas Instruments Incorporated grants a world-wide, royalty-free,
 non-exclusive license under copyrights and patents it now or hereafter
 owns or controls to make, have made, use, import, offer to sell and sell ("Utilize")
 this software subject to the terms herein.  With respect to the foregoing patent
 license, such license is granted  solely to the extent that any such patent is necessary
 to Utilize the software alone.  The patent license shall not apply to any combinations which
 include this software, other than combinations with devices manufactured by or for TI (TI Devices).
 No hardware patent is licensed hereunder.

 Redistributions must preserve existing copyright notices and reproduce this license (including the
 above copyright notice and the disclaimer and (if applicable) source code license limitations below)
 in the documentation and/or other materials provided with the distribution

 Redistribution and use in binary form, without modification, are permitted provided that the following
 conditions are met:

 * No reverse engineering, decompilation, or disassembly of this software is permitted with respect to any
 software provided in binary form.
 * any redistribution and use are licensed by TI for use only with TI Devices.
 * Nothing shall obligate TI to provide you with source code for the software licensed and provided to you in object code.

 If software source code is provided to you, modification and redistribution of the source code are permitted
 provided that the following conditions are met:

 * any redistribution and use of the source code, including any resulting derivative works, are licensed by
 TI for use only with TI Devices.
 * any redistribution and use of any object code compiled from the source code and any resulting derivative
 works, are licensed by TI for use only with TI Devices.

 Neither the name of Texas Instruments Incorporated nor the names of its suppliers may be used to endorse or
 promote products derived from this software without specific prior written permission.

 DISCLAIMER.

 THIS SOFTWARE IS PROVIDED BY TI AND TIS LICENSORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 IN NO EVENT SHALL TI AND TIS LICENSORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 POSSIBILITY OF SUCH DAMAGE.


 **************************************************************************************************/
package com.mooshim.mooshimeter.activities;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.idevicesinc.sweetblue.BleDevice;
import com.idevicesinc.sweetblue.BleManager;
import com.idevicesinc.sweetblue.BleManagerConfig;
import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.common.FirmwareFile;
import com.mooshim.mooshimeter.common.StatLockManager;
import com.mooshim.mooshimeter.devices.BLEDeviceBase;
import com.mooshim.mooshimeter.devices.MooshimeterDeviceBase;
import com.mooshim.mooshimeter.devices.PeripheralWrapper;
import com.mooshim.mooshimeter.interfaces.NotifyHandler;
import com.mooshim.mooshimeter.devices.OADDevice;
import com.mooshim.mooshimeter.common.Util;

import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

public class OADActivity extends MyActivity {
    // Activity
    private static final int FILE_BUFFER_SIZE = 0x40000;
    private static final int HAL_FLASH_WORD_SIZE = 4;
    // Log
    private static String TAG = "FwUpdateActivity";
    // GUI
    private TextView mFileImage;
    private TextView mProgressInfo;
    private TextView mLog;
    private ScrollingMovementMethod mLogScroller;
    private ProgressBar mProgressBar;
    private Button mBtnStart;
    private CheckBox mLegacyMode;
    // BLE
    private BLEDeviceBase mMeter;
    private ProgInfo mProgInfo = new ProgInfo();
    // Housekeeping
    private boolean mProgramming = false;
    private FirmwareFile mFirmwareFile;

    private int mOADDelayMs;

    private BleManager mBleManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Sorry about the casting games
        Intent intent = getIntent();
        String addr = intent.getStringExtra("addr");
        mMeter = getDeviceWithAddress(addr);
        if(mMeter==null) {
            Util.logNullMeterEvent(addr);
            finish();
            return;
        }

        mBleManager = BleManager.get(this);

        // GUI init
        setContentView(R.layout.activity_oad);

        // Icon padding
        ImageView view = (ImageView) findViewById(android.R.id.home);
        view.setPadding(10, 0, 20, 10);

        // Context title
        setTitle(R.string.title_oad);

        // Initialize widgets
        mProgressInfo = (TextView)    findViewById(R.id.tw_info);
        mFileImage    = (TextView)    findViewById(R.id.tw_file);
        mLog          = (TextView)    findViewById(R.id.tw_log);
        mProgressBar  = (ProgressBar) findViewById(R.id.pb_progress);
        mBtnStart     = (Button)      findViewById(R.id.btn_start);
        mLegacyMode   = (CheckBox)    findViewById(R.id.legacy_mode_checkbox);

        mLogScroller = new ScrollingMovementMethod();
        mLog.setMovementMethod(mLogScroller);

        mBtnStart.setEnabled(false);
        // If we're on an older version of Android, enable the checkbox by default
        mLegacyMode.setChecked(android.os.Build.VERSION.SDK_INT < 21);
        mLegacyMode.setEnabled(true);

        if(mLegacyMode.isChecked()) {
            final Context context = this;
            Util.dispatch(new Runnable() {
                @Override
                public void run() {
                    Util.blockOnAlertBox(context, "Warning: Unstable upload", "This version of Android is known to be unstable when updating firmware.  We recommend using Android 5.0 or later, or performing the firmware upload from an iOS device.");
                }
            });
        }

        if (mFirmwareFile == null) {
            Util.dispatch(new Runnable() {
                @Override
                public void run() {
                    dispatchToLog("Downloading latest firmware...\n");
                    // This can take a while, so must be done in the background thread
                    final FirmwareFile result = Util.downloadLatestFirmware();

                    // This must wait until after the download is finished, so we dispatch it back. Nice.
                    runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    if (result != null) {
                                        dispatchToLog("Successfully downloaded newer firmware file!\n");
                                    } else {
                                        dispatchToLog("Download failed.\n");
                                    }

                                    dispatchToLog("Unpacking firmware...\n");
                                    mFirmwareFile = Util.getLatestFirmware();
                                    unpackFirmwareFileBuffer();

                                }

                            }

                                 );
                }
            });
        } else {
            unpackFirmwareFileBuffer();
        }

        updateStartButton();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mProgramming = false;
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "onBackPressed");
        if (mProgramming) {
            stopProgramming();
        }
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(TAG, "onOptionsItemSelected");
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    Semaphore blockPacer;
    static short nextBlock = 0;
    static boolean in_recovery;

    private int synchronousDisconnect() {
        final StatLockManager mylock = new StatLockManager(new ReentrantLock(), "Disconnector");
        int rval = 0;
        BLEDeviceBase m = mMeter;
        Runnable disco_runnable = new Runnable() {
            @Override
            public void run() {
                addToLog("Received disconnect event.\n");
                mylock.sig();
            }
        };
        mMeter.mPwrap.addDisconnectCB(disco_runnable);
        m.disconnect();
        if(m.isConnected() && mylock.awaitMilli(10000)) {
            // Our wait timed out (disconnection failed)
            addToLog("Disconnect failed\n");
            rval = -1;
        } else {
            //Our wait was interrupted
            addToLog("Disconnect successful\n");
            rval = 0;
        }
        mMeter.mPwrap.cancelDisconnectCB(disco_runnable);
        return rval;
    }

    private BLEDeviceBase synchronousScan(BLEDeviceBase m) {
        final StatLockManager mylock = new StatLockManager(new ReentrantLock(), "SyncScan");
        final BLEDeviceBase to_match = m;
        final BLEDeviceBase rval[] = new BLEDeviceBase[1];
        rval[0]=null;

        addToLog("Scanning for meter...\n");

        MyActivity.clearDeviceCache();

        mBleManager.startScan(new BleManager.DiscoveryListener()
        {
            @Override public void onEvent(DiscoveryEvent event)
            {
                if( event.was(LifeCycle.DISCOVERED) || event.was(LifeCycle.REDISCOVERED))
                {
                    final BleDevice d = event.device();
                    final UUID[] uuids = d.getAdvertisedServices();
                    final byte[] manu_data = d.getManufacturerData();

                    // Filter devices
                    boolean oad_mode = false;
                    int build_time = 0;

                    // Always expecting a single service UUID
                    if(uuids.length != 1) {
                        return;
                    }
                    // Always expecting a 4 bytes of manufacturer data
                    if(manu_data.length != 4) {
                        return;
                    }
                    // In the case of the Mooshimeter this is the build time
                    // in UTC seconds
                    for(int j = 3; j >= 0; j--) {
                        build_time |= (manu_data[j] & 0xFF) << (8*j);
                    }

                    BLEDeviceBase detected = MyActivity.getDeviceWithAddress(d.getMacAddress());
                    if(detected==null) {
                        PeripheralWrapper p = new PeripheralWrapper(event.device(), Util.getRootContext());
                        detected = new BLEDeviceBase(p);
                        MyActivity.putDevice(detected);
                    }
                    detected.mOADMode = oad_mode;
                    detected.mBuildTime = build_time;
                    if(detected.getAddress().equals(to_match.getAddress())
                            && rval[0]==null
                            && detected.isInOADMode()) {
                        rval[0]=detected;
                        mylock.sig();
                    }
                }
            }
        });

        if (mylock.awaitMilli(20000)) {
            // Timeout
            addToLog("Did not see rebooted peripheral in scan!\n");
        } else {
            // We were interrupted, that means we found it!
            addToLog("Detected meter!\n");
        }

        mBleManager.stopScan();
        return rval[0];
    }

    private BLEDeviceBase connectAndDiscover(BLEDeviceBase m) {
        int rval = -1;
        int attempts = 0;
        while(attempts++ < 3 && rval != 0) {
            addToLog("Connecting... Attempt "+attempts+"\n");
            rval = m.connect();
        }
        if (0 != rval) {
            addToLog("Connection failed.  Status: "+rval+"\n");
            return null;
        }
        // At this point we are connected and have discovered characteristics for the BLE
        // device.  We need to figure out exactly what kind it is and start the right
        // activity for it.
        BLEDeviceBase tmp_m = m.chooseSubclass();
        if(tmp_m==null) {
            //Couldn't choose a subclass for some reason...
            addToLog("I don't recognize this device... aborting\n");
            m.disconnect();
            return null;
        } else {
            m = tmp_m;
        }
        // Replace the copy in the singleton dict
        putDevice(m);
        addToLog("Initializing...\n");
        rval = m.initialize();
        if(rval != 0) {
            addToLog("Initialization failed.  Status: "+rval+"\n");
            m.disconnect();
            return null;
        }
        addToLog("Connected!\n");
        return m;
    }

    private BLEDeviceBase walkThroughManualReconnectInOADMode(BLEDeviceBase m) {
        String[] choices = {"Continue","See video"};
        int choice = Util.offerChoiceDialog(this,"Manual Reboot Required",
                                            "The version of firmware on the Mooshimeter is incapable of automatically rebooting from Android.  You must manually reset the meter by pressing the button on the side of the meter.",
                                            choices);
        switch (choice) {
            case 0:
                Log.d(TAG,"User elected to continue");
            break;
            case 1:
                {Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://moosh.im/upgrading-mooshimeter-firmware/"));
                startActivity(browserIntent);
                return null;}
        }
        if(0!=synchronousDisconnect()) {
            return null;
        }

        addToLog("PRESS THE RESET BUTTON NOW\n");

        Util.delay(500);  // VOODOO

        if(null==(m=synchronousScan(m))) {
            return null;
        }

        Util.delay(500);  // VOODOO

        addToLog("Waiting for device to reboot...\n");
        Util.delay(mOADDelayMs);
        if(null==(m=connectAndDiscover(m))) {
            return null;
        }

        if(!(m instanceof OADDevice)) {
            addToLog("Connected, but not in OAD mode...\n");
            m.disconnect();
            return null;
        }

        return m;
    }

    ////////////////////////////////
    // State transitions
    ////////////////////////////////

    private BLEDeviceBase autoReconnectInOADMode(BLEDeviceBase m) {
        addToLog("Rebooting meter...\n");
        ((MooshimeterDeviceBase)m).reboot();
        Util.delay(500); // Give time for the command to get out
/*
                                 .       .
                                / `.   .' \
                        .---.  <    > <    >  .---.
                        |    \  \ - ~ ~ - /  /    |
                         ~-..-~             ~-..-~
                     \~~~\.'                    `./~~~/
           .-~~^-.    \__/                        \__/
         .'  O    \     /               /       \  \
        (_____,    `._.'               |         }  \/~~~/
         `----.          /       }     |        /    \__/
               `-.      |       /      |       /      `. ,~~|
                   ~-.__|      /_ - ~ ^|      /- _      `..-'   f: f:
                        |     /        |     /     ~-.     `-. _||_||_
                        |_____|        |_____|         ~ - . _ _ _ _ _>

        HERE BE DRAGONS: With older versions of firmware (144xxx and earlier), the reboot command
        immediately reboots the meter.  Android is very stupid and gets confused internally - it won't
        register the disconnection internally for many seconds, long enough that the meter falls out of bootloader mode.

        If you try to scan for the meter, find the scan record, and try to connect to the new meter,
        Android gets even more confused.  It will fail to connect for many seconds.

        If you try to disconnect() after calling reboot(), it doesn't seem to help.  Android still won't
        pass the connection state change.

        You have wasted too many hours here.  Don't bother coming back here until Android 7, at least.
*/
        if(0!=synchronousDisconnect()) {
            return null;
        }

        addToLog("Waiting for device to reboot...");
        Util.delay(mOADDelayMs);

        // WE NEED TO SCAN FOR THE METER AND CONNECT TO THE NEW SCANNED DEVICE, TRYING TO CONNECT TO THE OLD ONE FAILS

        if(null==(m=synchronousScan(m))) {
            return null;
        }

        if(null==(m=connectAndDiscover(m))) {
            return null;
        }

        if(!(m instanceof OADDevice)) {
            addToLog("Connected, but not in OAD mode...\n");
            m.disconnect();
            return null;
        }

        return m;
    }

    private void startProgramming() {
        final boolean legacy_mode = mLegacyMode.isChecked();
        BLEDeviceBase rval_meter=null;

        if(mProgramming) {
            Log.e(TAG, "startProgramming called, but programming already underway!");
            return;
        }

        // Keep the screen on
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });

        // On meter builds later than 1454xxx there is a delayed reset command that allows us to reboot the meter
        // and reconnect in OAD mode.
        if(  !mMeter.isInOADMode() ) {
            if(mMeter.mBuildTime<1454355414) {
                mOADDelayMs = 0;
                rval_meter=walkThroughManualReconnectInOADMode(mMeter);
            } else {
                mOADDelayMs = 5000;
                rval_meter=autoReconnectInOADMode(mMeter);
            }
            if(rval_meter==null) {
                Util.blockOnAlertBox(this,"Reconnect failed","The reconnection failed.  Please go back to the scan screen, reboot the meter, and connect from there.");
                addToLog("Failed to enter OAD mode!\n");
                return;
            }
            mMeter = rval_meter;
        }

        final OADDevice m = (OADDevice)mMeter;
        m.oad_identity.unpackFromFile(mFirmwareFile.getFileBuffer());

        in_recovery = false;
        addToLog("Programming started\n");
        mProgramming = true;

        nextBlock = 0;
        updateStartButton();

        // If uploading in legacy mode, scale back on the speed substantially.
        blockPacer = new Semaphore(legacy_mode ? 0:8);

        // Update connection parameters
        //mMeter.setConnectionInterval((short) 20, (short) 1000);

        // Send image notification
        m.oad_block.enableNotify(true, new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                mProgInfo.requestedBlock = m.oad_block.requestedBlock;
                final short rb = mProgInfo.requestedBlock;
                Log.d(TAG, "Meter requested block " + rb);
                if (legacy_mode) {
                    // In legacy mode, we always send only the block that has been requested
                    nextBlock = rb;
                    blockPacer.release();
                } else {
                    if (!in_recovery && rb + 10 < nextBlock) {
                        // Something went wrong and we've skipped ahead
                        Log.e(TAG, "ERROR: Meter requested discontinuous block: " + rb);
                        nextBlock = rb;
                        in_recovery = true;
                    }
                    if (in_recovery) {
                        // Give a 500ms delay for the BLE stack to catch up and clear
                        Util.postDelayedToMain(new Runnable() {
                            @Override
                            public void run() {
                                blockPacer.release();
                                in_recovery = false;
                            }
                        }, 500);
                    } else {
                        blockPacer.release();
                    }
                }
                if (rb % 32 == 0) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mProgressBar.setProgress((rb * 100) / mProgInfo.nBlocks);
                            displayStats();
                        }
                    });
                }
            }
        });
        m.oad_identity.enableNotify(true, new NotifyHandler() {
            @Override
            public void onReceived(double timestamp_utc, Object payload) {
                Log.d(TAG, "OAD Image identify notification!");
            }
        });

        // The meter will request block zero if the identity is received.
        m.oad_identity.send();
        // Initialize stats
        mProgInfo.reset();
        final Runnable[] disco_runnable = new Runnable[1];
        disco_runnable[0] = new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        stopProgramming();
                        addToLog("Meter disconnected.  Exiting...\n");
                    }
                });
                Util.postDelayedToMain(new Runnable() {
                    @Override
                    public void run() {
                        mMeter.mPwrap.cancelDisconnectCB(disco_runnable[0]);
                        finish();
                    }
                }, 3000);
            }
        };
        mMeter.mPwrap.addDisconnectCB(disco_runnable[0]);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                while(mProgramming) {
                    try {
                        blockPacer.acquire();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    programBlock(nextBlock++);
                    if(nextBlock==mProgInfo.nBlocks) {
                        nextBlock--;
                    }
                }
            }
        });
        t.start();
    }

    private void stopProgramming() {
        if(!mProgramming) {
            Log.e(TAG, "stopProgramming called, but programming already stopped!");
            return;
        }
        mProgramming = false;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                mProgressInfo.setText("");
                mProgressBar.setProgress(0);
            }
        });
        updateStartButton();

        // NOTE: This is not an entirely accurate completion criteria because the meter disconnects
        // as soon as it receives the final block.  Since the blocks are sent in gangs, we may not
        // receive confirmation notifications on the last 4-5 blocks, otherwise I would just check
        // mProgInfo.requestedBlock here instead of nextBlock
        if ( nextBlock >= mProgInfo.nBlocks - 1 ) {
            addToLog("Programming complete!\n");
        } else {
            addToLog("Programming cancelled\n");
        }
    }

    /////////////////////////////////
    // GUI Callbacks
    /////////////////////////////////

    public void onStartButton(View v) {
        Util.dispatch(new Runnable() {
            @Override
            public void run() {
                if (mProgramming) {
                    stopProgramming();
                } else {
                    startProgramming();
                }
            }
        });
    }

    ////////////////////////////
    // GUI Refreshers
    ////////////////////////////

    private void updateStartButton() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mProgramming) {
                    mBtnStart.setText(R.string.cancel);
                    mProgressInfo.setText("Programming...");
                } else {
                    mProgressBar.setProgress(0);
                    mBtnStart.setText(R.string.start_prog);
                    mProgressInfo.setText("Idle");
                }
                mLegacyMode.setEnabled(!mProgramming);
            }
        });
    }

    private void displayImageInfo(TextView v) {
        String s = String.format("Current Build: %d<br/>New Build: %d", mMeter.mBuildTime, mFirmwareFile.getVersion());
        v.setText(Html.fromHtml(s));
    }

    private void displayStats() {
        if(!(mMeter instanceof OADDevice)) {
            return;
        }
        OADDevice m = (OADDevice)mMeter;
        String txt;
        final int iBytes = mProgInfo.requestedBlock*FirmwareFile.OAD_BLOCK_SIZE;
        final double byteRate;
        final double elapsed = (Util.getUTCTime() - mProgInfo.timeStart);
        if (elapsed > 0) {
            byteRate = ((double)iBytes) / elapsed;
        } else {
            byteRate = 0;
        }
        final double timeEstimate = ((double) (m.oad_identity.len * 4) / (double) iBytes) * elapsed;

        txt = String.format("Time: %d / %d sec", (int) elapsed, (int) timeEstimate);
        txt += String.format("    Bytes: %d (%d/sec)", iBytes, (int) byteRate);
        final String dtxt = txt;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProgressInfo.setText(dtxt);
            }
        });
    }

    /////////////////////////////
    // Utility
    /////////////////////////////

    private void dispatchToLog(final String s) {
        runOnUiThread(new Runnable() {
                          @Override
                          public void run() {
                              addToLog(s);
                          }
                      }
        );
    }
    private void addToLog(final String s) {
        Log.v(TAG,s);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLog.append(s);
                // HERE BE DRAGONS:  Android textview scrolling doesn't seem to work correctly programmatically
                // mLog.getBottom returns a very large value even when there's no text in the log... as a result
                // trying to scroll to the bottom always scrolls way past where it should and shows a blank log.
                // I can't figure out how to get the bottom of the text and this is wasting time.
                //int bottom = mLog.getBottom();
                //int height = mLog.getHeight();
                //int scroll_target = bottom-height;
                //if(scroll_target>0) {
                //    mLog.scrollTo(0, scroll_target);
                //}
            }
        });
    }

    private void unpackFirmwareFileBuffer() {
        // Show image info
        displayImageInfo(mFileImage);

        // Verify image types
        int resid = R.style.dataStyle1;
        mFileImage.setTextAppearance(this, resid);

        // Enable programming button only if image types differ
        mBtnStart.setEnabled(true);

        // Expected duration
        displayStats();

        // Log
        dispatchToLog("Image Loaded.\n");
        dispatchToLog("Ready to program device!\n");

        updateStartButton();
    }

    private synchronized void programBlock(final short bnum){
        if(!(mMeter instanceof OADDevice)) {
            return;
        }
        OADDevice m = (OADDevice)mMeter;
        if (!mProgramming)
            return;

        // Prepare block
        m.oad_block.blockNum = bnum;
        m.oad_block.bytes = mFirmwareFile.getFileBlock(bnum);

        Log.d(TAG, "Sending block " + bnum);
        int rval;
        do {
            rval = m.oad_block.send();
            if(rval!=0) {
                Log.e("","SEND ERROR OCCURRED:" + rval);
                if(!m.isConnected()) {
                    stopProgramming();
                }
            }
        } while(rval!=0);
    }

    /////////////////////////
    // Convenience classes
    /////////////////////////

    private class ProgInfo {
        short requestedBlock = 0; // Number of blocks sent
        short nBlocks = 0; // Total number of blocks
        double timeStart = Util.getUTCTime();

        void reset() {
            if(!(mMeter instanceof OADDevice)) {
                return;
            }
            OADDevice m = (OADDevice)mMeter;
            timeStart = Util.getUTCTime();
            nBlocks = (short) (m.oad_identity.len / (FirmwareFile.OAD_BLOCK_SIZE / HAL_FLASH_WORD_SIZE));
        }
    }

}
