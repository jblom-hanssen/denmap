
package com.example.osmparsing.way;

import java.io.Serializable;
import java.util.*;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;

public class MultiPolygon implements Serializable {
    protected List<Way> outerWays;
    protected List<Way> innerWays;
    private String type; // The type of the multipolygon (e.g., "forest", "water")

    public MultiPolygon(String type, List<Way> outerWays, List<Way> innerWays) {
        this.type = type;
        this.outerWays = connectWays(outerWays);

        //Coastlines contains empty innerway list so check for it before handiling
        if(innerWays != null && !innerWays.isEmpty()) {
            this.innerWays = connectWays(innerWays);
        }
        else {
            this.innerWays = Collections.emptyList();
        }
    }


    public void draw(GraphicsContext gc) {
        // Draw each outer way as a separate path
        for (Way outerWay : outerWays) {
            gc.beginPath();
            outerWay.trace(gc);
            gc.closePath(); // Make sure path is closed
            gc.stroke();
        }

    }

    public void fill(GraphicsContext gc) {
        // Get the color for this polygon's type
        Color color = StylesUtility.getColor(type);


        // Apply the color
        gc.setFill(color);

        // Fill outer ways
        for (Way outerWay : outerWays) {
            float[] coords = outerWay.getCoords();
            int numPoints = coords.length / 2;

            // Create separate arrays for x and y coordinates
            double[] xPoints = new double[numPoints];
            double[] yPoints = new double[numPoints];

            // Populate the arrays
            for (int i = 0, j = 0; i < coords.length; i += 2, j++) {
                xPoints[j] = coords[i];       // lon values (x)
                yPoints[j] = coords[i + 1];   // lat values (y)
            }

            // Use fillPolygon to fill the polygon
            gc.fillPolygon(xPoints, yPoints, numPoints);
        }

    }


    public void fill2(GraphicsContext gc) {

        // For each polygon component (in case of multi-polygons)
        for (Way outerWay : outerWays) {
            // Create path for this component
            gc.beginPath();

            // Add outer path
            boolean firstPoint = true;
            float[] coords = outerWay.getCoords();
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

        }

        // Draw inner ways (holes) if any
        //if (!innerWays.isEmpty()) {
        //    gc.setFill(Color.TRANSPARENT);
        //    for (Way innerWay : innerWays) {
        //        gc.beginPath();
        //        boolean firstPoint = true;
        //        float[] coords = innerWay.getCoords();
        //        for (int i = 0; i < coords.length; i += 2) {
        //            if (firstPoint) {
        //                gc.moveTo(coords[i], coords[i+1]);
        //                firstPoint = false;
        //            } else {
        //                gc.lineTo(coords[i], coords[i+1]);
        //           }
        //       }
        //        gc.closePath();
        //        gc.fill();
        //        gc.stroke();
    }



    public List<Way> connectWays(List<Way> originalWays) {
        if (originalWays == null || originalWays.isEmpty()) {
            return new ArrayList<>();
        }

        // Create the new list for connected parts
        List<Way> connectedParts = new ArrayList<>();

        // Add the first polyline and remove it immediately from original list
        Way firstWay = originalWays.remove(0);
        connectedParts.add(firstWay);

        // Check if we need to reverse the first way
        if (!originalWays.isEmpty()) {
            Way first = connectedParts.get(0);
            Way second = originalWays.get(0);

            if (first.getFirstX() == second.getFirstX() && first.getFirstY() == second.getFirstY() ||
                    first.getFirstX() == second.getLastX() && first.getFirstY() == second.getLastY()) {
                first.reverse();
            }
        }

        // Process remaining ways directly from original list
        while (!originalWays.isEmpty()) {
            Way last = connectedParts.get(connectedParts.size() - 1);

            // Find a connecting polyline
            Way foundPart = null;
            int foundIndex = -1;

            for (int i = 0; i < originalWays.size(); i++) {
                Way part = originalWays.get(i);

                // Check if this part connects to the last one we added
                if (part.getFirstX() == last.getLastX() && part.getFirstY() == last.getLastY()) {
                    foundPart = part;
                    foundIndex = i;
                    break;
                }
                else if (part.getLastX() == last.getLastX() && part.getLastY() == last.getLastY()) {
                    part.reverse();
                    foundPart = part;
                    foundIndex = i;
                    break;
                }
            }

            // If no connecting part found, just take the first one
            if (foundPart == null) {
                foundPart = originalWays.get(0);
                foundIndex = 0;
            }

            // Set the jump flag to false
            foundPart.setJump(false);

            // Add to connected parts and remove from original list using index
            // (more efficient than removing by object reference)
            connectedParts.add(foundPart);
            originalWays.remove(foundIndex);
        }

        return connectedParts;
    }

    public String getType() {
        return type;
    }

}