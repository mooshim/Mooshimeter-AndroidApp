package com.mooshim.mooshimeter.common;

/**
 * Created by First on 3/31/2016.
 */
public class TemperatureUnitsHelper {
    static float AbsK2C(float K) {
        return (float) (K-273.15);
    }
    static float AbsK2F(float K) {
        return (float) ((K - 273.15)* 1.8000 + 32.00);
    }
    static float AbsC2F(float C) {
        return (float) ((C)* 1.8000 + 32.00);
    }
    static float RelK2F(float C) {
        return (float) ((C)* 1.8000);
    }
}
