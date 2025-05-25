package com.example.osmparsing.mvc;

import com.example.osmparsing.address.AddressHandler;
import com.example.osmparsing.algorithms.DirectedEdge;
import com.example.osmparsing.utility.PointOfInterest;
import com.example.osmparsing.utility.RouteGuidance;
import com.example.osmparsing.utility.RouteHandler;
import com.example.osmparsing.way.StylesUtility;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import com.example.osmparsing.utility.TransportMode;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import java.util.List;


public class UIElements {

    private ProgressBar progressBar;
    private Label zoomLabel;

    Boolean nightMode = false;
    public TextField hoverNodeField;

    VBox routeVBox = new VBox();
    private double xOffset = 0;
    private double yOffset = 0;

    //Has to be updated in redraw
    public void UpdateScaleBar(View view) {
        updateScaleBar(view.getZoomLevel());
    }

    public void drawAllUI(StackPane container, View view) {
        drawSimpleScaleBar(container, view);
        drawZoomButtons(container, view);
        createNightModeButton(container, view);
        createHoverFieldWithToggleButton(container, view);
        addPOI(container, view);
    }

    private void drawZoomButtons(StackPane container, View view) {
        // Define button dimensions
        double btnWidth = 40;
        double btnHeight = 40;

        // Create buttons
        Button btnMinus = createButton("-", btnWidth, btnHeight);
        Button btnPlus = createButton("+", btnWidth, btnHeight);

        // Position buttons (using a VBox for vertical layout)
        VBox buttonGroup = new VBox(5); // 5px spacing
        buttonGroup.setAlignment(Pos.TOP_RIGHT);
        buttonGroup.setPickOnBounds(false);
        buttonGroup.getChildren().addAll(btnPlus, btnMinus);

        // Position group in StackPane
        StackPane.setAlignment(buttonGroup, Pos.TOP_RIGHT);
        StackPane.setMargin(buttonGroup, new Insets(5, 25, 10, 0));

        // Add to root
        container.getChildren().add(buttonGroup);

        // Add button handlers
        btnMinus.setOnAction(e -> view.zoom(0.7));
        btnPlus.setOnAction(e -> view.zoom(1.3));
    }

    //Used for the zoomButtons
    private Button createButton(String text, double width, double height) {
        Button btn = new Button(text);
        btn.setPrefSize(width, height);
        btn.setStyle("""
        -fx-background-color: rgba(0, 0, 0, 0.8);
        -fx-text-fill: white;
        -fx-background-radius: 5;
        -fx-border-color: white;
        -fx-border-radius: 5;
        -fx-font-size: 16px;
        -fx-cursor: hand;
    """);
        return btn;
    }

    private void drawSimpleScaleBar(StackPane container, View view) {
        // Create HBox container
        HBox scaleBar = new HBox(5);
        scaleBar.setId("scaleBar");
        scaleBar.setPickOnBounds(false);
        scaleBar.setAlignment(Pos.BOTTOM_LEFT);
        //scaleBar.setPadding(new Insets(5));

        // Create ProgressBar
        progressBar = new ProgressBar(view.getZoomLevel()/20.0);
        progressBar.setPrefWidth(200);
        progressBar.setPrefHeight(20);

        // Style the progress bar
        progressBar.setStyle("-fx-accent: red; -fx-control-inner-background: black;");

        // Create label
        zoomLabel = new Label("Zoom: " + view.getZoomLevel());
        zoomLabel.setTextFill(Color.WHITE);

        // Add components
        scaleBar.getChildren().addAll(progressBar, zoomLabel);
        container.getChildren().add(scaleBar);

        // Position in StackPane
        StackPane.setAlignment(progressBar, Pos.BOTTOM_LEFT);
        StackPane.setMargin(scaleBar, new Insets(0,25,45,0));
    }

    private void updateScaleBar(int newZoomLevel) {
        if (progressBar != null) {
            progressBar.setProgress(newZoomLevel / 20.0);
            zoomLabel.setText("Zoom: " + newZoomLevel);
        }
    }

