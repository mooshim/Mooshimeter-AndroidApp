package com.mooshim.mooshimeter.common;

import java.util.List;

import lecho.lib.hellocharts.model.PointValue;

public interface GraphingActivityInterface {
    /**
     * Redraws the screen
     */
    void refresh();

    /**
     * Add point to a series
     * @param series_n series to which point is to be added
     * @param x x value
     * @param y y value
     */
    void addPoint(int series_n, float x, float y);

    void addPoints(final int series_n, final List<PointValue> new_values);

    /**
     * Set X Axis title
     */
    void setXAxisTitle(String title);

    /**
     * Set series title
     */
    void setYAxisTitle(int series_n, String title);
}
