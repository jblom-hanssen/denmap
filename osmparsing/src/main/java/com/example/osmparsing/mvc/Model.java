package com.example.osmparsing.mvc;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.*;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.example.osmparsing.utility.*;
import com.example.osmparsing.way.StylesUtility;
import com.example.osmparsing.address.OSMAddress;
import com.example.osmparsing.address.AddressHandler;
import com.example.osmparsing.algorithms.EdgeWeightedDigraph;
import com.example.osmparsing.algorithms.KdTree;
import com.example.osmparsing.way.*;
import javafx.geometry.Point2D;

public class Model implements Serializable {
    List<Line> list = new ArrayList<Line>();
    public List<Way> ways = new ArrayList<>();
    private AddressHandler addressHandler = new AddressHandler();

    // OPTIMIZED: Replace id2node with more efficient storage
    private transient SortedNodeList sortedNodes;

    // OPTIMIZED: Pre-processing data structures
    private transient Set<Long> requiredNodes = new HashSet<>();
    public transient Set<Long> intersectionNodes = new HashSet<>();
    private transient long currentParsingNodeId = -1;

    // This collection will hold area features (polygons)
    List<MultiPolygon> multiPolygons = new ArrayList<>();
    KdTree kdTree = new KdTree();
    private transient GraphBuilder graphBuilder;
    private boolean buildGraphDuringParsing = true;
    public int roadWaysProcessed = 0;
    public int nonRoadWaysProcessed = 0;
    public Map<String, ArrayList<Way>> typeWays = new HashMap<>();
    private TransportMode currentTransportMode = TransportMode.CAR;
    private Map<String, Integer> unknownHighwayTypes = new HashMap<>();
    private List<SerializedRoad> serializedRoads = new ArrayList<>();
    private FileBasedGraph fileBasedGraph;
    private String baseFilename;
    private Map<Long, float[]> addressNodeCoordinates = new HashMap<>();

    // curly braces to create an instance initializer block
    {
        Set<String> featureTypes = StylesUtility.getAllFeatureTypes();
        for(String featureType : featureTypes) {
            typeWays.put(featureType, new ArrayList<>());
        }
    }

    private static class SerializedRoad implements Serializable {
        List<Long> nodeIds;
        String roadType;
        boolean oneWay;

        SerializedRoad(List<Long> nodeIds, String roadType, boolean oneWay) {
            this.nodeIds = nodeIds;
            this.roadType = roadType;
            this.oneWay = oneWay;
        }
    }

    Map<String, String> addressTags = new HashMap<>();

    float minlat, maxlat, minlon, maxlon;

    private void initializeRoadGraph() {
        if (graphBuilder == null) {
            System.out.println("Initializing GraphBuilder for two-pass construction");
            graphBuilder = new GraphBuilder(this);
        }
    }

