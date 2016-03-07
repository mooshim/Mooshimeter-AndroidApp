package com.mooshim.mooshimeter.common;

public interface GraphingActivityInterface {
    /**
     * Redraws the screen
     */
    void refresh();

    /**
     * Set maximum number of points visible on the screen
     * @param maxPoints number of points
     */
    void setNPointOnScreen(int maxPoints);

    /**
     * Add new series to the graph
     * @param title title of the series
     * @return index of the created series
     */
    int addStream(String title);

    /**
     * Add point to a series
     * @param series_n series to which point is to be added
     * @param x x value
     * @param y y value
     */
    void addPoint(int series_n, float x, float y);

    /**
     * Clear all points for a series
     */
    void clearPoints(int series_n);

    /**
     * Set number of axis on the graph
     */
    void setNAxes(int n_axes);

    /**
     * Set X Axis title
     */
    void setXAxisTitle(String title);

    /**
     * Set series title
     */
    void setYAxisTitle(int series_n, String title);
}
