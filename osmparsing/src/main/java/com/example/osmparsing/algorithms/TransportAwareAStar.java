package com.example.osmparsing.algorithms;

import com.example.osmparsing.utility.FloatMath;
import com.example.osmparsing.utility.RoadProperties;
import com.example.osmparsing.utility.TransportMode;
import com.example.osmparsing.utility.GraphBuilder;
import java.util.*;

public class TransportAwareAStar {
    private final EdgeWeightedDigraph graph;
    private final int source;
    private final int target;
    private final TransportMode mode;
    private final GraphBuilder graphBuilder;
    private float[] distTo;
    private DirectedEdge[] edgeTo;
    private IndexMinPQ<Float> pq;
    private boolean pathFound = false;
    private final float[] targetCoords;
    private final float[][] vertexCoords;

    public TransportAwareAStar(EdgeWeightedDigraph graph, int source, int target,
                               TransportMode mode, GraphBuilder graphBuilder,
                               float[] targetCoords, float[][] vertexCoords) {
        this.graph = graph;
        this.source = source;
        this.target = target;
        this.mode = mode;
        this.graphBuilder = graphBuilder;
        this.targetCoords = targetCoords;
        this.vertexCoords = vertexCoords;

        // Initialize arrays
        distTo = new float[graph.V()];
        edgeTo = new DirectedEdge[graph.V()];
        Arrays.fill(distTo, Float.POSITIVE_INFINITY);
        distTo[source] = 0.0f;

        // FIXED: Use graph.V() instead of limiting to 10000
        pq = new IndexMinPQ<>(graph.V());

        System.out.println("Starting " + mode + " route search from " + source + " to " + target);
        System.out.println("Graph has " + graph.V() + " vertices");

        // Run A* with transport mode consideration
        runSearch();
    }

    private void runSearch() {
        // Add source to priority queue with heuristic
        float sourceHeuristic = calculateHeuristic(source);
        pq.insert(source, distTo[source] + sourceHeuristic);

        int iterations = 0;
        int maxIterations = 100000; // Safety limit

        while (!pq.isEmpty() && iterations < maxIterations) {
            iterations++;

            int v = pq.delMin();

            if (v == target) {
                pathFound = true;
                System.out.println("Path found after " + iterations + " iterations");
                break;
            }

            // Log progress
            if (iterations % 10000 == 0) {
                System.out.println("Bicycle routing: " + iterations + " iterations, queue size: " + pq.size());
            }

            // Relax edges considering transport mode
            for (DirectedEdge e : graph.adj(v)) {
                relaxWithMode(e);
            }
        }

        if (!pathFound) {
            System.out.println("No bicycle path found after " + iterations + " iterations");
        }
    }

    private void relaxWithMode(DirectedEdge e) {
        int v = e.from();
        int w = e.to();

        // Validate vertex
        if (w < 0 || w >= graph.V()) {
            System.err.println("WARNING: Invalid vertex " + w + " in edge from " + v);
            return;
        }

        // Get road properties for this edge
        RoadProperties props = graphBuilder.getEdgeProperties(v, w);

        // Skip if not accessible by current transport mode
        if (props != null && !props.isAccessibleBy(mode)) {
            // Debug: log when we skip edges
            if (mode == TransportMode.BICYCLE && props.getRoadType().equals("motorway")) {
                // Don't spam logs for common cases
            } else {
                // Log other restrictions
                System.out.println("Skipping edge " + v + "->" + w +
                        " (" + props.getRoadType() + ") - not accessible by " + mode);
            }
            return;
        }

        // Calculate weight based on mode
        float weight = calculateModeWeight(e, props);

        if (distTo[w] > distTo[v] + weight) {
            distTo[w] = distTo[v] + weight;
            edgeTo[w] = e;

            // Calculate priority with heuristic
            float heuristic = calculateHeuristic(w);
            float priority = distTo[w] + heuristic;

            // Update priority queue
            if (pq.contains(w)) {
                pq.decreaseKey(w, priority);
            } else {
                pq.insert(w, priority);
            }
        }
    }

