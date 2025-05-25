package com.example.osmparsing.utility;

import com.example.osmparsing.algorithms.DirectedEdge;
import com.example.osmparsing.algorithms.EdgeWeightedDigraph;
import com.example.osmparsing.mvc.Model;

import com.example.osmparsing.way.Way;

import java.util.*;
import java.io.Serializable;

/**
 * Two-Pass Graph Builder - Analyzes intersections first, then builds graph
 * This ensures proper connectivity in the road network
 */
public class GraphBuilder implements Serializable {
    // Data structures for graph construction
    private Map<Long, Integer> nodeToVertex = new HashMap<>();
    private Map<Integer, float[]> vertexCoords = new HashMap<>();
    private EdgeWeightedDigraph graph;
    private int vertexCount = 0;
    private Model model;

    // Track node usage and road data for two-pass approach
    private Map<Long, Integer> nodeUsageCount = new HashMap<>();
    private List<RoadData> roadDataList = new ArrayList<>();
    public Map<Long, float[]> nodeCoordinateCache = new HashMap<>(); // Cache for node coordinates
    private Map<String, RoadProperties> edgeProperties = new HashMap<>(); // Key: "from-to"

    // Statistics
    private int intersectionCount = 0;
    private int deadEndCount = 0;
    private int connectorCount = 0;

    // Inner class to store road data for second pass
    private static class RoadData implements Serializable {
        Way way;
        List<Long> nodeIds;
        String roadType;
        boolean isOneWay;
        RoadProperties properties; // ADD THIS

        RoadData(Way way, List<Long> nodeIds, String roadType, boolean isOneWay, RoadProperties properties) {
            this.way = way;
            this.nodeIds = nodeIds;
            this.roadType = roadType;
            this.isOneWay = isOneWay;
            this.properties = properties; // ADD THIS
        }
    }

    public GraphBuilder(Model model) {
        System.out.println("Initializing Two-Pass Graph Builder");
        this.model = model;
    }

    public EdgeWeightedDigraph initializeGraph(int capacity) {
        // Don't create graph yet - wait for analysis
        this.vertexCount = 0;
        this.nodeToVertex.clear();
        this.vertexCoords.clear();
        this.nodeUsageCount.clear();
        this.roadDataList.clear();
        this.nodeCoordinateCache.clear();
        return null;
    }

    /**
     * First pass: Store road data and count node usage
     */
    public void addRoadWithNodeIds(Way way, List<Long> nodeIds, String roadType, boolean isOneWay,
                                   String wayName, int speedLimit, boolean bicycleAllowed, boolean carAllowed) { // ADD PARAMETERS
        if (!isImportantRoad(roadType) || nodeIds.size() < 2) {
            return;
        }

        // Create road properties with all fields
        RoadProperties properties = new RoadProperties(wayName, roadType, speedLimit,
                isOneWay, bicycleAllowed, carAllowed);

        // Store road data for second pass
        roadDataList.add(new RoadData(way, new ArrayList<>(nodeIds), roadType, isOneWay, properties));

        // Count node usage
        for (long nodeId : nodeIds) {
            nodeUsageCount.merge(nodeId, 1, Integer::sum);
        }
    }

    /**
     * Finalize graph construction using collected data
     */
    public EdgeWeightedDigraph finalizeGraph() {
            System.out.println("=== TWO-PASS GRAPH CONSTRUCTION ===");
            long startTime = System.currentTimeMillis();

            // PASS 1: Analyze intersections
            System.out.println("Pass 1: Analyzing intersections from " + roadDataList.size() + " roads");

            // Debug: Check if we have road data
            if (roadDataList.isEmpty()) {
                System.err.println("ERROR: No road data collected during parsing!");
                return new EdgeWeightedDigraph(1);
            }

            analyzeIntersections();

            // Debug: Check vertex creation
            if (vertexCount == 0) {
                System.err.println("ERROR: No vertices were created!");
                return new EdgeWeightedDigraph(1);
            }

            // Create graph with exact vertex count
            graph = new EdgeWeightedDigraph(vertexCount);
            System.out.println("Created graph with capacity: " + vertexCount);

            // PASS 2: Build edges
            System.out.println("Pass 2: Building edges");
            buildEdges();

        long endTime = System.currentTimeMillis();
        System.out.println("Graph construction completed in " + (endTime - startTime) + "ms");
        System.out.println("Final graph: " + vertexCount + " vertices, " + graph.E() + " edges");

        // Analyze connectivity
        analyzeConnectivity();

        // Clean up temporary data
        roadDataList.clear();
        nodeUsageCount.clear();
        System.gc();

        return graph;
    }

