package com.retile.brush;

import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;

public final class BrushSettings {
    public List<Identifier> filters = new ArrayList<>();
    public boolean includeAir = false;
    public List<WeightedEntry> replacements = new ArrayList<>();
    public float chaos = 0f;
    public boolean limitHeight = false;
    public int minY = -64;
    public int maxY = 320;
    public int radius = 5;
    public BrushShapeType shape = BrushShapeType.SPHERE;
    public boolean fixedSeed = false;
    public long seed = 0L;

    public record WeightedEntry(Identifier id, int weight) {}

    public BrushSettings copy() {
        BrushSettings c = new BrushSettings();
        c.filters = new ArrayList<>(filters);
        c.includeAir = includeAir;
        c.replacements = new ArrayList<>(replacements);
        c.chaos = chaos;
        c.limitHeight = limitHeight;
        c.minY = minY;
        c.maxY = maxY;
        c.radius = radius;
        c.shape = shape;
        c.fixedSeed = fixedSeed;
        c.seed = seed;
        return c;
    }

    public int normalizedMinY() {
        return Math.min(minY, maxY);
    }

    public int normalizedMaxY() {
        return Math.max(minY, maxY);
    }

    public int clampedRadius() {
        return Math.clamp(radius, 1, BrushConstants.MAX_RADIUS);
    }

    public boolean isValidForApply() {
        return !replacements.isEmpty();
    }

    public static float snapChaos(float v) {
        float snapped = Math.round(v / BrushConstants.CHAOS_STEP) * BrushConstants.CHAOS_STEP;
        return Math.clamp(snapped, BrushConstants.CHAOS_MIN, BrushConstants.CHAOS_MAX);
    }

    public static int clampWeight(int w) {
        return Math.clamp(w, BrushConstants.MIN_WEIGHT, BrushConstants.MAX_WEIGHT);
    }
}
