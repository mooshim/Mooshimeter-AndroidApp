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
package com.mooshim.mooshimeter.main;

import android.app.Activity;
import android.bluetooth.BluetoothGatt;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.common.MooshimeterDevice;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Semaphore;

public class FwUpdateActivity extends Activity {
    // Activity
    private static final int FILE_BUFFER_SIZE = 0x40000;

    // Programming parameters
    private final byte[] mFileBuffer = new byte[FILE_BUFFER_SIZE];
    private static final String FW_FILE_A = "Mooshimeter.bin";

    private static final int OAD_BLOCK_SIZE = 16;
    private static final int OAD_BUFFER_SIZE = 2 + OAD_BLOCK_SIZE;
    private static final int HAL_FLASH_WORD_SIZE = 4;
    // Log
    private static String TAG = "FwUpdateActivity";
    // GUI
    private TextView mFileImage;
    private TextView mProgressInfo;
    private TextView mLog;
    private ProgressBar mProgressBar;
    private Button mBtnStart;
    private CheckBox mLegacyMode;
    // BLE
    private MooshimeterDevice mMeter;
    private ProgInfo mProgInfo = new ProgInfo();
    // Housekeeping
    private boolean mProgramming = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");

        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        mMeter = ScanActivity.getDeviceWithAddress(intent.getStringExtra("addr"));

        // GUI init
        setContentView(R.layout.activity_fwupdate);

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

        mBtnStart.setEnabled(false);
        // If we're on an older version of Android, enable the checkbox by default
        mLegacyMode.setChecked(android.os.Build.VERSION.SDK_INT < 21);
        mLegacyMode.setEnabled(true);

        loadFile(FW_FILE_A, true);

