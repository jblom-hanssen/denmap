package com.example.osmparsing.mvc;

import com.example.osmparsing.algorithms.EdgeWeightedDigraph;
import com.example.osmparsing.algorithms.DirectedEdge;
import com.example.osmparsing.algorithms.Viewport;
import com.example.osmparsing.utility.FloatMath;
import com.example.osmparsing.utility.GraphBuilder;
import com.example.osmparsing.utility.PointOfInterest;
import com.example.osmparsing.way.*;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.scene.layout.HBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class View {
    float height = 640.0F;
    float width = 480.0F;
    Canvas canvas = new Canvas(height, width);
    GraphicsContext gc = canvas.getGraphicsContext2D();
    private boolean showGraph = false;
    private HBox toolbar; // Make toolbar accessible
    private List<DirectedEdge> currentRoute;
    private boolean showRoute = false;

    UIElements uiElements = new UIElements();
    public String gcBackgroundColor = "#27bbe8";

    int maxZoom = 20;
    int minZoom = 0;
    int zoomLevel;

    float x = 0;
    float y = 0;
    int radius = 300;
    float[] circleCenterLatLon = null;

    Affine trans = new Affine();

    Model model;
    Stage stage;
    Viewport viewport;

    boolean drawCircle = false;

    public boolean togglePOI = false;
    List<PointOfInterest> pois = new ArrayList<>();
    public StackPane canvasContainer;

    public View(Model model, Stage stage) {

        this.model = model;
        this.stage = stage;
        viewport = new Viewport(this.model.minlon, this.model.maxlon, this.model.minlat, this.model.maxlat, (float) canvas.getWidth(), (float) canvas.getHeight());
        stage.setTitle("Draw Lines");
        BorderPane pane = new BorderPane(canvas);
        // Create a StackPane to hold canvas and UI elements
        canvasContainer = new StackPane();
        canvasContainer.getChildren().add(canvas);

        // Add the canvas container to center of BorderPane
        pane.setCenter(canvasContainer);

        uiElements.drawTextField(canvasContainer, model, this);// Add the TextField
        uiElements.drawAllUI(canvasContainer, this);// Add the day/night button

        Scene scene = new Scene(pane);
        stage.setScene(scene);
        // Add resize listener to the scene
        scene.widthProperty().addListener((obs, oldVal, newVal) -> handleResize());
        scene.heightProperty().addListener((obs, oldVal, newVal) -> handleResize());

        stage.show();
        redraw();

        float initialFactor = (float) (canvas.getHeight() / (model.maxlat - model.minlat));
        //ZoomLevel is calculated based on the initialFactor and approximates (log2 scale)
        zoomLevel = (int) Math.round(Math.log(initialFactor)-5); //-int is a offset that can be changed
        zoomLevel = Math.min(maxZoom, Math.max(minZoom, zoomLevel)); // Clamp zoomLevel
        System.out.println("Initial Zoom Level: " + zoomLevel);

        pan((float) (-0.56 * model.minlon), model.maxlat);
        zoom(initialFactor);
        System.out.println("Start factor : " + initialFactor);
    }

    private void handleResize() {
        //Use platform if more performance is needed.
        //Platform.runLater(() -> {
        canvas.setWidth(stage.getScene().getWidth());
        canvas.setHeight(stage.getScene().getHeight());
        viewport.updateScreenSize((float) canvas.getWidth(), (float) canvas.getHeight());
        redraw();
        //});
    }

    public void redraw() {
        gc.setTransform(new Affine());
        gc.setFill(Color.web(gcBackgroundColor));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc.setTransform(trans);
        gc.setLineWidth(1 / FloatMath.sqrt((float) trans.determinant()));
        viewport.updateBounds(canvas);

        for (Line line : model.list) {
            line.draw(gc);
        }


        for (MultiPolygon multiPolygon : model.multiPolygons) {
            if (multiPolygon.getType().equals("coastline")) {
                gc.setStroke(Color.BLACK);
                multiPolygon.draw(gc); // New combined method
            } else {
                gc.setFill(StylesUtility.getColor(multiPolygon.getType()));
                multiPolygon.fill2(gc);
            }
        }
        for (Map.Entry<String, ArrayList<Way>> entry : model.typeWays.entrySet()) {
            String type = entry.getKey();
            if (type.equals("building")) continue; // Skip buildings in main loop
            if (zoomLevel > StylesUtility.getZoomLevelRender(type)) {
                ArrayList<Way> typeWaysList = entry.getValue();
                if (StylesUtility.isFillable(type)) {
                    gc.setFill(StylesUtility.getColor(type));
                    for (Way way : typeWaysList) {
                        if (way.bbox == null || !viewport.isBBoxVisibleWithBuffer(way.bbox, 0.1f)) continue;
                        way.fill(gc);
                    }
                } else {
                    gc.setStroke(StylesUtility.getColor(type));
                    for (Way way : typeWaysList) {
                        if (way.bbox == null || !viewport.isBBoxVisibleWithBuffer(way.bbox, 0.1f)) continue;
                        way.fill(gc);
                    }
                }
            }
        }
        // Now draw buildings on top
        if (model.typeWays.containsKey("building") &&
                zoomLevel > StylesUtility.getZoomLevelRender("building")) {

            ArrayList<Way> buildings = model.typeWays.get("building");
            gc.setFill(StylesUtility.getColor("building"));
            for (Way way : buildings) {
                if (way.bbox == null || !viewport.isBBoxVisibleWithBuffer(way.bbox, 0.1f)) continue;
                way.fill(gc);
            }
        }

        if (drawCircle) {
            gc.save();                    // Save current transform
            gc.setTransform(new Affine()); // Reset transform to identity
            drawSearchCircle(gc, viewport, circleCenterLatLon, radius);
            gc.restore();
        }
        if (showRoute && currentRoute != null) {
            drawRoute();
        }

        for (PointOfInterest poi : pois) {
            // Insert draw logic
            drawPin(gc, poi.lat, poi.lon, 0.0005);
        }

        //All UI elements gets drawn here :)
        uiElements.UpdateScaleBar(this);
        canvas.setHeight(stage.getHeight());
        canvas.setWidth(stage.getWidth());
    }

    void pan(float dx, float dy) {
        trans.prependTranslation(dx, dy);
        // Update viewport offset
        viewport.offsetX -= dx / viewport.scale;
        viewport.offsetY -= dy / viewport.scale;
        redraw();
    }

    void zoom(double factor) {
        System.out.println("factor: " + factor);
        // Calculate proposed new zoom level (log2 scale)
        int newZoomLevel = zoomLevel + (int) Math.signum(factor - 1);

        System.out.println("New Zoom Level: " + newZoomLevel);

        // Clamp between minZoom and maxZoom
        newZoomLevel = Math.min(maxZoom, Math.max(minZoom, newZoomLevel));

        // Only apply zoom if within bounds
        if (newZoomLevel != zoomLevel) {
            trans.prependScale(factor, factor);
            viewport.scale *= factor;
            zoomLevel = newZoomLevel; // Update tracked zoom level
            redraw();
        }
    }

    public int getZoomLevel() {
        return zoomLevel;
    }

    public void drawSearchCircle(GraphicsContext gc, Viewport viewport, float[] centerLatLon, float radiusMeters) {
        float[] screenCoords = viewport.worldToScreen(centerLatLon[0], centerLatLon[1]);
        float screenX = screenCoords[0]; // lon
        float screenY = screenCoords[1]; // lat

        // Convert radius from meters to degrees
        float radiusLatDegrees = (float) (radiusMeters / 111000.0);
        float radiusLonDegrees = (float) (radiusMeters / (111000.0 * Math.cos(Math.toRadians(centerLatLon[0]))));

        // Convert degrees into screen units using scale
        float radiusX = radiusLonDegrees * viewport.scale;
        float radiusY = radiusLatDegrees * viewport.scale;

        System.out.println("Drawing circle at (" + screenX + ", " + screenY + ") with radii " + radiusX + ", " + radiusY);

        gc.setStroke(Color.RED);
        gc.setLineWidth(2);
        gc.setFill(Color.rgb(255, 0, 0, 0.2)); // semi-transparent red
        gc.strokeOval(screenX - radiusX, screenY - radiusY, radiusX * 2, radiusY * 2);
        gc.fillOval(screenX - radiusX, screenY - radiusY, radiusX * 2, radiusY * 2);
    }

    public Point2D mousetoModel(float lastX, float lastY) {
        try {
            return trans.inverseTransform(lastX, lastY);
        } catch (NonInvertibleTransformException e) {
            throw new RuntimeException(e);
        }
    }

    public HBox getToolbar() {
        return toolbar;
    }

    public void toggleGraphVisibility() {
        // Build the graph if it doesn't exist yet
        if (model.getRoadGraph() == null) {
            System.out.println("Building road network graph...");
            long startTime = System.currentTimeMillis();
            model.buildRoadGraph();
            long endTime = System.currentTimeMillis();
            System.out.println("Graph built in " + (endTime - startTime) + "ms");
        }

        // Toggle graph visibility
        showGraph = !showGraph;
        System.out.println("Graph visualization: " + (showGraph ? "enabled" : "disabled"));
        redraw();
    }

    public boolean isGraphVisible() {
        return showGraph;
    }

    private void drawGraph() {
        GraphBuilder builder = model.getGraphBuilder();
        EdgeWeightedDigraph graph = model.getRoadGraph();

        // Draw edges
        gc.setStroke(javafx.scene.paint.Color.LIMEGREEN);
        gc.setLineWidth(0.8 / Math.sqrt(trans.determinant()));

        // Sample vertices to avoid overdrawing
        int sampleRate = Math.max(1, builder.getVertexCount() / 1000);
        int edgeCount = 0;

        for (int v = 0; v < builder.getVertexCount(); v += sampleRate) {
            float[] fromCoords = builder.getCoordinatesForVertex(v);
            if (fromCoords != null) {
                for (DirectedEdge e : graph.adj(v)) {
                    if (e.to() % sampleRate == 0) { // Only connect sampled vertices
                        float[] toCoords = builder.getCoordinatesForVertex(e.to());
                        if (toCoords != null) {
                            gc.beginPath();
                            gc.moveTo(fromCoords[0], fromCoords[1]);
                            gc.lineTo(toCoords[0], toCoords[1]);
                            gc.stroke();

                            if (++edgeCount > 3000) break; // Limit edges for performance
                        }
                    }
                }
                if (edgeCount > 3000) break;
            }
        }

        // Draw vertices
        gc.setFill(javafx.scene.paint.Color.RED);
        float vertexSize = (float) (3.0 / Math.sqrt(trans.determinant()));

        for (int v = 0; v < builder.getVertexCount(); v += sampleRate) {
            float[] coords = builder.getCoordinatesForVertex(v);
            if (coords != null) {
                gc.fillOval(coords[0] - vertexSize / 2, coords[1] - vertexSize / 2, vertexSize, vertexSize);
            }
        }
    }

    public void displayRoute(List<DirectedEdge> route) {
        if (route == null || route.isEmpty()) {
            System.out.println("No route to display");
            return;
        }

        this.currentRoute = route;
        this.showRoute = true;


        // Redraw with route
        redraw();
    }

    public void clearRoute() {
        this.currentRoute = null;
        this.showRoute = false;
        redraw();
    }

    private void drawRoute() {
        if (!showRoute || currentRoute == null || currentRoute.isEmpty()) return;

        GraphBuilder builder = model.getGraphBuilder();

        // Set a distinctive style for the route
        gc.setStroke(Color.BLUE);
        gc.setLineWidth(3.0 / Math.sqrt(trans.determinant()));

        // Draw each segment of the route
        for (DirectedEdge edge : currentRoute) {
            float[] fromCoords = builder.getCoordinatesForVertex(edge.from());
            float[] toCoords = builder.getCoordinatesForVertex(edge.to());

            if (fromCoords != null && toCoords != null) {
                gc.beginPath();
                gc.moveTo(fromCoords[0], fromCoords[1]);
                gc.lineTo(toCoords[0], toCoords[1]);
                gc.stroke();
            }
        }

        // Draw route endpoints with distinctive markers
        if (!currentRoute.isEmpty()) {
            // Start point (green)
            float[] startCoords = builder.getCoordinatesForVertex(currentRoute.get(0).from());
            if (startCoords != null) {
                float markerSize = (float) (10.0 / Math.sqrt(trans.determinant()));
                gc.setFill(Color.GREEN);
                gc.fillOval(startCoords[0] - markerSize / 2, startCoords[1] - markerSize / 2, markerSize, markerSize);
            }

            // End point (red)
            float[] endCoords = builder.getCoordinatesForVertex(currentRoute.get(currentRoute.size() - 1).to());
            if (endCoords != null) {
                float markerSize = (float) (10.0 / Math.sqrt(trans.determinant()));
                gc.setFill(Color.RED);
                gc.fillOval(endCoords[0] - markerSize / 2, endCoords[1] - markerSize / 2, markerSize, markerSize);
            }
        }
    }

    private void resetView() {
        // Calculate view bounds from the model data
        float minLon = (float) Double.MAX_VALUE, maxLon = (float) Double.MIN_VALUE;
        float minLat = (float) Double.MAX_VALUE, maxLat = (float) Double.MIN_VALUE;


        System.out.println("Map bounds: Lon[" + minLon + ", " + maxLon + "], Lat[" + minLat + ", " + maxLat + "]");

        // Set up viewport with actual data bounds
        viewport = new Viewport(minLon, maxLon, minLat, maxLat, (float) canvas.getWidth(), (float) canvas.getHeight());

        // Calculate initial zoom to fit the data
        float dataWidth = maxLon - minLon;
        float dataHeight = maxLat - minLat;
        float viewWidth = (float) canvas.getWidth();
        float viewHeight = (float) canvas.getHeight();

        // Avoid division by zero
        if (dataWidth > 0 && dataHeight > 0) {
            float scaleX = viewWidth / dataWidth;
            float scaleY = viewHeight / dataHeight;
            float initialScale = (float) (Math.min(scaleX, scaleY) * 0.9); // 90% to leave margins

            System.out.println("Initial scale: " + initialScale);

            // Set initial zoom level based on scale
            zoomLevel = (int) Math.round(Math.log(initialScale) / Math.log(1.2));
            zoomLevel = Math.min(maxZoom, Math.max(minZoom, zoomLevel)); // Clamp to valid range

            System.out.println("Initial zoom level: " + zoomLevel);
        }

        // Reset transform and pan to center the map
        trans = new Affine();
        pan((float) (-0.56 * (minLon + maxLon) / 2), -(minLat + maxLat) / 2);

        redraw();
    }

    private void drawPin(GraphicsContext gc, double x, double y, double size) {
        /*
         * Draws a map pin centered horizontally at x,
         * with the bottom tip exactly at y.
         *
         * size = height of the pin from tip to top of the circle.
         */

        double circleRadius = (size * 0.4)*((maxZoom - zoomLevel)*0.5);   // circle takes about 40% of height
        double tailHeight = (size * 0.6)*((maxZoom - zoomLevel)*0.5);     // tail is the remaining 60%
        if (zoomLevel == maxZoom) {
            circleRadius = (size * 0.4)*(0.5);
            tailHeight = (size * 0.6)*(0.5);
        }

        // Coordinates for the circle center:
        double circleCenterX = x;
        double circleCenterY = y - tailHeight - circleRadius;

        // Draw pin tail (triangle)
        gc.setFill(Color.RED);
        gc.beginPath();
        gc.moveTo(x, y);                              // Tip point
        gc.lineTo(x - circleRadius, y - tailHeight); // Bottom-left of circle
        gc.lineTo(x + circleRadius, y - tailHeight); // Bottom-right of circle
        gc.closePath();
        gc.fill();
        gc.stroke();

        // Draw circle "head"
        gc.setFill(Color.RED);
        gc.fillOval(circleCenterX - circleRadius, circleCenterY - circleRadius,
                circleRadius * 2, circleRadius * 2);
        gc.setStroke(Color.BLACK);
        gc.strokeOval(circleCenterX - circleRadius, circleCenterY - circleRadius,
                circleRadius * 2, circleRadius * 2);

        // Optionally draw a smaller white circle for highlight
        gc.setFill(Color.WHITE);
        double highlightRadius = circleRadius * 0.4;
        gc.fillOval(circleCenterX - highlightRadius/2, circleCenterY - highlightRadius/2,
                highlightRadius, highlightRadius);
    }
}