    public void createNightModeButton(StackPane container, View view) {
        // Create a horizontal container for the text field + button
        HBox bottomRightBox = new HBox(10);
        bottomRightBox.setAlignment(Pos.BOTTOM_RIGHT);
        bottomRightBox.setPadding(new Insets(0, 25, 50, 0)); // Bottom padding
        bottomRightBox.setPickOnBounds(false); // Important: allows mouse events to pass through

        // Create the hover text field
        hoverNodeField = new TextField();
        hoverNodeField.setPromptText("Hover node info");
        hoverNodeField.setEditable(false);
        hoverNodeField.setMaxWidth(200);
        hoverNodeField.setMouseTransparent(true); // Don't block mouse events

        // Create the day/night button
        Button nightButton = new Button();
        setButtonColor(nightButton);

        nightButton.setOnMouseClicked(e -> {
            if (!nightMode) {
                StylesUtility.switchColors(true);
                view.gcBackgroundColor = "#0d485a";
                nightMode = true;
            } else {
                StylesUtility.switchColors(false);
                view.gcBackgroundColor = "#27bbe8";
                nightMode = false;
            }
            setButtonColor(nightButton);
            view.redraw();
        });

        // Add text field and button to the HBox
        bottomRightBox.getChildren().addAll(hoverNodeField, nightButton);

        // Add the box to the container and align bottom right
        container.getChildren().add(bottomRightBox);
        StackPane.setAlignment(bottomRightBox, Pos.BOTTOM_RIGHT);
    }

    private void setButtonColor(Button button) {
        if(nightMode) {
            button.setStyle(
                    "-fx-background-color: black; " +
                            "-fx-text-fill: white; " +
                            "-fx-font-size: 10px;" +
                            "-fx-cursor: hand;"
            );
            button.setText("Night");
        } else {
            button.setStyle(
                    "-fx-background-color: white; " +
                            "-fx-text-fill: black; " +
                            "-fx-font-size: 10px;" +
                            "-fx-cursor: hand;"
            );
            button.setText("Day");
        }
    }


    public void drawTextField(StackPane container, Model model, View view) {
        // Create the main container for both text fields
        HBox fieldsContainer = new HBox();
        fieldsContainer.setPickOnBounds(false);//Allows clicks to pass through empty HBox areas.
        fieldsContainer.setSpacing(5); // Add spacing between the fields
        fieldsContainer.setPadding(new Insets(5)); // Add padding around the container

        // Create first text field
        TextField toField = new TextField();
        toField.setPromptText("Enter address");
        toField.setMaxWidth(200);

        // Create second text field
        TextField fromField = new TextField();
        fromField.setPromptText("From address");
        fromField.setMaxWidth(200);

        // Create a RouteHandler for finding routes
        RouteHandler routeHandler = new RouteHandler(model);

        // Create search button
        Button searchButton = new Button("Search");
        searchButton.setStyle("-fx-font-size: 12px;" + "-fx-cursor: hand;");

        // Create route button
        Button routeButton = new Button("Route");
        routeButton.setStyle("-fx-font-size: 12px;" + "-fx-text-fill: BLACK;" + "-fx-background-color: #6aaa6a;" + "-fx-cursor: hand;");
        routeButton.setVisible(false);

        // Transport mode toggle group
        ToggleGroup transportGroup = new ToggleGroup();

        // Car button
        ToggleButton carButton = new ToggleButton();
        carButton.setStyle("-fx-cursor: hand;");
        carButton.setVisible(false); //is not visible when no address search
        carButton.setToggleGroup(transportGroup);
        carButton.setSelected(true); // Default to car
        carButton.setUserData(TransportMode.CAR);
        carButton.setText("🚗"); // Use emoji or load image

        // Bicycle button
        ToggleButton bicycleButton = new ToggleButton();
        bicycleButton.setStyle("-fx-cursor: hand;");
        bicycleButton.setVisible(false);
        bicycleButton.setToggleGroup(transportGroup);
        bicycleButton.setUserData(TransportMode.BICYCLE);
        bicycleButton.setText("🚴");

        // Add button listeners
        searchButton.setOnAction(e -> {
            if(toField.getText().isEmpty()) {
                flashTextFieldRed(toField);
            }if(fromField.getText().isEmpty()){
                flashTextFieldRed(fromField);
            }
            else{
                findRoute(fromField, toField, view, routeHandler, model);
                carButton.setVisible(true);
                bicycleButton.setVisible(true);
                routeButton.setVisible(true);
            }
        });
        carButton.setOnAction(e -> {
            model.setTransportMode(TransportMode.CAR);
            searchButton.fire();
        });
        bicycleButton.setOnAction(e -> {
            model.setTransportMode(TransportMode.BICYCLE);
            searchButton.fire();
        });
        routeButton.setOnAction(e -> {
            popUpAddressRoute(view.stage);
        });

        Button clearButton = new Button("Clear");
        clearButton.setStyle("-fx-font-size: 12px; -fx-background-color: #f44336; -fx-text-fill: white;" + "-fx-cursor: hand;");
        clearButton.setVisible(false);
        clearButton.setOnAction(e -> {
            view.clearRoute();
            fromField.clear();
            toField.clear();
            carButton.setVisible(false);
            bicycleButton.setVisible(false);
            routeButton.setVisible(false);
            clearButton.setVisible(false);
        });

        // Add both fields to the HBox
        fieldsContainer.getChildren().addAll(toField, fromField, searchButton, carButton, bicycleButton, routeButton, clearButton);

        // Position the container at top-left corner
        fieldsContainer.setLayoutX(5);  // 5px from left edge
        fieldsContainer.setLayoutY(5);  // 5px from top edge

        // Add the container to the StackPane
        container.getChildren().add(fieldsContainer);

        // Set alignment to top-left in StackPane
        StackPane.setAlignment(fieldsContainer, Pos.TOP_LEFT);

        // Autocomplete functionality for the text fields
        setupAutocomplete(toField, model);
        setupAutocomplete(fromField, model);
    }