    public static Model load(String filename) throws FileNotFoundException, IOException, ClassNotFoundException, XMLStreamException, FactoryConfigurationError {
        if (filename.endsWith(".obj")) {
            try (var in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(filename)))) {
                Model model = (Model) in.readObject();
                // Reinitialize transient fields
                model.requiredNodes = new HashSet<>();
                model.intersectionNodes = new HashSet<>();
                return model;
            }
        }
        Model model = new Model(filename);
        model.save(filename + ".obj");
        return model;
    }

    public Model(String filename) throws XMLStreamException, FactoryConfigurationError, IOException, ClassNotFoundException {
        list = new ArrayList<>();
        ways = new ArrayList<>();
        multiPolygons = new ArrayList<>();
        addressHandler = new AddressHandler();
        kdTree = new KdTree();
        fileBasedGraph = new FileBasedGraph();
        addressNodeCoordinates = new HashMap<>();
        requiredNodes = new HashSet<>();
        intersectionNodes = new HashSet<>();

        // Initialize type-specific way collections
        Set<String> featureTypes = StylesUtility.getAllFeatureTypes();
        for (String featureType : featureTypes) {
            typeWays.put(featureType, new ArrayList<>());
        }

        // Initialize graph builder before parsing
        if (buildGraphDuringParsing) {
            initializeRoadGraph();
        }

        // OPTIMIZED: Two-pass parsing with pre-processing
        if (filename.endsWith(".osm.zip")) {
            this.baseFilename = filename.substring(0, filename.length() - 8);
            // First pass - identify required nodes
            requiredNodes = identifyRequiredNodes(filename);
            // Second pass - parse with filtering
            parseZIP(filename);
        } else if (filename.endsWith(".osm")) {
            this.baseFilename = filename.substring(0, filename.lastIndexOf('.'));
            // First pass - identify required nodes
            requiredNodes = identifyRequiredNodes(filename);
            // Second pass - parse with filtering
            parseOSM(filename);
        } else if (filename.isEmpty() || filename.endsWith(".pbf")) {
            parsePBF(filename);
            return;
        } else {
            parseTXT(filename);
        }

        save(filename + ".obj");
    }

    // OPTIMIZED: New pre-processing method to identify required nodes
    private Set<Long> identifyRequiredNodes(String filename) throws XMLStreamException, IOException {
        System.out.println("=== PRE-PROCESSING PASS: Identifying required nodes ===");
        long startTime = System.currentTimeMillis();

        Set<Long> required = new HashSet<>();
        Map<Long, Integer> nodeUsageCount = new HashMap<>();

        InputStream inputStream;
        if (filename.endsWith(".zip")) {
            var zipInput = new ZipInputStream(new FileInputStream(filename));
            zipInput.getNextEntry();
            inputStream = zipInput;
        } else {
            inputStream = new FileInputStream(filename);
        }

        var input = XMLInputFactory.newInstance().createXMLStreamReader(new InputStreamReader(inputStream));

        long currentNodeId = -1;
        boolean inNode = false;
        boolean hasAddress = false;
        int nodeCount = 0;
        int wayCount = 0;
        int orphanNodes = 0;

        while (input.hasNext()) {
            var tagKind = input.next();
            if (tagKind == XMLStreamConstants.START_ELEMENT) {
                var name = input.getLocalName();

                if (name.equals("node")) {
                    nodeCount++;
                    currentNodeId = Long.parseLong(input.getAttributeValue(null, "id"));
                    inNode = true;
                    hasAddress = false;
                } else if (name.equals("way")) {
                    wayCount++;
                    inNode = false;
                } else if (name.equals("nd")) {
                    // This is a node reference in a way - we need ALL of these
                    long ref = Long.parseLong(input.getAttributeValue(null, "ref"));
                    nodeUsageCount.merge(ref, 1, Integer::sum);
                    required.add(ref); // ALL nodes in ways are required for geometry
                } else if (name.equals("tag") && inNode) {
                    // Check if this node has an address
                    String k = input.getAttributeValue(null, "k");
                    if (k != null && k.startsWith("addr:")) {
                        hasAddress = true;
                        required.add(currentNodeId);
                    }
                } else if (name.equals("member")) {
                    // Also check for nodes referenced in relations
                    String type = input.getAttributeValue(null, "type");
                    if ("node".equals(type)) {
                        long ref = Long.parseLong(input.getAttributeValue(null, "ref"));
                        required.add(ref);
                    }
                }
            } else if (tagKind == XMLStreamConstants.END_ELEMENT) {
                if (input.getLocalName().equals("node")) {
                    inNode = false;
                    if (hasAddress) {
                        required.add(currentNodeId);
                    }
                }
            }
        }

        // Identify intersection nodes (used by 2+ ways)
        for (Map.Entry<Long, Integer> entry : nodeUsageCount.entrySet()) {
            if (entry.getValue() >= 2) {
                intersectionNodes.add(entry.getKey());
            }
        }

        orphanNodes = nodeCount - required.size();

        input.close();
        inputStream.close();

        long endTime = System.currentTimeMillis();
        System.out.println("Pre-processing complete in " + (endTime - startTime) + "ms:");
        System.out.println("  - Total nodes in file: " + nodeCount);
        System.out.println("  - Total ways in file: " + wayCount);
        System.out.println("  - Required nodes: " + required.size() + " (" +
                String.format("%.1f%%", 100.0 * required.size() / nodeCount) + ")");
        System.out.println("  - Orphan nodes (not used): " + orphanNodes);
        System.out.println("  - Intersection nodes: " + intersectionNodes.size());
        System.out.println("  - Memory saved by filtering orphans: ~" +
                (orphanNodes * 50 / 1024 / 1024) + "MB");
        System.out.println("  NOTE: We keep all nodes referenced by ways to maintain geometry");

        return required;
    }

    // Helper method to skip XML elements
    private void skipToEndElement(XMLStreamReader reader, String elementName) throws XMLStreamException {
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
                if (depth == 0 && reader.getLocalName().equals(elementName)) {
                    return;
                }
            }
        }
    }

    void save(String filename) throws FileNotFoundException, IOException {
        try (var out = new ObjectOutputStream(new FileOutputStream(filename))) {
            out.writeObject(this);
        }
    }

    private void parsePBF(String filename) throws IOException, XMLStreamException, FactoryConfigurationError, ClassNotFoundException {
        OsmPbfToOsmConverter converter = new OsmPbfToOsmConverter(filename);
        converter.convert();
        this.baseFilename = filename.substring(0, filename.lastIndexOf('.'));
        parseOSM(converter.getOutputFile());
    }

    private void parseZIP(String filename) throws IOException, XMLStreamException, FactoryConfigurationError {
        var input = new ZipInputStream(new FileInputStream(filename));
        input.getNextEntry();
        parseOSM(input);
    }

    private void parseOSM(String filename) throws FileNotFoundException, XMLStreamException, FactoryConfigurationError {
        parseOSM(new FileInputStream(filename));
    }

    private void parseOSM(InputStream inputStream) throws FileNotFoundException, XMLStreamException, FactoryConfigurationError {
        System.out.println("=== STARTING OSM PARSING WITH MEMORY OPTIMIZATION ===");
        long startTime = System.currentTimeMillis();

        // OPTIMIZED: Initialize sorted nodes with exact capacity
        if (sortedNodes == null) {
            sortedNodes = new SortedNodeList(requiredNodes.size());
        }

        var input = XMLInputFactory.newInstance().createXMLStreamReader(new InputStreamReader(inputStream));

        //Temporary collections for parsing
        var wayNodes = new ArrayList<Node>();
        var id2way = new HashMap<Long, Way>();
        Set<String> skipHighwayTypes = Set.of(
                "proposed", "construction", "abandoned", "platform", "raceway",
                "corridor", "elevator", "escalator", "steps", "bus_guideway",
                "busway", "services", "rest_area", "escape", "emergency_access"
        );

        //All ways who might be in an relation later are stored here before added to the arraylist of ways
        Map<String, HashMap<Long, Way>> potentialRelationWays = new HashMap<>();
        String[] refTypes = {"coastline", "lake", "forest", "unknown", "grass", "building",
                "residential", "farmland", "wetland", "meadow", "grassland",
                "scrub", "commercial", "industrial", "water"};
        for (String type : refTypes) {
            potentialRelationWays.put(type, new HashMap<>());
        }

        //new implementation
        var innerRelation = new ArrayList<Way>();
        var OuterRelation = new ArrayList<Way>();
        var type = "unknown";
        var memberIDs = new ArrayList<Long>();
        long relID = 0;
        boolean road = false;
        boolean isRoad = false;
        String roadType = null;
        boolean isOneWay = false;
        boolean bicycleAllowed = true;
        boolean carAllowed = true;
        int speedLimit = 50;

        String wayName = "Unknown road";

        long currentNodeId = -1;
        Map<String, String> currentTags = new HashMap<>();
        boolean isAddressNode = false;
        boolean inRelation = false;
        long currentRelationId = -1;
        long currentWayId = -1;
        boolean skipCurrentWay = false;

        boolean skipRelation = false;
        boolean isNaturalFeature = false;

        Map<Long, Map<String, String>> nodeAddressTags = new HashMap<>();
        boolean inNode = false;
        boolean processingAddressNode = false;

        // Progress tracking
        int nodeCount = 0;
        int nodesSkipped = 0;
        int wayCount = 0;
        int relationCount = 0;
        long lastProgressTime = System.currentTimeMillis();
        long lastMemoryCheck = System.currentTimeMillis();

        System.out.println("Starting XML parsing with " + requiredNodes.size() + " required nodes...");

        while (input.hasNext()) {
            var tagKind = input.next();
            if (tagKind == XMLStreamConstants.START_ELEMENT) {
                var name = input.getLocalName();

                if (name.equals("bounds")) {
                    minlat = (float) Double.parseDouble(input.getAttributeValue(null, "minlat"));
                    maxlat = (float) Double.parseDouble(input.getAttributeValue(null, "maxlat"));
                    minlon = (float) Double.parseDouble(input.getAttributeValue(null, "minlon"));
                    maxlon = (float) Double.parseDouble(input.getAttributeValue(null, "maxlon"));
                    System.out.println("Bounds: lat[" + minlat + " to " + maxlat + "], lon[" + minlon + " to " + maxlon + "]");

                } else if (name.equals("node")) {
                    nodeCount++;

                    var id = Long.parseLong(input.getAttributeValue(null, "id"));

                    // OPTIMIZED: Skip non-required nodes
                    if (!requiredNodes.contains(id)) {
                        nodesSkipped++;
                        skipToEndElement(input, "node");
                        continue;
                    }

                    // Progress reporting for nodes
                    if (nodeCount % 1000000 == 0) {
                        long currentTime = System.currentTimeMillis();
                        System.out.println("Processed " + nodeCount + " nodes (" + nodesSkipped + " skipped) in " +
                                (currentTime - lastProgressTime) + "ms");
                        lastProgressTime = currentTime;
                    }

                    // OPTIMIZED: Memory check every 30 seconds
                    if (System.currentTimeMillis() - lastMemoryCheck > 30000) {
                        checkAndManageMemory();
                        lastMemoryCheck = System.currentTimeMillis();
                    }

                    inNode = true;
                    var lat = Double.parseDouble(input.getAttributeValue(null, "lat"));
                    var lon = Double.parseDouble(input.getAttributeValue(null, "lon"));
                    Node node = new Node(id, (float) -lat, (float) (0.56 * lon));

                    // OPTIMIZED: Use sorted node list
                    sortedNodes.add(node);

                    if (graphBuilder != null) {
                        graphBuilder.storeNodeCoordinates(id, (float) (0.56 * lon), (float) -lat);
                    }

                    currentNodeId = id;
                    currentTags.clear();
                    isAddressNode = false;
                    processingAddressNode = false;

                } else if (name.equals("way")) {
                    wayCount++;

                    // Progress reporting and memory management for ways
                    if (wayCount % 100000 == 0) {
                        long currentTime = System.currentTimeMillis();
                        System.out.println("Processed " + wayCount + " ways in " +
                                (currentTime - lastProgressTime) + "ms");
                        lastProgressTime = currentTime;

                        // OPTIMIZED: Aggressive memory management
                        checkAndManageMemory();
                    }

                    skipCurrentWay = false;
                    type = "unknown";
                    isOneWay = false;
                    road = false;
                    inNode = false;
                    wayNodes.clear();
                    relID = Long.parseLong(input.getAttributeValue(null, "id"));

                } else if (name.equals("relation")) {
                    relationCount++;

                    if (relationCount % 10000 == 0) {
                        System.out.println("Processed " + relationCount + " relations");
                    }

                    inNode = false;
                    inRelation = true;
                    skipRelation = false;
                    isNaturalFeature = false;

                    currentRelationId = Long.parseLong(input.getAttributeValue(null, "id"));
                    type = "unknown";

                } else if (name.equals("tag")) {
                    var k = internIfNotNull(input.getAttributeValue(null, "k"));
                    var v = internIfNotNull(input.getAttributeValue(null, "v"));

                    if (skipCurrentWay) {
                        continue;
                    }

                    if (k.equals("building")) {
                        type = "building";
                        road = false;
                    }
                    if (k.equals("name")) {
                        if (road) {
                            wayName = v;
                        }
                    }

                    // Process node tags for addresses
                    else if (inNode) {
                        // Check if this is an address node
                        if (k.startsWith("addr:")) {
                            processingAddressNode = true;

                            // Get or create address tags map for this node
                            Map<String, String> nodeTags = nodeAddressTags.computeIfAbsent(currentNodeId,
                                    key -> new HashMap<>());

                            // Store without "addr:" prefix
                            nodeTags.put(k.substring(5), v);
                        }
                    } else if (inRelation) {
                        // Filter routes efficiently without storing tags
                        if (k.equals("type") && v.equals("route")) {
                            skipRelation = true;
                        }
                        if (k.equals("route")) {
                            skipRelation = true;
                        }

                        // Natural features identification - using flags instead of collections
                        if (k.equals("natural") && (v.equals("wood") || v.equals("forest") || v.equals("water")
                                || v.equals("wetland") || v.equals("scrub"))) {
                            isNaturalFeature = true;
                        }
                        if (k.equals("landuse") && (v.equals("forest") || v.equals("meadow") || v.equals("grass")
                                || v.equals("farmland"))) {
                            isNaturalFeature = true;
                        }

                        // Type assignment for rendering
                        if (k.equals("leisure")) {
                            road = false;
                            switch (v) {
                                case "park":
                                case "garden":
                                case "dog_park":
                                case "pitch":
                                case "track":
                                case "sports_centre":
                                case "stadium":
                                case "golf course":
                                    type = "grass";
                                    break;
                            }
                        }
                        if (k.equals("landuse")) {
                            road = false;
                            switch (v) {
                                case "farmland":
                                    type = "farmland";
                                    break;
                                case "grass":
                                    type = "grass";
                                    break;
                                case "residential":
                                    type = "residential";
                                    break;
                                case "meadow":
                                    type = "meadow";
                                    break;
                                case "recreation_ground":
                                    type = "grass";
                                    break;
                                case "forest":
                                    type = "forest";
                                    break;
                                case "industrial":
                                    type = "industrial";
                                    break;
                                case "commercial":
                                    type = "commercial";
                                    break;
                            }
                        }
                        if (k.equals("place")) {
                            road = false;
                            switch (v) {
                                case "island":
                                case "islet":
                                case "peninsula":
                                    type = "coastline";
                                    break;
                            }
                        }
                        if (k.equals("natural")) {
                            road = false;
                            switch (v) {
                                case "water":
                                    type = "water";
                                    break;
                                case "wetland":
                                    type = "wetland";
                                    break;
                                case "coastline":
                                    type = "coastline";
                                    break;
                                case "grassland":
                                    type = "grassland";
                                    break;
                                case "scrub":
                                    type = "scrub";
                                    break;
                                case "wood":
                                    type = "forest";
                                    break;
                            }
                        }
                    } else {
                        // Process way tags
                        if (k.equals("highway")) {
                            speedLimit = 50; // Default speed limit
                            road = true;

                            // Reset access permissions to defaults
                            bicycleAllowed = true;  // Default: bicycles allowed on most roads
                            carAllowed = true;      // Default: cars allowed on most roads

                            switch (v) {
                                // Major roads and their links
                                case "motorway":
                                    type = "motorway";
                                    speedLimit = 110;
                                    bicycleAllowed = false; // Bicycles not allowed on motorways
                                    break;
                                case "motorway_link":
                                    type = "motorway_link";
                                    speedLimit = 80;
                                    bicycleAllowed = false; // Bicycles not allowed on motorway links
                                    break;
                                case "trunk":
                                    type = "trunk";
                                    speedLimit = 90;
                                    bicycleAllowed = false; // Usually bicycles not allowed (country-specific)
                                    break;
                                case "trunk_link":
                                    type = "trunk_link";
                                    speedLimit = 70;
                                    bicycleAllowed = false;
                                    break;
                                case "primary":
                                    type = "primary";
                                    speedLimit = 80;
                                    // Bicycles allowed but not preferred
                                    break;
                                case "primary_link":
                                    type = "primary_link";
                                    speedLimit = 60;
                                    break;
                                case "secondary":
                                    type = "secondary";
                                    speedLimit = 70;
                                    break;
                                case "secondary_link":
                                    type = "secondary_link";
                                    speedLimit = 50;
                                    break;
                                case "tertiary":
                                    type = "tertiary";
                                    speedLimit = 60;
                                    break;
                                case "tertiary_link":
                                    type = "tertiary_link";
                                    speedLimit = 40;
                                    break;

                                // Minor roads
                                case "unclassified":
                                    type = "unclassifiedhighway";
                                    speedLimit = 50;
                                    break;
                                case "residential":
                                    type = "residentialroad";
                                    speedLimit = 30;
                                    break;
                                case "service":
                                    type = "serviceroad";
                                    speedLimit = 20;
                                    break;
                                case "living_street":
                                    type = "living_street";
                                    speedLimit = 20;
                                    break;

                                // Bicycle/pedestrian specific
                                case "cycleway":
                                    type = "cycleway";
                                    speedLimit = 20;
                                    bicycleAllowed = true;
                                    carAllowed = false; // Cars not allowed on cycleways
                                    break;
                                case "path":
                                    type = "path";
                                    speedLimit = 10;
                                    bicycleAllowed = true;  // Usually allowed, but check bicycle tag
                                    carAllowed = false;
                                    break;
                                case "footway":
                                    type = "footway";
                                    speedLimit = 5;
                                    bicycleAllowed = false; // Default: no bicycles on footways
                                    carAllowed = false;
                                    break;
                                case "pedestrian":
                                    type = "pedestrian";
                                    speedLimit = 5;
                                    bicycleAllowed = false; // Usually not allowed
                                    carAllowed = false;
                                    break;
                                case "track":
                                    type = "track";
                                    speedLimit = 30;
                                    bicycleAllowed = true;
                                    // Cars may or may not be allowed on tracks
                                    break;
                                case "proposed":
                                case "construction":
                                case "abandoned":
                                case "platform":
                                case "raceway":
                                case "corridor":
                                case "elevator":
                                case "escalator":
                                case "steps":
                                case "bus_guideway":
                                case "busway":
                                case "services":
                                case "rest_area":
                                case "escape":
                                case "emergency_access":
                                    road = false;
                                    skipCurrentWay = true;  // MARK FOR SKIPPING

                                    // Optional: Track what we're skipping
                                    if (unknownHighwayTypes.merge(v, 1, Integer::sum) == 1) {
                                        System.out.println("Skipping highway type: " + v);
                                    }
                                    break;
                                default:
                                    // Unknown highway type - skip it
                                    road = false;
                                    skipCurrentWay = true;  // MARK FOR SKIPPING

                                    if (unknownHighwayTypes.merge(v, 1, Integer::sum) == 1) {
                                        System.out.println("Skipping unknown highway type: " + v);
                                    }
                                    break;
                            }
                        }

                        // Also check for explicit bicycle access tags
                        if (k.equals("bicycle")) {
                            switch (v) {
                                case "yes":
                                case "designated":
                                    bicycleAllowed = true;
                                    break;
                                case "no":
                                case "dismount":
                                    bicycleAllowed = false;
                                    break;
                                case "permissive":
                                case "private":
                                    // Could set based on your preference
                                    bicycleAllowed = true;
                                    break;
                            }
                        }

                        // Check for motor vehicle access
                        if (k.equals("motor_vehicle") || k.equals("motorcar")) {
                            switch (v) {
                                case "yes":
                                    carAllowed = true;
                                    break;
                                case "no":
                                    carAllowed = false;
                                    break;
                            }
                        }

                        if (k.equals("maxspeed")) {
                            try {
                                speedLimit = Integer.parseInt(v);
                            } catch (NumberFormatException e) {
                                //if its not an number dont handle
                            }
                        }
                        if (k.equals("oneway")) {
                            if (v.equals("yes")) {
                                isOneWay = true;
                            }
                        }

                        if (k.equals("leisure")) {
                            road = false;
                            switch (v) {
                                case "park":
                                case "garden":
                                case "dog_park":
                                case "pitch":
                                case "track":
                                case "sports_centre":
                                case "stadium":
                                case "golf course":
                                    type = "grass";
                                    break;
                            }
                        }

                        if (k.equals("landuse")) {
                            road = false;
                            switch (v) {
                                case "farmland":
                                    type = "farmland";
                                    break;
                                case "grass":
                                    type = "grass";
                                    break;
                                case "residential":
                                case "industrial":
                                    type = "residential";
                                    break;
                                case "meadow":
                                    type = "meadow";
                                    break;
                                case "recreation_ground":
                                    type = "grass";
                                    break;
                                case "forest":
                                    type = "forest";
                                    break;
                            }
                        }

                        if (k.equals("place")) {
                            road = false;
                            switch (v) {
                                case "island":
                                case "islet":
                                case "peninsula":
                                    type = "coastline";
                                    break;
                            }
                        }

                        if (k.equals("natural")) {
                            road = false;
                            switch (v) {
                                case "water":
                                    type = "lake";
                                    break;
                                case "wetland":
                                    type = "wetland";
                                    break;
                                case "coastline":
                                    type = "coastline";
                                    break;
                                case "grassland":
                                    type = "grassland";
                                    break;
                                case "scrub":
                                    type = "scrub";
                                    break;
                                case "wood":
                                    type = "forest";
                                    break;
                            }
                        }
                    }

                } else if (name.equals("nd")) {
                    if (!skipCurrentWay) {
                        var ref = Long.parseLong(input.getAttributeValue(null, "ref"));
                        // OPTIMIZED: Use sorted node list
                        var node = sortedNodes.get(ref);
                        if (node != null) {
                            wayNodes.add(node);
                        }
                    }

                } else if (name.equals("member")) {
                    // [Member processing remains the same]
                    var ref = Long.parseLong(input.getAttributeValue(null, "ref"));
                    var elm = id2way.get(ref);
                    if (elm != null) {
                        memberIDs.add(ref);
                        String role = input.getAttributeValue(null, "role");
                        if (role.equals("inner")) {
                            innerRelation.add(elm);
                        } else {
                            OuterRelation.add(elm);
                        }
                    }
                }
            } else if (tagKind == XMLStreamConstants.END_ELEMENT) {
                var name = input.getLocalName();
                if (name.equals("node")) {
                    if (processingAddressNode) {
                        createAndAddAddress(currentNodeId, nodeAddressTags.get(currentNodeId));
                    }
                    inNode = false;
                    processingAddressNode = false;

                } else if (name.equals("relation")) {
                    // Natural features override the skip flag
                    if (isNaturalFeature) {
                        skipRelation = false;
                    }

                    // Process relation if it's not a route or if it's a valid area feature
                    if (!skipRelation && type != "unknown" && potentialRelationWays.keySet().contains(type) && !OuterRelation.isEmpty() && type != "coastline") {
                        for (long id : memberIDs) {
                            if (potentialRelationWays.get("unknown").remove(id) == null) {
                                potentialRelationWays.get(type).remove(id);
                            }
                        }
                        // Create multiPolygon - note we're clearing collections as we go
                        MultiPolygon multiPolygon = new MultiPolygon(type, OuterRelation, innerRelation);
                        multiPolygons.add(multiPolygon);
                        multiPolygon = null;
                        OuterRelation.clear();
                        innerRelation.clear();
                        memberIDs.clear();
                    } else if (type == "coastline" && !OuterRelation.isEmpty()) {
                        for (long id : memberIDs) {
                            if (potentialRelationWays.get(type).remove(id) == null) {
                                potentialRelationWays.get("unknown").remove(id);
                            }
                        }
                        MultiPolygon multiPolygon = new MultiPolygon(type, OuterRelation, Collections.emptyList());
                        multiPolygons.add(multiPolygon);
                        multiPolygon = null;
                        OuterRelation.clear();
                        innerRelation.clear();
                        memberIDs.clear();
                    } else {
                        // Clear collections for skipped relations
                        memberIDs.clear();
                        OuterRelation.clear();
                        innerRelation.clear();
                    }

                    inRelation = false;

                } else if (name.equals("way")) {
                    // CHECK SKIP FLAG BEFORE CREATING WAY
                    if (skipCurrentWay) {
                        // Don't create Way object, just clear and continue
                        wayNodes.clear();
                        nonRoadWaysProcessed++;  // Still count it
                        continue;  // Skip to next element
                    }

                    // Only create Way if not skipping AND has nodes
                    if (!wayNodes.isEmpty()) {
                        Way way = new Way(wayNodes, type);

                        // Debug logging
                        if (wayCount <= 10 || wayCount % 10000 == 0) {
                            System.out.println("Processing way " + wayCount + ": type=" + type +
                                    ", nodes=" + wayNodes.size() + ", road=" + road);
                        }

                        // Add to appropriate collections
                        if (potentialRelationWays.keySet().contains(type)) {
                            potentialRelationWays.get(type).put(relID, way);
                            id2way.put(relID, way);
                        } else if (road && buildGraphDuringParsing && graphBuilder != null) {
                            // Process for routing
                            List<Long> nodeIds = new ArrayList<>(wayNodes.size());
                            for (Node node : wayNodes) {
                                nodeIds.add(node.getId());
                            }

                            // IMPORTANT: Store the full way data for edge building
                            graphBuilder.addRoadWithNodeIds(way, nodeIds, type, isOneWay,
                                    wayName, speedLimit, bicycleAllowed, carAllowed);

                            roadWaysProcessed++;

                            // Also add to typeWays for rendering roads
                            if (!typeWays.containsKey(type)) {
                                typeWays.put(type, new ArrayList<>());
                            }
                            typeWays.get(type).add(way);
                        } else {
                            // Non-road way - add to typeWays for rendering
                            if (!typeWays.containsKey(type)) {
                                typeWays.put(type, new ArrayList<>());
                            }
                            typeWays.get(type).add(way);

                            // Debug: track what types we're adding
                            if (typeWays.get(type).size() == 1) {
                                System.out.println("First way of type '" + type + "' added");
                            }
                        }
                    } else {
                        System.out.println("WARNING: Way " + wayCount + " has no nodes!");
                    }

                    // Clear for next way
                    wayNodes.clear();
                }
            }
        }

        long parseTime = System.currentTimeMillis();
        System.out.println("=== OSM PARSING COMPLETE ===");
        System.out.println("Parsed in " + (parseTime - startTime) + "ms:");
        System.out.println("- " + nodeCount + " nodes processed (" + nodesSkipped + " skipped)");
        System.out.println("- " + wayCount + " ways");
        System.out.println("- " + relationCount + " relations");
        System.out.println("- " + roadWaysProcessed + " roads");
        System.out.println("Memory saved by node filtering: ~" + (nodesSkipped * 50 / 1024 / 1024) + "MB");

        // Build graph if enabled
        if (buildGraphDuringParsing && graphBuilder != null) {
            System.out.println("=== STARTING GRAPH CONSTRUCTION ===");
            long graphStartTime = System.currentTimeMillis();

            EdgeWeightedDigraph tempGraph = graphBuilder.finalizeGraph();

            long graphEndTime = System.currentTimeMillis();
            System.out.println("=== GRAPH CONSTRUCTION COMPLETE ===");
            System.out.println("Graph built in " + (graphEndTime - graphStartTime) + "ms");
            System.out.println("Final graph: " + graphBuilder.getVertexCount() + " vertices, " + tempGraph.E() + " edges");

            // Save graph to file
            try {
                fileBasedGraph.saveGraph(tempGraph, graphBuilder, baseFilename);
                tempGraph = null;
                graphBuilder = null;
                System.gc();
                System.out.println("Graph saved to: " + baseFilename + ".graph");
            } catch (IOException e) {
                throw new RuntimeException("Failed to save graph to file: " + e.getMessage());
            }
        }

        // Process unused relation ways
        for (String featureType : potentialRelationWays.keySet()) {
            HashMap<Long, Way> unusedWays = potentialRelationWays.get(featureType);
            if (unusedWays != null && !unusedWays.isEmpty()) {
                System.out.println("Adding " + unusedWays.size() + " unused " + featureType + " ways to rendering");

                if (!typeWays.containsKey(featureType)) {
                    typeWays.put(featureType, new ArrayList<>());
                }

                typeWays.get(featureType).addAll(unusedWays.values());
            }
        }

        // Build KD-tree
        System.out.println("Building KD-tree for spatial queries...");
        for (String featureType : typeWays.keySet()) {
            ArrayList<Way> typeWaysList = typeWays.get(featureType);
            for (Way way : typeWaysList) {
                if (way.averageWayNode != null) {
                    kdTree.insert(way.averageWayNode.lon, way.averageWayNode.lat, way.data);
                }
            }
        }

        // OPTIMIZED: Final cleanup
        System.out.println("Performing final cleanup...");
        performFinalCleanup();

        // Report statistics
        int totalWays = 0;
        for (ArrayList<Way> wayList : typeWays.values()) {
            totalWays += wayList.size();
        }

        long totalTime = System.currentTimeMillis();
        System.out.println("=== FINAL STATISTICS ===");
        System.out.println("Total processing time: " + (totalTime - startTime) + "ms");
        System.out.println("Loaded " + totalWays + " ways into typeWays");
        printProcessingStats();

        // Print details for each type
        for (String featureType : typeWays.keySet()) {
            int count = typeWays.get(featureType).size();
            if (count > 0) {
                System.out.println("  - " + featureType + ": " + count + " ways");
            }
        }

        // Final memory report
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        System.out.println("Final memory usage: " + usedMB + "MB");
        System.out.println("=== OSM PROCESSING COMPLETE ===");
    }

    // OPTIMIZED: Memory management method
    private void checkAndManageMemory() {
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        long maxMB = rt.maxMemory() / 1024 / 1024;

        System.out.println("Memory usage: " + usedMB + "MB / " + maxMB + "MB (" +
                String.format("%.1f%%", 100.0 * usedMB / maxMB) + ")");

        if (usedMB > maxMB * 0.7) { // If using more than 70% of max memory
            System.out.println("High memory usage detected, triggering cleanup...");

            // Compact sorted nodes if possible
            if (sortedNodes != null) {
                sortedNodes.compact();
            }

            System.gc();

            // Check memory again
            usedMB = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
            System.out.println("Memory after GC: " + usedMB + "MB");
        }
    }

    // OPTIMIZED: Final cleanup method
    private void performFinalCleanup() {
        // Clear pre-processing data structures
        if (requiredNodes != null) {
            requiredNodes.clear();
            requiredNodes = null;
        }
        if (intersectionNodes != null) {
            intersectionNodes.clear();
            intersectionNodes = null;
        }

        // Compact sorted nodes
        if (sortedNodes != null) {
            sortedNodes.compact();
        }

        // Clear temporary data
        ways = null;

        System.gc();
    }

    private void createAndAddAddress(long nodeId, Map<String, String> addressTags) {
        if (addressTags == null || addressTags.isEmpty()) {
            return;
        }

        String street = addressTags.get("street");
        String house = addressTags.get("housenumber");
        String city = addressTags.getOrDefault("city", internIfNotNull(addressTags.get("place")));
        city = internIfNotNull(city);

        if (street == null && city == null) {
            return;
        }

        OSMAddress.Builder builder = new OSMAddress.Builder()
                .street(street)
                .house(house)
                .postcode(addressTags.get("postcode"))
                .city(city)
                .nodeId(nodeId);

        if (addressTags.containsKey("floor")) {
            builder.floor(addressTags.get("floor"));
        }

        if (addressTags.containsKey("unit")) {
            builder.side(addressTags.get("unit"));
        }

        OSMAddress address = builder.build();
        addressHandler.addAddress(address);

        // Store coordinates for address nodes
        if (sortedNodes != null) {
            Node node = sortedNodes.get(nodeId);
            if (node != null) {
                addressNodeCoordinates.put(nodeId, new float[]{node.lon, node.lat});
            }
        }
    }

    private void parseTXT(String filename) throws FileNotFoundException, IOException {
        var f = new File(filename);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                list.add(new Line(line));
            }
        }
    }

    public void add(Point2D p1, Point2D p2) {
        list.add(new Line(p1, p2));
    }

    public EdgeWeightedDigraph buildRoadGraph() {
        System.out.println("buildRoadGraph() called but using file-based storage");
        return null;
    }

    public GraphBuilder getGraphBuilder() {
        if (graphBuilder == null) {
            buildRoadGraph();
        }
        return graphBuilder;
    }

    public EdgeWeightedDigraph getRoadGraph() {
        throw new UnsupportedOperationException(
                "In-memory graph not available. Use getFileBasedGraph() for routing."
        );
    }

    public FileBasedGraph getFileBasedGraph() {
        return fileBasedGraph;
    }

    public int findNearestVertex(float x, float y) {
        // First check if we have a file-based graph
        if (fileBasedGraph != null && fileBasedGraph.graphFileExists()) {
            int vertex = fileBasedGraph.findNearestVertex(x, y);

            // If file-based graph returns -1 or an invalid vertex, try fallback
            if (vertex < 0) {
                System.err.println("WARNING: FileBasedGraph couldn't find nearest vertex for coordinates [" + x + ", " + y + "]");
                // Try to find from stored coordinates during parsing
                return findNearestVertexFromStoredCoords(x, y);
            }

            return vertex;
        }

        System.err.println("ERROR: No graph available for finding nearest vertex");
        return -1;
    }

    // Add fallback method to find nearest vertex from stored coordinates
    private int findNearestVertexFromStoredCoords(float x, float y) {
        if (graphBuilder != null) {
            // During parsing, use GraphBuilder's vertex coordinates
            return graphBuilder.findNearestVertex(x, y);
        }

        // After loading from file, we need to reconstruct this information
        // This is a limitation of the current approach
        System.err.println("ERROR: Cannot find nearest vertex - GraphBuilder not available");
        return -1;
    }

    public AddressHandler getAddressHandler() {
        return addressHandler;
    }

    private String internIfNotNull(String value) {
        return value != null ? value.intern() : null;
    }

    public void printid2NodeSortCount() {
        // No longer applicable with SortedNodeList
        System.out.println("Using optimized SortedNodeList");
    }

    public int addressHandlerSize() {
        return addressHandler.size();
    }

    public Map<String, ArrayList<Way>> getTypeWays() {
        return typeWays;
    }

    public float[] getNodeCoordinates(long nodeId) {
        System.out.println("DEBUG: Getting coordinates for node ID " + nodeId);

        float[] coords = addressNodeCoordinates.get(nodeId);
        if (coords != null) {
            System.out.println("DEBUG: Found address node coordinates: [" + coords[0] + ", " + coords[1] + "]");
            return coords;
        }

        if (fileBasedGraph != null) {
            System.out.println("DEBUG: Node " + nodeId + " not found in address coordinates");
        }

        System.out.println("DEBUG: No coordinates found for node " + nodeId);
        return null;
    }

    public void printProcessingStats() {
        System.out.println("\n=== FINAL PROCESSING STATISTICS ===");
        System.out.println("Roads processed for graph: " + roadWaysProcessed);
        System.out.println("Non-roads processed: " + nonRoadWaysProcessed);
        System.out.println("Total ways: " + (roadWaysProcessed + nonRoadWaysProcessed));
        System.out.println("Percentage roads: " +
                String.format("%.1f%%", 100.0 * roadWaysProcessed / (roadWaysProcessed + nonRoadWaysProcessed)));
        System.out.println("=====================================");
    }

    public void setTransportMode(TransportMode mode) {
        this.currentTransportMode = mode;
    }

    public TransportMode getTransportMode() {
        return currentTransportMode;
    }

    public Set<Long> getIntersectionNodes() {
        return intersectionNodes != null ? intersectionNodes : new HashSet<>();
    }

    // Also add this to help GraphBuilder
    public boolean isIntersectionNode(long nodeId) {
        return intersectionNodes != null && intersectionNodes.contains(nodeId);
    }
}

