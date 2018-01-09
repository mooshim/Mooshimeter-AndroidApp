package com.mooshim.mooshimeter.common;

import android.os.Environment;

import com.mooshim.mooshimeter.devices.MooshimeterDeviceBase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
    //public ByteArrayOutputStream mData = new ByteArrayOutputStream();
    private FileOutputStream mWriter = null;
    private File mFile = null;

    static final String mLogDir = "MooshimeterLogs/";

    static {
        File logdir = new File(Environment.getExternalStorageDirectory(), mLogDir);
        if(!logdir.exists()) {
            logdir.mkdirs();
        }
    }

    public String getFilePath() {
        return mLogDir+getFileName();
    }

    public String getFileName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmm");
        String end_time_string = sdf.format(new Date(mEndTime*1000));
        return mMeter.getName()+"-Log"+mIndex+"-"+end_time_string+".csv";
    }

    public File getFile() {
        if(mFile==null) {
            mFile = new File(Environment.getExternalStorageDirectory(), getFileName());
        }
        return mFile;
    }

    public void appendToFile(byte[] data) throws IOException {
        if(mWriter == null) {
            mWriter = new FileOutputStream(getFile(),true);
        }
        mWriter.write(data);
        mWriter.flush();
        if(getFile().length()>=mBytes) {
            mWriter.close();
        }
    }

    public String getFileData() throws IOException {
        byte[] buf = new byte[(int)getFile().length()];
        FileInputStream reader = new FileInputStream(getFile());
        reader.read(buf);
        reader.close();
        return new String(buf);
    }
}
