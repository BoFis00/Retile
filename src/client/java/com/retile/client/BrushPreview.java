package com.retile.client;
import com.retile.brush.*;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import java.util.*;

public final class BrushPreview {
    private static BlockPos center;
    private static BrushSettings cachedSettings;
    private static Object world;
    private static List<BlockPos> shape = List.of();
    private static BrushPreviewGeometry geometry = BrushPreviewGeometry.EMPTY;
    private static final float FILL_RED = .4f, FILL_GREEN = .75f, FILL_BLUE = 1f, FILL_ALPHA = .15f;
    private static final int OUTLINE_COLOR = 0xCCFFFFFF;
    private static final RenderStateDataKey<BrushPreviewGeometry> FRAME_GEOMETRY =
            RenderStateDataKey.create(() -> "retile brush surface");
    private static long stateHash;
    private static long cachedSeed;
    private static int ticks;
    private BrushPreview() {}
    public static void register() {
        WorldRenderEvents.END_EXTRACTION.register(context -> {
            Minecraft client = Minecraft.getInstance();
            BlockPos target = BrushSelection.isBrush(client.player) ? RetileClient.target(client) : null;
            ((FabricRenderState) context.worldState()).setData(FRAME_GEOMETRY,
                    target != null && target.equals(center) && context.world() == world ? geometry : BrushPreviewGeometry.EMPTY);
        });
        WorldRenderEvents.END_MAIN.register(context -> {
            BrushPreviewGeometry frame = ((FabricRenderState) context.worldState()).getDataOrDefault(FRAME_GEOMETRY, BrushPreviewGeometry.EMPTY);
            if (frame.quads().isEmpty()) return;
            Vec3 camera = context.worldState().cameraRenderState.pos;
            var matrices = context.matrices();
            matrices.pushPose();
            matrices.translate(frame.origin().getX() - camera.x, frame.origin().getY() - camera.y, frame.origin().getZ() - camera.z);
            var pose = matrices.last();
            // 1.21.11 DEBUG_QUADS: POSITION_COLOR, translucent blend, LEQUAL depth test, no depth writes/culling.
            var fill = context.consumers().getBuffer(RenderTypes.debugQuads());
            for (var quad : frame.quads()) {
                fill.addVertex(pose, quad.a().x(), quad.a().y(), quad.a().z()).setColor(FILL_RED, FILL_GREEN, FILL_BLUE, FILL_ALPHA);
                fill.addVertex(pose, quad.b().x(), quad.b().y(), quad.b().z()).setColor(FILL_RED, FILL_GREEN, FILL_BLUE, FILL_ALPHA);
                fill.addVertex(pose, quad.c().x(), quad.c().y(), quad.c().z()).setColor(FILL_RED, FILL_GREEN, FILL_BLUE, FILL_ALPHA);
                fill.addVertex(pose, quad.d().x(), quad.d().y(), quad.d().z()).setColor(FILL_RED, FILL_GREEN, FILL_BLUE, FILL_ALPHA);
            }
            var consumer = context.consumers().getBuffer(RenderTypes.linesTranslucent());
            for (var line : frame.lines()) {
                consumer.addVertex(pose, line.a().x(), line.a().y(), line.a().z()).setColor(OUTLINE_COLOR)
                        .setNormal(pose, line.nx(), line.ny(), line.nz()).setLineWidth(1.5f);
                consumer.addVertex(pose, line.b().x(), line.b().y(), line.b().z()).setColor(OUTLINE_COLOR)
                        .setNormal(pose, line.nx(), line.ny(), line.nz()).setLineWidth(1.5f);
            }
            matrices.popPose();
        });
    }
    public static void tick(Minecraft client) {
        ticks++;
        BlockPos target = BrushSelection.isBrush(client.player) ? RetileClient.target(client) : null;
        if (target == null || client.level == null) {
            center = null; geometry = BrushPreviewGeometry.EMPTY; world = null; return;
        }
        BrushSettings settings = RetileClient.settings;
        long seed = RetileClient.seedForNextApply();
        boolean changed = !target.equals(center) || world != client.level || !same(settings, cachedSettings) || seed != cachedSeed;
        if (changed) {
            center = target; world = client.level; cachedSettings = settings.copy(); cachedSeed = seed;
            shape = BrushShape.collectPositions(center, settings.radius, settings.shape, settings.limitHeight, settings.minY, settings.maxY);
        }
        if (!changed && ticks % BrushConstants.CACHE_RECHECK_TICKS != 0) return;
        long hash = hash(client);
        if (!changed && hash == stateHash) return;
        stateHash = hash;
        List<BlockPos> selected = BrushSelection.collect(client.level, shape, settings, seed);
        if (selected.size() > BrushConstants.PREVIEW_MAX) {
            selected.sort(Comparator.comparingLong(pos -> distance(pos, center)));
            selected = selected.subList(0, BrushConstants.PREVIEW_MAX);
        }
        geometry = BrushPreviewGeometry.build(selected, center);
    }
    private static long distance(BlockPos a, BlockPos b) {
        long x = a.getX() - b.getX(), y = a.getY() - b.getY(), z = a.getZ() - b.getZ(); return x*x + y*y + z*z;
    }
    private static long hash(Minecraft client) {
        long h = 0;
        for (BlockPos pos : shape) {
            int id = client.level.isOutsideBuildHeight(pos) || !BrushSelection.isChunkLoaded(client.level, pos)
                    ? -1 : net.minecraft.world.level.block.Block.getId(client.level.getBlockState(pos));
            h = BrushHash.splitMix64(h ^ BrushHash.hash2(0, pos.asLong(), id, BrushConstants.SALT_CHOICE));
        }
        return h;
    }
    private static boolean same(BrushSettings a, BrushSettings b) {
        return b != null && a.radius == b.radius && a.shape == b.shape && a.filters.equals(b.filters)
                && a.replacements.equals(b.replacements) && a.includeAir == b.includeAir && a.chaos == b.chaos
                && a.limitHeight == b.limitHeight && a.minY == b.minY && a.maxY == b.maxY && a.fixedSeed == b.fixedSeed && a.seed == b.seed;
    }
}
