package com.example.osmparsing.way;

import javafx.scene.paint.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class StylesUtility {
    // Single source of truth for all colors in the application
    private static final Map<String, Color> DAY_COLORS = new HashMap<>();
    private static final Map<String, Color> NIGHT_COLORS = new HashMap<>();
    private static final Map<String, Color> PRESENT_COLORS = new HashMap<>();
    private static final Map<String, Boolean> fillable = new HashMap<>();
    private static final Map<String, Integer> zoomLevelRender = new HashMap<>();
    // For line width multipliers
    private static final Map<String, Float> LINE_WIDTH_MULTIPLIERS = new HashMap<>();

    static {
        DAY_COLORS.put("commercial", Color.web("#F2EFE9"));
        fillable.put("commercial", true);
        zoomLevelRender.put("commercial", 12);
        NIGHT_COLORS.put("commercial", Color.web("#303841"));

        DAY_COLORS.put("coastline", Color.web("#F2EFE9"));
        fillable.put("coastline", true);
        NIGHT_COLORS.put("coastline", Color.web("#303841"));
        zoomLevelRender.put("coastline", 1);

        DAY_COLORS.put("water", Color.web("#f2a891"));
        fillable.put("water", false);
        zoomLevelRender.put("water", 12);
        NIGHT_COLORS.put("water", Color.web("#1B262C"));

        DAY_COLORS.put("industrial", Color.web("#f5efe2"));
        fillable.put("industrial", true);
        NIGHT_COLORS.put("industrial", Color.web("#303841"));
        zoomLevelRender.put("industrial", 12);


        DAY_COLORS.put("forest", Color.web("#addd99"));
        fillable.put("forest", true);
        NIGHT_COLORS.put("forest", Color.web("#02383C"));
        zoomLevelRender.put("forest", 12);

        DAY_COLORS.put("area", Color.RED);
        fillable.put("area", true);
        zoomLevelRender.put("area", 12);

        DAY_COLORS.put("farmland", Color.web("#e7eacb"));
        fillable.put("farmland", true);
        NIGHT_COLORS.put("farmland", Color.web("#423F3E"));
        zoomLevelRender.put("farmland", 12);

        DAY_COLORS.put("grass", Color.web("#c8f2a0"));
        fillable.put("grass", true);
        NIGHT_COLORS.put("grass", Color.web("#191A19"));
        zoomLevelRender.put("grass", 12);

        DAY_COLORS.put("grassland", Color.web("#CDEBB0"));
        fillable.put("grassland", true);
        NIGHT_COLORS.put("grassland", Color.web("#062925"));
        zoomLevelRender.put("grassland", 12);

        DAY_COLORS.put("meadow", Color.web("#DDFFBC"));
        fillable.put("meadow", true);
        NIGHT_COLORS.put("meadow", Color.web("#191A19"));
        zoomLevelRender.put("meadow", 12);

        DAY_COLORS.put("wetland", Color.web("#A1CAE2"));
        fillable.put("wetland", true);
        zoomLevelRender.put("wetland", 12);

        DAY_COLORS.put("residential", Color.web("#E7DFDF"));
        fillable.put("residential", true);
        NIGHT_COLORS.put("residential", Color.web("#282D4F"));
        zoomLevelRender.put("residential", 12);

        DAY_COLORS.put("campsite", Color.web("#DEF1C0"));
        fillable.put("campsite", true);
        NIGHT_COLORS.put("campsite", Color.web("#62374E"));
        zoomLevelRender.put("campsite", 12);

        DAY_COLORS.put("scrub", Color.LIGHTSTEELBLUE);
        fillable.put("scrub", true);
        NIGHT_COLORS.put("scrub", Color.web("#191A19"));
        zoomLevelRender.put("scrub", 12);


        DAY_COLORS.put("track", Color.GRAY);
        fillable.put("track", false);
        zoomLevelRender.put("track", 12);

        DAY_COLORS.put("path", Color.BISQUE);
        fillable.put("path", false);
        zoomLevelRender.put("path", 12);

        DAY_COLORS.put("trunk", Color.web("#F9B26C"));
        fillable.put("trunk", false);
        zoomLevelRender.put("trunk", 12);

        DAY_COLORS.put("serviceroad", Color.WHITE);
        fillable.put("serviceroad", false);
        NIGHT_COLORS.put("serviceroad", Color.DARKGREY);
        zoomLevelRender.put("serviceroad", 12);

        DAY_COLORS.put("residentialroad", Color.LIGHTGREY);
        fillable.put("residentialroad", false);
        NIGHT_COLORS.put("residentialroad", Color.GREY);
        zoomLevelRender.put("residentialroad", 12);

        DAY_COLORS.put("unknown", Color.web("#C8C2BC"));
        fillable.put("unknown", false);
        zoomLevelRender.put("unknown", 12);

        DAY_COLORS.put("cycleway", Color.YELLOW);
        fillable.put("cycleway", false);
        zoomLevelRender.put("cycleway", 12);

        DAY_COLORS.put("primary", Color.web("#F9B29C"));
        fillable.put("primary", false);
        zoomLevelRender.put("primary", 12);

        DAY_COLORS.put("unclassifiedhighway", Color.DARKGREY);
        fillable.put("unclassifiedhighway", false);
        zoomLevelRender.put("unclassifiedhighway", 12);

        DAY_COLORS.put("building", Color.web("#777777"));
        fillable.put("building", true);
        zoomLevelRender.put("building", 13);
        NIGHT_COLORS.put("building", Color.web("#1D3E53"));

        DAY_COLORS.put("tertiary", Color.web("#76837E"));
        fillable.put("tertiary", false);
        NIGHT_COLORS.put("tertiary", Color.DARKGREY);
        zoomLevelRender.put("tertiary", 12);

        DAY_COLORS.put("lake", Color.web("#B8FFF9"));
        fillable.put("lake", true);
        zoomLevelRender.put("lake", 12);
        NIGHT_COLORS.put("lake", Color.web("#1B262C"));

        DAY_COLORS.put("motorway", Color.web("#E892A6"));
        fillable.put("motorway", false);
        zoomLevelRender.put("motorway", 12);
        NIGHT_COLORS.put("tertiary", Color.DARKGREY);

        DAY_COLORS.put("secondary", Color.web("#C3931F"));
        fillable.put("secondary", false);
        zoomLevelRender.put("secondary", 12);
        NIGHT_COLORS.put("secondary", Color.DARKGREY);

        //========================================================================
        // Initialize width multipliers

        // Wider roads
        LINE_WIDTH_MULTIPLIERS.put("river", 1.3f);
        LINE_WIDTH_MULTIPLIERS.put("waterway", 1.3f);
        LINE_WIDTH_MULTIPLIERS.put("motorway", 1.3f);
        LINE_WIDTH_MULTIPLIERS.put("motorway_link", 1.3f);
        LINE_WIDTH_MULTIPLIERS.put("trunk", 1.3f);
        LINE_WIDTH_MULTIPLIERS.put("trunk_link", 1.3f);

        // All other types default to 1.0

        //=================================================================
        PRESENT_COLORS.putAll(DAY_COLORS);
    }

    public static boolean isFillable(String featureType) {
        return fillable.getOrDefault(featureType, false);
    }

    public static int getZoomLevelRender(String featureType) {
        return zoomLevelRender.getOrDefault(featureType, 15);
    }

    public static Color getColor(String featureType) {
        return PRESENT_COLORS.getOrDefault(featureType, PRESENT_COLORS.get("default"));
    }

    public static Set<String> getAllFeatureTypes() {
        return new HashSet<>(DAY_COLORS.keySet());
    }

    public static float getLineWidthMultiplier(String highwayType) {
        return LINE_WIDTH_MULTIPLIERS.getOrDefault(highwayType, 0.8f);
    }

    public static void switchColors(Boolean night){
        if(night){
            PRESENT_COLORS.clear();
            PRESENT_COLORS.putAll(NIGHT_COLORS);
        }else{
            PRESENT_COLORS.clear();
            PRESENT_COLORS.putAll(DAY_COLORS);
        }
    }
}