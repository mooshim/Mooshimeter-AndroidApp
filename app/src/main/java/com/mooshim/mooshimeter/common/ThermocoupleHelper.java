package com.mooshim.mooshimeter.common;

import android.util.Log;

/**
 * Created by First on 3/26/2016.
 */
public class ThermocoupleHelper {
    // All coefficients from Omega's datasheet:
    // ITS-90 Thermocouple Direct and Inverse Polynomials
    // https://www.omega.com/temperature/Z/pdf/z198-201.pdf
    public static class helper {
        final double coefficients[];
        final double low,high;
        public helper(double c[], double low, double high) {
            coefficients=c;
            this.low=low;
            this.high=high;
        }
        public double voltsToDegC(double v) {
            double uv = v*1e6;
            double out = 0.0;
            int pow = 0;
            if(coefficients.length != 10) {
                Log.w("THERMO", "Coefficients vector is too short, result might be inaccurate");
            }
            for(double c:coefficients) {
                out += c*Math.pow(uv,pow);
                pow++;
            }
            if(out < low || out > high) {
                Log.w("THERMO", "Using polynomial fit outside of its recommended range, result might be inaccurate");
            }
            return out;
        }
    }
    public static final helper J = new helper(new double[]{ // TODO: ADD TERMS
            0.0,
            1.978425e-2,
            -2.001204e-7,
            1.036969e-11,
            -2.549687e-16
    },0,760);
    public static final helper K = new helper(new double[]{ // TODO: REWARD gerischer@helmholtz-berlin.de
            0.0,
            2.508355e-2,
            7.860106e-8,
            -2.503131e-10,
            8.315270e-14,
            -1.228034e-17,
            9.804036e-22,
            -4.413030e-26,
            1.057734e-30,
            -1.052755e-35,
    },0,500);
    public static final helper T = new helper(new double[]{ // TODO: ADD TERMS
            0.0,
            2.592800e-2,
            -7.602961e-7,
            4.637791e-11,
            -2.165394e-15,
    },0,400);
    public static final helper E = new helper(new double[]{ // TODO: ADD TERMS
            0.0,
            1.7057035e-2,
            -2.3301759e-7,
            6.5435585e-12,
            -7.3562749e-17,
    },0,1000);
    public static final helper N = new helper(new double[]{ // TODO: ADD TERMS
            0.0,
            3.8783277e-2,
            -1.1612344e-6,
            6.9525655e-11,
            -3.0090077e-15,
    },0,600);
    public static final helper B = new helper(new double[]{ // TODO: ADD TERMS
            9.842332e1,
            6.997150e-1,
            -8.4765304e-4,
            1.0052644e-6,
            -83345952e-10,
    },250,700);
    public static final helper R = new helper(new double[]{ // TODO: ADD TERMS
            0.0,
            1.8891380e-1,
            -9.3835290e-5,
            1.3068619e-7,
            -2.2703580e-10,
            3.5145659e-13,
    },-50,250);
    public static final helper S = new helper(new double[]{ // TODO: ADD TERMS
            0.0,
            1.84949460e-1,
            -8.00504062e-5,
            1.02237430e-7,
            -1.52248592e-10,
            1.88821343e-13,
    },-50,250);
}
