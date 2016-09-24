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
    private static int bufferSize;
    private static final int duration = 1; // seconds
    private static final int sampleRate = 8000;
    private static final double freqOfTone = 800; // hz
    private static final int samplesPerPeriod = (int)(sampleRate/freqOfTone);
    private static final short leadin = 10 * samplesPerPeriod;
    private static final short body = 300 * samplesPerPeriod;
    private static final short leadout = 10 * samplesPerPeriod;
    private static final int numSamples = leadin + body + leadout;

    // Generated PCM samples for the lead-in, some number of full periods and then lead-out.
    // Playing all three back to back should give a jitter- and crackle-free tone.
    private static short[] sampHead = new short[leadin];
    private static short[] sampBody = new short[body];
    private static short[] sampTail = new short[leadout];

    private static final long[] vibratePattern = { 0, 50, 200, 50,};    // Pattern for Vibrator.vibrate()

    private static boolean vibrate = false;

    private static AudioTrack audioTrack;
    private static Vibrator vibrator;
    private static boolean currentlyVibrating = false;
    private static boolean currentlyBeeping = false;

    private static ScheduledThreadPoolExecutor executor;
    private static Runnable stopAlert;
    private static ScheduledFuture nextStopAlert;

    private static short waveform(short i) {
        return (short) (0.5 * Math.sin(2 * Math.PI * i / (samplesPerPeriod)) * 32767);
    }

    private static AudioTrack generateTone() {

        // Attenuated intro
        for (short i = 0; i < leadin; ++i) {
            float frac = (float) i / leadin;
            sampHead[i] = (short) (waveform(i) * frac);
        }

        // Full-volume waveform in between
        for (short i = 0; i < body; ++i) {
            sampBody[i] = waveform(i);
        }

        // Attenuated outro
        for (short i = 0; i < leadout; ++i) {
            float frac = (float) 1 -  (float)i/ leadout;
            sampTail[i] = (short) (waveform(i) * frac);
        }

        // Since AudioTrack.write() is blocking, we need to have enough
        // buffer space to write each new chunk Rate/freqOfToneof audio while there is still
        // enough old data in the buffer to play without a hickup.
        AudioTrack ret = new AudioTrack(AudioManager.STREAM_RING,
                sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, 2 * numSamples,
                AudioTrack.MODE_STREAM);

        ret.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
            @Override
            public void onMarkerReached(AudioTrack track) {
                // Do nothing, we don't have any markers
            }

            @Override
            public void onPeriodicNotification(AudioTrack track) {
                if (currentlyBeeping) {
                    track.write(sampBody, 0, sampBody.length);
                }
            }
        });

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

        audioTrack.play();
    }

    private static void beep() {
        if(audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {

            // On the initial play, we play including the attenuated lead-in
            // After that, we periodically feed in full periods of wave to get a continuous beep.
            audioTrack.flush();
            currentlyBeeping = true;

            // Call back every time this many frames have been played, so we know we can
            // enqueue another bit of audio data.
            audioTrack.setPositionNotificationPeriod(body);

            audioTrack.setPlaybackHeadPosition(0);
            audioTrack.write(sampHead, 0, sampHead.length);

            // Playback will not start if we haven't filled up minBuffer bytes.
            // The exact number is device-dependent!
            // Prime the buffer with enough full waveform periods.
            audioTrack.write(sampBody, 0, sampBody.length);
            audioTrack.write(sampBody, 0, sampBody.length);

            audioTrack.play();
        }

    }

    private static void stopBeeping() {
        if (currentlyBeeping) {
            // Let the buffer deplete after we've played the lead-out
            audioTrack.write(sampTail, 0, sampTail.length);
            currentlyBeeping = false;
        }

        // Finish playing what's been buffered and then stop
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
