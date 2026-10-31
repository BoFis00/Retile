package com.retile.brush;

public final class BrushHash {
    private BrushHash() {}

    public static long splitMix64(long x) {
        x += 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    public static long hash(long seed, long a, long b, long c, long salt) {
        long h = seed ^ salt;
        h ^= a * 0x9E3779B97F4A7C15L;
        h ^= b * 0xBF58476D1CE4E5B9L;
        h ^= c * 0x94D049BB133111EBL;
        return splitMix64(h);
    }

    public static long hash2(long seed, long a, long b, long salt) {
        long h = seed ^ salt;
        h ^= a * 0x9E3779B97F4A7C15L;
        h ^= b * 0xBF58476D1CE4E5B9L;
        return splitMix64(h);
    }

    public static double toUnitDouble(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }

    public static float toUnitFloat(long hash) {
        return (float) toUnitDouble(hash);
    }
}
