package com.example.osmparsing.mvc;

import com.example.osmparsing.way.Node;
import java.io.Serializable;
import java.util.*;

public class SortedNodeList implements Serializable {
    private List<Node> nodes;
    private boolean sorted = false;

    public SortedNodeList(int initialCapacity) {
        this.nodes = new ArrayList<>(initialCapacity);
    }

    public void add(Node node) {
        nodes.add(node);
        sorted = false;
    }

    public Node get(long id) {
        if (!sorted) {
            nodes.sort(Comparator.comparingLong(Node::getId));
            sorted = true;
        }

        int index = Collections.binarySearch(nodes,
                new Node(id, 0, 0),
                Comparator.comparingLong(Node::getId));

        return index >= 0 ? nodes.get(index) : null;
    }

    public void clear() {
        nodes.clear();
        sorted = false;
    }

    public int size() {
        return nodes.size();
    }

    public void compact() {
        if (!sorted) {
            nodes.sort(Comparator.comparingLong(Node::getId));
            sorted = true;
        }
        // Trim to size
        if (nodes instanceof ArrayList) {
            ((ArrayList<Node>) nodes).trimToSize();
        }
    }
}