package com.retile.brush;

import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.List;

public final class BrushShape {
    private BrushShape() {}

    public static List<BlockPos> collectPositions(BlockPos center, int radius, BrushShapeType shape,
                                                  boolean limitHeight, int minY, int maxY) {
        int r = Math.clamp(radius, 1, BrushConstants.MAX_RADIUS);
        int effMinY = Math.min(minY, maxY);
        int effMaxY = Math.max(minY, maxY);
        double rSq = (double) r * r;
        double cx = center.getX() + 0.5;
        double cy = center.getY() + 0.5;
        double cz = center.getZ() + 0.5;

        List<BlockPos> out = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    int x = center.getX() + dx;
                    int y = center.getY() + dy;
                    int z = center.getZ() + dz;
                    if (limitHeight && (y < effMinY || y > effMaxY)) continue;

                    if (shape == BrushShapeType.SPHERE) {
                        double bx = x + 0.5;
                        double by = y + 0.5;
                        double bz = z + 0.5;
                        double ddx = bx - cx;
                        double ddy = by - cy;
                        double ddz = bz - cz;
                        if (ddx * ddx + ddy * ddy + ddz * ddz > rSq + 1e-9) continue;
                    } else {
                        double bx = x + 0.5;
                        double bz = z + 0.5;
                        double ddx = bx - cx;
                        double ddz = bz - cz;
                        if (ddx * ddx + ddz * ddz > rSq + 1e-9) continue;
                        if (Math.abs(dy) > r) continue;
                    }
                    out.add(new BlockPos(x, y, z));
                }
            }
        }
        return out;
    }
}
