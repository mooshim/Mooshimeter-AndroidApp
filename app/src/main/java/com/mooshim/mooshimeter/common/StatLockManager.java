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

import android.util.Log;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * Created by First on 10/2/2015.
 */
public class StatLockManager {
    public int stat;
    private Condition con;
    private Lock lock;
    private String name;
    private void dbg(String msg) {
        if(false && name!=null && name.length()>0) {
            Log.d("LOCK:" + name, msg);
        }
    }
    public StatLockManager(Lock l,String dbg_name) {
        name = dbg_name;
        stat = 0;
        lock = l;
        con = l.newCondition();
    }
    public StatLockManager(Lock l) {
        this(l, "");
    }
    public void l() {
        dbg("L");
        lock.lock();
    }
    public void l(int newstat) {
        l();
        stat = newstat;
    }
    public void ul() {
        dbg("UL");
        lock.unlock();
    }
    public void sig() {
        dbg("SIG");
        con.signalAll();
    }
    // returns whether it was interrupted or not
    public boolean awaitMilli(int ms) {
        boolean rval = false;
        try {
            l();
            dbg("AWAIT");
            if(ms!=0) {
                if(!con.await(ms, TimeUnit.MILLISECONDS)) {
                    // The waiting time elapsed!
                    dbg("TIMEOUT");
                    rval=true;
                }
            } else {
                con.await();
            }
            ul();
        } catch (InterruptedException e) {
            if(ms==0) {
                // We should never be interrupted like this...
                dbg("INTERRUPTION!");
                e.printStackTrace();
            }
            rval=true;
        }
        return rval;
    }
    public boolean await() {
        return awaitMilli(0);
    }
}
