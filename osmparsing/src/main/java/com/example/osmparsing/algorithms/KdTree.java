package com.example.osmparsing.algorithms;

import com.example.osmparsing.utility.FloatMath;
import com.example.osmparsing.way.Way;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class KdTree implements Serializable {
    public List<Node> kdlist = new ArrayList<Node>();

    static class Node implements Serializable {
        float lon, lat;
        String data;
        Node left, right;
        int depth;

        Node(float lon, float lat, String data, int depth) {
            this.lon = lon;
            this.lat = lat;
            this.data = data;
            this.depth = depth;
        }


        float distanceTo(float lon, float lat) {
            float dx = this.lon - lon;
            float dy = this.lat - lat;
            return FloatMath.sqrt(dx * dx + dy * dy);
        }
    }

    private static final float EARTH_RADIUS_KM = 6371.0F;

    private static float haversine(float lon1, float lat1, float lon2, float lat2) {
        float dLat = FloatMath.toRadians(lat2 - lat1);
        float dLon = FloatMath.toRadians(lon2 - lon1);

        float latRad1 = FloatMath.toRadians(lat1);
        float latRad2 = FloatMath.toRadians(lat2);

        float sinDLatHalf = FloatMath.sin(dLat/2);
        float sinDLonHalf = FloatMath.sin(dLon/2);

        float a = sinDLatHalf * sinDLatHalf +
                FloatMath.cos(latRad1) * FloatMath.cos(latRad2) *
                        sinDLonHalf * sinDLonHalf;

        return EARTH_RADIUS_KM * 2.0f * FloatMath.atan2(
                FloatMath.sqrt(a), FloatMath.sqrt(1.0f - a));
    }

    private Node root;

    public void insertList(List<Way> ways) {
        for (Way w : ways) {
            kdlist.add(new Node(w.averageWayNode.lon, w.averageWayNode.lat, w.data, w.depth));
        }
        build(kdlist);

    }

    public void insert(float lon, float lat, String data) {
        root = insertRec(root, lon, lat, data, 0);
    }

    private Node insertRec(Node node, float lon, float lat, String data, int depth) {
        if (node == null) return new Node(lon, lat, data, depth);
        int axis = depth % 2;
        if ((axis == 0 ? lon < node.lon : lat < node.lat)) {
            node.left = insertRec(node.left, lon, lat, data, depth + 1);
        } else {
            node.right = insertRec(node.right, lon, lat, data, depth + 1);
        }
        return node;
    }

    public String nearest(float lon, float lat) {
        // Check if the tree is empty
        if (root == null) {
            return null; // Return null if the tree is empty
        }

        Node nearestNode = nearestRec(root, lon, lat, root, Float.MAX_VALUE);

        // Check if nearest node is null before accessing data
        return (nearestNode != null) ? nearestNode.data : null;
    }

    private Node nearestRec(Node node, float lon, float lat, Node best, float bestDist) {
        if (node == null) return best;

        float d = node.distanceTo(lon, lat);
        if (best == null || d < bestDist) {
            best = node;
            bestDist = d;
        }

        int axis = node.depth % 2;
        boolean goLeft = (axis == 0) ? lon < node.lon : lat < node.lat;

        // Explore closer branch first
        Node closer = goLeft ? node.left : node.right;
        Node further = goLeft ? node.right : node.left;

        if (closer != null) {
            best = nearestRec(closer, lon, lat, best, bestDist);
            // Update best distance if we found a better node
            bestDist = (best != null) ? best.distanceTo(lon, lat) : bestDist;
        }

        // Check if we need to explore the other branch
        float axisDistance = (axis == 0) ? Math.abs(lon - node.lon) : Math.abs(lat - node.lat);

        if (further != null && axisDistance < bestDist) {
            best = nearestRec(further, lon, lat, best, bestDist);
        }

        return best;
    }

    public List<String> findWithinRadius(float lon, float lat, float radiusKm) {
        List<String> result = new ArrayList<>();
        findWithinRadiusRec(root, lon, lat, radiusKm/1000, result);
        return result;
    }

    private void findWithinRadiusRec(Node node, float lon, float lat, float radiusKm, List<String> result) {
        if (node == null) return;


        float dist = haversine(lon, lat, node.lon, node.lat);
        if (dist <= radiusKm) {
            result.add(node.data);
        }

        int axis = node.depth % 2;
        float delta = (axis == 0 ? lon - node.lon : lat - node.lat);

        // Always check the "near" side
        Node near = delta < 0 ? node.left : node.right;
        Node far = delta < 0 ? node.right : node.left;

        findWithinRadiusRec(near, lon, lat, radiusKm, result);

        // Check the "far" side only if needed
        float maxPossibleDist = axis == 0
                ? haversine(lon, lat, node.lon + delta, lat)
                : haversine(lon, lat, lon, node.lat + delta);

        if (Math.abs(delta) < radiusKm) {
            findWithinRadiusRec(far, lon, lat, radiusKm, result);
        }
    }

    public String nearestWithinRadius(float lon, float lat, float maxDistanceMeters) {
        Node[] best = { null };
        float[] bestDist = { Float.MAX_VALUE };
        nearestWithinRadiusRec(root, lon, lat, (float) (maxDistanceMeters / 1000.0), best, bestDist);
        return best[0] != null ? best[0].data : null;
    }

    private void nearestWithinRadiusRec(Node node, float lon, float lat, float maxDistanceKm, Node[] best, float[] bestDist) {
        if (node == null) return;

        float dist = haversine(lon, lat, node.lon, node.lat);
        if (dist < bestDist[0] && dist <= maxDistanceKm) {
            best[0] = node;
            bestDist[0] = dist;
        }

        int axis = node.depth % 2;
        float delta = (axis == 0 ? lon - node.lon : lat - node.lat);

        Node near = delta < 0 ? node.left : node.right;
        Node far = delta < 0 ? node.right : node.left;

        nearestWithinRadiusRec(near, lon, lat, maxDistanceKm, best, bestDist);

        // Check far side only if needed
        if (Math.abs(delta) < bestDist[0]) {
            nearestWithinRadiusRec(far, lon, lat, maxDistanceKm, best, bestDist);
        }
    }

    public void build(List<Node> points) {
        this.root = buildRecursive(points, 0);
    }

    private Node buildRecursive(List<Node> points, int depth) {
        if (points.isEmpty()) return null;

        int axis = depth % 2;
        int medianIndex = points.size() / 2;
        Node medianNode = quickSelect(points, medianIndex, axis);

        List<Node> leftPoints = new ArrayList<>();
        List<Node> rightPoints = new ArrayList<>();
        for (Node node : points) {
            if (node == medianNode) continue; // Skip the median itself
            if ((axis == 0 && node.lon < medianNode.lon) || (axis == 1 && node.lat < medianNode.lat)) {
                leftPoints.add(node);
            } else {
                rightPoints.add(node);
            }
        }

        medianNode.depth = depth;
        medianNode.left = buildRecursive(leftPoints, depth + 1);
        medianNode.right = buildRecursive(rightPoints, depth + 1);
        return medianNode;
    }

    private Node quickSelect(List<Node> list, int k, int axis) {
        return quickSelectHelper(list, 0, list.size() - 1, k, axis);
    }

    private Node quickSelectHelper(List<Node> list, int left, int right, int k, int axis) {
        if (left == right) return list.get(left);

        int pivotIndex = left + (int) (Math.random() * (right - left + 1));
        pivotIndex = partition(list, left, right, pivotIndex, axis);

        if (k == pivotIndex) {
            return list.get(k);
        } else if (k < pivotIndex) {
            return quickSelectHelper(list, left, pivotIndex - 1, k, axis);
        } else {
            return quickSelectHelper(list, pivotIndex + 1, right, k, axis);
        }
    }

    private int partition(List<Node> list, int left, int right, int pivotIndex, int axis) {
        Node pivotValue = list.get(pivotIndex);
        float pivotCoord = (axis == 0) ? pivotValue.lon : pivotValue.lat;

        swap(list, pivotIndex, right);
        int storeIndex = left;

        for (int i = left; i < right; i++) {
            float iCoord = (axis == 0) ? list.get(i).lon : list.get(i).lat;
            if (iCoord < pivotCoord) {
                swap(list, storeIndex, i);
                storeIndex++;
            }
        }

        swap(list, storeIndex, right);
        return storeIndex;
    }

    private void swap(List<Node> list, int i, int j) {
        Node temp = list.get(i);
        list.set(i, list.get(j));
        list.set(j, temp);
    }




}