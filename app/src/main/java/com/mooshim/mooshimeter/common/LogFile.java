package com.mooshim.mooshimeter.common;

import android.os.Environment;

import com.mooshim.mooshimeter.devices.MooshimeterDevice;
import com.mooshim.mooshimeter.devices.MooshimeterDeviceBase;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by First on 10/4/2016.
 */
public class LogFile {
    public MooshimeterDeviceBase mMeter;
    public int mIndex    = -1;
    public int mBytes    = -1;
    public long mEndTime = -1;
    public ByteArrayOutputStream mData = new ByteArrayOutputStream();

    public String getFileName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmm");
        String end_time_string = sdf.format(new Date(mEndTime*1000));
        return mMeter.getName()+"-Log"+mIndex+"-"+end_time_string+".csv";
    }

    public File getFile() {
        return new File(Environment.getExternalStorageDirectory(), getFileName());
    }

    public void writeToFile() {
        Util.postToMain(new Runnable() {
            @Override
            public void run() {
                try {
                    File sdCardFile = getFile();
                    if(sdCardFile.exists()) {
                        // The file already exists!  Don't bother writing it again.
                    } else {
                        FileWriter writer = new FileWriter(sdCardFile);
                        writer.write(mData.toString());
                        writer.flush();
                        writer.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

}
