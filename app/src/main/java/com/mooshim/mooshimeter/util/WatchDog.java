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

package com.mooshim.mooshimeter.util;

import android.os.Handler;
import android.util.Log;

/**
 * Created by First on 2/16/2015.
 */
public class WatchDog extends Handler {
    private int mTimeout = 10000;  // Time to dog bite in ms
    private Runnable mBite = defaultCB; // Callback for when timer expires

    private static Runnable defaultCB = new Runnable() {
        @Override
        public void run() {
            Log.e("Watchdog", "Watchdog bit without a callback declared!");
        }
    };
    public WatchDog() {
        super();
    }
    public WatchDog(Runnable newCB, int timeout) {
        super();
        setCB(newCB);
        mTimeout = timeout;
    }
    public void setCB(Runnable newCB) {
        stop();
        mBite = newCB;
    }
    public void feed() {
        stop();
        postDelayed(mBite,mTimeout);
    }
    public void feed(int timeout) {
        mTimeout = timeout;
        feed();
    }
    public void stop() {
        removeCallbacks(mBite);
    }
}
