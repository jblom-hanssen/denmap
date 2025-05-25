package com.example.osmparsing.way;

import java.io.Serializable;
import java.util.ArrayList;
import javafx.scene.canvas.GraphicsContext;

public class Way implements Serializable{
    public float[] bbox; // [minLon, maxLon, minLat, maxLat]
    private float[] coords; // flat array of coordinates [lon1, lat1, lon2, lat2.. etc
    public String data;
    public int depth = 0;
    public Node averageWayNode;
    private boolean jump = false;

    public Way(ArrayList<Node> way, String data) {
        this.data = data;

        if (way == null || way.isEmpty()) {
            coords = new float[0];
            bbox = new float[0];
            averageWayNode = new Node(0, 0);
            return;
        }

        // Create the flat coordinate array
        coords = new float[way.size() * 2];

        // Initialize variables for tracking bbox and average
        float totalLat = 0;
        float totalLon = 0;
        float minLon =  Float.MAX_VALUE;
        float maxLon = - Float.MAX_VALUE;
        float minLat =  Float.MAX_VALUE;
        float maxLat = - Float.MAX_VALUE;

        // Process all nodes in a single pass
        int i = 0;
        for (Node node : way) {
            float lon = (float) node.lon;
            float lat = (float) node.lat;

            // Store coordinates in flat array
            coords[i++] = lon;
            coords[i++] = lat;

            // Update bounding box
            if (lat < minLat) minLat = lat;
            if (lat > maxLat) maxLat = lat;
            if (lon < minLon) minLon = lon;
            if (lon > maxLon) maxLon = lon;

            // Accumulate for average calculation
            totalLat += lat;
            totalLon += lon;
        }

        // Set the bounding box
        bbox = new float[]{minLon, maxLon, minLat, maxLat};

        // Calculate average coordinates
        totalLat = totalLat / way.size();
        totalLon = totalLon / way.size();
        averageWayNode = new Node(totalLat, totalLon);
    }

    public void draw(GraphicsContext gc) {
        // Draw the way
        gc.beginPath();
        trace(gc);
        gc.stroke();
    }

    public Way(float[] coords){
        this.coords = coords;
    }

    public void trace(GraphicsContext gc) {
        int i = 0;
        if (jump) {
            gc.moveTo(coords[0], coords[1]);
            i = 2;
        }
        for (; i < coords.length; i += 2) {
            gc.lineTo(coords[i], coords[i+1]);
        }
    }

    public void fill(GraphicsContext gc) {
        if(isClosed()) {
            gc.beginPath();
            // Add outer path
            boolean firstPoint = true;
            for (int i = 0; i < coords.length; i += 2) {
                if (firstPoint) {
                    gc.moveTo(coords[i], coords[i+1]);
                    firstPoint = false;
                } else {
                    gc.lineTo(coords[i], coords[i+1]);
                }
            }
            gc.closePath();
            // Fill outer path
            gc.fill();
        } else {
            draw(gc);
        }

    }

    public void setJump(boolean jump) {
        this.jump = jump;
    }

    public float[] getCoords() {
        return coords;
    }

    public void reverse() {
        for (int i = 0; i < coords.length/2; i += 2) {
            exchange(i, coords.length-2-i);
            exchange(i+1, coords.length-1-i);
        }
    }

    private void exchange(int a, int b) {
        float aux = coords[a];
        coords[a] = coords[b];
        coords[b] = aux;
    }

    public boolean isClosed() {
        // Check if there are at least 2 points
        if (coords.length < 4) {
            return false;
        }

        // Check if first and last point are the same (both x and y)
        return (Math.abs(getFirstX() - getLastX()) < 0.000001 &&
                Math.abs(getFirstY() - getLastY()) < 0.000001);
    }

    public float getFirstX() {
        return coords[0];
    }
    public float getLastX() {
        return coords[coords.length-2];
    }
    public float getFirstY() {
        return coords[1];
    }
    public float getLastY() {
        return coords[coords.length-1];
    }


}
