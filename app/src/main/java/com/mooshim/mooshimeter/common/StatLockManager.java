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
    public StatLockManager(Lock l) {
        stat = 0;
        lock = l;
        con = l.newCondition();
    }
    public void l() {
        lock.lock();
    }
    public void l(int newstat) {
        l();
        stat = newstat;
    }
    public void ul() {
        lock.unlock();
    }
    public void sig() {
        con.signalAll();
    }
    public boolean awaitMilli(int ms) {
        try {
            l();
            if(ms!=0) {
                con.await(ms, TimeUnit.MILLISECONDS);
            } else {
                con.await();
            }
            ul();
        } catch (InterruptedException e) {
            if(ms==0) {
                // We should never be interrupted like this...
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }
    public void await() {
        awaitMilli(0);
    }
}
