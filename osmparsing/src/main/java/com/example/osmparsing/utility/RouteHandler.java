package com.example.osmparsing.utility;

import com.example.osmparsing.address.AddressHandler;
import com.example.osmparsing.address.OSMAddress;
import com.example.osmparsing.algorithms.AStarSP;
import com.example.osmparsing.algorithms.DirectedEdge;
import com.example.osmparsing.algorithms.EdgeWeightedDigraph;
import com.example.osmparsing.algorithms.TransportAwareAStar;
import com.example.osmparsing.mvc.Model;
import com.example.osmparsing.utility.TransportMode;

import java.util.ArrayList;
import java.util.List;

public class RouteHandler {
    private final Model model;

    public RouteHandler(Model model) {
        this.model = model;
    }

    public List<DirectedEdge> findRoute(String startAddressStr, String endAddressStr) {
        // Ensure graph is built
        if (model.getRoadGraph() == null) {
            model.buildRoadGraph();
        }

        EdgeWeightedDigraph roadGraph = model.getRoadGraph();
        AddressHandler addressHandler = model.getAddressHandler();

        // Find the addresses
        System.out.println("Looking for start address: " + startAddressStr);
        OSMAddress startAddress = findBestMatchingAddress(addressHandler, startAddressStr);
        System.out.println("Looking for end address: " + endAddressStr);
        OSMAddress endAddress = findBestMatchingAddress(addressHandler, endAddressStr);

        if (startAddress == null) {
            System.out.println("ERROR: Start address not found: " + startAddressStr);
            return null;
        }
        if (endAddress == null) {
            System.out.println("ERROR: End address not found: " + endAddressStr);
            return null;
        }

        System.out.println("Found start address: " + startAddress);
        System.out.println("Found end address: " + endAddress);

        // Get node coordinates - these should now work with the fallback
        float[] startCoords = model.getNodeCoordinates(startAddress.getNodeId());
        float[] endCoords = model.getNodeCoordinates(endAddress.getNodeId());

        if (startCoords == null) {
            System.out.println("ERROR: No coordinates for start address (nodeId: " + startAddress.getNodeId() + ")");
            return null;
        }
        if (endCoords == null) {
            System.out.println("ERROR: No coordinates for end address (nodeId: " + endAddress.getNodeId() + ")");
            return null;
        }

        System.out.println("Start coordinates: [" + startCoords[0] + ", " + startCoords[1] + "]");
        System.out.println("End coordinates: [" + endCoords[0] + ", " + endCoords[1] + "]");

        // IMPORTANT: Since addresses might not be vertices, find nearest graph vertices
        int startVertex = model.findNearestVertex(startCoords[0], startCoords[1]);
        int endVertex = model.findNearestVertex(endCoords[0], endCoords[1]);

        if (startVertex == -1) {
            System.out.println("ERROR: Could not find network vertex near start address");
            return null;
        }
        if (endVertex == -1) {
            System.out.println("ERROR: Could not find network vertex near end address");
            return null;
        }

        if (startVertex < 0 || startVertex >= roadGraph.V()) {
            System.out.println("ERROR: Invalid start vertex: " + startVertex);
            return null;
        }
        if (endVertex < 0 || endVertex >= roadGraph.V()) {
            System.out.println("ERROR: Invalid end vertex: " + endVertex);
            return null;
        }

        // Handle case where start and end are the same vertex
        if (startVertex == endVertex) {
            System.out.println("WARNING: Start and end vertices are the same (vertex " + startVertex + ")");

            // Try to find alternative vertices nearby
            endVertex = findAlternativeVertex(startVertex, endCoords, startCoords);

            if (startVertex == endVertex) {
                System.out.println("ERROR: Could not find distinct vertices for the route");
                return null;
            }

            System.out.println("Found alternative end vertex: " + endVertex);
        }

        System.out.println("Routing from vertex " + startVertex + " to " + endVertex);

        // Create array of vertex coordinates for A*
        GraphBuilder builder = model.getGraphBuilder();
        float[][] vertexCoords = new float[roadGraph.V()][];
        for (int v = 0; v < roadGraph.V(); v++) {
            vertexCoords[v] = builder.getCoordinatesForVertex(v);
        }

        // Use transport-aware routing
        System.out.println("Starting " + model.getTransportMode() + " route search...");

        if (model.getTransportMode() == TransportMode.BICYCLE) {
            TransportAwareAStar aStar = new TransportAwareAStar(
                    roadGraph, startVertex, endVertex,
                    model.getTransportMode(), builder,
                    endCoords, vertexCoords
            );

            if (aStar.hasPathTo(endVertex)) {
                List<DirectedEdge> path = new ArrayList<>();
                for (DirectedEdge edge : aStar.pathTo(endVertex)) {
                    path.add(edge);
                }
                System.out.println("Bicycle path found with " + path.size() + " edges!");
                return path;
            }
        } else {
            // Use regular A* for cars
            AStarSP aStar = new AStarSP(roadGraph, startVertex, endVertex, endCoords, vertexCoords);

            if (aStar.hasPathTo(endVertex)) {
                List<DirectedEdge> path = new ArrayList<>();
                for (DirectedEdge edge : aStar.pathTo(endVertex)) {
                    path.add(edge);
                }
                System.out.println("Car path found with " + path.size() + " edges!");
                return path;
            }
        }

        // No path found - add connectivity check
        boolean connected = builder.areConnected(startVertex, endVertex);
        System.out.println("BFS connectivity check: " + (connected ? "Connected" : "Not connected"));

        if (connected) {
            System.out.println("ISSUE DETECTED: Vertices are connected, but A* cannot find a path!");
        } else {
            System.out.println("Vertices are not connected in the road network");

            // Additional debugging: check vertex connections
            System.out.println("Start vertex " + startVertex + " has " +
                    roadGraph.outdegree(startVertex) + " outgoing edges");
            System.out.println("End vertex " + endVertex + " has " +
                    roadGraph.indegree(endVertex) + " incoming edges");
        }

        return null;
    }


