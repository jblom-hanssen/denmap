package com.example.osmparsing.utility;

import java.io.Serializable;

public class RoadProperties implements Serializable {
    private final String wayName;  // ADD THIS
    private final String roadType;
    private final int speedLimit;
    private final boolean oneWay;
    private final boolean bicycleAllowed;
    private final boolean carAllowed;

    public RoadProperties(String wayName, String roadType, int speedLimit, boolean oneWay,
                          boolean bicycleAllowed, boolean carAllowed) {
        this.wayName = wayName != null ? wayName : "Unknown road";  // ADD THIS
        this.roadType = roadType;
        this.speedLimit = speedLimit;
        this.oneWay = oneWay;
        this.bicycleAllowed = bicycleAllowed;
        this.carAllowed = carAllowed;
    }
    public String getRoadType() { return roadType; }


    public boolean isAccessibleBy(TransportMode mode) {
        if (mode == TransportMode.CAR) {
            return carAllowed;
        } else if (mode == TransportMode.BICYCLE) {
            return bicycleAllowed;
        }
        return true;
    }
}