    /**
     * Pass 1: Identify and create vertices for intersections and endpoints
     */
    private void analyzeIntersections() {
        long analysisStart = System.currentTimeMillis();

        System.out.println("Analyzing " + roadDataList.size() + " roads...");
        int roadsProcessed = 0;

        // First, create vertices for all intersections and endpoints
        Set<Long> processedEndpoints = new HashSet<>();

        for (RoadData road : roadDataList) {
            if (road.nodeIds.isEmpty()) continue;

            roadsProcessed++;
            if (roadsProcessed % 100000 == 0) {
                System.out.println("  Processed " + roadsProcessed + "/" + roadDataList.size() +
                        " roads (" + (roadsProcessed * 100 / roadDataList.size()) + "%)");
            }

            // Cache coordinates for all nodes in this road
            float[] coords = road.way.getCoords();
            for (int i = 0; i < road.nodeIds.size() && i * 2 + 1 < coords.length; i++) {
                nodeCoordinateCache.putIfAbsent(road.nodeIds.get(i),
                        new float[]{coords[i * 2], coords[i * 2 + 1]});
            }

            // Always create vertices for endpoints
            long firstNodeId = road.nodeIds.get(0);
            long lastNodeId = road.nodeIds.get(road.nodeIds.size() - 1);

            if (!processedEndpoints.contains(firstNodeId)) {
                createVertexForNode(firstNodeId, road.way, 0);
                processedEndpoints.add(firstNodeId);
                if (nodeUsageCount.get(firstNodeId) == 1) {
                    deadEndCount++;
                }
            }

            if (!processedEndpoints.contains(lastNodeId)) {
                createVertexForNode(lastNodeId, road.way, road.nodeIds.size() - 1);
                processedEndpoints.add(lastNodeId);
                if (nodeUsageCount.get(lastNodeId) == 1) {
                    deadEndCount++;
                }
            }
        }

        System.out.println("Creating vertices for intersections...");
        int intersectionsProcessed = 0;

        // Create vertices for all intersection nodes (usage > 1)
        Set<Long> intersectionNodes = new HashSet<>();
        for (Map.Entry<Long, Integer> entry : nodeUsageCount.entrySet()) {
            if (entry.getValue() > 1) {
                intersectionNodes.add(entry.getKey());
                if (!nodeToVertex.containsKey(entry.getKey())) {
                    // Use cached coordinates
                    float[] coords = nodeCoordinateCache.get(entry.getKey());
                    if (coords != null) {
                        createVertex(entry.getKey(), coords[0], coords[1]);
                        intersectionCount++;
                        intersectionsProcessed++;

                        if (intersectionsProcessed % 10000 == 0) {
                            System.out.println("  Created " + intersectionsProcessed + " intersection vertices");
                        }
                    }
                }
            }
        }

        System.out.println("Adding intermediate vertices for long segments...");
        roadsProcessed = 0;

        // Add intermediate vertices for long segments between intersections
        for (RoadData road : roadDataList) {
            roadsProcessed++;
            if (roadsProcessed % 100000 == 0) {
                System.out.println("  Processing intermediate vertices: " +
                        roadsProcessed + "/" + roadDataList.size());
            }
            addIntermediateVertices(road, intersectionNodes);
        }

        long analysisEnd = System.currentTimeMillis();
        System.out.println("Intersection analysis completed in " + (analysisEnd - analysisStart) + "ms");
        System.out.println("Found " + intersectionCount + " intersections, " +
                deadEndCount + " dead ends, " + connectorCount + " connectors");
        System.out.println("Created " + vertexCount + " vertices");
    }

