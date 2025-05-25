package com.example.osmparsing.utility;

import com.example.osmparsing.address.AddressHandler;
import com.example.osmparsing.address.OSMAddress;
import com.example.osmparsing.algorithms.AStarSP;
import com.example.osmparsing.algorithms.DirectedEdge;
import com.example.osmparsing.mvc.Model;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RouteHandler {
    private final Model model;

    public RouteHandler(Model model) {
        this.model = model;
    }

    public List<DirectedEdge> findRoute(String startAddressStr, String endAddressStr) {
        // Ensure file-based graph exists
        if (model.getFileBasedGraph() == null || !model.getFileBasedGraph().graphFileExists()) {
            System.err.println("ERROR: No graph file available. Please parse OSM data first.");
            return null;
        }

        FileBasedGraph fileBasedGraph = model.getFileBasedGraph();
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

        // Get node coordinates
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

        // Find nearest graph vertices using file-based graph
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

        if (startVertex < 0 || startVertex >= fileBasedGraph.getVertexCount()) {
            System.out.println("ERROR: Invalid start vertex: " + startVertex);
            return null;
        }
        if (endVertex < 0 || endVertex >= fileBasedGraph.getVertexCount()) {
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

        // Use file-based A* search
        System.out.println("Starting file-based " + model.getTransportMode() + " route search...");

        AStarSP aStar = new AStarSP(fileBasedGraph, startVertex, endVertex, endCoords);

        if (aStar.hasPathTo(endVertex)) {
            List<DirectedEdge> path = new ArrayList<>();
            for (DirectedEdge edge : aStar.pathTo(endVertex)) {
                path.add(edge);
            }
            System.out.println("Path found with " + path.size() + " edges!");
            return path;
        } else {
            // No path found - check connectivity
            System.out.println("No path found between vertices " + startVertex + " and " + endVertex);
            System.out.println("This could mean the vertices are not connected in the road network");

            // Check if vertices have any connections
            try {
                List<DirectedEdge> startEdges = fileBasedGraph.getAdjacentEdges(startVertex);
                List<DirectedEdge> endEdges = fileBasedGraph.getAdjacentEdges(endVertex);

                System.out.println("Start vertex " + startVertex + " has " + startEdges.size() + " outgoing edges");
                System.out.println("End vertex " + endVertex + " has " + endEdges.size() + " outgoing edges");

                if (startEdges.isEmpty()) {
                    System.out.println("WARNING: Start vertex has no connections!");
                }
                if (endEdges.isEmpty()) {
                    System.out.println("WARNING: End vertex has no connections!");
                }
            } catch (IOException e) {
                System.err.println("Error checking vertex connections: " + e.getMessage());
            }

            return null;
        }
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
    private int findAlternativeVertex(int currentVertex, float[] targetCoords, float[] originalCoords) {
        FileBasedGraph fbGraph = model.getFileBasedGraph();

        // First try: look through adjacent vertices
        try {
            for (DirectedEdge e : fbGraph.getAdjacentEdges(currentVertex)) {
                int adjVertex = e.to();
                float[] adjCoords = fbGraph.getCoordinatesForVertex(adjVertex);

                if (adjCoords != null) {
                    // Check if this adjacent vertex is closer to the target than to the original
                    float distToTarget = calculateDistance(adjCoords, targetCoords);
                    float distToOriginal = calculateDistance(adjCoords, originalCoords);

                    if (distToTarget < distToOriginal) {
                        return adjVertex;
                    }
                }
            }
        } catch (IOException ex) {
            System.err.println("Error reading adjacent edges: " + ex.getMessage());
        }

        // Second try: find the second-closest vertex to the target
        float minDist = Float.MAX_VALUE;
        int bestVertex = currentVertex;

        // Only check a subset of vertices to avoid performance issues
        int checkLimit = Math.min(1000, fbGraph.getVertexCount());
        for (int v = 0; v < checkLimit; v++) {
            if (v == currentVertex) continue;

            float[] coords = fbGraph.getCoordinatesForVertex(v);
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

}