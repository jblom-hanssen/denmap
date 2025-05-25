package com.example.osmparsing.address;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Objects;


public class OSMAddress implements Serializable {
    private final String street;
    private final String house;
    private final String floor;
    private final String side;
    private final String postcode;
    private final String city;
    private final long nodeId;  // Store only the node ID instead of the entire Node object

    private OSMAddress(String street, String house, String floor, String side,
                       String postcode, String city, long nodeId) {
        this.street = street;
        this.house = house;
        this.floor = floor;
        this.side = side;
        this.postcode = postcode;
        this.city = city;
        this.nodeId = nodeId;
    }

    public String getStreet() {
        return street;
    }

    public String getHouse() {
        return house;
    }

    public String getFloor() {
        return floor;
    }

    public String getSide() {
        return side;
    }

    public String getPostcode() {
        return postcode;
    }

    public String getCity() {
        return city;
    }

    public long getNodeId() {
        return nodeId;
    }

    @Override
    public String toString() {
        if (floor == null || side == null) {
            return street + " " + house + "\n" + postcode + " " + city;
        }
        return street + " " + house + ", " + floor + " " + side + "\n" + postcode + " " + city;
    }


    public String generateSearchKey() {
        StringBuilder key = new StringBuilder();
        if (street != null) {
            key.append(street.toLowerCase()).append(" ");
        }
        if (house != null) {
            key.append(house.toLowerCase()).append(" ");
        }
        if (city != null) {
            key.append(city.toLowerCase()).append(" ");
        }
        return key.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OSMAddress that = (OSMAddress) o;
        return Objects.equals(street, that.street) &&
                Objects.equals(house, that.house) &&
                Objects.equals(city, that.city) &&
                Objects.equals(postcode, that.postcode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(street, house, city, postcode);
    }

    public static class Builder {
        private String street, house, floor, side, postcode, city;
        private long nodeId = -1;

        public Builder street(String street) {
            this.street = street;
            return this;
        }

        public Builder house(String house) {
            this.house = house;
            return this;
        }

        public Builder floor(String floor) {
            this.floor = floor;
            return this;
        }

        public Builder side(String side) {
            this.side = side;
            return this;
        }

        public Builder postcode(String postcode) {
            this.postcode = postcode;
            return this;
        }

        public Builder city(String city) {
            this.city = city;
            return this;
        }

        public Builder nodeId(long nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        public OSMAddress build() {
            return new OSMAddress(street, house, floor, side, postcode, city, nodeId);
        }
    }
}