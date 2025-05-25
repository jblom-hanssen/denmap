package com.example.osmparsing.address;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Class to manage address data using a TrieST for efficient lookups.
 */
public class AddressHandler implements Serializable {
    private final TrieST<OSMAddress> addressTrie;
    private final Map<Long, OSMAddress> idToAddress;

    public AddressHandler() {
        addressTrie = new TrieST<>();
        idToAddress = new HashMap<>();
    }

    /**
     * Add an OSMAddress to the trie
     * @param address The address to add
     */
    public void addAddress(OSMAddress address) {
        if (address == null) {
            return;
        }

        // Generate search key
        String searchKey = address.generateSearchKey();

        // Store address in trie
        if (!searchKey.isEmpty()) {
            addressTrie.put(searchKey, address);
        }

        // Store address in id map for quick lookup by node id
        long nodeId = address.getNodeId();
        if (nodeId >= 0) {
            idToAddress.put(nodeId, address);
        }
    }


    public OSMAddress findAddress(String street, String house, String city) {
        StringBuilder key = new StringBuilder();
        if (street != null) {
            key.append(street.toLowerCase());
        }
        if (house != null) {
            key.append(house.toLowerCase());
        }
        if (city != null) {
            key.append(city.toLowerCase());
        }

        return addressTrie.get(key.toString());
    }


    public OSMAddress findAddressByKey(String searchKey) {
        if (searchKey == null || searchKey.isEmpty()) {
            return null;
        }

        // Normalize search key (only convert to lowercase, preserve spaces)
        searchKey = searchKey.toLowerCase();
        return addressTrie.get(searchKey);
    }


    public Iterable<String> findAddressesByPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return addressTrie.keys();
        }

        // Normalize prefix (only convert to lowercase, preserve spaces)
        prefix = prefix.toLowerCase();
        return addressTrie.keysWithPrefix(prefix);
    }


    public OSMAddress findAddressByNodeId(long nodeId) {
        return idToAddress.get(nodeId);
    }


    public int size() {
        return addressTrie.size();
    }


    public boolean isEmpty() {
        return addressTrie.isEmpty();
    }
}
