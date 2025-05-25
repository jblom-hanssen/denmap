package com.example.osmparsing.address;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrieST<Value> implements Serializable {
    private Node root = new Node();
    private int size = 0;

    private static class Node implements Serializable {
        Object value = null;
        Map<Character, Edge> edges = new HashMap<>();
    }

    private static class Edge implements Serializable {
        String label;
        Node target;

        Edge(String label, Node target) {
            this.label = label;
            this.target = target;
        }
    }


    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public Value get(String key) {
        if (key == null) throw new IllegalArgumentException("Key cannot be null");

        Node node = root;
        int i = 0;

        while (i < key.length()) {
            char c = key.charAt(i);
            if (!node.edges.containsKey(c)) return null;

            Edge edge = node.edges.get(c);
            if (!key.substring(i).startsWith(edge.label)) return null;

            i += edge.label.length();
            node = edge.target;
        }

        return (Value) node.value;
    }


    public boolean contains(String key) {
        return get(key) != null;
    }


    public void put(String key, Value value) {
        if (key == null) throw new IllegalArgumentException("Key cannot be null");
        if (value == null) return;  // No deletion support

        Node current = root;
        int i = 0;

        while (i < key.length()) {
            char c = key.charAt(i);
            String remaining = key.substring(i);

            // Create new edge if character doesn't exist
            if (!current.edges.containsKey(c)) {
                Node newNode = new Node();
                current.edges.put(c, new Edge(remaining, newNode));
                current = newNode;
                break;
            }

            Edge edge = current.edges.get(c);

            // Find common prefix length
            int commonLength = 0;
            int maxLength = Math.min(edge.label.length(), remaining.length());
            while (commonLength < maxLength && edge.label.charAt(commonLength) == remaining.charAt(commonLength)) {
                commonLength++;
            }

            // Edge exactly matches remaining key
            if (commonLength == edge.label.length() && commonLength == remaining.length()) {
                current = edge.target;
                break;
            }

            // Edge matches a prefix of the remaining key
            if (commonLength == edge.label.length()) {
                i += commonLength;
                current = edge.target;
                continue;
            }

            // Split edge - create a new intermediate node
            Node splitNode = new Node();

            // Original edge gets shortened
            String originalSuffix = edge.label.substring(commonLength);
            Node originalTarget = edge.target;

            // Update original edge to point to split node
            edge.label = edge.label.substring(0, commonLength);
            edge.target = splitNode;

            // Add the remainder of the original edge from split node
            splitNode.edges.put(originalSuffix.charAt(0), new Edge(originalSuffix, originalTarget));

            // If split point is the end of the key
            if (commonLength == remaining.length()) {
                current = splitNode;
                break;
            }

        }

        // Set value at final node
        if (current.value == null) size++;
        current.value = value;
    }


    public Iterable<String> keys() {
        return keysWithPrefix("");
    }


    public Iterable<String> keysWithPrefix(String prefix) {
        List<String> results = new ArrayList<>();

        // Find node corresponding to prefix
        Node current = root;
        int i = 0;
        String exactPrefix = "";

        while (i < prefix.length()) {
            char c = prefix.charAt(i);
            if (!current.edges.containsKey(c)) return results;

            Edge edge = current.edges.get(c);
            String label = edge.label;

            // Prefix continues past this edge
            if (prefix.length() - i >= label.length() && prefix.substring(i, i + label.length()).equals(label)) {
                exactPrefix += label;
                i += label.length();
                current = edge.target;
                continue;
            }

            // Prefix ends within this edge
            if (label.startsWith(prefix.substring(i))) {
                String fullPath = prefix + label.substring(prefix.length() - i);
                collectKeys(edge.target, fullPath, results);
                return results;
            }

            // No match
            return results;
        }

        // Found the prefix node, collect all keys below it
        collectKeys(current, exactPrefix, results);
        return results;
    }

    private void collectKeys(Node node, String prefix, List<String> results) {
        if (node.value != null) {
            results.add(prefix);
        }

        for (Edge edge : node.edges.values()) {
            collectKeys(edge.target, prefix + edge.label, results);
        }
    }

}
