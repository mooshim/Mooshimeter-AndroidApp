package com.mooshim.mooshimeter.common;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * Created by First on 6/5/2016.
 */
public class Beeper {
    private static final int duration = 1; // seconds
    private static final int sampleRate = 8000;
    private static final int leadin = 200;
    private static final int numSamples = duration * sampleRate;
    private static final double freqOfTone = 880; // hz
    private static final byte generatedSnd[] = new byte[numSamples*2];

    private static AudioTrack audioTrack;

    private static void singletonInit() {
        if(audioTrack!=null) {
            return;
        }
        final float sample[] = new float[numSamples];
        // fill out the array
        for (int i = 0; i < numSamples; ++i) {
            sample[i]  = (float)Math.sin(2 * Math.PI * i / (sampleRate/freqOfTone));
        }
        // Put an envelope on the beginning and end to avoid speaker crackle
        for(int i = 0; i < leadin; i++) {
            float frac = ((float)i)/leadin;
            sample[i] *= frac;
            sample[sample.length-(i+1)] *= frac;
        }
        // convert to 16 bit pcm sound array
        // assumes the sample buffer is normalised.
        int idx = 0;
        for (double dVal : sample) {
            short val = (short) (dVal * 32767);
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
        }
        audioTrack = new AudioTrack(AudioManager.STREAM_RING,
                                    8000, AudioFormat.CHANNEL_OUT_MONO,
                                    AudioFormat.ENCODING_PCM_16BIT, generatedSnd.length,
                                    AudioTrack.MODE_STATIC);
        audioTrack.write(generatedSnd, 0, generatedSnd.length);
    }
    public static void beep() {
        singletonInit();
        if(audioTrack.getPlaybackHeadPosition()<numSamples &&
           audioTrack.getPlayState()==AudioTrack.PLAYSTATE_PLAYING) {
            return;
        }
        audioTrack.stop();
        audioTrack.setPlaybackHeadPosition(0);
        audioTrack.play();
    }
    public static void stopBeeping() {
        singletonInit();
        audioTrack.stop();
    }
}
