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
    private Map<Integer, Long> vertexToNodeId = new HashMap<>();
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
        Way way;  // ADD THIS - Store reference to the Way object
        List<Long> nodeIds;
        String roadType;
        boolean isOneWay;
        RoadProperties properties;

        RoadData(Way way, List<Long> nodeIds, String roadType, boolean isOneWay, RoadProperties properties) {
            this.way = way;  // ADD THIS
            this.nodeIds = nodeIds;
            this.roadType = roadType;
            this.isOneWay = isOneWay;
            this.properties = properties;
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
                                   String wayName, int speedLimit, boolean bicycleAllowed, boolean carAllowed) {
        if (!isImportantRoad(roadType) || nodeIds.size() < 2) {
            return;
        }

        // Create road properties with all fields
        RoadProperties properties = new RoadProperties(wayName, roadType, speedLimit,
                isOneWay, bicycleAllowed, carAllowed);

        // Store road data for second pass - INCLUDE THE WAY OBJECT
        roadDataList.add(new RoadData(way, new ArrayList<>(nodeIds), roadType, isOneWay, properties));

        // Count node usage
        for (long nodeId : nodeIds) {
            nodeUsageCount.merge(nodeId, 1, Integer::sum);
        }

        // Debug logging
        if (roadDataList.size() <= 5 || roadDataList.size() % 1000 == 0) {
            System.out.println("Added road #" + roadDataList.size() + ": " + roadType +
                    " with " + nodeIds.size() + " nodes");
        }
    }
    private void createVertexIfNeeded(long nodeId, Way way, int nodeIndex) {
        if (!nodeToVertex.containsKey(nodeId)) {
            float[] coords = getNodeCoordinates(nodeId, way, nodeIndex);
            if (coords != null) {
                createVertex(nodeId, coords[0], coords[1]);
            }
        }
    }

    private float[] getNodeCoordinates(long nodeId, Way way, int nodeIndex) {
        // Try to get from way first
        if (way != null && nodeIndex >= 0) {
            float[] wayCoords = way.getCoords();
            if (nodeIndex * 2 + 1 < wayCoords.length) {
                return new float[]{wayCoords[nodeIndex * 2], wayCoords[nodeIndex * 2 + 1]};
            }
        }

        // Fallback to stored coordinates
        return nodeCoordinateCache.get(nodeId);
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

        // Get pre-identified intersection nodes from Model
        Set<Long> modelIntersections = model.getIntersectionNodes();
        System.out.println("Using " + modelIntersections.size() + " pre-identified intersection nodes");

        // Create vertices for all intersection nodes that appear in our roads
        Set<Long> usedIntersections = new HashSet<>();

        for (RoadData road : roadDataList) {
            if (road.nodeIds == null || road.nodeIds.isEmpty()) continue;

            // Create vertices for first and last nodes (endpoints)
            long firstNodeId = road.nodeIds.get(0);
            long lastNodeId = road.nodeIds.get(road.nodeIds.size() - 1);

            // Always create vertices for endpoints
            createVertexIfNeeded(firstNodeId, road);
            createVertexIfNeeded(lastNodeId, road);

            // Create vertices for intersection nodes along this road
            for (Long nodeId : road.nodeIds) {
                if (modelIntersections.contains(nodeId)) {
                    createVertexIfNeeded(nodeId, road);
                    usedIntersections.add(nodeId);
                }
            }
        }

        intersectionCount = usedIntersections.size();

        System.out.println("Created " + vertexCount + " vertices:");
        System.out.println("  - " + intersectionCount + " intersection vertices");
        System.out.println("  - " + (vertexCount - intersectionCount) + " endpoint vertices");

        long analysisEnd = System.currentTimeMillis();
        System.out.println("Intersection analysis completed in " + (analysisEnd - analysisStart) + "ms");
    }
    private void createVertexIfNeeded(long nodeId, RoadData road) {
        if (nodeToVertex.containsKey(nodeId)) {
            return; // Already created
        }

        // Try to get coordinates from the road's way
        float[] coords = null;

        // First try to get from nodeCoordinateCache
        coords = nodeCoordinateCache.get(nodeId);

        // If not found and we have a way, try to extract from way
        if (coords == null && road.way != null) {
            int nodeIndex = road.nodeIds.indexOf(nodeId);
            if (nodeIndex >= 0) {
                float[] wayCoords = road.way.getCoords();
                if (nodeIndex * 2 + 1 < wayCoords.length) {
                    coords = new float[]{wayCoords[nodeIndex * 2], wayCoords[nodeIndex * 2 + 1]};
                    // Cache for future use
                    nodeCoordinateCache.put(nodeId, coords);
                }
            }
        }

        if (coords != null) {
            createVertex(nodeId, coords[0], coords[1]);
        } else {
            System.err.println("WARNING: No coordinates found for node " + nodeId);
        }
    }
    /**
     * Add intermediate vertices for long segments
     */
    private void addIntermediateVertices(RoadData road, Set<Long> intersectionNodes) {
        return;
    }

    /**
     * Pass 2: Build edges between vertices
     */
    /**
     * Pass 2: Build edges between vertices
     */
    private void buildEdges() {
        long edgeStart = System.currentTimeMillis();
        int edgeCount = 0;
        int roadsProcessed = 0;

        System.out.println("Building edges from " + roadDataList.size() + " roads...");
        System.out.println("Graph has " + graph.V() + " vertices");

        for (RoadData road : roadDataList) {
            roadsProcessed++;

            if (road.nodeIds == null || road.nodeIds.size() < 2) {
                continue;
            }

            // Debug first few roads
            if (roadsProcessed <= 5) {
                System.out.println("Processing road " + roadsProcessed + ": " +
                        road.roadType + " with " + road.nodeIds.size() + " nodes");
            }

            // Create a list of vertices for this road (only intersection/endpoints)
            List<Integer> roadVertices = new ArrayList<>();
            List<Float> segmentDistances = new ArrayList<>();

            // Collect vertices along this road
            for (int i = 0; i < road.nodeIds.size(); i++) {
                long nodeId = road.nodeIds.get(i);
                Integer vertex = nodeToVertex.get(nodeId);

                // Add vertex if it exists (intersection or endpoint)
                if (vertex != null) {
                    // Calculate distance from last vertex
                    if (!roadVertices.isEmpty() && i > 0) {
                        float distance = calculateSegmentDistance(road,
                                roadVertices.size() > 0 ? findNodeIndex(road, roadVertices.get(roadVertices.size()-1)) : 0,
                                i);
                        segmentDistances.add(distance);
                    }
                    roadVertices.add(vertex);
                }
            }

            // Debug vertex collection
            if (roadsProcessed <= 5) {
                System.out.println("  Found " + roadVertices.size() + " vertices on this road");
            }

            // Create edges between consecutive vertices
            for (int i = 0; i < roadVertices.size() - 1; i++) {
                int fromVertex = roadVertices.get(i);
                int toVertex = roadVertices.get(i + 1);

                if (fromVertex == toVertex) {
                    continue; // Skip self-loops
                }

                // Calculate weight (distance or time-based)
                float distance = (i < segmentDistances.size()) ? segmentDistances.get(i) :
                        calculateDirectDistance(fromVertex, toVertex);

                if (distance <= 0) {
                    System.err.println("WARNING: Zero or negative distance between vertices " +
                            fromVertex + " and " + toVertex);
                    distance = 0.001f; // Minimum distance
                }

                float weight = distance * getWeightMultiplier(road.roadType);

                // Create edge(s)
                try {
                    DirectedEdge edge = new DirectedEdge(fromVertex, toVertex, weight);
                    graph.addEdge(edge);
                    edgeCount++;

                    // Store properties
                    String edgeKey = createEdgeKey(fromVertex, toVertex);
                    if (road.properties != null) {
                        edgeProperties.put(edgeKey, road.properties);
                    }

                    // Add reverse edge if not one-way
                    if (!road.isOneWay) {
                        DirectedEdge reverseEdge = new DirectedEdge(toVertex, fromVertex, weight);
                        graph.addEdge(reverseEdge);
                        edgeCount++;

                        String reverseKey = createEdgeKey(toVertex, fromVertex);
                        if (road.properties != null) {
                            edgeProperties.put(reverseKey, road.properties);
                        }
                    }

                    // Debug first few edges
                    if (edgeCount <= 10) {
                        System.out.println("Created edge: " + fromVertex + " -> " + toVertex +
                                " (weight: " + weight + ", distance: " + distance + "km)");
                    }

                } catch (Exception e) {
                    System.err.println("ERROR creating edge: " + e.getMessage());
                }
            }
        }

        long edgeEnd = System.currentTimeMillis();
        System.out.println("\n=== EDGE BUILDING SUMMARY ===");
        System.out.println("Processed " + roadsProcessed + " roads");
        System.out.println("Created " + edgeCount + " edges");
        System.out.println("Time taken: " + (edgeEnd - edgeStart) + "ms");

        if (edgeCount == 0) {
            System.err.println("ERROR: No edges were created!");
            debugWhyNoEdges();
        }
    }

    /**
     * Find node index in road
     */
    private int findNodeIndex(RoadData road, int vertex) {
        Long nodeId = vertexToNodeId.get(vertex);
        if (nodeId == null) return -1;

        for (int i = 0; i < road.nodeIds.size(); i++) {
            if (road.nodeIds.get(i).equals(nodeId)) {
                return i;
            }
        }
        return -1;
    }

    private float calculateSegmentDistance(RoadData road, int startNodeIndex, int endNodeIndex) {
        if (road.way == null) {
            return calculateNodeDistance(road.nodeIds.get(startNodeIndex),
                    road.nodeIds.get(endNodeIndex));
        }

        float totalDistance = 0;
        float[] coords = road.way.getCoords();

        // Sum distances between consecutive nodes
        for (int i = startNodeIndex; i < endNodeIndex && i < road.nodeIds.size() - 1; i++) {
            if (i * 2 + 3 < coords.length) {
                float x1 = coords[i * 2];
                float y1 = coords[i * 2 + 1];
                float x2 = coords[(i + 1) * 2];
                float y2 = coords[(i + 1) * 2 + 1];

                totalDistance += calculateDistance(x1, y1, x2, y2);
            }
        }

        return totalDistance;
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
        vertexToNodeId.put(vertex, nodeId);  // Add reverse mapping
        vertexCoords.put(vertex, new float[]{x, y});
    }
    public Long getNodeIdForVertex(int vertex) {
        return vertexToNodeId.get(vertex);
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
     * Calculate direct distance between two vertices
     */
    private float calculateDirectDistance(int v1, int v2) {
        float[] coords1 = vertexCoords.get(v1);
        float[] coords2 = vertexCoords.get(v2);

        if (coords1 == null || coords2 == null) {
            return 0.001f; // Minimum distance
        }

        return calculateDistance(coords1[0], coords1[1], coords2[0], coords2[1]);
    }

    private float calculateDistance(float v, float v1, float v2, float v3) {
    }

    /**
     * Calculate distance between two nodes by ID
     */
    private float calculateNodeDistance(long nodeId1, long nodeId2) {
        float[] coords1 = nodeCoordinateCache.get(nodeId1);
        float[] coords2 = nodeCoordinateCache.get(nodeId2);

        if (coords1 == null || coords2 == null) {
            return 0.001f; // Minimum distance
        }

        return calculateDistance(coords1[0], coords1[1], coords2[0], coords2[1]);
    }

    private void debugWhyNoEdges() {
        System.err.println("\n=== DEBUGGING WHY NO EDGES ===");

        // Check road data
        System.err.println("Total roads in roadDataList: " + roadDataList.size());
        if (roadDataList.isEmpty()) {
            System.err.println("ERROR: No roads were stored!");
            return;
        }

        // Sample first few roads
        int sampleCount = Math.min(5, roadDataList.size());
        for (int i = 0; i < sampleCount; i++) {
            RoadData road = roadDataList.get(i);
            System.err.println("\nRoad " + i + ":");
            System.err.println("  Type: " + road.roadType);
            System.err.println("  Nodes: " + (road.nodeIds != null ? road.nodeIds.size() : "null"));

            if (road.nodeIds != null && road.nodeIds.size() >= 2) {
                // Check how many vertices this road has
                int vertexCount = 0;
                for (Long nodeId : road.nodeIds) {
                    if (nodeToVertex.containsKey(nodeId)) {
                        vertexCount++;
                    }
                }
                System.err.println("  Vertices on road: " + vertexCount);

                if (vertexCount < 2) {
                    System.err.println("  ERROR: Road has less than 2 vertices!");
                }
            }
        }

        // Check vertex mapping
        System.err.println("\nVertex mapping statistics:");
        System.err.println("  Total vertices: " + vertexCount);
        System.err.println("  nodeToVertex entries: " + nodeToVertex.size());
        System.err.println("  vertexCoords entries: " + vertexCoords.size());
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