        updateStartButton();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "onBackPressed");
        if (mProgramming) {
            Toast.makeText(this, R.string.prog_ogoing, Toast.LENGTH_LONG).show();
        } else
            super.onBackPressed();
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
    protected void onResume() {
        super.onResume();
        /*mBLEUtil.setDisconnectCB(new Runnable() {
            @Override
            public void run() {
                stopProgramming();
                finish();
            }
        });*/
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

    }

    Semaphore blockPacer;
    static short nextBlock = 0;
    static boolean in_recovery;

    ////////////////////////////////
    // State transitions
    ////////////////////////////////

    private void startProgramming() {
        final boolean legacy_mode = mLegacyMode.isChecked();

        if(mProgramming) {
            Log.e(TAG, "startProgramming called, but programming already underway!");
            return;
        }
        in_recovery = false;
        mLog.append("Programming started\n");
        mProgramming = true;
        nextBlock = 0;
        updateStartButton();

        // If uploading in legacy mode, scale back on the speed substantially.
        blockPacer = new Semaphore(legacy_mode ? 1:8);

        // Update connection parameters
        //mMeter.setConnectionInterval((short) 20, (short) 1000);

        final Handler delayed_poster = new Handler();

        // Send image notification
        mMeter.oad_block.enableNotify(true,new Runnable() {
            @Override
            public void run() {
                mProgInfo.requestedBlock = mMeter.oad_block.requestedBlock;
                final short rb = mProgInfo.requestedBlock;
                Log.d(TAG,"Meter requested block " + rb);
                if(legacy_mode) {
                    // In legacy mode, we always send only the block that has been requested
                    nextBlock = rb;
                    blockPacer.release();
                } else {
                    if(!in_recovery && rb+10 < nextBlock) {
                        // Something went wrong and we've skipped ahead
                        Log.e(TAG,"ERROR: Meter requested discontinuous block: " + rb);
                        nextBlock = rb;
                        in_recovery = true;
                    }
                    if(in_recovery) {
                        // Give a 500ms delay for the BLE stack to catch up and clear
                        delayed_poster.postDelayed(new Runnable() {
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
                if(rb%32==0) {
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
        mMeter.oad_identity.enableNotify(true, new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "OAD Image identify notification!");
            }
        });

        // The meter will request block zero if the identity is received.
        mMeter.oad_identity.send();
        // Initialize stats
        mProgInfo.reset();
        final Handler delay_handler = new Handler();
        mMeter.addConnectionStateCB(BluetoothGatt.STATE_DISCONNECTED, new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        stopProgramming();
                        mLog.append("Meter disconnected.  Exiting...\n");
                    }
                });
                delay_handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        setResult(RESULT_OK);
                        finish();
                    }
                }, 3000);
            }
        });
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                while(mProgramming) {
                    try {
                        blockPacer.acquire();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if(nextBlock != mProgInfo.nBlocks) {
                        programBlock(nextBlock++);
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
        mProgressInfo.setText("");
        mProgressBar.setProgress(0);
        updateStartButton();

        // NOTE: This is not an entirely accurate completion criteria because the meter disconnects
        // as soon as it receives the final block.  Since the blocks are sent in gangs, we may not
        // receive confirmation notifications on the last 4-5 blocks, otherwise I would just check
        // mProgInfo.requestedBlock here instead of nextBlock
        if ( nextBlock == mProgInfo.nBlocks ) {
            mLog.append("Programming complete!\n");
        } else {
            mLog.append("Programming cancelled\n");
        }
    }

    /////////////////////////////////
    // GUI Callbacks
    /////////////////////////////////

    public void onStart(View v) {
        if (mProgramming) {
            stopProgramming();
        } else {
            startProgramming();
        }
    }

    ////////////////////////////
    // GUI Refreshers
    ////////////////////////////

    private void updateStartButton() {
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

    private void displayImageInfo(TextView v) {
        int imgSize = mMeter.oad_identity.len * 4;
        String s = String.format("Ver.: %d Build: %d Size: %d", mMeter.oad_identity.ver, mMeter.oad_identity.build_time, imgSize);
        v.setText(Html.fromHtml(s));
    }

    private void displayStats() {
        String txt;
        final int iBytes = mProgInfo.requestedBlock*OAD_BLOCK_SIZE;
        final double byteRate;
        final double elapsed = (System.currentTimeMillis() - mProgInfo.timeStart) / 1000.0;
        if (elapsed > 0) {
            byteRate = ((double)iBytes) / elapsed;
        } else {
            byteRate = 0;
        }
        final double timeEstimate = ((double) (mMeter.oad_identity.len * 4) / (double) iBytes) * elapsed;

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

    private boolean loadFile(String filepath, boolean isAsset) {
        InputStream stream;

        // Load binary file
        try {
            // Read the file raw into a buffer
            if (isAsset) {
                stream = getAssets().open(filepath);
            } else {
                File f = new File(filepath);
                stream = new FileInputStream(f);
            }
        } catch (IOException e) {
            // Handle exceptions here
            mLog.setText("File open failed: " + filepath + "\n");
            return false;
        }
        try {
            stream.read(mFileBuffer, 0, mFileBuffer.length);
            stream.close();
        } catch (IOException e) {
            // Handle exceptions here
            mLog.setText("File read failed \n");
            return false;
        }
        unpackFirmwareFileBuffer();
        return true;
    }

    private void unpackFirmwareFileBuffer() {
        boolean fSuccess = false;
        // Show image info
        mMeter.oad_identity.unpackFromFile(mFileBuffer);
        displayImageInfo(mFileImage);

        // Verify image types
        int resid = R.style.dataStyle1;
        mFileImage.setTextAppearance(this, resid);

        // Enable programming button only if image types differ
        mBtnStart.setEnabled(true);

        // Expected duration
        displayStats();

        // Log
        mLog.setText("Image Loaded.\n");
        mLog.append("Ready to program device!\n");

        updateStartButton();
    }

    private synchronized void programBlock(final short bnum){
        if (!mProgramming)
            return;

        // Prepare block
        final byte oadBuffer[] = new byte[OAD_BLOCK_SIZE];
        System.arraycopy(mFileBuffer, bnum*OAD_BLOCK_SIZE, oadBuffer, 0, OAD_BLOCK_SIZE);

        mMeter.oad_block.blockNum = bnum;
        mMeter.oad_block.bytes = oadBuffer;

        Log.d(TAG, "Sending block " + bnum);
        int rval;
        do {
            rval = mMeter.oad_block.send();
            if(rval!=0) {
                Log.e("","SEND ERROR OCCURRED:" + rval);
            }
        } while(rval!=0);
    }

    /////////////////////////
    // Convenience classes
    /////////////////////////

    private class ProgInfo {
        short requestedBlock = 0; // Number of blocks sent
        short nBlocks = 0; // Total number of blocks
        long timeStart = System.currentTimeMillis();

        void reset() {
            timeStart = System.currentTimeMillis();
            nBlocks = (short) (mMeter.oad_identity.len / (OAD_BLOCK_SIZE / HAL_FLASH_WORD_SIZE));
        }
    }

}
