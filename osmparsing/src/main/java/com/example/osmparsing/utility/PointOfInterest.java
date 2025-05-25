package com.example.osmparsing.utility;

public class PointOfInterest {
    public final double lat;
    public final double lon;
    public final String label;

    public PointOfInterest(double lat, double lon, String label) {
        this.lat = lat;
        this.lon = lon;
        this.label = label;
    }
}
