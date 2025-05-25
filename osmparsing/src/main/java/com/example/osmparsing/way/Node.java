package com.example.osmparsing.way;

import java.io.Serializable;

public class Node implements Serializable {
    private long id;
    public float lat, lon;

    public Node(float lat, float lon) {
        this(-1, lat, lon); // Default ID to -1 if not specified
    }

    public Node(long id, float lat, float lon) {
        this.id = id;
        this.lat = lat;
        this.lon = lon;
    }

    public long getId() {
        return id;
    }

    public float getLat() {
        return lat;
    }

    public float getLon() {
        return lon;
    }
}