    /**
     * Add intermediate vertices for long segments
     */
    private void addIntermediateVertices(RoadData road, Set<Long> intersectionNodes) {
        List<Integer> segmentVertices = new ArrayList<>();
        float[] coords = road.way.getCoords();

        // Track which nodes in this way are vertices
        for (int i = 0; i < road.nodeIds.size(); i++) {
            long nodeId = road.nodeIds.get(i);
            if (nodeToVertex.containsKey(nodeId)) {
                segmentVertices.add(i);
            }
        }

        // Add intermediate vertices for long segments
        for (int i = 0; i < segmentVertices.size() - 1; i++) {
            int start = segmentVertices.get(i);
            int end = segmentVertices.get(i + 1);
            int segmentLength = end - start;

            // Add intermediate vertices based on road type and segment length
            int stepSize = isMajorRoad(road.roadType) ? 10 : 20;

            if (segmentLength > stepSize) {
                for (int j = start + stepSize; j < end; j += stepSize) {
                    if (j < road.nodeIds.size() && j * 2 + 1 < coords.length) {
                        long nodeId = road.nodeIds.get(j);
                        if (!nodeToVertex.containsKey(nodeId)) {
                            createVertex(nodeId, coords[j * 2], coords[j * 2 + 1]);
                            connectorCount++;
                        }
                    }
                }
            }
        }
    }

    /**
     * Pass 2: Build edges between vertices
     */
    private void buildEdges() {
        long edgeStart = System.currentTimeMillis();
        int edgeCount = 0;
        int roadsWithVertices = 0;
        int roadsSkipped = 0;
        int edgeCreationFailures = 0;

        System.out.println("Building edges from " + roadDataList.size() + " roads...");
        System.out.println("Total vertices available: " + vertexCount);

        for (RoadData road : roadDataList) {
            List<Integer> wayVertices = new ArrayList<>();

            if (road.nodeIds == null || road.nodeIds.isEmpty()) {
                roadsSkipped++;
                continue;
            }

            // Collect vertices for this way in order
            for (long nodeId : road.nodeIds) {
                Integer vertex = nodeToVertex.get(nodeId);
                if (vertex != null) {
                    wayVertices.add(vertex);
                }
            }

            if (!wayVertices.isEmpty()) {
                roadsWithVertices++;
            }

            if (wayVertices.size() < 2) {
                roadsSkipped++;
                continue;
            }

            // Get base weight multiplier for road type
            float baseWeightMultiplier = getWeightMultiplier(road.roadType);

            // Create edges between consecutive vertices
            for (int i = 0; i < wayVertices.size() - 1; i++) {
                int from = wayVertices.get(i);
                int to = wayVertices.get(i + 1);

                if (from < 0 || from >= graph.V() || to < 0 || to >= graph.V()) {
                    System.err.println("ERROR: Invalid vertex indices: " +
                            from + " -> " + to + " (graph size: " + graph.V() + ")");
                    edgeCreationFailures++;
                    continue;
                }

                if (from == to) {
                    continue; // Skip self-loops
                }

                // CRITICAL: Calculate actual distance between vertices
                float[] fromCoords = vertexCoords.get(from);
                float[] toCoords = vertexCoords.get(to);

                if (fromCoords == null || toCoords == null) {
                    System.err.println("WARNING: Missing coordinates for edge " + from + " -> " + to);
                    continue;
                }

                // Calculate distance in kilometers
                float distance = calculateDistance(fromCoords[0], fromCoords[1],
                        toCoords[0], toCoords[1]);

                // Calculate travel time or cost
                // Weight = distance * road_type_multiplier
                // This gives us a weight that represents "cost" of traversing this edge
                float weight = distance * baseWeightMultiplier;

                // For time-based routing, you could use:
                // float speedKmh = getSpeedForRoadType(road.roadType);
                // float timeHours = distance / speedKmh;
                // float weight = timeHours * 60; // Weight in minutes

                try {
                    graph.addEdge(new DirectedEdge(from, to, weight));
                    edgeCount++;

                    // Store properties for this edge
                    edgeProperties.put(createEdgeKey(from, to), road.properties);

                    if (!road.isOneWay) {
                        graph.addEdge(new DirectedEdge(to, from, weight));
                        edgeCount++;
                        edgeProperties.put(createEdgeKey(to, from), road.properties);
                    }
                } catch (Exception e) {
                    System.err.println("ERROR adding edge: " + e.getMessage());
                    edgeCreationFailures++;
                }
            }
        }

        long edgeEnd = System.currentTimeMillis();
        System.out.println("\n=== EDGE BUILDING SUMMARY ===");
        System.out.println("Edges created: " + edgeCount);
        System.out.println("Time taken: " + (edgeEnd - edgeStart) + "ms");
    }

