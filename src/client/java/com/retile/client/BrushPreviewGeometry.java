package com.retile.client;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Immutable, camera-independent surface data rebuilt only when the selection cache changes. */
public record BrushPreviewGeometry(BlockPos origin, List<Quad> quads, List<Line> lines) {
    public static final float SURFACE_OFFSET = .002f;
    public static final BrushPreviewGeometry EMPTY = new BrushPreviewGeometry(BlockPos.ZERO, List.of(), List.of());
    public record Vertex(float x, float y, float z) {}
    public record Quad(Vertex a, Vertex b, Vertex c, Vertex d) {}
    public record Line(Vertex a, Vertex b, float nx, float ny, float nz) {}
    private record Edge(BlockPos a, BlockPos b) {
        static Edge of(BlockPos a, BlockPos b) {
            return a.asLong() < b.asLong() ? new Edge(a, b) : new Edge(b, a);
        }
    }
    public BrushPreviewGeometry {
        origin = origin.immutable();
        quads = List.copyOf(quads);
        lines = List.copyOf(lines);
    }

    public static BrushPreviewGeometry build(List<BlockPos> selected, BlockPos origin) {
        if (selected.isEmpty()) return EMPTY;
        LongOpenHashSet positions = new LongOpenHashSet(selected.size());
        for (BlockPos pos : selected) positions.add(pos.asLong());
        List<Quad> quads = new ArrayList<>();
        Set<Edge> edges = new LinkedHashSet<>();
        for (long packed : positions) {
            BlockPos pos = BlockPos.of(packed);
            for (Direction normal : Direction.values()) {
                if (!external(positions, pos, normal)) continue;
                Direction u = normal.getAxis() == Direction.Axis.X ? Direction.UP : Direction.EAST;
                Direction v = normal.getAxis() == Direction.Axis.Z ? Direction.UP : Direction.SOUTH;
                BlockPos a = normal.getAxisDirection() == Direction.AxisDirection.POSITIVE ? pos.relative(normal) : pos;
                BlockPos b = a.relative(u), c = b.relative(v), d = a.relative(v);
                quads.add(new Quad(vertex(a, origin, normal), vertex(b, origin, normal),
                        vertex(c, origin, normal), vertex(d, origin, normal)));
                // Only boundaries of coplanar regions survive; neighbouring surface cells have no seam.
                if (!external(positions, pos.relative(v.getOpposite()), normal)) edges.add(Edge.of(a, b));
                if (!external(positions, pos.relative(u), normal)) edges.add(Edge.of(b, c));
                if (!external(positions, pos.relative(v), normal)) edges.add(Edge.of(c, d));
                if (!external(positions, pos.relative(u.getOpposite()), normal)) edges.add(Edge.of(d, a));
            }
        }
        List<Line> lines = new ArrayList<>(edges.size());
        for (Edge edge : edges) {
            Vertex a = vertex(edge.a, origin, null), b = vertex(edge.b, origin, null);
            lines.add(new Line(a, b, b.x - a.x, b.y - a.y, b.z - a.z));
        }
        return new BrushPreviewGeometry(origin, quads, lines);
    }

    private static boolean external(LongOpenHashSet positions, BlockPos pos, Direction normal) {
        return positions.contains(pos.asLong()) && !positions.contains(pos.relative(normal).asLong());
    }
    private static Vertex vertex(BlockPos pos, BlockPos origin, Direction normal) {
        return new Vertex(pos.getX() - origin.getX() + (normal == null ? 0 : normal.getStepX() * SURFACE_OFFSET),
                pos.getY() - origin.getY() + (normal == null ? 0 : normal.getStepY() * SURFACE_OFFSET),
                pos.getZ() - origin.getZ() + (normal == null ? 0 : normal.getStepZ() * SURFACE_OFFSET));
    }
}
