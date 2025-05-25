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

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

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
    // private transient EdgeWeightedDigraph roadGraph;
    private AddressHandler addressHandler = new AddressHandler();
    private id2Node id2node = new id2Node(1000000);
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
            // Don't create graph here - wait for finalization
        }
    }


    public static Model load(String filename) throws FileNotFoundException, IOException, ClassNotFoundException, XMLStreamException, FactoryConfigurationError {
        if (filename.endsWith(".obj")) {
            try (var in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(filename)))) {
                return (Model) in.readObject();
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

        // Initialize type-specific way collections
        Set<String> featureTypes = StylesUtility.getAllFeatureTypes();
        for (String featureType : featureTypes) {
            typeWays.put(featureType, new ArrayList<>());
        }

        // Initialize graph builder before parsing
        if (buildGraphDuringParsing) {
            initializeRoadGraph();
        }
        if (filename.endsWith(".osm.zip")) {
            parseZIP(filename);
        } else if (filename.endsWith(".osm")) {
            parseOSM(filename);
        } else if (filename.isEmpty() || filename.endsWith(".pbf")) {
            parsePBF(filename);
            return;
        } else {
            parseTXT(filename);
        }

         save(filename + ".obj");
    }

    void save(String filename) throws FileNotFoundException, IOException {
        try (var out = new ObjectOutputStream(new FileOutputStream(filename))) {
            out.writeObject(this);
        }
    }

    private void parsePBF(String filename) throws IOException, XMLStreamException, FactoryConfigurationError, ClassNotFoundException {
        OsmPbfToOsmConverter converter = new OsmPbfToOsmConverter(filename);
        converter.convert();
        // Store base filename before parsing
        this.baseFilename = filename.substring(0, filename.lastIndexOf('.'));
        parseOSM(converter.getOutputFile());
    }

    private void parseZIP(String filename) throws IOException, XMLStreamException, FactoryConfigurationError {
        // Store base filename before parsing
        this.baseFilename = filename.substring(0, filename.length() - 8);  // Remove .osm.zip
        var input = new ZipInputStream(new FileInputStream(filename));
        input.getNextEntry();
        parseOSM(input);
    }

    private void parseOSM(String filename) throws FileNotFoundException, XMLStreamException, FactoryConfigurationError {
        // Store base filename before parsing
        this.baseFilename = filename.substring(0, filename.lastIndexOf('.'));
        parseOSM(new FileInputStream(filename));
    }



    private void parseOSM(InputStream inputStream) throws FileNotFoundException, XMLStreamException, FactoryConfigurationError {
        System.out.println("=== STARTING OSM PARSING WITH DEBUG MODE ===");
        long startTime = System.currentTimeMillis();

        var input = XMLInputFactory.newInstance().createXMLStreamReader(new InputStreamReader(inputStream));

        //Temporary collections for parsing
        var wayNodes = new ArrayList<Node>();
        var id2way = new HashMap<Long, Way>();
        Set<String> skipHighwayTypes = Set.of(
                "proposed", "construction", "abandoned", "platform", "raceway",
                "corridor", "elevator", "escalator", "steps", "bus_guideway",
                "busway", "services", "rest_area", "escape", "emergency_access"
        );
        Map<String, Integer> completelySkipped = new HashMap<>();

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
        boolean bicycleAllowed = true;  // Default to true for most roads
        boolean carAllowed = true;      // Default to true for most roads
        int speedLimit = 50;

        //used for dijikstra

        String wayName = "Unknown road";

        //might delete in restructure
        var coast = false;
        var highway = false;
        long currentNodeId = -1;
        Map<String, String> currentTags = new HashMap<>();
        boolean isAddressNode = false;
        String highWayType = null;
        String landuseType = null;
        String natrualType = null; // Combined variable for natural types
        boolean inRelation = false;
        long currentRelationId = -1;
        long currentWayId = -1;
        boolean skipCurrentWay = false;  // Flag to skip entire way

        // For filtering routes - just a flag, no collection
        boolean skipRelation = false;
        boolean isNaturalFeature = false; // Flag to prioritize natural features

        // For address parsing
        Map<Long, Map<String, String>> nodeAddressTags = new HashMap<>();
        boolean inNode = false;
        boolean processingAddressNode = false;

        // DEBUG: Progress tracking
        int nodeCount = 0;
        int wayCount = 0;
        int relationCount = 0;
        long lastProgressTime = System.currentTimeMillis();

        System.out.println("Starting XML parsing...");

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

                    // Progress reporting for nodes
                    if (nodeCount % 1000000 == 0) {
                        long currentTime = System.currentTimeMillis();
                        System.out.println("Processed " + nodeCount + " nodes in " +
                                (currentTime - lastProgressTime) + "ms");
                        lastProgressTime = currentTime;

                        // Memory check
                        Runtime rt = Runtime.getRuntime();
                        long usedMB = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
                        System.out.println("Memory usage: " + usedMB + "MB");
                    }

                    inNode = true;
                    var id = Long.parseLong(input.getAttributeValue(null, "id"));
                    var lat = Double.parseDouble(input.getAttributeValue(null, "lat"));
                    var lon = Double.parseDouble(input.getAttributeValue(null, "lon"));
                    Node node = new Node(id, (float) -lat, (float) (0.56 * lon));
                    id2node.put(node);
                    if (graphBuilder != null) {
                        graphBuilder.storeNodeCoordinates(id, (float) (0.56 * lon), (float) -lat);
                    }
                    // Check if this node has address tags
                    currentNodeId = id;
                    currentTags.clear();
                    isAddressNode = false;

                    // Setup for address parsing
                    currentNodeId = id;
                    processingAddressNode = false;

                } else if (name.equals("way")) {
                    wayCount++;

                    // Progress reporting for ways
                    if (wayCount % 500000 == 0) {
                        long currentTime = System.currentTimeMillis();
                        System.out.println("Processed " + wayCount + " ways in " +
                                (currentTime - lastProgressTime) + "ms");
                        lastProgressTime = currentTime;

                        // Memory check
                        Runtime rt = Runtime.getRuntime();
                        long usedMB = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
                        System.out.println("Memory usage: " + usedMB + "MB");

                        if (usedMB > 2000) { // If using more than 2GB
                            System.gc();
                            System.out.println("Triggered garbage collection");
                        }
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

                    // Progress reporting for relations
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
                        continue;  // Skip processing this tag
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
                                speedLimit = Integer.parseInt(input.getAttributeValue(null, "value"));
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
                        var node = id2node.get(ref);
                        wayNodes.add(node);
                    }

                } else if (name.equals("member")) {
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
                    // If we've processed an address node, build the address and add to the trie
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
                    // var name = input.getLocalName();

                    if (name.equals("way")) {
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

                                graphBuilder.addRoadWithNodeIds(way, nodeIds, type, isOneWay,
                                        wayName, speedLimit, bicycleAllowed, carAllowed);

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
                            }
                        }

                        // Clear for next way
                        wayNodes.clear();
                    }
                }
            }
        }

        long parseTime = System.currentTimeMillis();
        System.out.println("=== OSM PARSING COMPLETE ===");
        System.out.println("Parsed in " + (parseTime - startTime) + "ms:");
        System.out.println("- " + nodeCount + " nodes");
        System.out.println("- " + wayCount + " ways");
        System.out.println("- " + relationCount + " relations");
        System.out.println("- " + roadWaysProcessed + " roads");

        // NEW: After parsing all ways, build the intersection-based graph
        if (buildGraphDuringParsing && graphBuilder != null) {
            System.out.println("=== STARTING GRAPH CONSTRUCTION ===");
            long graphStartTime = System.currentTimeMillis();

            EdgeWeightedDigraph tempGraph = graphBuilder.finalizeGraph();

            long graphEndTime = System.currentTimeMillis();
            System.out.println("=== GRAPH CONSTRUCTION COMPLETE ===");
            System.out.println("Graph built in " + (graphEndTime - graphStartTime) + "ms");
            System.out.println("Final graph: " + graphBuilder.getVertexCount() + " vertices, " + tempGraph.E() + " edges");

            // Save graph to file and immediately discard from memory
            try {
                fileBasedGraph.saveGraph(tempGraph, graphBuilder, baseFilename);  // Use the stored baseFilename
                tempGraph = null;  // Clear immediately
                graphBuilder = null;  // Also clear GraphBuilder
                System.gc();
                System.out.println("Graph saved to: " + baseFilename + ".graph");
            } catch (IOException e) {
                throw new RuntimeException("Failed to save graph to file: " + e.getMessage());
            }
        }

        // Continue with rest of processing...
        for (String featureType : potentialRelationWays.keySet()) {
            HashMap<Long, Way> unusedWays = potentialRelationWays.get(featureType);
            if (unusedWays != null && !unusedWays.isEmpty()) {
                System.out.println("Adding " + unusedWays.size() + " unused " + featureType + " ways to rendering");

                if (!typeWays.containsKey(featureType)) {
                    typeWays.put(featureType, new ArrayList<>());
                }

                // Add all the unused ways to typeWays
                typeWays.get(featureType).addAll(unusedWays.values());
            }
        }

        // Process KDTree, clean up and report statistics
        System.out.println("Building KD-tree for spatial queries...");
        for (String featureType : typeWays.keySet()) {
            ArrayList<Way> typeWaysList = typeWays.get(featureType);
            for (Way way : typeWaysList) {
                if (way.averageWayNode != null) {
                    kdTree.insert(way.averageWayNode.lon, way.averageWayNode.lat, way.data);
                }
            }
        }

        System.out.println("Cleaning up temporary data...");
        id2node.clear();
        ways = null;
        System.gc();

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

        System.out.println("=== OSM PROCESSING COMPLETE ===");
    }

    private void createAndAddAddress(long nodeId, Map<String, String> addressTags) {
        if (addressTags == null || addressTags.isEmpty()) {
            return;
        }

        // Get required address components
        String street = addressTags.get("street");
        String house = addressTags.get("housenumber");
        String city = addressTags.getOrDefault("city", internIfNotNull(addressTags.get("place")));
        city = internIfNotNull(city);

        // Check if we have the minimum required information
        if (street == null && city == null) {
            return;
        }

        // Build the address
        OSMAddress.Builder builder = new OSMAddress.Builder()
                .street(street)
                .house(house)
                .postcode(addressTags.get("postcode"))
                .city(city)
                .nodeId(nodeId);  // Store the node ID instead of the Node object

        // Add optional components if available
        if (addressTags.containsKey("floor")) {
            builder.floor(addressTags.get("floor"));
        }

        if (addressTags.containsKey("unit")) {
            builder.side(addressTags.get("unit"));
        }

        // Build and add to trie
        OSMAddress address = builder.build();
        addressHandler.addAddress(address);
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
    //KD tree method
    public void add(Point2D p1, Point2D p2) {
        list.add(new Line(p1, p2));
    }
    //Creating graph methods
    public EdgeWeightedDigraph buildRoadGraph() {
        // This method is no longer used but kept for compatibility
        System.out.println("buildRoadGraph() called but using file-based storage");
        return null;
    }

    /**
     * Access graph builder
     */
    public GraphBuilder getGraphBuilder() {
        if (graphBuilder == null) {
            buildRoadGraph(); // Ensure graph is built
        }
        return graphBuilder;
    }

    /**
     * Get road graph with segment handling
     */
    public EdgeWeightedDigraph getRoadGraph() {
        throw new UnsupportedOperationException(
                "In-memory graph not available. Use getFileBasedGraph() for routing."
        );
    }

    public FileBasedGraph getFileBasedGraph() {
        return fileBasedGraph;
    }

    public int findNearestVertex(float x, float y) {
        // Use file-based graph if available
        if (fileBasedGraph != null && fileBasedGraph.graphFileExists()) {
            return fileBasedGraph.findNearestVertex(x, y);
        }

        // Fallback error
        System.err.println("ERROR: No graph available for finding nearest vertex");
        return -1;

    }

    // Address-related methods
    public AddressHandler getAddressHandler() {
        return addressHandler;
    }

    private String internIfNotNull(String value) {
        return value != null ? value.intern() : null;
    }


    public void printid2NodeSortCount() {
        System.out.println("HOW OFTEN IS id2NODE SORTED -------------------------------");
        System.out.println(id2node.getSortCount());
    }

    public int addressHandlerSize() {
        return addressHandler.size();
    }

    public Map<String, ArrayList<Way>> getTypeWays() {
        return typeWays;
    }

    public float[] getNodeCoordinates(long nodeId) {
        System.out.println("DEBUG: Getting coordinates for node ID " + nodeId);

        // Check address node coordinates first (these survive serialization)
        float[] coords = addressNodeCoordinates.get(nodeId);
        if (coords != null) {
            System.out.println("DEBUG: Found address node coordinates: [" + coords[0] + ", " + coords[1] + "]");
            return coords;
        }

        // If not an address node, it might be a graph vertex
        if (fileBasedGraph != null) {
            // Check if this node is a vertex in the graph
            // Note: We'd need to add a nodeId->vertex mapping in FileBasedGraph for this
            // For now, we can't look up by nodeId in the graph
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
    // Add setter
    public void setTransportMode(TransportMode mode) {
        this.currentTransportMode = mode;
    }

    // Add getter
    public TransportMode getTransportMode() {
        return currentTransportMode;
    }

}