    /**
     * Create a vertex for a specific node in a way
     */
    private void createVertexForNode(long nodeId, Way way, int nodeIndex) {
        if (nodeToVertex.containsKey(nodeId)) {
            return; // Already created
        }

        float[] coords = way.getCoords();
        if (nodeIndex * 2 + 1 < coords.length) {
            createVertex(nodeId, coords[nodeIndex * 2], coords[nodeIndex * 2 + 1]);
        }
    }

    /**
     * Create a vertex with given coordinates
     */
    private void createVertex(long nodeId, float x, float y) {
        if (nodeToVertex.containsKey(nodeId)) {
            return; // Already exists
        }

        int vertex = vertexCount++;
        nodeToVertex.put(nodeId, vertex);
        vertexCoords.put(vertex, new float[]{x, y});
    }

    /**
     * Analyze graph connectivity
     */
    private void analyzeConnectivity() {
        System.out.println("Analyzing graph connectivity...");

        boolean[] visited = new boolean[vertexCount];
        int components = 0;
        int largestComponent = 0;
        int totalConnected = 0;

        for (int v = 0; v < vertexCount; v++) {
            if (!visited[v]) {
                int componentSize = dfsComponentSize(v, visited);
                components++;
                largestComponent = Math.max(largestComponent, componentSize);
                if (componentSize > 1) {
                    totalConnected += componentSize;
                }
            }
        }

        float connectivityRatio = vertexCount > 0 ?
                (float) largestComponent / vertexCount * 100 : 0;

        System.out.println("Graph connectivity analysis:");
        System.out.println("  - Total vertices: " + vertexCount);
        System.out.println("  - Connected components: " + components);
        System.out.println("  - Largest component size: " + largestComponent);
        System.out.println("  - Connectivity ratio: " + String.format("%.1f%%", connectivityRatio));

        if (connectivityRatio < 50) {
            System.out.println("WARNING: Low connectivity detected! The road network appears fragmented.");
        }
    }

    /**
     * DFS to find component size
     */
    private int dfsComponentSize(int start, boolean[] visited) {
        Stack<Integer> stack = new Stack<>();
        stack.push(start);
        visited[start] = true;
        int size = 1;

        while (!stack.isEmpty()) {
            int v = stack.pop();
            for (DirectedEdge e : graph.adj(v)) {
                int w = e.to();
                if (!visited[w]) {
                    visited[w] = true;
                    stack.push(w);
                    size++;
                }
            }
        }

        return size;
    }

    // Helper methods remain the same
    private boolean isImportantRoad(String roadType) {
        return isMajorRoad(roadType) || isMinorRoad(roadType) ||
                isCycleOrPedestrianRoad(roadType);
    }

