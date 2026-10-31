package com.retile.history;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Objects;

/** Pure data contract; implemented independently of Minecraft and Fabric. */
public final class UndoHistory {
    public record Step(String dimension, long[] positions, int[] before, int[] after) {
        public Step {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(positions, "positions");
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
            if (positions.length != before.length || positions.length != after.length) {
                throw new IllegalArgumentException("Step arrays must have equal lengths");
            }
            int changed = 0;
            for (int i = 0; i < positions.length; i++) {
                if (before[i] != after[i]) changed++;
            }
            long[] compactPositions = new long[changed];
            int[] compactBefore = new int[changed];
            int[] compactAfter = new int[changed];
            for (int i = 0, target = 0; i < positions.length; i++) {
                if (before[i] == after[i]) continue;
                compactPositions[target] = positions[i];
                compactBefore[target] = before[i];
                compactAfter[target++] = after[i];
            }
            positions = compactPositions;
            before = compactBefore;
            after = compactAfter;
        }

        public int size() { return positions.length; }
    }

    public static final class Builder {
        private long[] positions = new long[16];
        private int[] before = new int[16];
        private int[] after = new int[16];
        private int size;

        public void add(long position, int oldState, int newState) {
            if (oldState == newState) return;
            if (size == positions.length) {
                int capacity = Math.toIntExact(Math.min(Integer.MAX_VALUE, (long) size * 2));
                if (capacity == size) throw new IllegalStateException("Too many changed blocks");
                positions = Arrays.copyOf(positions, capacity);
                before = Arrays.copyOf(before, capacity);
                after = Arrays.copyOf(after, capacity);
            }
            positions[size] = position;
            before[size] = oldState;
            after[size++] = newState;
        }

        public Step build(String dimension) {
            return new Step(dimension, Arrays.copyOf(positions, size),
                    Arrays.copyOf(before, size), Arrays.copyOf(after, size));
        }
    }

    private final int maxSteps;
    private final int maxBlocks;
    private final Deque<Step> undo = new ArrayDeque<>();
    private final Deque<Step> redo = new ArrayDeque<>();
    private long blocks;

    public UndoHistory() { this(30, 500000); }

    public UndoHistory(int maxSteps, int maxBlocks) {
        if (maxSteps <= 0 || maxBlocks <= 0) {
            throw new IllegalArgumentException("History limits must be positive");
        }
        this.maxSteps = maxSteps;
        this.maxBlocks = maxBlocks;
    }

    public void beginApply() {
        for (Step step : redo) blocks -= step.size();
        redo.clear();
    }

    public void push(Step step) {
        Objects.requireNonNull(step, "step");
        beginApply();
        if (step.size() == 0) return;
        undo.addLast(step);
        blocks += step.size();
        while (undo.size() > maxSteps || blocks > maxBlocks) {
            blocks -= undo.removeFirst().size();
        }
    }

    public Step undo() {
        Step step = undo.pollLast();
        if (step != null) redo.addLast(step);
        return step;
    }

    public Step redo() {
        Step step = redo.pollLast();
        if (step != null) undo.addLast(step);
        return step;
    }

    public int undoSize() { return undo.size(); }
    public int redoSize() { return redo.size(); }
    public int blockCount() { return (int) blocks; }
}
