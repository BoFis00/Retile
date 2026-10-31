package com.retile;

import com.retile.brush.BrushLogic;
import com.retile.brush.BrushSelection;
import com.retile.brush.BrushSettings;
import com.retile.brush.BrushShape;
import com.retile.brush.BrushShapeType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exercises the shared preview/server selector using block snapshots without creating a world. */
public final class BrushSelectionTests {
    private static int passed;

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        test("selection excludes unchanged states and tracks the seed of the next apply", () -> {
            BrushSettings settings = new BrushSettings();
            settings.replacements.add(new BrushSettings.WeightedEntry(id("quartz_block"), 1));
            settings.replacements.add(new BrushSettings.WeightedEntry(id("white_wool"), 3));
            List<BlockPos> shape = BrushShape.collectPositions(new BlockPos(-7, 64, 11), 4,
                    BrushShapeType.SPHERE, false, -64, 320);
            Map<BlockPos, BlockState> snapshot = new HashMap<>();
            long seed = 624816;
            for (BlockPos pos : shape) snapshot.put(pos,
                    BuiltInRegistries.BLOCK.getValue(BrushLogic.pick(pos, settings, seed)).defaultBlockState());
            check(BrushSelection.collect(shape, settings, seed, snapshot::get).isEmpty(),
                    "Existing target states must produce no preview or server changes");
            Set<BlockPos> expected = new java.util.HashSet<>();
            for (BlockPos pos : shape) {
                BlockState next = BuiltInRegistries.BLOCK.getValue(BrushLogic.pick(pos, settings, seed + 1)).defaultBlockState();
                if (snapshot.get(pos) != next) expected.add(pos);
            }
            check(!expected.isEmpty(), "Fixture must change when its seed changes");
            check(expected.equals(Set.copyOf(BrushSelection.collect(shape, settings, seed + 1, snapshot::get))),
                    "Preview/server selection must match the states chosen by the supplied seed");
        });

        test("air toggle and explicit filters select the correct positions", () -> {
            BrushSettings settings = quartzSettings();
            BlockPos stone = new BlockPos(0, 64, 0), air = stone.above(), dirt = stone.below();
            Map<BlockPos, BlockState> snapshot = Map.of(stone, Blocks.STONE.defaultBlockState(),
                    air, Blocks.AIR.defaultBlockState(), dirt, Blocks.DIRT.defaultBlockState());
            List<BlockPos> shape = List.of(stone, air, dirt);
            checkSelection(Set.of(stone, dirt), shape, settings, snapshot);
            settings.includeAir = true;
            checkSelection(Set.of(stone, air, dirt), shape, settings, snapshot);
            settings.filters.add(id("stone"));
            checkSelection(Set.of(stone), shape, settings, snapshot);
            settings.filters.add(id("air"));
            checkSelection(Set.of(stone, air), shape, settings, snapshot);
            settings.includeAir = false;
            checkSelection(Set.of(stone), shape, settings, snapshot);
            settings.filters.remove(id("stone"));
            checkSelection(Set.of(), shape, settings, snapshot);
        });

        test("selection skips unavailable positions, fluids and block entities", () -> {
            BrushSettings settings = quartzSettings();
            settings.includeAir = true;
            List<BlockPos> shape = List.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                    new BlockPos(2, 64, 0), new BlockPos(3, 64, 0));
            Map<BlockPos, BlockState> snapshot = Map.of(shape.get(0), Blocks.STONE.defaultBlockState(),
                    shape.get(1), Blocks.WATER.defaultBlockState(), shape.get(2), Blocks.CHEST.defaultBlockState());
            checkSelection(Set.of(shape.get(0)), shape, settings, snapshot);
            settings.replacements.clear();
            check(BrushSelection.collect(shape, settings, 0, pos -> {
                throw new AssertionError("An empty replacement list must not read world states");
            }).isEmpty(), "Empty replacements must select no positions");
        });

        System.out.println(passed + " tests passed");
    }

    private static BrushSettings quartzSettings() {
        BrushSettings settings = new BrushSettings();
        settings.replacements.add(new BrushSettings.WeightedEntry(id("quartz_block"), 1));
        return settings;
    }

    private static Identifier id(String path) { return Identifier.withDefaultNamespace(path); }

    private static void checkSelection(Set<BlockPos> expected, List<BlockPos> shape, BrushSettings settings,
                                       Map<BlockPos, BlockState> snapshot) {
        check(expected.equals(Set.copyOf(BrushSelection.collect(shape, settings, 0, snapshot::get))),
                "Incorrect selection with filters " + settings.filters + " and includeAir=" + settings.includeAir);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void test(String name, Runnable body) {
        body.run();
        passed++;
        System.out.println("PASS " + name);
    }
}
