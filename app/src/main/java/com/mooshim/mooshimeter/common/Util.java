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

import android.os.Looper;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by JWhong on 9/26/2015.
 */
public class Util {

    // Worker thread
    private static final BlockingQueue<Runnable> worker_tasks = new LinkedBlockingQueue<Runnable>();
    private static final ExecutorService worker = new ThreadPoolExecutor(
            1,  // Number of worker threads to run
            1,  // Maximum number of worker threads to run
            1,  // Timeout
            TimeUnit.SECONDS, // Timeout units
            worker_tasks // Queue of runnables
    );
    static boolean inMainThread() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }
    public static void dispatch(Runnable r) {
        worker.execute(r);
    }
}
