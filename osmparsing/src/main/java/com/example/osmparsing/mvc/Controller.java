package com.example.osmparsing.mvc;

import com.example.osmparsing.algorithms.KdTree;
import com.example.osmparsing.utility.PointOfInterest;
import javafx.geometry.Point2D;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.scene.input.ScrollEvent;

public class Controller {
    private float lastX;
    private float lastY;
    private static final float ZOOM_IN_FACTOR = 1.3f;
    private static final float ZOOM_OUT_FACTOR = 0.7f;

    public Controller(Model model, View view) {
        view.canvas.setOnMouseMoved(e -> {
            var nearestNode = model.kdTree.nearest(
                    view.viewport.screenToWorld((float)e.getX(), (float)e.getY())[0],
                    view.viewport.screenToWorld((float)e.getX(), (float)e.getY())[1]
            );
            view.uiElements.hoverNodeField.setText(nearestNode);
            //System.out.println("Nearest ndoe: " + nearestNode);
        });

        view.canvas.getScene().addEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, e -> {
            var nearestNode = model.kdTree.nearest(
                    view.viewport.screenToWorld((float)e.getX(), (float)e.getY())[0],
                    view.viewport.screenToWorld((float)e.getX(), (float)e.getY())[1]
            );
            view.uiElements.hoverNodeField.setText(nearestNode);
            //System.out.println("Nearest ndoe: " + nearestNode);
        });

        view.canvas.setOnMousePressed(e -> {
            lastX = (float)e.getX();
            lastY = (float)e.getY();
            if (e.isPrimaryButtonDown()) {
                if (view.drawCircle){
                    List<String> nodesInRadius = model.kdTree.findWithinRadius(
                            view.viewport.screenToWorld((float)e.getX(), (float)e.getY())[0],
                            view.viewport.screenToWorld((float)e.getX(), (float)e.getY())[1],
                            view.radius
                    );
                    Map<String, Integer> numOfEachNode = new HashMap<>();
                    for (String names: nodesInRadius) {
                        if (!numOfEachNode.containsKey(names)) {
                            numOfEachNode.put(names, 1);
                        } else {
                            numOfEachNode.put(names, numOfEachNode.get(names) + 1);
                        }
                    }
                    for (Map.Entry<String, Integer> entry: numOfEachNode.entrySet()) {
                        System.out.println(entry.getKey() + " : " + entry.getValue());
                    }
                    float[] worldCoords = view.viewport.screenToWorld((float)e.getX(), (float)e.getY());
                    //System.out.println(worldCoords[0] + " " + worldCoords[1]);
                    view.circleCenterLatLon = worldCoords;
                    view.redraw();
                }

                if (view.togglePOI){
                    view.uiElements.showPoiInput(view.canvasContainer, view, (float)e.getX(), (float)e.getY());
                    view.togglePOI = false;
                    view.redraw();
                }

            }
        });

        view.canvas.setOnMouseDragged(e -> {
            var nearestNode = model.kdTree.nearest(
                    view.viewport.screenToWorld((float)e.getX(), (float)e.getY())[0],
                    view.viewport.screenToWorld((float)e.getX(), (float)e.getY())[1]
            );
            view.uiElements.hoverNodeField.setText(nearestNode);
            //System.out.println("Nearest node (dragging): " + nearestNode);
            if (e.isPrimaryButtonDown() || e.isSecondaryButtonDown()) {
                float dx = (float)e.getX() - lastX;
                float dy = (float)e.getY() - lastY;
                view.pan(dx, dy);

                //if you want to draw on the map, then uncomment.
                /*Point2D lastModel = view.mousetoModel(lastX, lastY);
                Point2D newModel = view.mousetoModel((float)e.getX(), (float)e.getY());
                model.add(lastModel, newModel);
                view.redraw();*/
            }

            lastX = (float)e.getX();
            lastY = (float)e.getY();
        });
        view.canvas.setOnScroll(e -> {
            float zoomFactor = e.getDeltaY() > 0 ? ZOOM_IN_FACTOR : ZOOM_OUT_FACTOR;
            view.zoom(zoomFactor);
        });
    }
}