package com.example.osmparsing.algorithms;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class EdgeWeightedDigraph implements Serializable {
    private static final String NEWLINE = System.getProperty("line.separator");
    private final int V;               // number of vertices
    private int E;                     // number of edges
    private List<DirectedEdge>[] adj;  // adjacency list for each vertex
    private int[] indegree;            // indegree for each vertex

    // Create an empty graph with V vertices
    public EdgeWeightedDigraph(int V) {
        if (V < 0) throw new IllegalArgumentException("Number of vertices must be non-negative");
        this.V = V;
        this.E = 0;
        this.indegree = new int[V];
        // Initialize array of lists
        adj = (List<DirectedEdge>[]) new List[V];
        for (int v = 0; v < V; v++) {
            adj[v] = new ArrayList<DirectedEdge>();
        }
    }

    // Create a deep copy of graph G
    public EdgeWeightedDigraph(EdgeWeightedDigraph G) {
        this(G.V());
        this.E = G.E();
        for (int v = 0; v < G.V(); v++) {
            this.indegree[v] = G.indegree(v);
        }
        for (int v = 0; v < G.V(); v++) {
            // Reverse to preserve the original order
            Stack<DirectedEdge> reverse = new Stack<DirectedEdge>();
            for (DirectedEdge e : G.adj(v)) {
                reverse.push(e);
            }
            for (DirectedEdge e : reverse) {
                adj[v].add(e);
            }
        }
    }

    // Return number of vertices
    public int V() {
        return V;
    }

    // Return number of edges
    public int E() {
        return E;
    }

    // Validate that vertex v is between 0 and V-1
    private void validateVertex(int v) {
        if (v < 0 || v >= V)
            throw new IllegalArgumentException("vertex " + v + " is not between 0 and " + (V - 1));
        if (v < 0 || v >= V) {
            // Before throwing the exception, print a more detailed error message
            System.err.println("ERROR: Vertex validation failed for vertex " + v);
            System.err.println("Graph capacity: " + V + " (valid indices: 0 to " + (V - 1) + ")");

            // If the vertex is just 1 over the limit, we could handle it specially
            if (v == V) {
                System.err.println("Vertex is exactly at the upper bound. Expanding graph capacity.");
                // However, we can't modify V directly as it's final, so we need to throw
            }

            throw new IllegalArgumentException("vertex " + v + " is not between 0 and " + (V - 1));
        }
    }

    // Add an edge to the graph
    public void addEdge(DirectedEdge e) {
        int v = e.from();
        int w = e.to();
        validateVertex(v);
        validateVertex(w);
        adj[v].add(e);
        indegree[w]++;
        E++;
    }

    // Return edges adjacent to vertex v
    public Iterable<DirectedEdge> adj(int v) {
        validateVertex(v);
        return adj[v];
    }

    // Return outdegree of vertex v
    public int outdegree(int v) {
        validateVertex(v);
        return adj[v].size();
    }

    // Return indegree of vertex v
    public int indegree(int v) {
        validateVertex(v);
        return indegree[v];
    }

    // Return all edges in the graph
    public Iterable<DirectedEdge> edges() {
        List<DirectedEdge> list = new ArrayList<DirectedEdge>();
        for (int v = 0; v < V; v++) {
            for (DirectedEdge e : adj(v)) {
                list.add(e);
            }
        }
        return list;
    }

    // Return a string representation of the graph
    /**
     * Return a string representation of the graph
     * Limited to avoid memory issues with huge graphs
     */
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append(V + " vertices, " + E + " edges" + NEWLINE);

        // Only print a sample of the graph for large graphs
        int maxVerticesToPrint = Math.min(V, 100);
        for (int v = 0; v < maxVerticesToPrint; v++) {
            s.append(v + ": ");
            if (adj[v] != null) {
                for (DirectedEdge e : adj[v]) {
                    s.append(e + "  ");
                }
            }
            s.append(NEWLINE);
        }

        if (V > maxVerticesToPrint) {
            s.append("... (remaining " + (V - maxVerticesToPrint) + " vertices not shown)" + NEWLINE);
        }

        return s.toString();
    }

    /**
     * Memory-efficient method to check if there's a path between two vertices
     * without loading the entire graph
     */
    public boolean hasPath(int source, int target) {
        if (source < 0 || source >= V || target < 0 || target >= V) {
            return false;
        }

        boolean[] visited = new boolean[V];

        // Use a simple stack instead of recursion to avoid stack overflow
        int[] stack = new int[1000]; // Fixed-size stack
        int stackSize = 0;

        stack[stackSize++] = source;
        visited[source] = true;

        while (stackSize > 0) {
            int v = stack[--stackSize];

            if (v == target) {
                return true;
            }

            if (adj[v] != null) {
                for (DirectedEdge e : adj[v]) {
                    int w = e.to();
                    if (!visited[w]) {
                        visited[w] = true;

                        // Check if stack is full
                        if (stackSize >= stack.length) {
                            // Expand stack
                            int[] newStack = new int[stack.length * 2];
                            System.arraycopy(stack, 0, newStack, 0, stack.length);
                            stack = newStack;
                        }

                        stack[stackSize++] = w;
                    }
                }
            }
        }

        return false;
    }



}
