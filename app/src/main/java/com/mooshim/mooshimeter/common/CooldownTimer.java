package com.mooshim.mooshimeter.common;

/**
 * Created by First on 2/12/2016.
 */
public class CooldownTimer {
    public boolean expired=true;
    private Runnable cb = new Runnable() {
        @Override
        public void run() {
            expired=true;
        }
    };
    public void fire(int ms) {
        Util.cancel(cb);
        Util.postDelayed(cb,ms);
        expired = false;
    }
}
