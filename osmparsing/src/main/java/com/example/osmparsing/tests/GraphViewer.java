package com.example.osmparsing.tests;

import com.example.osmparsing.algorithms.DirectedEdge;
import com.example.osmparsing.algorithms.EdgeWeightedDigraph;
import com.example.osmparsing.mvc.Model;
import com.example.osmparsing.utility.FloatMath;
import com.example.osmparsing.utility.GraphBuilder;
import com.example.osmparsing.way.Node;
import com.example.osmparsing.way.Way;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.stage.Stage;
import javafx.geometry.Point2D;

/**
 * Standalone graph visualization for debugging purposes
 */
public class GraphViewer extends Application {
    private Canvas canvas = new Canvas(800, 600);
    private GraphicsContext gc = canvas.getGraphicsContext2D();
    private Affine transform = new Affine();
    private Model model;
    private float lastX, lastY;
    private float minLon, maxLon, minLat, maxLat;

    @Override
    public void start(Stage stage) throws Exception {
        stage.setTitle("OSM Graph Viewer");

        // Load the model
        String filename = "C:\\Users\\Joach\\Documents\\BFST2025Group27\\osmparsing\\data\\bornholm.osm"; // Update with your file
        try {
            model = Model.load(filename);
            System.out.println("Loaded OSM data with " + model.ways.size() + " ways");

            // Build the graph
            long startTime = System.currentTimeMillis();
            model.buildRoadGraph();
            long endTime = System.currentTimeMillis();
            System.out.println("Graph built in " + (endTime - startTime) + "ms");

        } catch (Exception e) {
            System.err.println("Error loading model: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

        // Create the UI
        BorderPane root = new BorderPane(canvas);
        createButtons(root);

        // Set up mouse handlers for panning and zooming
        setupMouseHandlers();

        // Initial transformation to center the map
        resetView();

        // Draw the initial view
        redraw();

        // Set up the scene
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
    }

    private void createButtons(BorderPane pane) {
        Button zoomInBtn = new Button("+");
        Button zoomOutBtn = new Button("-");
        Button resetBtn = new Button("Reset");

        zoomInBtn.setOnAction(e -> zoom((float)(canvas.getWidth() / 2), (float)(canvas.getHeight() / 2), 1.3f));
        zoomOutBtn.setOnAction(e -> zoom((float)(canvas.getWidth() / 2), (float)(canvas.getHeight() / 2), 0.7f));
        resetBtn.setOnAction(e -> resetView());

        HBox toolbar = new HBox(5, zoomOutBtn, zoomInBtn, resetBtn);
        toolbar.setStyle("-fx-padding: 5px; -fx-background-color: #eee;");
        pane.setTop(toolbar);
    }

    private void setupMouseHandlers() {
        canvas.setOnMousePressed(e -> {
            lastX = (float)e.getX();
            lastY = (float)e.getY();
        });

        canvas.setOnMouseDragged(e -> {
            float dx = (float)e.getX() - lastX;
            float dy = (float)e.getY() - lastY;
            pan(dx, dy);
            lastX = (float)e.getX();
            lastY = (float)e.getY();
        });

        canvas.setOnScroll(e -> {
            float factor = (float)Math.pow(1.01, e.getDeltaY());
            zoom((float)e.getX(), (float)e.getY(), factor);
        });
    }

    private void resetView() {
        transform = new Affine();

        // Calculate the scale to fit the map
        float width = maxLon - minLon;
        float height = maxLat - minLat;

        float scaleX = (float)(canvas.getWidth() / (0.56f * width)); // 0.56 is from the conversion factor in Way
        float scaleY = (float)(canvas.getHeight() / height);
        float scale = FloatMath.min(scaleX, scaleY) * 0.9f; // 90% to leave some margin

        // Center the map
        float centerX = (float)(canvas.getWidth() / 2);
        float centerY = (float)(canvas.getHeight() / 2);
        float mapCenterX = 0.56f * (minLon + maxLon) / 2;
        float mapCenterY = -(minLat + maxLat) / 2; // Y is negated in the model

        transform.appendTranslation(centerX, centerY);
        transform.appendScale(scale, scale);
        transform.appendTranslation(-mapCenterX, -mapCenterY);

        redraw();
    }

    private void pan(float dx, float dy) {
        transform.prependTranslation(dx, dy);
        redraw();
    }

    private void zoom(float x, float y, float factor) {
        // First translate the center point to the origin
        transform.prependTranslation(-x, -y);
        // Then scale
        transform.prependScale(factor, factor);
        // Then translate back
        transform.prependTranslation(x, y);
        redraw();
    }

    private void redraw() {
        // Clear the canvas
        gc.setTransform(new Affine());
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Apply the current transformation
        gc.setTransform(transform);

        // Draw the graph
        if (model.getRoadGraph() != null) {
            drawGraph();
        }
    }

    private void drawGraph() {
        GraphBuilder builder = model.getGraphBuilder();
        EdgeWeightedDigraph graph = model.getRoadGraph();

        // Draw vertices
        gc.setFill(Color.RED);
        float vertexSize = 4.0f / (float)Math.sqrt(transform.determinant());

        // Draw vertices (sample to avoid cluttering)
        for (int v = 0; v < builder.getVertexCount(); v += 5) {
            float[] coords = builder.getCoordinatesForVertex(v);
            if (coords != null) {
                gc.fillOval(coords[0] - vertexSize/2, coords[1] - vertexSize/2, vertexSize, vertexSize);
            }
        }

        // Draw edges
        gc.setStroke(Color.GREEN);
        gc.setLineWidth(1.0f / (float)Math.sqrt(transform.determinant()));

        // Draw edges (sample to avoid cluttering)
        int edgeCount = 0;
        for (int v = 0; v < builder.getVertexCount(); v += 10) {
            float[] fromCoords = builder.getCoordinatesForVertex(v);
            if (fromCoords != null) {
                for (DirectedEdge e : graph.adj(v)) {
                    float[] toCoords = builder.getCoordinatesForVertex(e.to());
                    if (toCoords != null) {
                        gc.beginPath();
                        gc.moveTo(fromCoords[0], fromCoords[1]);
                        gc.lineTo(toCoords[0], toCoords[1]);
                        gc.stroke();

                        // Limit the number of edges drawn
                        if (++edgeCount > 5000) break;
                    }
                }
                if (edgeCount > 5000) break;
            }
        }

        // Show statistics
        gc.setTransform(new Affine()); // Reset transform for text
        gc.setFill(Color.WHITE);
        gc.fillText("Vertices: " + builder.getVertexCount(), 10, 20);
        gc.fillText("Edges: " + graph.E(), 10, 40);
    }

    // Convert from screen to model coordinates

    public static void main(String[] args) {
        launch(args);
    }
}