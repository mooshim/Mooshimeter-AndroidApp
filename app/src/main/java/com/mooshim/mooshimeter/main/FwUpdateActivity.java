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
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.common.BLEUtil;
import com.mooshim.mooshimeter.common.MooshimeterDevice;
import com.mooshim.mooshimeter.util.Conversion;
import com.mooshim.mooshimeter.util.WatchDog;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class FwUpdateActivity extends Activity {
    public final static String EXTRA_MESSAGE = "com.example.ti.ble.sensortag.MESSAGE";
    // Activity
    private static final int FILE_ACTIVITY_REQ = 0;
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
    // BLE
    private BLEUtil mBLEUtil = null;
    private ImgHdr mFileImgHdr = new ImgHdr();
    private ProgInfo mProgInfo = new ProgInfo();
    // Housekeeping
    private boolean mProgramming = false;
    private WatchDog mWatchdog = null;

    private class RetrieveFirmwareTask extends AsyncTask<Semaphore, Void, Void> {
        private Semaphore sem;
        public Boolean mSuccess = false;

        protected void releaseSem(boolean success) {
            mSuccess = success;
            sem.release();
        }

        @Override
        protected Void doInBackground(Semaphore... semaphores) {
            URL fw_url = null;
            final Void r = null;
            sem = semaphores[0];
            try {
                fw_url = new URL("https://moosh.im/s/f/mooshimeter-firmware-latest.bin");
            } catch (MalformedURLException e) {
                e.printStackTrace();
                releaseSem(false);
                return r;
            }
            try {
                InputStream stream = fw_url.openStream();
                try {
                    stream.read(mFileBuffer, 0, mFileBuffer.length);
                    stream.close();
                } catch (IOException e) {
                    // Handle exceptions here
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mLog.setText("Network read failed \n");
                        }
                    });
                    releaseSem(false);
                    return r;
                }
            } catch (IOException e) {
                e.printStackTrace();
                releaseSem(false);
                return r;
            }
            unpackFirmwareFileBuffer();
            releaseSem(true);
            return r;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");

        super.onCreate(savedInstanceState);

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
        mBtnStart.setEnabled(false);

        // Housekeeping variables
        mBLEUtil = BLEUtil.getInstance(this);
        if (!mBLEUtil.setPrimaryService(MooshimeterDevice.mUUID.OAD_SERVICE_UUID)) {
            Log.e(TAG, "Failed to find OAD service");
            finish();
        }
        URL fw_url = null;
        Semaphore network_wait = new Semaphore(0);
        RetrieveFirmwareTask fw_task = new RetrieveFirmwareTask();
        fw_task.execute(network_wait);
        try {
            network_wait.tryAcquire(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if(!fw_task.mSuccess) {
            loadFile(FW_FILE_A, true);
        }
        mWatchdog = new WatchDog(new Runnable() {
            @Override
            public void run() {
                stopProgramming();
            }
        }, 5000);

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
        mBLEUtil.setDisconnectCB(new Runnable() {
            @Override
            public void run() {
                stopProgramming();
                finish();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == FILE_ACTIVITY_REQ) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                String filename = data.getStringExtra(FileActivity.EXTRA_FILENAME);
                loadFile(filename, false);
            }
        }
    }

    ////////////////////////////////
    // State transitions
    ////////////////////////////////

    private void startProgramming() {
        if(mProgramming) {
            Log.e(TAG, "startProgramming called, but programming already underway!");
            return;
        }
        mLog.append("Programming started\n");
        mProgramming = true;
        updateStartButton();

        mWatchdog.feed();

        // Update connection parameters
        mBLEUtil.setConnectionInterval((short) 20, (short) 1000, new BLEUtil.BLEUtilCB() {
            @Override
            public void run() {
                // Send image notification
                final byte[] buf = mFileImgHdr.packForIdentify();
                mBLEUtil.enableNotify(MooshimeterDevice.mUUID.OAD_IMAGE_BLOCK, true, new BLEUtil.BLEUtilCB() {
                    @Override
                    public void run() {
                        mBLEUtil.enableNotify(MooshimeterDevice.mUUID.OAD_IMAGE_IDENTIFY, true, new BLEUtil.BLEUtilCB() {
                            @Override
                            public void run() {
                                mBLEUtil.send(MooshimeterDevice.mUUID.OAD_IMAGE_IDENTIFY, buf, new BLEUtil.BLEUtilCB() {
                                    @Override
                                    public void run() {
                                        if (error != BluetoothGatt.GATT_SUCCESS) {
                                            Log.e(TAG, "Error sending identify");
                                        } else {
                                            // Initialize stats
                                            mProgInfo.reset();
                                            programBlock((short)0);
                                        }
                                    }
                                });
                            }
                        }, new BLEUtil.BLEUtilCB() {
                            @Override
                            public void run() {
                                Log.d(TAG, "OAD Image identify notification!");
                            }
                        });
                    }
                }, new BLEUtil.BLEUtilCB() {
                    @Override
                    public void run() {
                        // After each block notify, allow the next block to fly
                        if (error == BluetoothGatt.GATT_SUCCESS) {
                            // Update stats
                            mWatchdog.feed();
                            final ByteBuffer bn = ByteBuffer.wrap(value);
                            bn.order(ByteOrder.LITTLE_ENDIAN);
                            mProgInfo.requestedBlock = bn.getShort();
                            final short rb = mProgInfo.requestedBlock;
                            mProgInfo.requested[rb]++;
                            mProgInfo.cBlocks++;
                            if(mProgInfo.requested[rb] == 1) {
                                Log.d(TAG,"Meter requested block " + rb);
                            } else {
                                Log.e(TAG,"MULTIREQUEST ON BLOCK " + rb);
                            }
                            programBlock(rb);
                        } else {
                            mLog.append("GATT writeCharacteristic failed\n");
                            stopProgramming();
                        }
                    }
                });
            }
        });

    }

    private void stopProgramming() {
        if(!mProgramming) {
            Log.e(TAG, "stopProgramming called, but programming already stopped!");
            return;
        }
        mWatchdog.stop();
        mProgramming = false;
        mProgressInfo.setText("");
        mProgressBar.setProgress(0);
        updateStartButton();

        if (mProgInfo.requestedBlock == mProgInfo.nBlocks) {
            mLog.append("Programming complete!\n");
        } else {
            mLog.append("Programming cancelled\n");
        }
        boolean all_confirmed = true;
        for(int i = 0; i < mProgInfo.nBlocks; i++) {
            if(mProgInfo.requested[i]!=1) {
                if(all_confirmed) {
                    mLog.append("WARNING: NOT ALL BLOCKS CONFIRMED\n");
                }
                all_confirmed = false;
                if(mProgInfo.requested[i] == 0) {
                    Log.e(TAG,"Block " + i + " unconfirmed");
                } else {
                    Log.e(TAG,"Block " + i + " OVERconfirmed");
                }
                break;

            }
        }
        if(all_confirmed) {
            mLog.append("All blocks confirmed!");
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
    }

    private void displayImageInfo(TextView v, ImgHdr h) {
        int imgSize = h.len * 4;
        String s = String.format("Ver.: %d Build: %d Size: %d", h.ver, h.build_time, imgSize);
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
        final double timeEstimate = ((double) (mFileImgHdr.len * 4) / (double) iBytes) * elapsed;

        txt = String.format("Time: %d / %d sec", (int) elapsed, (int) timeEstimate);
        txt += String.format("    Bytes: %d (%d/sec)", iBytes, (int) byteRate);
        mProgressInfo.setText(txt);
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
        mFileImgHdr.unpack(mFileBuffer);
        displayImageInfo(mFileImage, mFileImgHdr);

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
        final byte oadBuffer[] = new byte[OAD_BUFFER_SIZE];
        oadBuffer[0] = Conversion.loUint16(bnum);
        oadBuffer[1] = Conversion.hiUint16(bnum);
        System.arraycopy(mFileBuffer, bnum*OAD_BLOCK_SIZE, oadBuffer, 2, OAD_BLOCK_SIZE);
        Log.d(TAG,"Sending block " + bnum);
        mBLEUtil.send(MooshimeterDevice.mUUID.OAD_IMAGE_BLOCK, oadBuffer,null);
        if(bnum %32 == 0){
            mBLEUtil.addToRunQueue(new Runnable() {
                @Override
                public void run() {
                    mProgressBar.setProgress((bnum * 100) / mProgInfo.nBlocks);
                    displayStats();
                }
            });
        }
    }

    /////////////////////////
    // Convenience classes
    /////////////////////////

    private class ImgHdr {
        short crc0;
        short crc1;
        short ver;
        int len;
        int build_time;
        byte[] res = new byte[4];

        public void unpack(byte[] fbuf) {
            ByteBuffer b = ByteBuffer.wrap(fbuf);
            b.order(ByteOrder.LITTLE_ENDIAN);
            crc0 = b.getShort();
            crc1 = b.getShort();
            ver = b.getShort();
            len = 0xFFFF & ((int) b.getShort());
            build_time = b.getInt();
            for (int i = 0; i < 4; i++) {
                res[i] = b.get();
            }
        }

        public byte[] pack() {
            byte[] retval = new byte[16];
            ByteBuffer b = ByteBuffer.wrap(retval);
            b.order(ByteOrder.LITTLE_ENDIAN);
            b.putShort(crc0);
            b.putShort(crc1);
            b.putShort(ver);
            b.putShort((short) len);
            b.putInt(build_time);
            for (int i = 0; i < 4; i++) {
                b.put(res[i]);
            }
            return retval;
        }

        public byte[] packForIdentify() {
            byte[] retval = new byte[8];
            ByteBuffer b = ByteBuffer.wrap(retval);
            b.order(ByteOrder.LITTLE_ENDIAN);
            b.putShort(ver);
            b.putShort((short) len);
            b.putInt(build_time);
            return retval;
        }
    }

    private class ProgInfo {
        short requestedBlock = 0; // Number of blocks sent
        short cBlocks = 0; // Number of blocks confirmed
        short nBlocks = 0; // Total number of blocks
        byte[] requested;
        long timeStart = System.currentTimeMillis();

        void reset() {
            cBlocks = 0;
            timeStart = System.currentTimeMillis();
            nBlocks = (short) (mFileImgHdr.len / (OAD_BLOCK_SIZE / HAL_FLASH_WORD_SIZE));
            requested = new byte[nBlocks];
            for(int i = 0; i < nBlocks; i++){requested[i]=0;}
        }
    }

}
