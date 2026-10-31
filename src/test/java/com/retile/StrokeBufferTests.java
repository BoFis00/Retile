package com.retile;

import com.retile.history.StrokeBuffer;
import com.retile.history.UndoHistory;
import java.util.Arrays;

/** Pure Java assertion runner for changes accumulated during one brush stroke. */
public final class StrokeBufferTests {
    private static int passed;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void test(String name, Runnable body) {
        body.run();
        passed++;
        System.out.println("PASS " + name);
    }

    private static void expect(Class<? extends Throwable> type, Runnable body) {
        try { body.run(); }
        catch (Throwable error) {
            if (type.isInstance(error)) return;
            throw new AssertionError("Expected " + type.getSimpleName(), error);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

    public static void main(String[] args) {
        test("empty and unchanged strokes produce no history step", () -> {
            StrokeBuffer buffer = new StrokeBuffer(2);
            check(buffer.size() == 0 && !buffer.isFull(), "New stroke must be empty and have room");
            check(buffer.build("test:empty") == null, "Empty stroke must not create a step");
            for (long position : new long[]{0, Long.MIN_VALUE, Long.MAX_VALUE}) {
                check(!buffer.contains(position), "Empty stroke unexpectedly contains " + position);
                check(!buffer.add(position, 7, 7), "Unchanged block was accepted at " + position);
                check(!buffer.contains(position), "Unchanged block reserved position " + position);
            }
            check(buffer.size() == 0 && !buffer.isFull() && buffer.build("test:no_op") == null,
                    "Unchanged blocks must not consume capacity or create a step");
            check(buffer.add(0, 7, 8), "Real change after a no-op must be accepted");
            UndoHistory.Step step = buffer.build("test:changed");
            check(step != null && step.size() == 1 && buffer.contains(0), "Real change must produce one entry");
            checkEntry(step, 0, 0, 7, 8);
        });

        test("duplicates preserve first states and insertion order", () -> {
            StrokeBuffer buffer = new StrokeBuffer(3);
            check(buffer.add(41, 1, 2), "First change must be accepted");
            check(!buffer.add(41, 2, 3), "Duplicate change must be rejected");
            check(!buffer.add(41, 2, 1), "Duplicate reverting change must be rejected");
            check(buffer.add(-9, 4, 5), "Second distinct change must be accepted");
            check(!buffer.add(41, 99, 100), "Duplicate must not overwrite the original states");
            check(!buffer.add(-9, 5, 5), "Unchanged duplicate must be rejected");
            check(buffer.add(0, 6, 7), "Third distinct change must be accepted");
            check(buffer.size() == 3 && buffer.isFull(), "Only accepted positions should fill the stroke");
            UndoHistory.Step step = buffer.build("test:ordered");
            check(step != null && step.size() == 3 && "test:ordered".equals(step.dimension()),
                    "Ordered step size and dimension");
            checkEntry(step, 0, 41, 1, 2);
            checkEntry(step, 1, -9, 4, 5);
            checkEntry(step, 2, 0, 6, 7);
        });

        test("growing primitive storage supports extreme positions and isolated snapshots", () -> {
            StrokeBuffer buffer = new StrokeBuffer(4096);
            long[] initialPositions = {0, Long.MIN_VALUE, Long.MAX_VALUE, -1, 1};
            for (int i = 0; i < initialPositions.length; i++) {
                check(buffer.add(initialPositions[i], i, i + 1), "Extreme position rejected: " + initialPositions[i]);
            }
            for (int i = 0; i < 1000; i++) {
                long position = (i + 2L) << 32;
                check(buffer.add(position, i + 10, i + 11), "Growing stroke rejected position " + position);
            }
            UndoHistory.Step snapshot = buffer.build("test:first");
            int snapshotSize = initialPositions.length + 1000;
            check(snapshot != null && snapshot.size() == snapshotSize
                    && snapshot.positions().length == snapshotSize && snapshot.before().length == snapshotSize
                    && snapshot.after().length == snapshotSize, "Snapshot arrays must be compact");
            check("test:first".equals(snapshot.dimension()), "Snapshot dimension must be preserved");
            for (int i = 0; i < initialPositions.length; i++) {
                check(buffer.contains(initialPositions[i]), "Missing extreme position " + initialPositions[i]);
                checkEntry(snapshot, i, initialPositions[i], i, i + 1);
            }
            for (int i = 0; i < 1000; i++) {
                long position = (i + 2L) << 32;
                check(buffer.contains(position), "Missing accepted position " + position);
                checkEntry(snapshot, initialPositions.length + i, position, i + 10, i + 11);
            }
            long[] savedPositions = snapshot.positions().clone();
            int[] savedBefore = snapshot.before().clone();
            int[] savedAfter = snapshot.after().clone();
            for (int i = 0; i < 1200; i++) {
                check(buffer.add(-10000L - i, -i - 1, -i - 2), "Additional change rejected at " + i);
            }
            check(buffer.size() == snapshotSize + 1200 && !buffer.isFull(), "Building must not clear the buffer");
            check(!buffer.add(Long.MIN_VALUE, 200, 201), "Building must not forget accepted positions");
            check(snapshot.size() == snapshotSize && Arrays.equals(snapshot.positions(), savedPositions)
                    && Arrays.equals(snapshot.before(), savedBefore) && Arrays.equals(snapshot.after(), savedAfter),
                    "Further changes and array growth must not mutate an earlier snapshot");
            UndoHistory.Step next = buffer.build("test:next");
            check(next != null && next.size() == buffer.size() && "test:next".equals(next.dimension()),
                    "Later snapshot must contain the entire stroke");
            checkEntry(next, snapshotSize, -10000, -1, -2);
            checkEntry(next, next.size() - 1, -11199, -1200, -1201);
        });

        test("configurable limit rejects new positions at the exact cap", () -> {
            StrokeBuffer buffer = new StrokeBuffer(2);
            check(!buffer.add(70, 3, 3) && !buffer.contains(70), "No-op must not reserve a slot");
            check(buffer.add(10, 1, 2) && !buffer.isFull(), "Stroke must have room below its cap");
            check(!buffer.add(10, 2, 3) && buffer.size() == 1, "Duplicate must not consume capacity");
            check(buffer.add(20, 4, 5) && buffer.size() == 2 && buffer.isFull(), "Stroke must be full at its exact cap");
            check(!buffer.add(30, 6, 7) && !buffer.contains(30), "Over-limit change must not reserve its position");
            check(!buffer.add(70, 3, 4) && !buffer.contains(70), "Earlier no-op remains unaccepted when full");
            check(!buffer.add(10, 8, 9), "Duplicate in a full stroke must remain rejected");
            UndoHistory.Step step = buffer.build("test:capped");
            check(step != null && step.size() == 2 && buffer.size() == 2 && buffer.isFull(),
                    "Rejected changes and build must preserve full stroke");
            checkEntry(step, 0, 10, 1, 2);
            checkEntry(step, 1, 20, 4, 5);
            StrokeBuffer single = new StrokeBuffer(1);
            check(single.add(Long.MIN_VALUE, -1, 0) && single.isFull(), "Single-block limit must accept one change");
            check(!single.add(Long.MAX_VALUE, 0, 1) && !single.contains(Long.MAX_VALUE),
                    "Single-block limit must reject the next position");
        });

        test("invalid limits and non-empty null dimensions are rejected", () -> {
            expect(IllegalArgumentException.class, () -> new StrokeBuffer(0));
            expect(IllegalArgumentException.class, () -> new StrokeBuffer(-1));
            expect(IllegalArgumentException.class, () -> new StrokeBuffer(Integer.MIN_VALUE));
            StrokeBuffer buffer = new StrokeBuffer(2);
            check(buffer.add(5, 1, 2), "Setup change must be accepted");
            expect(NullPointerException.class, () -> buffer.build(null));
            check(buffer.size() == 1 && buffer.contains(5), "Rejected dimension must not alter the stroke");
            UndoHistory.Step step = buffer.build("plain-dimension-string");
            check(step != null && "plain-dimension-string".equals(step.dimension()), "Dimension is a plain string");
            checkEntry(step, 0, 5, 1, 2);
        });

        test("default limit accepts exactly 500000 changed blocks", () -> {
            StrokeBuffer buffer = new StrokeBuffer();
            for (int i = 0; i < 500_000; i++) {
                check(!buffer.isFull(), "Default stroke became full before position " + i);
                check(buffer.add(i, 0, 1), "Default stroke rejected position " + i + " below the cap");
            }
            check(buffer.size() == 500_000 && buffer.isFull(), "Default stroke must be full at 500000 blocks");
            check(!buffer.add(500_000, 0, 1) && !buffer.contains(500_000),
                    "The 500001st attempt must be rejected without reserving a position");
            UndoHistory.Step step = buffer.build("test:default_cap");
            check(step != null && step.size() == 500_000 && buffer.size() == 500_000 && buffer.isFull(),
                    "Default-cap snapshot must contain all changes without clearing the buffer");
            for (int i = 0; i < 500_000; i++) {
                check(buffer.contains(i), "Default stroke lost accepted position " + i);
                checkEntry(step, i, i, 0, 1);
            }
        });

        System.out.println(passed + " tests passed");
    }

    private static void checkEntry(UndoHistory.Step step, int index, long position, int before, int after) {
        check(step.positions()[index] == position && step.before()[index] == before && step.after()[index] == after,
                "Entry " + index + " must preserve position " + position + " and first states " + before + " -> " + after);
    }
}
