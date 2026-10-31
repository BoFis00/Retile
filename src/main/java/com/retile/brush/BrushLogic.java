package com.retile.brush;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

public final class BrushLogic {
    private BrushLogic() {}

    public static Identifier pick(BlockPos pos, BrushSettings settings, long seed) {
        if (settings.replacements.isEmpty()) return null;

        int totalWeight = 0;
        for (BrushSettings.WeightedEntry e : settings.replacements) totalWeight += e.weight();

        double rand;
        float chaos = settings.chaos;

        if (chaos == 0f) {
            long h = BrushHash.hash(seed, pos.getX(), pos.getY(), pos.getZ(), BrushConstants.SALT_INDEPENDENT);
            rand = BrushHash.toUnitDouble(h);
        } else if (chaos == 1f) {
            long h = BrushHash.hash2(seed, pos.getY(), 0, BrushConstants.SALT_LAYER);
            rand = BrushHash.toUnitDouble(h);
        } else if (chaos == -1f) {
            long packed = ((long) pos.getX() << 32) ^ (pos.getZ() & 0xFFFFFFFFL);
            long h = BrushHash.hash2(seed, packed, 0, BrushConstants.SALT_COLUMN);
            rand = BrushHash.toUnitDouble(h);
        } else {
            boolean isLayerGroup = chaos > 0;
            float absChaos = Math.abs(chaos);
            long decisionHash = BrushHash.hash(seed, pos.getX(), pos.getY(), pos.getZ(), BrushConstants.SALT_CHOICE);
            double decision = BrushHash.toUnitDouble(decisionHash);
            boolean useGroup = decision < absChaos;
            if (useGroup) {
                if (isLayerGroup) {
                    long h = BrushHash.hash2(seed, pos.getY(), 0, BrushConstants.SALT_LAYER);
                    rand = BrushHash.toUnitDouble(h);
                } else {
                    long packed = ((long) pos.getX() << 32) ^ (pos.getZ() & 0xFFFFFFFFL);
                    long h = BrushHash.hash2(seed, packed, 0, BrushConstants.SALT_COLUMN);
                    rand = BrushHash.toUnitDouble(h);
                }
            } else {
                long h = BrushHash.hash(seed, pos.getX(), pos.getY(), pos.getZ(), BrushConstants.SALT_INDEPENDENT);
                rand = BrushHash.toUnitDouble(h);
            }
        }

        double target = rand * totalWeight;
        double accum = 0;
        for (BrushSettings.WeightedEntry e : settings.replacements) {
            accum += e.weight();
            if (target < accum) return e.id();
        }
        return settings.replacements.get(settings.replacements.size() - 1).id();
    }

    public static boolean shouldReplace(net.minecraft.world.level.block.state.BlockState state,
                                        java.util.Set<net.minecraft.world.level.block.Block> filterBlocks,
                                        boolean filterEmpty,
                                        boolean includeAir) {
        boolean isAir = state.isAir();
        if (filterEmpty) {
            if (isAir) return includeAir;
            return true;
        }
        if (isAir) {
            return includeAir && filterBlocks.contains(state.getBlock());
        }
        return filterBlocks.contains(state.getBlock());
    }

    public static boolean shouldReplaceById(net.minecraft.world.level.block.state.BlockState state,
                                            java.util.Set<Identifier> filterIds,
                                            boolean filterEmpty,
                                            boolean includeAir) {
        boolean isAir = state.isAir();
        if (filterEmpty) {
            if (isAir) return includeAir;
            return true;
        }
        if (isAir) {
            if (!includeAir) return false;
            return filterIds.contains(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        }
        return filterIds.contains(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }
}
