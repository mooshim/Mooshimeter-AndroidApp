package com.mooshim.mooshimeter.common;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by First on 3/30/2016.
 */
public class Chooser<ValueType> {
    private List<ValueType> l = new ArrayList<>();
    private Integer chosen_key = 0;
    public void add(ValueType p) {
        l.add(p);
    }
    public ValueType get(Integer k) {
        return l.get(k);
    }
    public void clear() {
        l.clear();
    }
    public ValueType choose(Integer k) {
        chosen_key = k;
        return getChosen();
    }
    public ValueType choose(ValueType v) {
        if(l.contains(v)) {
            chosen_key = l.indexOf(v);
        } else {
            Log.e("CHOOSER","Tried to choose a value not in the list!");
        }
        return getChosen();
    }
    public Integer getChosenI() {
        return chosen_key;
    }
    public boolean isChosen(Integer k) {
        return chosen_key.equals(k);
    }
    public boolean isChosen(ValueType v) {
        return getChosen().equals(v);
    }
    public ValueType getChosen() {
        return l.get(chosen_key);
    }
    public List<ValueType> getChoices() {
        return l;
    }
    public List<String> getChoiceNames() {
        return Util.stringifyCollection((Collection<Object>) l);
    }
    public int size() {
        return l.size();
    }
    public ValueType getChoiceBelow() {
        int c = chosen_key>0?chosen_key-1:0;
        return l.get(c);
    }
}
