package com.retile;

import com.retile.history.UndoHistory;
import java.util.Arrays;

/** Pure Java assertion runner; no game initialization or test framework. */
public final class UndoHistoryTests {
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
        test("empty stacks and invalid arguments", () -> {
            UndoHistory history = new UndoHistory();
            check(history.undo() == null && history.redo() == null, "Empty stack result");
            check(history.undoSize() == 0 && history.redoSize() == 0 && history.blockCount() == 0, "Empty counts");
            expect(IllegalArgumentException.class, () -> new UndoHistory(0, 10));
            expect(IllegalArgumentException.class, () -> new UndoHistory(10, -1));
            expect(NullPointerException.class, () -> history.push(null));
            expect(NullPointerException.class, () -> new UndoHistory.Step(null, new long[0], new int[0], new int[0]));
            expect(NullPointerException.class, () -> new UndoHistory.Step("test", null, new int[0], new int[0]));
            expect(NullPointerException.class, () -> new UndoHistory.Step("test", new long[0], null, new int[0]));
            expect(NullPointerException.class, () -> new UndoHistory.Step("test", new long[0], new int[0], null));
            expect(IllegalArgumentException.class, () -> new UndoHistory.Step("test", new long[1], new int[0], new int[1]));
            expect(IllegalArgumentException.class, () -> new UndoHistory.Step("test", new long[1], new int[1], new int[0]));
        });
        test("undo and redo order preserve combined count", () -> {
            UndoHistory history = new UndoHistory();
            UndoHistory.Step first = step(1, 2), second = step(2, 3), third = step(3, 4);
            history.push(first); history.push(second); history.push(third);
            check(history.undo() == third && history.undo() == second && history.undo() == first, "Undo order");
            check(history.undo() == null && history.undoSize() == 0 && history.redoSize() == 3, "Undo exhaustion");
            check(history.blockCount() == 9, "Undo must retain all block counts");
            check(history.redo() == first && history.redo() == second && history.redo() == third, "Redo order");
            check(history.redo() == null && history.undoSize() == 3 && history.redoSize() == 0, "Redo exhaustion");
            check(history.blockCount() == 9, "Redo must retain all block counts");
        });
        test("step and block limits evict oldest undo entries", () -> {
            UndoHistory history = new UndoHistory(3, 100);
            for (int i = 0; i < 5; i++) history.push(step(i, 2));
            check(history.undoSize() == 3 && history.blockCount() == 6, "Step limit");
            check(history.undo().positions()[0] == 4 && history.undo().positions()[0] == 3
                    && history.undo().positions()[0] == 2 && history.undo() == null, "Oldest entries evicted");
            history = new UndoHistory(10, 5);
            history.push(step(1, 2)); history.push(step(2, 3));
            check(history.undoSize() == 2 && history.blockCount() == 5, "Exact block cap");
            history.push(step(3, 1));
            check(history.undoSize() == 2 && history.blockCount() == 4, "Block eviction");
            check(history.undo().positions()[0] == 3 && history.undo().positions()[0] == 2, "Block eviction order");
        });
        test("new application clears redo even when nothing changes", () -> {
            UndoHistory history = new UndoHistory();
            history.push(step(1, 2)); history.push(step(2, 3)); history.undo();
            history.beginApply(); history.beginApply();
            check(history.redo() == null && history.undoSize() == 1 && history.blockCount() == 2, "No-op apply clears redo only");
            history.undo(); history.push(step(3, 0));
            check(history.redoSize() == 0 && history.undoSize() == 0 && history.blockCount() == 0, "Empty push clears redo");
            history.push(step(4, 2)); history.push(step(5, 3)); history.undo(); history.push(step(6, 1));
            check(history.redoSize() == 0 && history.undoSize() == 2 && history.blockCount() == 3, "Changed push clears redo");
        });
        test("oversized steps and default caps", () -> {
            UndoHistory history = new UndoHistory(30, 5);
            history.push(step(1, 2)); history.push(step(2, 6));
            check(history.undoSize() == 0 && history.blockCount() == 0, "Oversized step evicted with older entries");
            history = new UndoHistory();
            for (int i = 0; i < 31; i++) history.push(step(i, 1));
            check(history.undoSize() == 30 && history.blockCount() == 30, "Default step cap");
            history = new UndoHistory();
            history.push(step(1, 250001)); history.push(step(2, 250000));
            check(history.undoSize() == 1 && history.blockCount() == 250000, "Default block cap");
            history.push(step(3, 250000));
            check(history.undoSize() == 2 && history.blockCount() == 500000, "Exact default cap");
        });
        test("builder grows, skips unchanged blocks and returns compact snapshots", () -> {
            UndoHistory.Builder builder = new UndoHistory.Builder();
            check(builder.build("empty").size() == 0, "Empty builder");
            for (int i = 0; i < 1000; i++) { builder.add(i, i, i + 1); builder.add(-i, i, i); }
            UndoHistory.Step built = builder.build("dimension");
            check(built.size() == 1000 && built.before().length == 1000 && built.after().length == 1000, "Compact arrays");
            check(built.dimension().equals("dimension"), "Dimension");
            for (int i = 0; i < 1000; i++) {
                check(built.positions()[i] == i && built.before()[i] == i && built.after()[i] == i + 1, "Builder order " + i);
            }
            builder.add(1000, 1, 2);
            check(built.size() == 1000 && builder.build("next").size() == 1001, "Builder snapshot isolation");
            expect(NullPointerException.class, () -> builder.build(null));
        });
        test("direct steps contain only changes and copy input arrays", () -> {
            long[] positions = {5, 6, 7}; int[] before = {1, 2, 3}, after = {1, 4, 5};
            UndoHistory.Step step = new UndoHistory.Step("test", positions, before, after);
            positions[1] = 99; before[1] = 99; after[1] = 99;
            check(step.size() == 2 && step.positions()[0] == 6 && step.before()[0] == 2 && step.after()[0] == 4, "Direct step compaction and copy");
            UndoHistory history = new UndoHistory();
            history.push(new UndoHistory.Step("test", new long[]{1}, new int[]{7}, new int[]{7}));
            check(history.undoSize() == 0 && history.blockCount() == 0, "All unchanged direct step skipped");
        });
        System.out.println(passed + " tests passed");
    }

    private static UndoHistory.Step step(long marker, int count) {
        long[] positions = new long[count];
        if (count > 0) positions[0] = marker;
        int[] after = new int[count];
        Arrays.fill(after, 1);
        return new UndoHistory.Step("minecraft:overworld", positions, new int[count], after);
    }
}
