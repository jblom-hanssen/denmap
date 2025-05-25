package com.example.osmparsing.tests;

import com.example.osmparsing.algorithms.DirectedEdge;
import com.example.osmparsing.algorithms.EdgeWeightedDigraph;
import com.example.osmparsing.mvc.Model;
import com.example.osmparsing.way.Way;

import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

/**
 * Test class for verifying the graph construction from OSM data
 */
public class GraphTest {
    public static void main(String[] args) {
        try {
            // Load OSM data from file
            String filename = "C:\\Users\\Joach\\Documents\\BFST2025Group27\\osmparsing\\data\\bornholm.osm"; // Make sure this file exists
            System.out.println("Loading OSM data from " + filename);

            Model model = Model.load(filename);
            System.out.println("Loaded OSM data with:");
            System.out.println("  - " + model.ways.size() + " ways");


            System.out.println("  - "+ " highway ways");

            // Build the road network graph
            System.out.println("\nBuilding road network graph...");
            long startTime = System.currentTimeMillis();
            EdgeWeightedDigraph graph = model.buildRoadGraph();
            long endTime = System.currentTimeMillis();

            System.out.println("Graph built in " + (endTime - startTime) + " ms");
            System.out.println("Graph has " + graph.V() + " vertices and " +
                    graph.E() + " edges");

            // Analyze vertex connectivity
            analyzeVertexDegrees(graph);

            // Verify graph connectivity
            analyzeConnectivity(graph);

            // Display sample edges
            System.out.println("\nSample edges from the graph:");
            int count = 0;
            for (DirectedEdge edge : graph.edges()) {
                System.out.println("  " + edge);
                if (++count >= 10) break;
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Analyze the degree distribution of the graph vertices
     */
    private static void analyzeVertexDegrees(EdgeWeightedDigraph graph) {
        int[] degreeCount = new int[10]; // Count vertices with 0-9+ connections
        int maxDegree = 0;
        int maxDegreeVertex = -1;

        for (int v = 0; v < graph.V(); v++) {
            int degree = 0;
            for (DirectedEdge e : graph.adj(v)) {
                degree++;
            }

            // Record the maximum degree
            if (degree > maxDegree) {
                maxDegree = degree;
                maxDegreeVertex = v;
            }

            // Count degrees in bins (0, 1, 2, ..., 9+)
            if (degree >= degreeCount.length) {
                degreeCount[degreeCount.length - 1]++; // 9+ connections
            } else {
                degreeCount[degree]++;
            }
        }

        System.out.println("\nVertex degree distribution:");
        for (int i = 0; i < degreeCount.length - 1; i++) {
            System.out.println("  Vertices with " + i + " edges: " + degreeCount[i]);
        }
        System.out.println("  Vertices with 9+ edges: " + degreeCount[degreeCount.length - 1]);
        System.out.println("  Maximum degree: " + maxDegree + " (vertex " + maxDegreeVertex + ")");
    }

    /**
     * Analyze the connectivity of the graph
     */
    private static void analyzeConnectivity(EdgeWeightedDigraph graph) {
        System.out.println("\nAnalyzing graph connectivity...");

        if (graph.V() == 0) {
            System.out.println("  Graph is empty!");
            return;
        }

        // Find connected components using BFS
        boolean[] visited = new boolean[graph.V()];
        List<Set<Integer>> components = new ArrayList<>();

        for (int v = 0; v < graph.V(); v++) {
            if (!visited[v]) {
                // Start a new component
                Set<Integer> component = new HashSet<>();

                // BFS from vertex v
                Set<Integer> frontier = new HashSet<>();
                frontier.add(v);
                visited[v] = true;

                while (!frontier.isEmpty()) {
                    Set<Integer> newFrontier = new HashSet<>();

                    for (int current : frontier) {
                        component.add(current);

                        // Visit all adjacent vertices
                        for (DirectedEdge e : graph.adj(current)) {
                            int w = e.to();
                            if (!visited[w]) {
                                visited[w] = true;
                                newFrontier.add(w);
                            }
                        }
                    }

                    frontier = newFrontier;
                }

                components.add(component);
            }
        }

        // Sort components by size (largest first)
        components.sort((c1, c2) -> c2.size() - c1.size());

        System.out.println("  Found " + components.size() + " connected components");

        // Display details of the largest components
        for (int i = 0; i < Math.min(5, components.size()); i++) {
            Set<Integer> component = components.get(i);
            System.out.println("  Component " + (i+1) + ": " + component.size() + " vertices");
        }

        int totalVisited = 0;
        for (Set<Integer> component : components) {
            totalVisited += component.size();
        }

        System.out.println("  Total vertices in components: " + totalVisited + " / " + graph.V());
    }
}