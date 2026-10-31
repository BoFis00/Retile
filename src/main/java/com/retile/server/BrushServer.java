package com.retile.server;
import com.retile.brush.*;
import com.retile.history.StrokeBuffer;
import com.retile.history.UndoHistory;
import com.retile.network.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class BrushServer {
    private static final Map<UUID, UndoHistory> HISTORIES = new HashMap<>();
    private static final Map<UUID, Long> LAST_APPLY = new HashMap<>();
    private static final Map<UUID, Stroke> STROKES = new HashMap<>();
    // A timeout records the step, but reaching the cap still requires release before painting again.
    private static final Map<UUID, StrokeOwner> CAPPED_STROKES = new HashMap<>();

    private record StrokeOwner(String dimension, int slot) {
        static StrokeOwner of(ServerPlayer player) {
            return new StrokeOwner(player.level().dimension().identifier().toString(), player.getInventory().getSelectedSlot());
        }
        boolean matches(ServerPlayer player) {
            return BrushSelection.isBrush(player) && slot == player.getInventory().getSelectedSlot()
                    && dimension.equals(player.level().dimension().identifier().toString());
        }
    }

    private static final class Stroke {
        final StrokeBuffer changes = new StrokeBuffer(BrushConstants.MAX_STROKE_BLOCKS);
        final StrokeOwner owner;
        final long seed;
        long lastPacketTime;
        int lastProgressTick;
        int lastReportedCount;

        Stroke(StrokeOwner owner, long seed, long now, int tick) {
            this.owner = owner;
            this.seed = seed;
            lastPacketTime = now;
            lastProgressTick = tick - BrushConstants.STROKE_PROGRESS_TICKS;
        }
    }
    private BrushServer() {}
    public static void register() {
        PayloadTypeRegistry.playC2S().register(ApplyBrushPayload.TYPE, ApplyBrushPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StrokeEndPayload.TYPE, StrokeEndPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UndoPayload.TYPE, UndoPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RedoPayload.TYPE, RedoPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ApplyBrushPayload.TYPE, (p, ctx) -> apply(ctx.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(StrokeEndPayload.TYPE, (p, ctx) -> endStroke(ctx.player(), true));
        ServerPlayNetworking.registerGlobalReceiver(UndoPayload.TYPE, (p, ctx) -> restore(ctx.player(), false));
        ServerPlayNetworking.registerGlobalReceiver(RedoPayload.TYPE, (p, ctx) -> restore(ctx.player(), true));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            endStroke(handler.player, false);
            HISTORIES.remove(handler.player.getUUID()); LAST_APPLY.remove(handler.player.getUUID());
        });
        ServerTickEvents.END_SERVER_TICK.register(BrushServer::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STROKES.clear(); CAPPED_STROKES.clear(); HISTORIES.clear(); LAST_APPLY.clear();
        });
    }
    public static boolean valid(ApplyBrushPayload p) {
        if (p.phase() == null || p.radius() < 1 || p.radius() > BrushConstants.MAX_RADIUS || p.shape() == null
                || !Float.isFinite(p.chaos()) || Math.abs(p.chaos()) > 1 || p.filters().size() > BrushConstants.MAX_FILTER
                || p.replacements().isEmpty() || p.replacements().size() > BrushConstants.MAX_REPLACEMENTS) return false;
        for (Identifier id : p.filters()) if (!validBlock(id, true)) return false;
        for (BrushSettings.WeightedEntry e : p.replacements())
            if (e.weight() < 1 || e.weight() > BrushConstants.MAX_WEIGHT || !validBlock(e.id(), false)) return false;
        return true;
    }
    private static boolean validBlock(Identifier id, boolean air) {
        return id != null && BuiltInRegistries.BLOCK.containsKey(id) && BrushSelection.allowed(BuiltInRegistries.BLOCK.getValue(id), air);
    }
    private static void apply(ServerPlayer player, ApplyBrushPayload p) {
        if (!BrushSelection.isBrush(player) || !valid(p)
                || player.getEyePosition().distanceToSqr(Vec3.atCenterOf(p.center()))
                > BrushConstants.EYE_TO_CENTER_MAX * BrushConstants.EYE_TO_CENTER_MAX) return;
        UUID id = player.getUUID();
        long now = System.nanoTime(); Long last = LAST_APPLY.get(id);
        if (last != null && now - last < BrushConstants.APPLY_THROTTLE_MS * 1_000_000L) return;
        ServerLevel world = player.level();
        StrokeOwner owner = StrokeOwner.of(player);
        Stroke stroke = STROKES.get(id);
        if (stroke != null && stroke.owner.matches(player) && p.phase() == ApplyBrushPayload.Phase.CONTINUE
                && stroke.seed != p.seed()) return;
        LAST_APPLY.put(id, now);
        if ((stroke != null && !stroke.owner.matches(player))
                || (CAPPED_STROKES.containsKey(id) && !CAPPED_STROKES.get(id).matches(player))) {
            endStroke(player, true);
            stroke = null;
        }
        if (CAPPED_STROKES.containsKey(id)) {
            if (stroke != null) stroke.lastPacketTime = now;
            return;
        }
        if (p.phase() != ApplyBrushPayload.Phase.CONTINUE) {
            finishStroke(id, player, true);
            stroke = null;
        }
        // CONTINUE also recovers after a timeout or an ignored START; transport itself is ordered.
        int tick = world.getServer().getTickCount();
        if (stroke == null) {
            stroke = new Stroke(owner, p.seed(), now, tick);
            STROKES.put(id, stroke);
        }
        stroke.lastPacketTime = now;
        BrushSettings settings = p.settings();
        List<BlockPos> shape = BrushShape.collectPositions(p.center(), settings.radius, settings.shape, settings.limitHeight, settings.minY, settings.maxY);
        for (BlockPos pos : BrushSelection.collect(world, shape, settings, p.seed())) {
            if (stroke.changes.isFull()) break;
            long position = pos.asLong();
            if (stroke.changes.contains(position)) continue;
            BlockState before = world.getBlockState(pos);
            BlockState after = BuiltInRegistries.BLOCK.getValue(BrushLogic.pick(pos, settings, p.seed())).defaultBlockState();
            if (before != after && set(world, pos, after)) stroke.changes.add(position, Block.getId(before), Block.getId(after));
        }
        if (stroke.changes.isFull()) {
            CAPPED_STROKES.put(id, owner);
            stroke.lastProgressTick = tick;
            stroke.lastReportedCount = stroke.changes.size();
            message(player, "stroke_limit", BrushConstants.MAX_STROKE_BLOCKS);
        }
        if (p.phase() == ApplyBrushPayload.Phase.SINGLE) endStroke(player, true);
        else progress(player, stroke, tick);
    }
    private static void tick(MinecraftServer server) {
        long now = System.nanoTime();
        int tick = server.getTickCount();
        for (UUID id : new ArrayList<>(STROKES.keySet())) {
            Stroke stroke = STROKES.get(id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                finishStroke(id, null, false);
                CAPPED_STROKES.remove(id);
            } else if (!stroke.owner.matches(player)) {
                endStroke(player, true);
            } else if (now - stroke.lastPacketTime > BrushConstants.STROKE_TIMEOUT_MS * 1_000_000L) {
                finishStroke(id, player, true);
            } else progress(player, stroke, tick);
        }
        CAPPED_STROKES.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            return !entry.getValue().matches(player);
        });
    }
    private static void progress(ServerPlayer player, Stroke stroke, int tick) {
        if (!stroke.changes.isFull() && stroke.changes.size() != stroke.lastReportedCount
                && tick - stroke.lastProgressTick >= BrushConstants.STROKE_PROGRESS_TICKS) {
            message(player, "stroke_progress", stroke.changes.size());
            stroke.lastProgressTick = tick;
            stroke.lastReportedCount = stroke.changes.size();
        }
    }
    private static void endStroke(ServerPlayer player, boolean announce) {
        UUID id = player.getUUID();
        finishStroke(id, player, announce);
        CAPPED_STROKES.remove(id);
    }
    private static void finishStroke(UUID id, ServerPlayer player, boolean announce) {
        Stroke stroke = STROKES.remove(id);
        if (stroke == null) return;
        UndoHistory history = HISTORIES.computeIfAbsent(id, key ->
                new UndoHistory(BrushConstants.MAX_HISTORY_STEPS, BrushConstants.MAX_HISTORY_BLOCKS));
        UndoHistory.Step step = stroke.changes.build(stroke.owner.dimension());
        if (step == null) history.beginApply();
        else history.push(step);
        if (announce && player != null) message(player, "applied", stroke.changes.size());
    }
    private static boolean set(ServerLevel world, BlockPos pos, BlockState state) {
        // UPDATE_CLIENTS is Mojang's NOTIFY_LISTENERS. onPlace can update neighbors even without UPDATE_NEIGHBORS.
        return world.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
                | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_ON_PLACE);
    }
    private static void restore(ServerPlayer player, boolean redo) {
        if (!player.isCreative()) return;
        finishStroke(player.getUUID(), player, true);
        UndoHistory history = HISTORIES.get(player.getUUID());
        UndoHistory.Step step = history == null ? null : redo ? history.redo() : history.undo();
        if (step == null) { message(player, redo ? "nothing_redo" : "nothing_undo"); return; }
        ServerLevel world = player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(step.dimension())));
        int changed = 0;
        if (world != null) for (int i = 0; i < step.size(); i++) {
            BlockPos pos = BlockPos.of(step.positions()[i]);
            if (world.isOutsideBuildHeight(pos) || !BrushSelection.isChunkLoaded(world, pos)) continue;
            BlockState current = world.getBlockState(pos); int expected = redo ? step.before()[i] : step.after()[i];
            BlockState target = Block.stateById(redo ? step.after()[i] : step.before()[i]);
            if (!current.hasBlockEntity() && !target.hasBlockEntity() && Block.getId(current) == expected && set(world, pos, target)) changed++;
        }
        message(player, redo ? "redone" : "undone", changed, step.size() - changed);
    }
    private static void message(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("retile.message." + key, args), true);
    }
}
