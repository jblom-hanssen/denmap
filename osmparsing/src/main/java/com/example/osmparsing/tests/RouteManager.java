package com.example.osmparsing.tests;
import com.example.osmparsing.algorithms.DirectedEdge;
import com.example.osmparsing.mvc.Model;
import com.example.osmparsing.mvc.View;
import com.example.osmparsing.utility.RouteHandler;

import java.util.List;

// First, let's create a more comprehensive implementation for the route display
public class RouteManager {
    private final Model model;
    private final View view;
    private List<DirectedEdge> currentRoute;

    public RouteManager(Model model, View view) {
        this.model = model;
        this.view = view;
        this.currentRoute = null;
    }

    public boolean findAndDisplayRoute(String startAddress, String endAddress) {
        // Ensure graph is built
        if (model.getRoadGraph() == null) {
            System.out.println("Building road graph...");
            model.buildRoadGraph();
        }

        // Use RouteHandler to find the route
        RouteHandler routeHandler = new RouteHandler(model);
        List<DirectedEdge> route = routeHandler.findRoute(startAddress, endAddress);

        if (route == null || route.isEmpty()) {
            return false;
        }

        // Store and display the route
        currentRoute = route;
        view.displayRoute(route);
        return true;
    }

    public List<DirectedEdge> getCurrentRoute() {
        return currentRoute;
    }

    public void clearRoute() {
        currentRoute = null;
        view.redraw();
    }
}