    private OSMAddress findBestMatchingAddress(AddressHandler addressHandler, String addressStr) {
        // Try direct lookup first
        OSMAddress address = addressHandler.findAddressByKey(addressStr.toLowerCase());
        if (address != null) return address;

        Iterable<String> matches = addressHandler.findAddressesByPrefix(addressStr.toLowerCase());
        String bestMatch = null;
        float bestMatchScore = 0;

        for (String match : matches) {
            // Simple scoring - longer common prefix is better
            float score = calculateMatchScore(addressStr.toLowerCase(), match);
            if (score > bestMatchScore) {
                bestMatchScore = score;
                bestMatch = match;
            }
        }

        if (bestMatch != null && bestMatchScore > 0.5) { // Threshold for acceptable match
            return addressHandler.findAddressByKey(bestMatch);
        }

        return null;
    }
    private  float calculateMatchScore(String input, String candidate) {
        // Simple scoring based on common characters
        int commonChars = 0;
        for (int i = 0; i < Math.min(input.length(), candidate.length()); i++) {
            if (input.charAt(i) == candidate.charAt(i)) {
                commonChars++;
            } else {
                break; // Stop at first difference
            }
        }
        return ( float) commonChars / Math.max(input.length(), candidate.length());
    }
    // Helper method to find an alternative vertex when start and end map to the same one
    private int findAlternativeVertex(int currentVertex,  float[] targetCoords,  float[] originalCoords) {
        GraphBuilder builder = model.getGraphBuilder();
        EdgeWeightedDigraph graph = model.getRoadGraph();

        // First try: look through adjacent vertices
        for (DirectedEdge e : graph.adj(currentVertex)) {
            int adjVertex = e.to();
            float[] adjCoords = builder.getCoordinatesForVertex(adjVertex);

            if (adjCoords != null) {
                // Check if this adjacent vertex is closer to the target than to the original
               float distToTarget = calculateDistance(adjCoords, targetCoords);
                float distToOriginal = calculateDistance(adjCoords, originalCoords);

                if (distToTarget < distToOriginal) {
                    return adjVertex;
                }
            }
        }

        // Second try: find the second-closest vertex to the target
        float minDist = Float.MAX_VALUE;
        int bestVertex = currentVertex;

        // Only check a subset of vertices to avoid performance issues
        int checkLimit = Math.min(1000, graph.V());
        for (int v = 0; v < checkLimit; v++) {
            if (v == currentVertex) continue;

            float[] coords = builder.getCoordinatesForVertex(v);
            if (coords != null) {
                float dist = calculateDistance(coords, targetCoords);
                if (dist < minDist) {
                    minDist = dist;
                    bestVertex = v;
                }
            }
        }

        return bestVertex;
    }

