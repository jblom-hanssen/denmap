package com.example.osmparsing.utility;

import java.io.*;
import java.util.*;

import org.xml.sax.*;
import org.xml.sax.helpers.*;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class PrintHandler extends DefaultHandler {
    PrintStream out;
    int num_of_tags = 0;
    Set<String> unique_tags = new HashSet<>();
    int spaces = 0;
    StringBuilder output = new StringBuilder();
    Map<String, Set<String>> attributes_map = new HashMap<>();
    Set<String> unique_attributes = new HashSet<>();
    List<String> listWithKeyAttributes = new ArrayList<>();

    // For multipolygon tracking
    private boolean insideRelation = false;
    private Map<String, String> currentRelationTags = new HashMap<>();
    private Map<String, Set<String>> multipolygonTypeValues = new HashMap<>();

    // List of relevant multipolygon tags we want to track
    private final Set<String> relevantTags = new HashSet<>(Arrays.asList(
            "natural", "landuse", "building", "man_made", "amenity",
            "leisure", "highway", "waterway", "boundary", // Added boundary to make sure it's tracked
            "admin_level" // This often appears with boundary=administrative
    ));

    public PrintHandler(PrintStream _out) {
        out = _out;
    }

    public void startDocument() {
        out.printf("startDocument()\n");
    }

    public void startElement(String uri, String localName, String qName, Attributes atts) {
        num_of_tags++;

        if (!unique_tags.contains(qName)) {
            unique_tags.add(qName);
            for (int i = 0; i < spaces; i++) {
                output.append(" ");
            }
            output.append("<").append(qName).append(">\n");
        }
        spaces++;

        // Track relation start
        if (qName.equals("relation")) {
            insideRelation = true;
            currentRelationTags.clear();
        }

        // Collect tags inside relation
        if (insideRelation && qName.equals("tag")) {
            String k = atts.getValue("k");
            String v = atts.getValue("v");
            if (k != null && v != null) {
                currentRelationTags.put(k, v);
            }
        }

        // Existing attribute tracking
        if (atts.getLength() > 0) {
            Set<String> innerSet = attributes_map.get(atts.getValue(0));
            if (innerSet == null) {
                innerSet = new HashSet<>();
                if (atts.getLength() > 1) {
                    innerSet.add(atts.getValue(1));
                }
                attributes_map.put(atts.getValue(0), innerSet);
            } else {
                if (atts.getLength() > 1) {
                    innerSet.add(atts.getValue(1));
                }
            }
        }
    }

    public void endElement(String uri, String localName, String qName) {
        num_of_tags++;
        spaces--;

        if (qName.equals("relation")) {
            // We are finishing a relation
            String relationType = currentRelationTags.get("type");

            // Track both multipolygon and boundary relations
            if ("multipolygon".equals(relationType) || "boundary".equals(relationType)) {
                // For each relevant tag in the relation, collect its value
                for (Map.Entry<String, String> entry : currentRelationTags.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();

                    if (relevantTags.contains(key)) {
                        // Add this value to our set for this tag type
                        multipolygonTypeValues
                                .computeIfAbsent(key, k -> new HashSet<>())
                                .add(value);
                    }
                }
            }
            insideRelation = false;
            currentRelationTags.clear();
        }
    }

    public void endDocument() {
        out.printf("End document\n");
    }

    public void setListWithKeyAttributes() {
        listWithKeyAttributes.add("highway");
        listWithKeyAttributes.add("building");
    }

    public void getNumOfTags() {
        System.out.println("Number of tags: " + num_of_tags);
    }

    public void getAttributes() {
        System.out.println("Attributes:");
        setListWithKeyAttributes();
        for (Map.Entry<String, Set<String>> entry : attributes_map.entrySet()) {
            if (!listWithKeyAttributes.contains(entry.getKey())) {
                continue;
            }
            System.out.println(entry.getKey() + "{");
            for (String value : entry.getValue()) {
                System.out.println("    " + value);
            }
            System.out.println("}");
        }
    }

    public void getNumOfUniqueTags() {
        System.out.println("Number of unique tags: " + unique_tags.size());
    }

    public void getXMLSummary() {
        System.out.println("XML summary:\n" + output.toString());
    }

    // Print the multipolygon types by category
    public void printMultipolygonTypes() {
        System.out.println("\nMultipolygon & Boundary Types:");
        System.out.println("-----------------------------");

        if (multipolygonTypeValues.isEmpty()) {
            System.out.println("No multipolygon or boundary types found.");
            return;
        }

        // Get all keys sorted
        List<String> sortedKeys = new ArrayList<>(multipolygonTypeValues.keySet());
        Collections.sort(sortedKeys);

        int totalTypes = 0;

        // Print each category and its values
        for (String key : sortedKeys) {
            Set<String> values = multipolygonTypeValues.get(key);
            System.out.println(key + ":");

            List<String> sortedValues = new ArrayList<>(values);
            Collections.sort(sortedValues);

            for (String value : sortedValues) {
                System.out.println("    " + value);
            }

            totalTypes += values.size();
            System.out.println();
        }

        System.out.println("Total unique types: " + totalTypes);
    }

    public static void main(String[] args) {
        try {
            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            parserFactory.setNamespaceAware(true);
            SAXParser parser = parserFactory.newSAXParser();
            XMLReader saxReader = parser.getXMLReader();
            PrintHandler handler = new PrintHandler(System.out);

            saxReader.setContentHandler(handler);
            saxReader.parse("osmparsing/data/small.osm");

            handler.getNumOfTags();
            handler.getNumOfUniqueTags();
            handler.getXMLSummary();
            handler.getAttributes();
            // Print multipolygon types
            handler.printMultipolygonTypes();

        } catch (SAXException | IOException | ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }
}