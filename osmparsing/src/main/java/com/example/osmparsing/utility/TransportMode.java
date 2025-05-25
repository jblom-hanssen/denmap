package com.example.osmparsing.utility;

public enum TransportMode {
    CAR("Car", "🚗"),
    BICYCLE("Bicycle", "🚴");

    private final String displayName;
    private final String emoji;

    TransportMode(String displayName, String emoji) {
        this.displayName = displayName;
        this.emoji = emoji;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmoji() {
        return emoji;
    }

    @Override
    public String toString() {
        return displayName;
    }
}