    private float calculateDistance(float[] coords1, float[] coords2) {
        return FloatMath.sqrt(
                FloatMath.pow(coords1[0] - coords2[0], 2) +
                        FloatMath.pow(coords1[1] - coords2[1], 2)
        );
    }
    public void debugRouteDetails(String startAddressStr, String endAddressStr) {
        System.out.println("\n=== ROUTE DEBUGGING ===");
        System.out.println("From: " + startAddressStr);
        System.out.println("To: " + endAddressStr);

        // Ensure graph is built
        if (model.getRoadGraph() == null) {
            model.buildRoadGraph();
        }

        EdgeWeightedDigraph roadGraph = model.getRoadGraph();
        AddressHandler addressHandler = model.getAddressHandler();

        // Find the addresses
        OSMAddress startAddress = findBestMatchingAddress(addressHandler, startAddressStr);
        OSMAddress endAddress = findBestMatchingAddress(addressHandler, endAddressStr);

        if (startAddress == null || endAddress == null) {
            System.out.println("ERROR: One or both addresses not found");
            return;
        }

        // Get node coordinates
        float[] startCoords = model.getNodeCoordinates(startAddress.getNodeId());
        float[] endCoords = model.getNodeCoordinates(endAddress.getNodeId());

        if (startCoords == null || endCoords == null) {
            System.out.println("ERROR: Could not get coordinates for addresses");
            return;
        }

        System.out.println("Start node ID: " + startAddress.getNodeId());
        System.out.println("Start coordinates: [" + startCoords[0] + ", " + startCoords[1] + "]");
        System.out.println("End node ID: " + endAddress.getNodeId());
        System.out.println("End coordinates: [" + endCoords[0] + ", " + endCoords[1] + "]");

        // Find nearest vertices WITHOUT transforming coordinates
        int startVertex = model.findNearestVertex(startCoords[0], startCoords[1]);
        int endVertex = model.findNearestVertex(endCoords[0], endCoords[1]);

        System.out.println("Start vertex: " + startVertex);
        System.out.println("End vertex: " + endVertex);

        if (startVertex < 0 || endVertex < 0) {
            System.out.println("ERROR: Could not find network vertices");
            return;
        }

        // Check vertex connectivity
        System.out.println("Start vertex connections: " +
                roadGraph.outdegree(startVertex) + " outgoing, " +
                roadGraph.indegree(startVertex) + " incoming");
        System.out.println("End vertex connections: " +
                roadGraph.outdegree(endVertex) + " outgoing, " +
                roadGraph.indegree(endVertex) + " incoming");

        // Get the path
        List<DirectedEdge> route = findRoute(startAddressStr, endAddressStr);

        if (route != null && !route.isEmpty()) {
            System.out.println("Route found with " + route.size() + " edges");

            // Calculate and print detailed route information
            GraphBuilder builder = model.getGraphBuilder();
            float totalDist = 0;

            System.out.println("\nDetailed route segment information:");
            System.out.println("-----------------------------------");

            for (int i = 0; i < route.size(); i++) {
                DirectedEdge edge = route.get(i);
                float[] fromCoords = builder.getCoordinatesForVertex(edge.from());
                float[] toCoords = builder.getCoordinatesForVertex(edge.to());

                float dist = calculateHaversineDistance(
                        fromCoords[0], fromCoords[1],
                        toCoords[0], toCoords[1]);

                totalDist += dist;

                System.out.printf("Edge %d: %d -> %d, Weight=%.5f, Distance=%.3f km\n",
                        i, edge.from(), edge.to(), edge.weight(), dist);
                System.out.printf("  From: [%.6f, %.6f]\n", fromCoords[0], fromCoords[1]);
                System.out.printf("  To:   [%.6f, %.6f]\n", toCoords[0], toCoords[1]);
            }

            System.out.println("\nTotal route distance: " + totalDist + " km");
            System.out.println("Average distance per segment: " + (totalDist / route.size()) + " km");

            // Check if there might be missing segments
            float directDistance = calculateHaversineDistance(
                    startCoords[0], startCoords[1],
                    endCoords[0], endCoords[1]);

            System.out.println("Direct (straight-line) distance: " + directDistance + " km");
            System.out.println("Route/direct ratio: " + (totalDist / directDistance));

            if (totalDist / directDistance < 1.1) {
                System.out.println("WARNING: Route is very close to straight-line distance!");
                System.out.println("This suggests the route may be missing intermediate road segments.");
            }
        } else {
            System.out.println("No route found between these addresses");
        }

        System.out.println("=== END DEBUGGING ===\n");
    }
    private float calculateHaversineDistance(float lon1, float lat1, float lon2, float lat2) {
        // Convert to radians
        float latRad1 = FloatMath.toRadians(lat1);
        float lonRad1 = FloatMath.toRadians(lon1);
        float latRad2 = FloatMath.toRadians(lat2);
        float lonRad2 = FloatMath.toRadians(lon2);

        // Haversine formula
        float dLat = latRad2 - latRad1;
        float dLon = lonRad2 - lonRad1;

        float sinDLatHalf = FloatMath.sin(dLat/2);
        float sinDLonHalf = FloatMath.sin(dLon/2);

        float a = sinDLatHalf * sinDLatHalf +
                FloatMath.cos(latRad1) * FloatMath.cos(latRad2) *
                        sinDLonHalf * sinDLonHalf;

        float c = 2.0f * FloatMath.atan2(FloatMath.sqrt(a), FloatMath.sqrt(1.0f - a));

        // Earth radius in kilometers
        float earthRadiusKm = 6371.0f;
        return earthRadiusKm * c;
    }
}