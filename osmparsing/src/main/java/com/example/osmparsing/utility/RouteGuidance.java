package com.example.osmparsing.utility;

import com.example.osmparsing.algorithms.DirectedEdge;
import com.example.osmparsing.algorithms.EdgeWeightedDigraph;
import javafx.geometry.Pos;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates practical turn-by-turn directions for a calculated route
 */
public class RouteGuidance {
    private final List<DirectedEdge> route;
    private final GraphBuilder graphBuilder;
    private final EdgeWeightedDigraph roadGraph;
    private static final float EARTH_RADIUS_KM = 6371.0f; // Earth radius in kilometers

    // Thresholds for navigation decisions
    private static final float TURN_THRESHOLD = 30.0f;           // Minimum angle change to consider a turn significant
    private static final float MIN_SEGMENT_DISTANCE = 0.1f;      // Minimum distance (km) for a navigation segment
    private static final float INTERSECTION_IGNORE_DISTANCE = 0.05f; // Distance (km) to ignore intersections after a turn

    // Different turn types
    public enum TurnType {
        STRAIGHT, SLIGHT_RIGHT, RIGHT, SHARP_RIGHT, SLIGHT_LEFT, LEFT, SHARP_LEFT, U_TURN
    }

    // Class to represent a single navigation step
    public static class RouteStep {
        private final TurnType turnType;
        private final float distance; // in kilometers
        private final float bearing;

        public RouteStep(TurnType turnType, float distance, float bearing) {
            this.turnType = turnType;
            this.distance = distance;
            this.bearing = bearing;
        }

        @Override
        public String toString() {
            String direction = formatTurnType(turnType);
            String distanceStr = formatDistance(distance);

            if (turnType == TurnType.STRAIGHT) {
                return "Continue straight for " + distanceStr;
            } else {
                return "Turn " + direction + " and continue for " + distanceStr;
            }
        }

        private String formatTurnType(TurnType type) {
            switch (type) {
                case SLIGHT_RIGHT: return "slight right";
                case RIGHT: return "right";
                case SHARP_RIGHT: return "sharp right";
                case SLIGHT_LEFT: return "slight left";
                case LEFT: return "left";
                case SHARP_LEFT: return "sharp left";
                case U_TURN: return "around (U-turn)";
                default: return "straight";
            }
        }

        private String formatDistance(float distanceKm) {
            if (distanceKm < 0.1) {
                // Convert to meters for short distances
                int meters = (int) Math.round(distanceKm * 1000);
                return meters + " meters";
            } else {
                // Use kilometers with one decimal place
                return String.format("%.1f km", distanceKm);
            }
        }
    }

    public RouteGuidance(List<DirectedEdge> route, GraphBuilder graphBuilder, EdgeWeightedDigraph roadGraph) {
        this.route = route;
        this.graphBuilder = graphBuilder;
        this.roadGraph = roadGraph;
    }

    /**
     * Generate practical turn-by-turn directions with consolidated segments
     * @return A list of navigation steps
     */
    public List<RouteStep> generateDirections() {
        List<RouteStep> steps = new ArrayList<>();

        if (route == null || route.isEmpty()) {
            return steps;
        }

        // Process the edges to create meaningful segments
        List<Segment> segments = createSegments();

        // Convert segments to route steps
        for (Segment segment : segments) {
            steps.add(new RouteStep(segment.turnType, segment.distance, segment.bearing));
        }

        return steps;
    }

    // Internal class to represent a route segment
    private class Segment {
        TurnType turnType;
        float distance;
        float bearing;

        Segment(TurnType turnType, float distance, float bearing) {
            this.turnType = turnType;
            this.distance = distance;
            this.bearing = bearing;
        }
    }

