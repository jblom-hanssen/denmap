package com.example.osmparsing.utility;

import com.example.osmparsing.algorithms.DirectedEdge;
import com.example.osmparsing.algorithms.EdgeWeightedDigraph;
import java.io.*;
import java.util.*;

public class FileBasedGraph implements Serializable {
    private String graphFilePath;
    private transient RandomAccessFile graphFile;
    private Map<Integer, Long> vertexFilePositions;
    private Map<Integer, float[]> vertexCoordinates;
    private int vertexCount;

    public FileBasedGraph() {
        this.vertexFilePositions = new HashMap<>();
        this.vertexCoordinates = new HashMap<>();
    }

    /**
     * Save an in-memory graph to a file
     */
    public void saveGraph(EdgeWeightedDigraph graph, GraphBuilder builder, String baseFilename) throws IOException {
        this.graphFilePath = baseFilename + ".graph";
        this.vertexCount = graph.V();

        System.out.println("Saving graph to file: " + graphFilePath);
        System.out.println("Graph has " + graph.V() + " vertices and " + graph.E() + " edges");
        long startTime = System.currentTimeMillis();

        try (RandomAccessFile file = new RandomAccessFile(graphFilePath, "rw")) {
            // Write header
            file.writeInt(graph.V()); // number of vertices
            file.writeInt(graph.E()); // number of edges

            // First, write all vertex coordinates
            System.out.println("Writing vertex coordinates...");
            for (int v = 0; v < graph.V(); v++) {
                float[] coords = builder.getCoordinatesForVertex(v);
                if (coords != null) {
                    file.writeBoolean(true); // Has coordinates
                    file.writeFloat(coords[0]);
                    file.writeFloat(coords[1]);
                    // Store in memory map
                    vertexCoordinates.put(v, coords);
                } else {
                    file.writeBoolean(false); // No coordinates
                    System.err.println("WARNING: Vertex " + v + " has no coordinates!");
                }
            }

            // Then write adjacency lists
            System.out.println("Writing adjacency lists...");
            int edgesWritten = 0;
            for (int v = 0; v < graph.V(); v++) {
                // Store file position for this vertex
                vertexFilePositions.put(v, file.getFilePointer());

                // Count edges for this vertex
                int edgeCount = 0;
                for (DirectedEdge e : graph.adj(v)) {
                    edgeCount++;
                }

                // Write edge count
                file.writeInt(edgeCount);

                // Write edges
                for (DirectedEdge e : graph.adj(v)) {
                    file.writeInt(e.to());
                    file.writeFloat(e.weight());
                    edgesWritten++;
                }

                if (v % 10000 == 0) {
                    System.out.println("Saved " + v + "/" + graph.V() + " vertices, " +
                            edgesWritten + " edges so far");
                }
            }

            System.out.println("Total edges written: " + edgesWritten);
        }

        long endTime = System.currentTimeMillis();
        System.out.println("Graph saved to file in " + (endTime - startTime) + "ms");
        System.out.println("File size: " + (new File(graphFilePath).length() / 1024 / 1024) + "MB");
    }

    /**
     * Open the graph file for reading
     */
    public void openForReading() throws IOException {
        if (graphFile == null && graphFilePath != null) {
            graphFile = new RandomAccessFile(graphFilePath, "r");

            // Read header
            int vertices = graphFile.readInt();
            int edges = graphFile.readInt();

            // Read vertex coordinates
            for (int v = 0; v < vertices; v++) {
                boolean hasCoords = graphFile.readBoolean();
                if (hasCoords) {
                    float x = graphFile.readFloat();
                    float y = graphFile.readFloat();
                    vertexCoordinates.put(v, new float[]{x, y});
                }
            }

            // The file pointer is now at the start of adjacency lists
            // Store this position
            long adjListStart = graphFile.getFilePointer();

            // We need to rebuild vertexFilePositions by scanning through
            // This is necessary for getAdjacentEdges to work
            for (int v = 0; v < vertices; v++) {
                vertexFilePositions.put(v, graphFile.getFilePointer());
                int edgeCount = graphFile.readInt();
                // Skip the edges (each edge is an int + float = 8 bytes)
                graphFile.skipBytes(edgeCount * 8);
            }

            System.out.println("Opened graph file with " + vertices + " vertices and " + edges + " edges");
        }
    }

    public int findNearestVertex(float x, float y) {
        int nearest = -1;
        float minDist = Float.MAX_VALUE;

        // Search through all vertices (this could be optimized with spatial indexing)
        for (Map.Entry<Integer, float[]> entry : vertexCoordinates.entrySet()) {
            int v = entry.getKey();
            float[] coords = entry.getValue();

            if (coords != null) {
                float dx = x - coords[0];
                float dy = y - coords[1];
                float dist = dx * dx + dy * dy;  // Squared distance is fine for comparison

                if (dist < minDist) {
                    minDist = dist;
                    nearest = v;
                }
            }
        }

        return nearest;
    }

    /**
     * Get adjacent edges for a vertex (reads from file)
     */
    public List<DirectedEdge> getAdjacentEdges(int vertex) throws IOException {
        if (vertex < 0 || vertex >= vertexCount) {
            return Collections.emptyList();
        }

        Long position = vertexFilePositions.get(vertex);
        if (position == null) {
            return Collections.emptyList();
        }

        openForReading();

        List<DirectedEdge> edges = new ArrayList<>();

        graphFile.seek(position);
        int edgeCount = graphFile.readInt();

        for (int i = 0; i < edgeCount; i++) {
            int to = graphFile.readInt();
            float weight = graphFile.readFloat();
            edges.add(new DirectedEdge(vertex, to, weight));
        }

        return edges;
    }

    /**
     * Get coordinates for a vertex
     */
    public float[] getCoordinatesForVertex(int vertex) {
        return vertexCoordinates.get(vertex);
    }

    /**
     * Get vertex count
     */
    public int getVertexCount() {
        return vertexCount;
    }

    /**
     * Close the file when done
     */
    public void close() throws IOException {
        if (graphFile != null) {
            graphFile.close();
            graphFile = null;
        }
    }

    /**
     * Check if graph file exists
     */
    public boolean graphFileExists() {
        return graphFilePath != null && new File(graphFilePath).exists();
    }
}