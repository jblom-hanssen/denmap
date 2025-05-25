package com.example.osmparsing.utility;

import com.example.osmparsing.algorithms.DirectedEdge;
import com.example.osmparsing.algorithms.EdgeWeightedDigraph;
import com.example.osmparsing.mvc.Model;
import com.example.osmparsing.way.Way;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Random;

public class ExtendedGraphViewer extends Application {
    private Canvas canvas = new Canvas(1200, 900);
    private GraphicsContext gc = canvas.getGraphicsContext2D();
    private Label statusLabel = new Label();
    private Model model;
    private Random random = new Random();

    // Viewport control
    private float scale = 1.0f;
    private float offsetX = 0.0f;
    private float offsetY = 0.0f;

    @Override
    public void start(Stage stage) {
        try {
            // Load the model
            String filename = "C:\\Users\\Joach\\Documents\\BFST2025Group27\\osmparsing\\data\\denmark-latest.osm"; // Update path
            model = Model.load(filename);

            // Count total ways from typeWays instead of model.ways
            int totalWays = 0;
            for (ArrayList<Way> wayList : model.getTypeWays().values()) {
                totalWays += wayList.size();
            }
            System.out.println("Loaded OSM data with " + totalWays + " ways");

            // Build road graph if it doesn't exist
            if (model.getRoadGraph() == null) {
                System.out.println("Building road network graph...");
                long startTime = System.currentTimeMillis();
                EdgeWeightedDigraph graph = model.buildRoadGraph();
                long endTime = System.currentTimeMillis();
                System.out.println("Graph built in " + (endTime - startTime) + "ms with " +
                        graph.V() + " vertices and " + graph.E() + " edges");
            }

            EdgeWeightedDigraph graph = model.getRoadGraph();
            if (graph != null) {
                System.out.println("Using graph with " + graph.V() + " vertices and " + graph.E() + " edges");
            }

            // Create the UI
            BorderPane root = new BorderPane();
            root.setCenter(canvas);

            // Create toolbar with control buttons
            HBox toolbar = new HBox(10);

            Button zoomInBtn = new Button("+");
            Button zoomOutBtn = new Button("-");
            Button resetBtn = new Button("Reset");
            Button randomBtn = new Button("Random View");

            zoomInBtn.setOnAction(e -> {
                scale *= 1.5f;
                drawGraph();
            });

            zoomOutBtn.setOnAction(e -> {
                scale *= 0.7f;
                drawGraph();
            });

            resetBtn.setOnAction(e -> {
                scale = 1.0f;
                offsetX = 0.0f;
                offsetY = 0.0f;
                drawGraph();
            });

            randomBtn.setOnAction(e -> {
                // Generate random view of the graph
                generateRandomView();
                drawGraph();
            });

            toolbar.getChildren().addAll(zoomInBtn, zoomOutBtn, resetBtn, randomBtn);
            root.setTop(toolbar);
            root.setBottom(statusLabel);

            // Add canvas mouse events for panning
            canvas.setOnMousePressed(e -> {
                canvas.setUserData(new float[]{(float)e.getX(), (float)e.getY()});
            });

            canvas.setOnMouseDragged(e -> {
                float[] lastPos = (float[]) canvas.getUserData();
                if (lastPos != null) {
                    offsetX += (e.getX() - lastPos[0]) / scale;
                    offsetY += (e.getY() - lastPos[1]) / scale;
                    canvas.setUserData(new float[]{(float)e.getX(), (float)e.getY()});
                    drawGraph();
                }
            });

            Scene scene = new Scene(root, 1200, 900);
            stage.setTitle("Extended Graph Visualization - Road Network");
            stage.setScene(scene);

            // Draw initial graph
            drawGraph();

            stage.show();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void drawGraph() {
        // Clear the canvas
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Get graph components
        GraphBuilder builder = model.getGraphBuilder();
        EdgeWeightedDigraph graph = model.getRoadGraph();

        if (builder == null || graph == null) {
            System.err.println("Graph not built properly");
            gc.setFill(Color.WHITE);
            gc.fillText("Graph not available. Building graph...", 50, 50);

            // Try to build the graph
            try {
                model.buildRoadGraph();
                builder = model.getGraphBuilder();
                graph = model.getRoadGraph();
            } catch (Exception e) {
                gc.fillText("Error building graph: " + e.getMessage(), 50, 70);
                return;
            }

            if (builder == null || graph == null) {
                gc.fillText("Failed to build graph", 50, 70);
                return;
            }
        }

        // First pass: collect and analyze coordinates
        if (scale == 1.0f && offsetX == 0.0f && offsetY == 0.0f) {
            analyzeCoordinates(builder);
        }

        // Draw based on the current viewport (scale and offset)
        float centerX = (float)(canvas.getWidth() / 2);
        float centerY = (float)(canvas.getHeight() / 2);

        // Sample rate based on scale (show more vertices when zoomed in)
        int sampleRate = Math.max(1, (int)(builder.getVertexCount() / (1000 * scale)));
        sampleRate = Math.max(1, sampleRate); // Ensure at least 1

        // Draw edges
        gc.setStroke(Color.GREEN);
        gc.setLineWidth(0.5f * FloatMath.min(1.0f, scale));

        int edgeCount = 0;
        int maxEdgesToDraw = Math.min(50000, graph.E());

        for (int v = 0; v < builder.getVertexCount(); v += sampleRate) {
            float[] fromCoords = builder.getCoordinatesForVertex(v);
            if (fromCoords != null) {
                // Apply view transformation
                float x1 = centerX + (fromCoords[0] + offsetX) * scale;
                float y1 = centerY + (fromCoords[1] + offsetY) * scale;

                // Skip if outside of canvas with some margin
                if (x1 < -100 || x1 > canvas.getWidth() + 100 ||
                        y1 < -100 || y1 > canvas.getHeight() + 100) {
                    continue;
                }

                try {
                    for (DirectedEdge e : graph.adj(v)) {
                        if (e.to() % sampleRate != 0) continue; // Skip edges to non-sampled vertices

                        float[] toCoords = builder.getCoordinatesForVertex(e.to());
                        if (toCoords != null) {
                            // Apply view transformation
                            float x2 = centerX + (toCoords[0] + offsetX) * scale;
                            float y2 = centerY + (toCoords[1] + offsetY) * scale;

                            // Skip if outside of canvas with some margin
                            if (x2 < -100 || x2 > canvas.getWidth() + 100 ||
                                    y2 < -100 || y2 > canvas.getHeight() + 100) {
                                continue;
                            }

                            gc.beginPath();
                            gc.moveTo(x1, y1);
                            gc.lineTo(x2, y2);
                            gc.stroke();

                            if (++edgeCount > maxEdgesToDraw) break;
                        }
                    }
                } catch (Exception ex) {
                    // Skip this vertex if there's an error accessing its adjacency list
                    continue;
                }

                if (edgeCount > maxEdgesToDraw) break;
            }
        }

        // Draw vertices
        gc.setFill(Color.RED);
        float vertexSize = 3.0f * FloatMath.min(1.0f, scale);

        int vertexCount = 0;
        for (int v = 0; v < builder.getVertexCount(); v += sampleRate) {
            float[] coords = builder.getCoordinatesForVertex(v);
            if (coords != null) {
                // Apply view transformation
                float x = centerX + (coords[0] + offsetX) * scale;
                float y = centerY + (coords[1] + offsetY) * scale;

                // Only draw if within canvas bounds
                if (x >= 0 && x <= canvas.getWidth() && y >= 0 && y <= canvas.getHeight()) {
                    gc.fillOval(x - vertexSize/2, y - vertexSize/2, vertexSize, vertexSize);
                    vertexCount++;
                }
            }
        }

        // Draw status information
        gc.setFill(Color.WHITE);
        gc.fillText("Vertices: " + builder.getVertexCount() + " (showing ~" + vertexCount + ")", 20, 30);
        gc.fillText("Edges: " + graph.E() + " (showing ~" + edgeCount + ")", 20, 50);
        gc.fillText("Scale: " + String.format("%.2f", scale) + ", Offset: (" +
                String.format("%.2f", offsetX) + ", " + String.format("%.2f", offsetY) + ")", 20, 70);

        // Show road type counts
        int yPos = 90;
        String[] roadTypes = {"motorway", "trunk", "primary", "secondary",
                "tertiary", "unclassifiedhighway", "residentialroad",
                "serviceroad", "track", "path", "cycleway"};

        for (String roadType : roadTypes) {
            ArrayList<Way> roads = model.getTypeWays().get(roadType);
            if (roads != null && !roads.isEmpty()) {
                gc.fillText(roadType + ": " + roads.size(), 20, yPos);
                yPos += 15;
            }
        }

        // Update status label
        statusLabel.setText("Showing approximately " + vertexCount + " vertices and " + edgeCount +
                " edges at scale " + String.format("%.2f", scale));
    }

    private void analyzeCoordinates(GraphBuilder builder) {
        System.out.println("Analyzing coordinate ranges...");

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;

        int validCount = 0;

        // First pass: find basic bounds
        for (int v = 0; v < builder.getVertexCount(); v++) {
            float[] coords = builder.getCoordinatesForVertex(v);
            if (coords != null) {
                validCount++;
                minX = FloatMath.min(minX, coords[0]);
                minY = FloatMath.min(minY, coords[1]);
                maxX = FloatMath.max(maxX, coords[0]);
                maxY = FloatMath.max(maxY, coords[1]);
            }
        }

        System.out.println("Found " + validCount + " valid vertices with coordinates");
        System.out.println("Raw bounds: X [" + minX + " to " + maxX + "], Y [" + minY + " to " + maxY + "]");

        // Check if all coordinates are very close together
        float width = maxX - minX;
        float height = maxY - minY;

        System.out.println("Graph dimensions: width=" + width + ", height=" + height);

        if (width < 0.001f || height < 0.001f) {
            System.out.println("WARNING: Coordinates are too close together, likely a coordinate system issue");

            // Display a sample of coordinates for debugging
            System.out.println("Sample coordinates:");
            for (int v = 0; v < Math.min(10, builder.getVertexCount()); v++) {
                float[] coords = builder.getCoordinatesForVertex(v);
                if (coords != null) {
                    System.out.println("Vertex " + v + ": (" + coords[0] + ", " + coords[1] + ")");
                }
            }

            // Suggest a solution by spreading the vertices
            System.out.println("Attempting to spread out vertices...");

            // Calculate a reasonable scale
            float spreadFactor = (float)Math.min(canvas.getWidth(), canvas.getHeight()) / 4;
            scale = spreadFactor / FloatMath.max(width, height);

            // Center the graph
            offsetX = -minX - width/2;
            offsetY = -minY - height/2;

            System.out.println("Applied scale: " + scale + ", offset: (" + offsetX + ", " + offsetY + ")");
        }
    }

    private void generateRandomView() {
        // Generate a random viewport to explore different parts of the graph
        GraphBuilder builder = model.getGraphBuilder();

        if (builder.getVertexCount() == 0) {
            System.out.println("No vertices to explore");
            return;
        }

        // Pick a random vertex
        int randomVertex = random.nextInt(builder.getVertexCount());
        float[] coords = builder.getCoordinatesForVertex(randomVertex);

        if (coords != null) {
            // Center on this vertex
            offsetX = -coords[0];
            offsetY = -coords[1];

            // Random scale between 10 and 100
            scale = 10 + random.nextFloat() * 90;

            System.out.println("Random view centered on vertex " + randomVertex +
                    " at (" + coords[0] + ", " + coords[1] + ") with scale " + scale);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}