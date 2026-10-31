package com.retile.brush;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.Identifier;
import java.util.*;
import java.util.function.Function;

/** Shared eligibility rules used by server edits and client preview. */
public final class BrushSelection {
    private BrushSelection() {}
    public static boolean isBrush(Player player) {
        return player != null && player.isCreative() && player.getMainHandItem().is(Items.WOODEN_AXE);
    }
    public static boolean allowed(Block block, boolean allowAir) {
        BlockState state = block.defaultBlockState();
        return !state.hasBlockEntity() && !(block instanceof LiquidBlock) && state.getFluidState().isEmpty() && (allowAir || !state.isAir());
    }
    public static boolean selectable(Block block) { return block.asItem() != Items.AIR && allowed(block, false); }
    public static boolean isChunkLoaded(Level world, BlockPos pos) {
        return world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }
    public static Set<Block> resolveFilters(BrushSettings settings) {
        Set<Block> blocks = new HashSet<>();
        for (Identifier id : settings.filters) blocks.add(BuiltInRegistries.BLOCK.getValue(id)); return blocks;
    }
    public static boolean eligible(Level world, BlockPos pos, Set<Block> filters, boolean includeAir) {
        if (world.isOutsideBuildHeight(pos) || !isChunkLoaded(world, pos)) return false;
        return eligibleState(world.getBlockState(pos), filters, includeAir);
    }
    private static boolean eligibleState(BlockState state, Set<Block> filters, boolean includeAir) {
        return state != null && !state.hasBlockEntity() && !(state.getBlock() instanceof LiquidBlock) && state.getFluidState().isEmpty()
                && BrushLogic.shouldReplace(state, filters, filters.isEmpty(), includeAir);
    }
    public static List<BlockPos> collect(Level world, List<BlockPos> shape, BrushSettings settings, long seed) {
        return collect(shape, settings, seed, pos ->
                world.isOutsideBuildHeight(pos) || !isChunkLoaded(world, pos) ? null : world.getBlockState(pos));
    }
    /** The lookup returns null for positions outside the world or in unloaded chunks. */
    public static List<BlockPos> collect(List<BlockPos> shape, BrushSettings settings, long seed,
                                         Function<BlockPos, BlockState> stateLookup) {
        if (settings.replacements.isEmpty()) return List.of();
        Set<Block> filters = resolveFilters(settings);
        Map<Identifier, BlockState> replacements = new HashMap<>();
        for (BrushSettings.WeightedEntry entry : settings.replacements) {
            Block block = BuiltInRegistries.BLOCK.getValue(entry.id());
            if (allowed(block, false)) replacements.put(entry.id(), block.defaultBlockState());
        }
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos pos : shape) {
            BlockState before = stateLookup.apply(pos);
            if (!eligibleState(before, filters, settings.includeAir)) continue;
            BlockState replacement = replacements.get(BrushLogic.pick(pos, settings, seed));
            if (replacement != null && before != replacement) result.add(pos);
        }
        return result;
    }
}
