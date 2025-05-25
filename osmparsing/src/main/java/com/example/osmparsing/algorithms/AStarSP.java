package com.example.osmparsing.algorithms;

import com.example.osmparsing.utility.FloatMath;

import java.util.*;

/**
 * Memory-optimized A* search implementation using floats
 * Designed for very large graphs with up to 50 million vertices
 */
public class AStarSP {
    // Instance variables
    private final int source;
    private final int target;
    private float[] distTo;          // Distance from source to each vertex
    private DirectedEdge[] edgeTo;   // Last edge on the shortest path
    private IndexMinPQ<Float> pq;    // Priority queue of vertices
    private Map<Integer, Float> heuristicMap; // Sparse storage for heuristic values
    private final float[] targetCoords;       // Target coordinates
    private final float[][] vertexCoords;     // Vertex coordinates
    private boolean pathFound = false;        // Flag for path discovery
    private List<DirectedEdge> foundPath = null; // Cached path if found
    private final int pqMaxSize;              // Maximum priority queue size

    // Constants to control memory usage
    private static final int DEFAULT_PQ_SIZE = 2000000;
    private static final int MAX_VERTICES_EXPANDED = 5000000;
    private static final int PROGRESS_REPORT_INTERVAL = 100000;
    private static final float MAX_DISTANCE_MULTIPLIER = 3.0f;

    /**
     * Constructor for memory-optimized A* search
     * @param G The graph
     * @param s Source vertex
     * @param t Target vertex
     * @param targetCoords Coordinates of the target
     * @param vertexCoords Coordinates of all vertices
     */
    public AStarSP(EdgeWeightedDigraph G, int s, int t, float[] targetCoords, float[][] vertexCoords) {
        this.source = s;
        this.target = t;
        this.targetCoords = targetCoords;
        this.vertexCoords = vertexCoords;
        // FIXED: Use G.V() to ensure the priority queue can handle all vertex indices
        this.pqMaxSize = G.V();

        System.out.println("Starting memory-optimized A* search from " + s + " to " + t);
        System.out.println("Graph has " + G.V() + " vertices, PQ capacity: " + pqMaxSize);
        long startTime = System.currentTimeMillis();

        // Validate inputs
        if (s < 0 || s >= G.V() || t < 0 || t >= G.V()) {
            System.err.println("Invalid source or target vertex");
            return;
        }

        // Check for simple case
        if (s == t) {
            pathFound = true;
            foundPath = new ArrayList<>();
            System.out.println("Source and target are the same vertex");
            return;
        }

        // Estimate straight-line distance for search area limiting
        float straightLineDistance = Float.MAX_VALUE;
        if (vertexCoords[s] != null && targetCoords != null) {
            straightLineDistance = calculateDistance(
                    vertexCoords[s][0], vertexCoords[s][1],
                    targetCoords[0], targetCoords[1]
            );
        }
        float maxConsideredDistance = straightLineDistance * MAX_DISTANCE_MULTIPLIER;

        // Initialize sparse heuristic storage
        // Only allocate enough for the expected number of visited vertices
        int expectedVisitedVertices = Math.min(G.V(), 1000000);
        this.heuristicMap = new HashMap<>(expectedVisitedVertices / 4);

        // Initialize distance array
        distTo = new float[G.V()];
        for (int v = 0; v < G.V(); v++) {
            distTo[v] = Float.POSITIVE_INFINITY;
        }
        distTo[s] = 0.0f;

        // Initialize edge array
        edgeTo = new DirectedEdge[G.V()];

        // Initialize priority queue with float priorities
        pq = new IndexMinPQ<Float>(pqMaxSize);

        // Compute heuristic for source and add to priority queue
        float sourceHeuristic = computeHeuristic(s);
        pq.insert(s, distTo[s] + sourceHeuristic);

        // A* search
        int verticesExpanded = 0;

        while (!pq.isEmpty() && verticesExpanded < MAX_VERTICES_EXPANDED) {
            int v = pq.delMin();
            verticesExpanded++;

            // Log progress periodically
            if (verticesExpanded % PROGRESS_REPORT_INTERVAL == 0) {
                System.out.printf("A* search: expanded %,d vertices, queue size: %,d%n",
                        verticesExpanded, pq.size());

                // Report memory usage
                Runtime rt = Runtime.getRuntime();
                long usedMB = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
                System.out.printf("Memory usage: %d MB%n", usedMB);
            }

            // Check if we reached the target
            if (v == t) {
                pathFound = true;
                break;
            }

            // Only consider vertices that are within reasonable distance
            // This is a critical optimization for massive graphs
            if (vertexCoords[v] != null && targetCoords != null) {
                float distToTarget = calculateDistance(
                        vertexCoords[v][0], vertexCoords[v][1],
                        targetCoords[0], targetCoords[1]
                );

                if (distToTarget > maxConsideredDistance && v != s) {
                    // Skip vertices that are too far from the expected path
                    continue;
                }
            }

            // Relax outgoing edges
            for (DirectedEdge e : G.adj(v)) {
                relax(e);
            }
        }

        // Extract and cache the path if found
        if (pathFound) {
            foundPath = extractPath();

            long endTime = System.currentTimeMillis();
            System.out.println("A* search complete: path found in " + (endTime - startTime) + "ms");
            System.out.println("Path length: " + foundPath.size() + " edges");
        } else {
            long endTime = System.currentTimeMillis();
            if (verticesExpanded >= MAX_VERTICES_EXPANDED) {
                System.out.println("A* search terminated: reached max vertex expansion limit");
            } else {
                System.out.println("A* search terminated: no path exists to target");
            }
            System.out.println("Search took " + (endTime - startTime) + "ms, expanded " +
                    verticesExpanded + " vertices");
        }

        // Clean up to save memory
        heuristicMap = null;
        pq = null;
    }

