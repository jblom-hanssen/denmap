package com.example.osmparsing.mvc;

import org.openstreetmap.osmosis.core.Osmosis;

public class OsmPbfToOsmConverter {
    private String inputFile;
    private String outputFile;

    public OsmPbfToOsmConverter(String filename) {
        if (filename == null || filename.isEmpty()) {
            inputFile = "data/faroe-islands-latest.osm.pbf";
        } else {
            inputFile = filename;
        }
        outputFile = inputFile.substring(0, inputFile.lastIndexOf("."));
    }

    public void convert() {
        System.out.println("Starting OsmPbfToOsmConverter");

        String[] args = {
                "--read-pbf", "file=" + inputFile,
                "--write-xml", "file=" + outputFile
        };

        // Run Osmosis using its main() method (programmatically, just like from the command line)
        Osmosis.run(args);
        System.out.println("Conversion complete: " + outputFile);
    }

    public String getOutputFile() {
        return outputFile;
    }
}