package com.example.osmparsing;

import com.example.osmparsing.mvc.Controller;
import com.example.osmparsing.mvc.Model;
import com.example.osmparsing.mvc.UIElements;
import com.example.osmparsing.mvc.View;
import com.example.osmparsing.way.Way;
import javafx.application.Application;
import javafx.stage.Stage;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import com.example.osmparsing.address.OSMAddress;
import com.example.osmparsing.address.AddressHandler;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;


import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException, XMLStreamException, ClassNotFoundException {
        String filename = "C:\\Users\\Joach\\Documents\\BFST2025Group27\\osmparsing\\data\\denmark-latest.osm.obj";
        var model = Model.load(filename);
        System.out.println("ADRESSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS");
        System.out.println("adresshandler size " + model.addressHandlerSize());
        model.printid2NodeSortCount();
        var view = new View(model, stage);
        new Controller(model, view);
    }

    public static void main(String[] args) {
        launch();
    }
}