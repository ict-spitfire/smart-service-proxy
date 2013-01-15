package eu.spitfire_project.smart_service_proxy.noderegistration;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: olli
 * Date: 20.10.12
 * Time: 17:37
 * To change this template use File | Settings | File Templates.
 */
public class FuzzyRule {
    private final ArrayList<Double> xList = new ArrayList<Double>();
    private final ArrayList<Double> yList = new ArrayList<Double>();
    private double rMax;
    private double rMin;

    /**
     * @return count of points in the rule.
     */
    public int size() {
        if (xList.size() != yList.size()) {
            throw new RuntimeException("X,Y length not consistent");
        }
        return xList.size();
    }

    /**
     * Add a point as the next point.
     * @param x - x value of the point.
     * @param y - y value of the point.
     */
    public void add(Double x, Double y) {
        xList.add(x);
        yList.add(y);
    }

    /**
     * Primitive version of {@link #add(Double, Double)}.
     */
    public void add(double x, double y) {
        xList.add(x);
        yList.add(y);
    }

    /**
     * Set rule max to the given value.
     * @param rMax - rule max value.
     */
    public void setrMax(double rMax) {
        this.rMax = rMax;
    }

    /**
     * @return rule max value.
     */
    public double getrMax() {
        return rMax;
    }

    /**
     * Set rule min to the given value.
     * @param rMin - rule min value.
     */
    public void setrMin(double rMin) {
        this.rMin = rMin;
    }

    /**
     * @return rule min value.
     */
    public double getrMin() {
        return rMin;
    }

    /**
     * @return list of x value of the points in original order.
     */
    public ArrayList<Double> getxList() {
        return xList;
    }

    /**
     * @return  list of y value of the points in original order.
     */
    public ArrayList<Double> getyList() {
        return yList;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("FuzzyRule [rMax=");
        builder.append(rMax);
        builder.append(", rMin=");
        builder.append(rMin);
        builder.append(", size=");
        builder.append(xList.size());
        builder.append("]");
        return builder.toString();
    }
}
