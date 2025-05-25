package com.example.osmparsing.mvc;

import com.example.osmparsing.way.Node;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class id2Node implements Serializable {
    private static final long serialVersionUID = 1L;
    int sortCount = 0;
    private final List<Node> nodes;
    private boolean unsorted;

    public id2Node() {
        this.nodes = new ArrayList<>();
        this.unsorted = false;
    }

    public id2Node(int initialCapacity) {
        this.nodes = new ArrayList<>(initialCapacity);
        this.unsorted = false;
    }

    public void put(Node node) {
        nodes.add(node);
        unsorted = true;
    }

    public Node get(long id) {
        if (unsorted) {
            // Use TimSort via Collections.sort
            Collections.sort(nodes, Comparator.comparingLong(Node::getId));
            sortCount++;
            unsorted = false;
        }

        int index = binarySearch(id);
        return index >= 0 ? nodes.get(index) : null;
    }

    private int binarySearch(long id) {
        int low = 0;
        int high = nodes.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            Node midNode = nodes.get(mid);
            long midId = midNode.getId();

            if (midId < id) {
                low = mid + 1;
            } else if (midId > id) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -1;
    }

    public void remove(long id) {
        int index = binarySearch(id);
        nodes.remove(index);
    }


    public void clear() {
        nodes.clear();
        unsorted = false;
    }

    public int size() {
        return nodes.size();
    }

    public int getSortCount() {
        return sortCount;
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

}



