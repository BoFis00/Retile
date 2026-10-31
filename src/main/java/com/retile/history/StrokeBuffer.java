package com.retile.history;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;

/** Primitive changes for one brush stroke, independent of Minecraft and Fabric. */
public final class StrokeBuffer {
    private final int maxBlocks;
    private final LongOpenHashSet touched;
    private long[] positions;
    private int[] before;
    private int[] after;
    private int size;

    public StrokeBuffer() { this(500_000); }

    public StrokeBuffer(int maxBlocks) {
        if (maxBlocks <= 0) throw new IllegalArgumentException("Stroke limit must be positive");
        this.maxBlocks = maxBlocks;
        int capacity = Math.min(16, maxBlocks);
        positions = new long[capacity];
        before = new int[capacity];
        after = new int[capacity];
        touched = new LongOpenHashSet(capacity);
    }

    public boolean contains(long position) { return touched.contains(position); }

    public boolean add(long position, int oldState, int newState) {
        if (oldState == newState || isFull() || touched.contains(position)) return false;
        if (size == positions.length) {
            int capacity = (int) Math.min(maxBlocks, (long) positions.length * 2);
            positions = Arrays.copyOf(positions, capacity);
            before = Arrays.copyOf(before, capacity);
            after = Arrays.copyOf(after, capacity);
        }
        if (!touched.add(position)) return false;
        positions[size] = position;
        before[size] = oldState;
        after[size++] = newState;
        return true;
    }

    public int size() { return size; }

    public boolean isFull() { return size >= maxBlocks; }

    /** Returns a compact snapshot, or null when the stroke contains no changes. */
    public UndoHistory.Step build(String dimension) {
        if (size == 0) return null;
        return new UndoHistory.Step(dimension, Arrays.copyOf(positions, size),
                Arrays.copyOf(before, size), Arrays.copyOf(after, size));
    }
}
