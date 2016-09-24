package com.mooshim.mooshimeter.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Vibrator;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by First on 6/5/2016.
 */
public class Alerter implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int duration = 1; // seconds
    private static final int sampleRate = 8000;
    private static final int leadin = 200;
    private static final int numSamples = duration * sampleRate;
    private static final double freqOfTone = 880; // hz
    private static final int samplesPerLoop = (int)(100 * sampleRate/freqOfTone);

    private static final long[] vibratePattern = { 0, 50, 200, 50,};    // Pattern for Vibrator.vibrate()

    private static boolean vibrate = false;

    private static AudioTrack audioTrack;
    private static Vibrator vibrator;
    private static boolean currentlyVibrating = false;

    private static ScheduledThreadPoolExecutor executor;
    private static Runnable stopAlert;
    private static ScheduledFuture nextStopAlert;

    private static AudioTrack generateTone() {

        final float sample[] = new float[numSamples];
        // fill out the array
        for (int i = 0; i < numSamples; ++i) {
            sample[i]= (float)Math.sin(2 * Math.PI * i / (sampleRate/freqOfTone));
        }
        // Put an envelope on the beginning and end to avoid speaker crackle
        for(int i = 0; i < leadin; i++) {
            float frac = ((float)i)/leadin;
            sample[i] *= frac;
            sample[sample.length-(i+1)] *= frac;
        }
        // convert to 16 bit pcm sound array
        // assumes the sample buffer is normalised.
        byte generatedSnd[] = new byte[numSamples*2];
        int idx = 0;
        for (double dVal : sample) {
            short val = (short) (dVal * 32767);
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
        }
        AudioTrack ret = new AudioTrack(AudioManager.STREAM_RING,
                8000, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, generatedSnd.length,
                AudioTrack.MODE_STATIC);
        ret.write(generatedSnd, 0, generatedSnd.length);

        return ret;
    }

    private static void singletonInit() {

        if (audioTrack != null) return;

        audioTrack = generateTone();
        vibrator = (Vibrator) Util.getRootContext().getSystemService(Context.VIBRATOR_SERVICE);
        vibrate = Util.getPreferenceBoolean(Util.preference_keys.VIBRATION_ALERT);

        // A way to schedule a call to stopAlerting() in the future
        executor = new ScheduledThreadPoolExecutor(1);
        stopAlert = new Runnable() {
            @Override
            public void run() {
                stopAlerting();
            }
        };
    }

    private static void beep() {
        if(audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {

            // On the initial play, we play including the attenuated lead-in
            // After that, we loop full periods of beep to give a continuous tone.
            audioTrack.stop();
            audioTrack.setLoopPoints(leadin, leadin + samplesPerLoop, -1);
            audioTrack.setPlaybackHeadPosition(0);
            audioTrack.play();
        }


    }

    private static void stopBeeping() {
        audioTrack.stop();
    }

    private static void vibrate() {
        if (!currentlyVibrating) {
            vibrator.vibrate(vibratePattern, 2);
            currentlyVibrating = true;
        }
    }

    private static void stopVibrating() {
        vibrator.cancel();
        currentlyVibrating = false;
    }

    /**
     * Alert the user for one second.
     * If called again within the duration, the alert is extended.
     */
    public static void alert() {
        singletonInit();
        if (vibrate) {
            vibrate();
        } else {
            beep();
        }

        // The beep/vibration will loop indefinitely, until stopAlerting() is called.
        // This isn't quite good enough, since the user might switch modes while we
        // are alerting, causing an infinite beep/vibe. Therefore, we let the alert time
        // out using this trick.
        if (nextStopAlert != null) nextStopAlert.cancel(true);
        nextStopAlert = executor.schedule(stopAlert, duration, TimeUnit.SECONDS);
    }

    /**
     * Interrupt an alert started by alert()
     */
    public static void stopAlerting() {
        singletonInit();
        stopVibrating();
        stopBeeping();
    }

    /**
     * Replace beeps with vibrations
     */
    public static void enableVibration(boolean vibrate) {
        Alerter.vibrate = vibrate;
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(Util.preference_keys.VIBRATION_ALERT)) {
            Alerter.enableVibration(sharedPreferences.getBoolean(key, false));
        }
    }
}
