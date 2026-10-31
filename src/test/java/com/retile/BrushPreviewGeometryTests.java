package com.retile;

import com.retile.client.BrushPreviewGeometry;
import com.retile.client.BrushPreviewGeometry.Vertex;
import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Surface topology checks without starting a world or the renderer. */
public final class BrushPreviewGeometryTests {
    private static int passed;
    public static void main(String[] args) {
        check(BrushPreviewGeometry.build(List.of(), BlockPos.ZERO) == BrushPreviewGeometry.EMPTY, "Empty selection");
        topology(box(1, 1, 1), 6, 12, "single block");
        topology(box(2, 1, 1), 10, 16, "adjacent pair");
        topology(box(2, 2, 1), 16, 20, "flat rectangle");
        topology(box(2, 2, 2), 24, 24, "solid cube has no face grid");
        topology(List.of(BlockPos.ZERO, new BlockPos(1, 0, 0), new BlockPos(0, 0, 1)), 14, 22, "concave L");
        topology(List.of(BlockPos.ZERO, new BlockPos(3, 0, 0)), 12, 24, "disconnected components");
        List<BlockPos> hollow = box(3, 3, 3);
        hollow.remove(new BlockPos(1, 1, 1));
        topology(hollow, 60, 48, "enclosed cavity");
        topology(List.of(BlockPos.ZERO, BlockPos.ZERO), 6, 12, "duplicate positions");

        BrushPreviewGeometry flat = BrushPreviewGeometry.build(box(2, 1, 2), BlockPos.ZERO);
        for (var line : flat.lines()) {
            boolean top = line.a().y() == 1 && line.b().y() == 1;
            if (top) check(line.a().x() == 0 && line.b().x() == 0 || line.a().x() == 2 && line.b().x() == 2
                    || line.a().z() == 0 && line.b().z() == 0 || line.a().z() == 2 && line.b().z() == 2,
                    "Top plane must have perimeter lines only");
        }
        passed++;

        BrushPreviewGeometry unit = BrushPreviewGeometry.build(List.of(BlockPos.ZERO), BlockPos.ZERO);
        for (var quad : unit.quads()) {
            float[][] axes = {{quad.a().x(), quad.b().x(), quad.c().x(), quad.d().x()},
                    {quad.a().y(), quad.b().y(), quad.c().y(), quad.d().y()},
                    {quad.a().z(), quad.b().z(), quad.c().z(), quad.d().z()}};
            int offsetAxes = 0;
            for (float[] axis : axes) if (axis[0] == axis[1] && axis[1] == axis[2] && axis[2] == axis[3]) {
                check(Math.abs(axis[0] + .002f) < .00001f || Math.abs(axis[0] - 1.002f) < .00001f,
                        "Face must be moved .002 outwards");
                offsetAxes++;
            }
            check(offsetAxes == 1, "One offset axis per face");
        }
        BlockPos distant = new BlockPos(20_000_000, -50, -20_000_000);
        check(unit.quads().equals(BrushPreviewGeometry.build(List.of(distant), distant).quads()),
                "Local geometry must retain precision at distant world coordinates");
        passed++;
        try { unit.quads().clear(); throw new AssertionError("Mutable quads"); }
        catch (UnsupportedOperationException expected) { }
        try { unit.lines().clear(); throw new AssertionError("Mutable lines"); }
        catch (UnsupportedOperationException expected) { }
        passed++;
        System.out.println(passed + " preview geometry tests passed");
    }
    private static List<BlockPos> box(int xSize, int ySize, int zSize) {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = 0; x < xSize; x++) for (int y = 0; y < ySize; y++) for (int z = 0; z < zSize; z++)
            positions.add(new BlockPos(x, y, z));
        return positions;
    }
    private static void topology(List<BlockPos> positions, int faces, int edges, String description) {
        BrushPreviewGeometry geometry = BrushPreviewGeometry.build(positions, BlockPos.ZERO);
        check(geometry.quads().size() == faces, description + ": faces " + geometry.quads().size());
        check(geometry.lines().size() == edges, description + ": edges " + geometry.lines().size());
        Set<Set<Vertex>> unique = new HashSet<>();
        for (var line : geometry.lines()) {
            check(unique.add(Set.of(line.a(), line.b())), description + ": duplicated edge");
            check(line.nx() * line.nx() + line.ny() * line.ny() + line.nz() * line.nz() == 1,
                    description + ": normalized line direction");
        }
        passed++;
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