    private void setupAutocomplete(TextField field, Model model) {
        //Get address from the model file
        AddressHandler addressHandler = model.getAddressHandler();

        ListView<String> listView = new ListView<>();
        // Limit the visible row count to 3
        listView.setFixedCellSize(24); // set fixed cell height
        listView.setPrefHeight(3 * 24 + 2); // 3 items * cell height + borders

        PopupControl popup = new PopupControl();
        popup.getScene().setRoot(listView);
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);

        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.isEmpty()) {
                popup.hide();
            } else {
                ObservableList<String> filtered = FXCollections.observableArrayList();
                int count = 0;
                String search = newVal.toLowerCase();

                for (String suggestion : addressHandler.findAddressesByPrefix(search)) {
                    if (suggestion.toLowerCase().contains(search)) {
                        filtered.add(suggestion);
                        if (++count >= 10) break; // Show top 10 matches
                    }
                }

                listView.setItems(filtered);

                if (!filtered.isEmpty()) {
                    if (!popup.isShowing()) {
                        popup.show(field,
                                field.localToScreen(0, 0).getX(),
                                field.localToScreen(0, field.getHeight()).getY()
                        );
                    }
                } else {
                    popup.hide();
                }
            }
        });

        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                field.setText(listView.getSelectionModel().getSelectedItem());
                popup.hide();
            }
        });
        listView.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                field.setText(listView.getSelectionModel().getSelectedItem());
                popup.hide();
            }
        });
    }

    // flashError effect for the searchFields
    //the "..." means to can add either a single or multiple arguments. Even an array
    private void flashTextFieldRed(TextField textFields) {
        // Save original styles
        String originalStyles = textFields.getStyle();

        // Apply red border style
        String errorStyle = "-fx-border-color: red; -fx-border-width: 2px; -fx-background-color: darkred";
        textFields.setStyle(errorStyle);

        // Create a timeline to revert after 1.5 seconds
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(1.5),
                        e -> {
                            textFields.setStyle(originalStyles);
                        }
                ));
        timeline.play();
    }
    private void findRoute(TextField fromField, TextField toField, View view, RouteHandler routeHandler, Model model) {
        String fromAddress = fromField.getText();
        String toAddress = toField.getText();

        System.out.println("Finding route from: " + fromAddress + " to: " + toAddress);

        // Find route
        List<DirectedEdge> route = routeHandler.findRoute(fromAddress, toAddress);

        if (route != null && !route.isEmpty()) {
            // Display the route on the map
            view.displayRoute(route);

            // Show the clear button
            for (javafx.scene.Node node : ((HBox)fromField.getParent()).getChildren()) {
                if (node instanceof Button && ((Button)node).getText().equals("Clear")) {
                    node.setVisible(true);
                    break;
                }
            }

            // Generate and print route guidance
            RouteGuidance guidance = new RouteGuidance(
                    route,
                    model.getGraphBuilder(),
                    model.getRoadGraph()
            );
            guidance.printRoute();
            routeVBox = guidance.routeToText();
        } else {
            // No route found
            System.out.println("Could not find a route between these addresses.");

            // You could optionally show an alert or dialog here
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Route Not Found");
            alert.setHeaderText("Unable to find route");
            alert.setContentText("No route could be found between the specified addresses.");
            alert.showAndWait();
        }
    }

    public void createHoverFieldWithToggleButton(StackPane container, View view) {
        VBox bottomRightBox = new VBox(5); // spacing between button and textfield
        bottomRightBox.setAlignment(Pos.BOTTOM_RIGHT);
        bottomRightBox.setPadding(new Insets(0, 225, 50, 0));
        bottomRightBox.setPickOnBounds(false); // Important: allows mouse events to pass through

        // Button above the hover field
        Button toggleDrawButton = new Button("Toggle Circle");
        toggleDrawButton.setStyle("-fx-cursor: hand;");
        toggleDrawButton.setOnAction(e -> {
            if (view.drawCircle){
                view.drawCircle = false;
            } else {
                view.drawCircle = true;
            }
            //System.out.println("Draw toggled.");
        });
        // Add to container
        bottomRightBox.getChildren().addAll(toggleDrawButton);
        container.getChildren().add(bottomRightBox);
        StackPane.setAlignment(bottomRightBox, Pos.BOTTOM_RIGHT);
    }

    public void addPOI(StackPane container, View view) {
        VBox bottomRightBox = new VBox(10);
        bottomRightBox.setAlignment(Pos.BOTTOM_RIGHT);
        bottomRightBox.setPadding(new Insets(0, 320, 50, 0));
        bottomRightBox.setPickOnBounds(false); // Important: allows mouse events to pass through

        Button addPoiButton  = new Button("Add POI");
        addPoiButton.setStyle("-fx-cursor: hand;");
        addPoiButton.setOnAction(e -> {
            view.togglePOI = true;
            System.out.println("Toggle POI: " + view.togglePOI);
        });
        // Add to container
        bottomRightBox.getChildren().addAll(addPoiButton);
        container.getChildren().add(bottomRightBox);
        StackPane.setAlignment(bottomRightBox, Pos.BOTTOM_RIGHT);
    }

    public void showPoiInput(StackPane container, View view, float x, float y) {
        float[] point = view.viewport.screenToWorld(x, y);

        TextField poiNameField = new TextField();
        poiNameField.setPromptText("Enter POI name");

        Button confirmButton = new Button("OK");
        HBox inputBox = new HBox(10, poiNameField, confirmButton);
        inputBox.setAlignment(Pos.CENTER);
        inputBox.setStyle("-fx-padding: 50px; -fx-border-color: black;" + "-fx-cursor: hand;");

        StackPane.setAlignment(inputBox, Pos.CENTER);
        container.getChildren().add(inputBox);

        confirmButton.setOnAction(ev -> {
            String label = poiNameField.getText();
            if (!label.isEmpty()) {
                view.pois.add(new PointOfInterest(point[0], point[1], label));
                container.getChildren().remove(inputBox);
                view.redraw();
                System.out.println(point[0] + " " + point[1] + " Size:" + view.pois.size() + " Text: " + label);
            }
        });
        poiNameField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                confirmButton.fire();
            }
        });

    }

    public void popUpAddressRoute(Stage stage) {

        Stage popupStage = new Stage();
        popupStage.initOwner(stage);
        popupStage.initStyle(StageStyle.UTILITY); // Or StageStyle.UNDECORATED

        Button copyButton = new Button("Copy to clipboard");
        copyButton.setStyle("-fx-padding: 5px;" + "-fx-cursor: hand;");
        copyButton.setAlignment(Pos.CENTER);
        routeVBox.getChildren().add(copyButton);

        VBox root = new VBox(routeVBox);
        Scene scene = new Scene(root, root.getPrefWidth(), root.getPrefHeight());

        copyButton.setOnAction(e -> {
            StringBuilder allText = new StringBuilder();
            // Loop through all children in the VBox
            for (Node node : routeVBox.getChildren()) {
                if (node instanceof Text textNode) {  // Ensure it's a Text node
                    allText.append(textNode.getText()).append("\n"); // Add newline after each
                }
            }
            ClipboardContent content = new ClipboardContent();
            content.putString(allText.toString());
            Clipboard.getSystemClipboard().setContent(content);
            copyButton.setText("Copy to clipboard");
        });

        // Make the popup draggable
        root.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        root.setOnMouseDragged(event -> {
            popupStage.setX(event.getScreenX() - xOffset);
            popupStage.setY(event.getScreenY() - yOffset);
        });

        popupStage.setScene(scene);
        popupStage.show();

        System.out.println("Pop up address route");
    }
}