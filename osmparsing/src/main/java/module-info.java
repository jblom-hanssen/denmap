module com.example.osmparsing {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;

    requires osmosis.core;
    requires osmosis.pbf;
    requires osmosis.xml;
    requires java.desktop;

    opens com.example.osmparsing to javafx.fxml;
    exports com.example.osmparsing;
    exports com.example.osmparsing.mvc;
    opens com.example.osmparsing.mvc to javafx.fxml;
    exports com.example.osmparsing.algorithms;
    opens com.example.osmparsing.algorithms to javafx.fxml;
    exports com.example.osmparsing.utility;
    opens com.example.osmparsing.utility to javafx.fxml;
    exports com.example.osmparsing.way;
    opens com.example.osmparsing.way to javafx.fxml;
    exports com.example.osmparsing.tests;
    opens com.example.osmparsing.tests to javafx.fxml;
}