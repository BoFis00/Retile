package com.retile.brush;

public final class BrushConstants {
    public static final int MAX_RADIUS = 32;
    public static final int MAX_FILTER = 64;
    public static final int MAX_REPLACEMENTS = 64;
    public static final int MAX_WEIGHT = 100;
    public static final int MIN_WEIGHT = 1;

    public static final int MAX_HISTORY_STEPS = 30;
    public static final int MAX_HISTORY_BLOCKS = 500_000;
    public static final int MAX_STROKE_BLOCKS = 500_000;
    public static final int STROKE_SPACING_DIVISOR = 2;
    public static final long STROKE_TIMEOUT_MS = 2_000;
    public static final int STROKE_PROGRESS_TICKS = 5;

    public static final int PREVIEW_MAX = 20_000;
    public static final int RAYCAST_DISTANCE = 64;
    public static final int EYE_TO_CENTER_MAX = 80;
    public static final long APPLY_THROTTLE_MS = 50;
    public static final int CACHE_RECHECK_TICKS = 10;

    public static final float CHAOS_MIN = -1.0f;
    public static final float CHAOS_MAX = 1.0f;
    public static final float CHAOS_STEP = 0.05f;

    public static final long SALT_INDEPENDENT = 0x9E3779B97F4A7C15L;
    public static final long SALT_LAYER = 0xBF58476D1CE4E5B9L;
    public static final long SALT_COLUMN = 0x94D049BB133111EBL;
    public static final long SALT_CHOICE = 0xD2B74407B1CE4E5BL;

    private BrushConstants() {}
}
