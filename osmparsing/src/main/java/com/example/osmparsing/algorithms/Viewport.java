package com.example.osmparsing.algorithms;


import javafx.scene.canvas.Canvas;

import java.io.Serializable;

public class Viewport implements Serializable {
    public float offsetX = 0; // pan X (in lon)
    public float offsetY = 0; // pan Y (in lat)
    public float scale = 1.0f; // zoom level (1.0 = 1:1)

    public float lonMin, lonMax, latMin, latMax; // visible world coords
    float screenWidth, screenHeight; // canvas size in pixels

    public Viewport(float lonMin, float lonMax, float latMin, float latMax, float screenWidth, float screenHeight) {
        this.lonMin = lonMin*0.56f;
        this.lonMax = lonMax*0.56f;
        this.latMin = -latMin;
        this.latMax = -latMax;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    public boolean isBBoxVisibleWithBuffer(float[] bbox, float bufferFactor) {
        // bbox = [minLon, maxLon, minLat, maxLat]
        if (bbox == null) return false;

        // Calculate expanded bounds using the correct variable names
        float expandedLonMin = lonMin - (lonMax - lonMin) * bufferFactor;
        float expandedLonMax = lonMax + (lonMax - lonMin) * bufferFactor;
        float expandedLatMin = latMin - (latMax - latMin) * bufferFactor;
        float expandedLatMax = latMax + (latMax - latMin) * bufferFactor;

        // Check using expanded bounds (using same logic as original method)
        return !(bbox[1] < expandedLonMin ||  // bbox maxLon left of expanded view
                bbox[0] > expandedLonMax ||  // bbox minLon right of expanded view
                bbox[3] > expandedLatMin ||  // bbox maxLat below expanded view
                bbox[2] < expandedLatMax);   // bbox minLat above expanded view
    }

    // Convert lon/lat to screen pixel coordinates
    public float[] worldToScreen(float lon, float lat) {
        float x = (lon - lonMin) / (lonMax - lonMin) * screenWidth;
        float y = (latMax - lat) / (latMax - latMin) * screenHeight;
        return new float[]{x, y};
    }

    // Convert screen pixel coordinates to lon/lat
    public float[] screenToWorld(float x, float y) {
        float lon = lonMin + (x / screenWidth) * (lonMax - lonMin);
        float lat = latMax - (y / screenHeight) * (latMax - latMin);
        return new float[]{lon, lat};
    }

    public boolean isBBoxVisible(float[] bbox) {
        // bbox = [minLon, maxLon, minLat, maxLat]
        if (bbox == null) return false;

        //System.out.printf("Viewport: lon %.4f - %.4f, lat %.4f - %.4f%n", lonMin, lonMax, latMin, latMax);
        //System.out.println("Way bbox: " + Arrays.toString(bbox));

        return !(bbox[1] < lonMin ||  // bbox maxLon left of view
                bbox[0] > lonMax ||  // bbox minLon right of view
                bbox[3] > latMin ||  // bbox maxLat below view
                bbox[2] < latMax);   // bbox minLat above view
    }

    public void updateBounds(Canvas canvas) {
        float[] bounds = getLonLatBounds((float)canvas.getWidth(), (float)canvas.getHeight());
        lonMin = bounds[0];
        lonMax = bounds[1];
        latMin = bounds[2];
        latMax = bounds[3];
    }




    public float[] getLonLatBounds(float canvasWidth, float canvasHeight) {
        float lonMin = lonFromScreen(0);
        float lonMax = lonFromScreen(canvasWidth);
        float latMax = latFromScreen(0);
        float latMin = latFromScreen(canvasHeight); // canvas Y increases downward

        return new float[]{lonMin, lonMax, latMin, latMax};
    }

    // Convert lon/lat to screen pixels
    public float screenX(float lon) {
        return (lon - offsetX) * scale;
    }

    public float screenY(float lat) {
        return (lat - offsetY) * scale;
    }

    // Convert screen pixels back to lon/lat
    public float lonFromScreen(float screenX) {
        return screenX / scale + offsetX;
    }

    public float latFromScreen(float screenY) {
        return screenY / scale + offsetY;
    }

    public void updateScreenSize(float width, float height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }


}