    private boolean isMajorRoad(String roadType) {
        return Set.of("motorway", "motorway_link", "trunk", "trunk_link",
                "primary", "primary_link", "secondary", "secondary_link",
                "tertiary", "tertiary_link").contains(roadType);
    }

    private boolean isMinorRoad(String roadType) {
        return Set.of("residentialroad", "unclassifiedhighway", "serviceroad",
                "living_street", "road").contains(roadType);
    }
    private boolean isCycleOrPedestrianRoad(String roadType) {
        // Include all the new types you're parsing
        return Set.of("cycleway", "path", "track", "footway", "pedestrian",
                "bridleway", "steps").contains(roadType);
    }

    public void storeNodeCoordinates(long nodeId, float x, float y) {
        nodeCoordinateCache.put(nodeId, new float[]{x, y});
    }

    public float[] getStoredNodeCoordinates(long nodeId) {
        // First check if this node is a vertex
        Integer vertex = nodeToVertex.get(nodeId);
        if (vertex != null) {
            return vertexCoords.get(vertex);
        }

        // Also check the coordinate cache where ALL nodes are stored
        if (nodeCoordinateCache != null) {
            return nodeCoordinateCache.get(nodeId);
        }

        return null;
    }

    public int getVertexForNodeId(long nodeId) {
        return nodeToVertex.getOrDefault(nodeId, -1);
    }

    public float[] getCoordinatesForVertex(int vertex) {
        return vertexCoords.get(vertex);
    }

    public int getVertexCount() {
        return vertexCount;
    }

    private String createEdgeKey(int from, int to) {
        return from + "-" + to;
    }
    public RoadProperties getEdgeProperties(int from, int to) {
        return edgeProperties.get(createEdgeKey(from, to));
    }
    /**
     * Calculate distance between two points using Haversine formula
     * Returns distance in kilometers
     */
    private float calculateDistance(float x1, float y1, float x2, float y2) {
        // First, convert back from internal coordinates to lat/lon
        // Assuming x is lon*0.56 and y is -lat
        float lon1 = x1 / 0.56f;
        float lat1 = -y1;
        float lon2 = x2 / 0.56f;
        float lat2 = -y2;

        // Haversine formula
        float R = 6371.0f; // Earth's radius in kilometers
        float dLat = FloatMath.toRadians(lat2 - lat1);
        float dLon = FloatMath.toRadians(lon2 - lon1);

        float a = FloatMath.sin(dLat/2) * FloatMath.sin(dLat/2) +
                FloatMath.cos(FloatMath.toRadians(lat1)) *
                        FloatMath.cos(FloatMath.toRadians(lat2)) *
                        FloatMath.sin(dLon/2) * FloatMath.sin(dLon/2);

        float c = 2 * FloatMath.atan2(FloatMath.sqrt(a), FloatMath.sqrt(1-a));

        return R * c;
    }
    /**
     * Get weight multiplier for different road types
     * Lower values = preferred routes
     */
    private float getWeightMultiplier(String roadType) {
        return switch (roadType) {
            // Prefer highways for cars (lower multiplier = preferred)
            case "motorway" -> 0.8f;
            case "motorway_link" -> 0.9f;
            case "trunk" -> 1.0f;
            case "trunk_link" -> 1.1f;
            case "primary" -> 1.2f;
            case "primary_link" -> 1.3f;
            case "secondary" -> 1.5f;
            case "secondary_link" -> 1.6f;
            case "tertiary" -> 1.8f;
            case "tertiary_link" -> 1.9f;
            case "unclassifiedhighway" -> 2.5f;
            case "residentialroad" -> 3.0f;
            case "living_street" -> 4.0f;
            case "serviceroad" -> 4.0f;
            case "cycleway" -> 5.0f;  // High penalty for cars
            case "track" -> 6.0f;
            case "path" -> 10.0f;
            case "footway" -> 20.0f;  // Very high penalty
            default -> 2.5f;
        };
    }


}