    private float calculateModeWeight(DirectedEdge e, RoadProperties props) {
        float baseWeight = e.weight();

        if (props == null) {
            // If no properties, assume it's a regular road
            return baseWeight;
        }

        // Apply mode-specific adjustments
        if (mode == TransportMode.BICYCLE) {
            // Bicycle routing preferences
            switch (props.getRoadType()) {
                case "cycleway":
                    return baseWeight * 0.5f;  // Strong preference for cycleways
                case "path":
                case "track":
                    return baseWeight * 0.7f;  // Good for bicycles
                case "residentialroad":
                case "living_street":
                    return baseWeight * 0.8f;  // Safe for bicycles
                case "serviceroad":
                    return baseWeight * 0.9f;  // OK for bicycles
                case "tertiary":
                case "tertiary_link":
                    return baseWeight * 1.0f;  // Neutral
                case "secondary":
                case "secondary_link":
                    return baseWeight * 1.5f;  // Less preferred
                case "primary":
                case "primary_link":
                    return baseWeight * 2.0f;  // Avoid if possible
                case "trunk":
                case "trunk_link":
                    return baseWeight * 3.0f;  // Strongly avoid
                case "motorway":
                case "motorway_link":
                    return Float.POSITIVE_INFINITY; // Not allowed
                default:
                    return baseWeight;
            }
        } else { // CAR
            // Car routing preferences (prefer faster roads)
            switch (props.getRoadType()) {
                case "motorway":
                case "motorway_link":
                    return baseWeight * 0.7f;  // Prefer highways
                case "trunk":
                case "trunk_link":
                    return baseWeight * 0.8f;
                case "primary":
                case "primary_link":
                    return baseWeight * 0.9f;
                case "secondary":
                case "secondary_link":
                    return baseWeight * 1.0f;
                case "tertiary":
                case "tertiary_link":
                    return baseWeight * 1.1f;
                case "unclassifiedhighway":
                    return baseWeight * 1.2f;
                case "residentialroad":
                case "living_street":
                    return baseWeight * 1.5f;  // Avoid residential areas
                case "serviceroad":
                    return baseWeight * 1.8f;
                case "cycleway":
                    return Float.POSITIVE_INFINITY; // Cars not allowed on cycleways
                case "footway":
                case "path":
                    return Float.POSITIVE_INFINITY; // Cars not allowed
                default:
                    return baseWeight;
            }
        }
    }

    private float calculateHeuristic(int v) {
        if (targetCoords == null || vertexCoords == null ||
                v >= vertexCoords.length || vertexCoords[v] == null) {
            return 0.0f;
        }

        // Use Euclidean distance as heuristic (faster than Haversine)
        float dx = vertexCoords[v][0] - targetCoords[0];
        float dy = vertexCoords[v][1] - targetCoords[1];
        return FloatMath.sqrt(dx * dx + dy * dy);
    }

    public boolean hasPathTo(int v) {
        return v == target && pathFound;
    }

    public Iterable<DirectedEdge> pathTo(int v) {
        if (!hasPathTo(v)) return null;

        Stack<DirectedEdge> path = new Stack<>();
        for (DirectedEdge e = edgeTo[target]; e != null; e = edgeTo[e.from()]) {
            path.push(e);
        }

        List<DirectedEdge> result = new ArrayList<>();
        while (!path.isEmpty()) {
            result.add(path.pop());
        }

        System.out.println("Bicycle path found with " + result.size() + " edges");

        // Debug: Check the path composition
        Map<String, Integer> roadTypeCounts = new HashMap<>();
        for (DirectedEdge e : result) {
            RoadProperties props = graphBuilder.getEdgeProperties(e.from(), e.to());
            if (props != null) {
                roadTypeCounts.merge(props.getRoadType(), 1, Integer::sum);
            }
        }

        System.out.println("Path composition:");
        for (Map.Entry<String, Integer> entry : roadTypeCounts.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue() + " segments");
        }

        return result;
    }
}