    /**
     * Create meaningful route segments from the raw edges
     */
    private List<Segment> createSegments() {
        List<Segment> segments = new ArrayList<>();

        if (route.isEmpty()) {
            return segments;
        }

        // Variables to track the current segment
        TurnType currentTurnType = TurnType.STRAIGHT;
        float accumulatedDistance = 0;
        float currentBearing = calculateBearing(route.get(0));
        float lastTurnDistance = 0;

        // Initialize with the first edge
        accumulatedDistance += calculateDistance(route.get(0));

        // Process remaining edges
        for (int i = 1; i < route.size(); i++) {
            DirectedEdge edge = route.get(i);
            float newBearing = calculateBearing(edge);
            float edgeDistance = calculateDistance(edge);

            // Calculate the angle between segments
            float angleDiff = calculateAngleDifference(currentBearing, newBearing);
            TurnType turnType = determineTurnType(angleDiff);

            // Is this a significant direction change?
            boolean isSignificantTurn = Math.abs(angleDiff) > TURN_THRESHOLD;

            // Is this a major intersection? (excluding those too close to recent turns)
            boolean isMajorIntersection = false;
            int currentVertex = route.get(i-1).to();

            if (accumulatedDistance - lastTurnDistance > INTERSECTION_IGNORE_DISTANCE) {
                // Count outgoing edges from this vertex
                int connections = roadGraph.outdegree(currentVertex);
                isMajorIntersection = connections >= 4; // Only consider major intersections (4+ ways)
            }

            // Decide if we need a new segment
            if (isSignificantTurn || isMajorIntersection) {
                // Only create a segment if we've traveled a meaningful distance
                if (accumulatedDistance >= MIN_SEGMENT_DISTANCE) {
                    segments.add(new Segment(currentTurnType, accumulatedDistance, currentBearing));
                }

                // Start a new segment
                currentTurnType = turnType;
                accumulatedDistance = edgeDistance;
                currentBearing = newBearing;
                lastTurnDistance = 0;
            } else {
                // Continue the current segment
                accumulatedDistance += edgeDistance;
            }

            lastTurnDistance += edgeDistance;
        }

        // Add the final segment if it has meaningful distance
        if (accumulatedDistance >= MIN_SEGMENT_DISTANCE) {
            segments.add(new Segment(currentTurnType, accumulatedDistance, currentBearing));
        }

        // Further consolidate similar segments where possible
        return consolidateSegments(segments);
    }

    /**
     * Consolidate consecutive segments that have similar directions
     */
    private List<Segment> consolidateSegments(List<Segment> segments) {
        if (segments.size() <= 1) {
            return segments;
        }

        List<Segment> consolidated = new ArrayList<>();
        Segment current = segments.get(0);

        for (int i = 1; i < segments.size(); i++) {
            Segment next = segments.get(i);

            // If both segments are straight or have the same turn type, combine them
            if ((current.turnType == TurnType.STRAIGHT && next.turnType == TurnType.STRAIGHT) ||
                    (current.turnType == next.turnType)) {
                current.distance += next.distance;
            } else {
                consolidated.add(current);
                current = next;
            }
        }

        // Add the last segment
        consolidated.add(current);

        return consolidated;
    }

    /**
     * Calculate the distance of an edge in kilometers
     */
    private float calculateDistance(DirectedEdge edge) {
        float[] fromCoords = graphBuilder.getCoordinatesForVertex(edge.from());
        float[] toCoords = graphBuilder.getCoordinatesForVertex(edge.to());

        if (fromCoords == null || toCoords == null) {
            return 0.0F;
        }

        float fromLon = (float) (fromCoords[0] / 0.56);
        float fromLat = -fromCoords[1];

        float toLon = (float) (toCoords[0] / 0.56);
        float toLat = -toCoords[1];

        return haversineDistance(fromLat, fromLon, toLat, toLon);
    }

    /**
     * Calculate the bearing (direction) of an edge in degrees (0-360)
     */
    private float calculateBearing(DirectedEdge edge) {
        float[] fromCoords = graphBuilder.getCoordinatesForVertex(edge.from());
        float[] toCoords = graphBuilder.getCoordinatesForVertex(edge.to());

        if (fromCoords == null || toCoords == null) {
            return 0.0F;
        }

        float fromLon = (float) (fromCoords[0] / 0.56);
        float fromLat = -fromCoords[1];

        float toLon = (float) (toCoords[0] / 0.56);
        float toLat = -toCoords[1];

        return calculateBearing(fromLat, fromLon, toLat, toLon);
    }

