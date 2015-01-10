package com.example.ti.ble.common;

/**
 * Created by First on 1/9/2015.
 *
 * I've been wrestling with semaphores to pace BLE reads.  It doesn't seem to work,
 * blocking the thread causes the requests to not fly.
 * Going to try a technique similar to what worked on iOS, which is to string together blocks
 * of anonymous code.
 */
public abstract class Block {
    public abstract void run();
}
