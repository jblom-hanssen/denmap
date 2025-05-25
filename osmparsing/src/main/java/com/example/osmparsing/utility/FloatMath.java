package com.example.osmparsing.utility;

/**
 * FloatMath - Utility class for float-specific math operations to avoid double conversions
 */
public final class FloatMath {
    private static final float PI = (float) Math.PI;
    private static final float RAD_TO_DEG = 180.0f / PI;
    private static final float DEG_TO_RAD = PI / 180.0f;

    // Private constructor to prevent instantiation
    private FloatMath() {}

    /**
     * Convert degrees to radians
     */
    public static float toRadians(float degrees) {
        return degrees * DEG_TO_RAD;
    }

    /**
     * Convert radians to degrees
     */
    public static float toDegrees(float radians) {
        return radians * RAD_TO_DEG;
    }

    /**
     * Calculate sine of an angle in radians
     */
    public static float sin(float radians) {
        return (float) Math.sin(radians);
    }

    /**
     * Calculate cosine of an angle in radians
     */
    public static float cos(float radians) {
        return (float) Math.cos(radians);
    }



    /**
     * Calculate arc tangent of y/x
     */
    public static float atan2(float y, float x) {
        return (float) Math.atan2(y, x);
    }

    /**
     * Calculate square root
     */
    public static float sqrt(float value) {
        return (float) Math.sqrt(value);
    }

    /**
     * Calculate value raised to the power of exponent
     */
    public static float pow(float value, float exponent) {
        return (float) Math.pow(value, exponent);
    }


    public static float min(float a, float b) {
        return Math.min(a, b);
    }
    public static float max(float a, float b) {
        return Math.max(a, b);
    }
}