    /**
     * Calculate Haversine distance between two lat/lon points
     */
    private float haversineDistance(float lat1, float lon1, float lat2, float lon2) {
        float latRad1 = FloatMath.toRadians(lat1);
        float lonRad1 = FloatMath.toRadians(lon1);
        float latRad2 = FloatMath.toRadians(lat2);
        float lonRad2 = FloatMath.toRadians(lon2);

        float dLat = latRad2 - latRad1;
        float dLon = lonRad2 - lonRad1;

        float sinDLatHalf = FloatMath.sin(dLat/2);
        float sinDLonHalf = FloatMath.sin(dLon/2);

        float a = sinDLatHalf * sinDLatHalf +
                FloatMath.cos(latRad1) * FloatMath.cos(latRad2) *
                        sinDLonHalf * sinDLonHalf;

        float c = 2.0f * FloatMath.atan2(FloatMath.sqrt(a), FloatMath.sqrt(1.0f-a));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Calculate the initial bearing from point 1 to point 2
     */
    private float calculateBearing(float lat1, float lon1, float lat2, float lon2) {
        float latRad1 = FloatMath.toRadians(lat1);
        float lonRad1 = FloatMath.toRadians(lon1);
        float latRad2 = FloatMath.toRadians(lat2);
        float lonRad2 = FloatMath.toRadians(lon2);

        float dLon = lonRad2 - lonRad1;

        float y = FloatMath.sin(dLon) * FloatMath.cos(latRad2);
        float x = FloatMath.cos(latRad1) * FloatMath.sin(latRad2) -
                FloatMath.sin(latRad1) * FloatMath.cos(latRad2) * FloatMath.cos(dLon);

        float bearingRad = FloatMath.atan2(y, x);
        float bearingDeg = FloatMath.toDegrees(bearingRad);

        return (bearingDeg + 360.0f) % 360.0f;
    }

    /**
     * Calculate the difference between two bearings, accounting for 0/360 wrapping
     */
    private float calculateAngleDifference(float bearing1, float bearing2) {
        float diff = bearing2 - bearing1;

        if (diff > 180.0f) {
            diff -= 360.0f;
        } else if (diff < -180.0f) {
            diff += 360.0f;
        }

        return diff;
    }

    /**
     * Determine the type of turn based on the angle difference
     */
    private TurnType determineTurnType(float angleDiff) {
        if (Math.abs(angleDiff) <= TURN_THRESHOLD) {
            return TurnType.STRAIGHT;
        } else if (angleDiff > 0) {
            // Right turns
            if (angleDiff < 45) return TurnType.SLIGHT_RIGHT;
            else if (angleDiff < 100) return TurnType.RIGHT;
            else if (angleDiff < 160) return TurnType.SHARP_RIGHT;
            else return TurnType.U_TURN;
        } else {
            // Left turns
            if (angleDiff > -45) return TurnType.SLIGHT_LEFT;
            else if (angleDiff > -100) return TurnType.LEFT;
            else if (angleDiff > -160) return TurnType.SHARP_LEFT;
            else return TurnType.U_TURN;
        }
    }

    /**
     * Calculate the total distance of the route in kilometers
     */
    public float calculateTotalDistance() {
        float totalDistance = 0.0F;

        for (DirectedEdge edge : route) {
            totalDistance += calculateDistance(edge);
        }

        return totalDistance;
    }

    /**
     * Print the route guidance to the console
     */
    public void printRoute() {
        List<RouteStep> steps = generateDirections();
        float totalDistance = calculateTotalDistance();

        System.out.println("\nROUTE GUIDANCE");
        System.out.println("=============");
        System.out.println("Total distance: " + String.format("%.2f km", totalDistance));
        System.out.println("Number of instructions: " + steps.size());
        System.out.println();

        for (int i = 0; i < steps.size(); i++) {
            System.out.println((i+1) + ". " + steps.get(i));
        }

        System.out.println("\nYou have reached your destination.");
    }

    //Gets used in UIElements
    public VBox routeToText(){
        List<RouteStep> steps = generateDirections();
        float totalDistance = calculateTotalDistance();

        VBox vBox = new VBox(5);
        vBox.setFillWidth(true);
        vBox.setAlignment(Pos.CENTER);
        double estimatedLineHeight = 20; // Approximate height per line in pixels
        double padding = 10; // Additional padding

        Text text1 = new Text("ROUTE GUIDANCE");
        Text text2 = new Text("Total distance: " + String.format("%.2f km", totalDistance));
        Text text3 = new Text("Number of instructions: " + steps.size() + "\n");
        vBox.getChildren().addAll(text1, text2, text3);

        // Iterates through the steps and adds them to the vBox
        for (int i = 0; i < steps.size(); i++) {
            String string = (i+1) + ". " + steps.get(i).toString()+ "\n"; //newline
            Text text = new Text(string);
            vBox.getChildren().add(text);
        }

        // Calculate total height: (number of items × line height) + spacing
        vBox.setPrefHeight(steps.size() * estimatedLineHeight + (steps.size() - 1) * vBox.getSpacing() + padding);

        return vBox;
    }
}