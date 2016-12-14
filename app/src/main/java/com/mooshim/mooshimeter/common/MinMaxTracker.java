package com.mooshim.mooshimeter.common;

/**
 * Created by First on 12/14/2016.
 */
public class MinMaxTracker {
    public float min, max;
    public MinMaxTracker(){
        this.clear();
    }
    public boolean process(float val) {
        if(val>max){
            max = val;
            return true;
        }
        if(val<min) {
            min = val;
            return true;
        }
        return false;
    }
    public void clear() {
        min =  Float.MAX_VALUE;
        max = -Float.MAX_VALUE;
    }
}