    /**
     * Relaxes edge e and updates the priority queue if needed
     */
    private void relax(DirectedEdge e) {
        int v = e.from(), w = e.to();

        // FIXED: Validate vertex indices to prevent out-of-bounds errors
        if (w < 0 || w >= distTo.length) {
            System.err.println("WARNING: Edge " + v + " -> " + w +
                    " has invalid target vertex (graph size: " + distTo.length + ")");
            return;
        }

        // Assume e.weight() now returns float
        float newDist = distTo[v] + e.weight();

        if (distTo[w] > newDist) {
            distTo[w] = newDist;
            edgeTo[w] = e;

            // Get or compute heuristic (lazy evaluation)
            float hValue;
            Float cached = heuristicMap.get(w);

            if (cached != null) {
                hValue = cached;
            } else {
                // Only compute and store heuristic for vertices we actually visit
                hValue = computeHeuristic(w);

                // Only cache if we expect to reference this vertex again
                // or it's an important vertex (like the target)
                if (w % 3 == 0 || w == target) { // Simple heuristic to limit cache size
                    heuristicMap.put(w, hValue);
                }
            }

            // Total priority is distance + heuristic
            float priority = newDist + hValue;

            if (pq.contains(w)) {
                pq.decreaseKey(w, priority);
            } else if (pq.size() < pqMaxSize) {
                pq.insert(w, priority);
            }
        }
    }

    /**
     * Compute heuristic value for a vertex
     */
    private float computeHeuristic(int v) {
        if (targetCoords == null || vertexCoords == null ||
                vertexCoords[v] == null || targetCoords.length < 2) {
            return 0.0f;
        }

        return calculateHaversineDistance(
                vertexCoords[v][0], vertexCoords[v][1],
                targetCoords[0], targetCoords[1]
        );
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

    /**
     * Calculate Euclidean distance using floats
     */
    private float calculateDistance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return FloatMath.sqrt(dx * dx + dy * dy);
    }

    /**
     * Extract the path from source to target
     */
    private List<DirectedEdge> extractPath() {
        if (!pathFound) return null;

        List<DirectedEdge> path = new ArrayList<>();

        // Start from target and work backwards
        for (DirectedEdge e = edgeTo[target]; e != null; e = edgeTo[e.from()]) {
            path.add(e);
        }

        // Reverse to get path from source to target
        Collections.reverse(path);
        return path;
    }

    /**
     * Check if a path exists from source to target
     */
    public boolean hasPathTo(int v) {
        if (v != target) return false; // We only search for path to target
        return pathFound;
    }

    /**
     * Return the path to the specified vertex
     */
    public Iterable<DirectedEdge> pathTo(int v) {
        if (v != target || !hasPathTo(v)) return null;
        return foundPath;
    }

    /**
     * Return the total distance (cost) of the path
     */
    public float getPathCost() {
        if (!pathFound) return Float.POSITIVE_INFINITY;
        return distTo[target];
    }

    /**
     * Return memory usage statistics
     */
    public String getMemoryStatistics() {
        Runtime rt = Runtime.getRuntime();
        long totalMB = rt.totalMemory() / 1024 / 1024;
        long freeMB = rt.freeMemory() / 1024 / 1024;
        long usedMB = totalMB - freeMB;

        StringBuilder stats = new StringBuilder();
        stats.append("Memory usage: ").append(usedMB).append("MB / ").append(totalMB).append("MB\n");
        stats.append("Heuristic cache entries: ").append(heuristicMap != null ? heuristicMap.size() : 0).append("\n");
        stats.append("Path found: ").append(pathFound).append("\n");
        if (pathFound) {
            stats.append("Path length: ").append(foundPath.size()).append(" edges\n");
            stats.append("Path cost: ").append(getPathCost());
        }

        return stats.toString();
    }

    /**
     * Clean up memory
     */
    public void cleanup() {
        // Retain only the path, clear everything else
        if (heuristicMap != null) {
            heuristicMap.clear();
            heuristicMap = null;
        }

        // If we don't need distTo and edgeTo anymore, clear them
        if (pathFound) {
            distTo = null;
            edgeTo = null;
        }

        System.gc(); // Suggest garbage collection
    }
}