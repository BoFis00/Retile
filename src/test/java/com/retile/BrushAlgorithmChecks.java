package com.retile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure Java contract for isolated brush tests; the test runner adapts Minecraft value types. */
public final class BrushAlgorithmChecks {
    public record Position(int x, int y, int z) {}

    public interface Probe {
        /** Replacement IDs for weights 1:3 are minecraft:quartz_block and minecraft:white_wool. */
        String pick(int x, int y, int z, float chaos, long seed);
        List<Position> shape(int x, int y, int z, int radius, boolean cylinder,
                             boolean limitHeight, int minY, int maxY);
    }

    private BrushAlgorithmChecks() {}

    public static int run(Probe probe) {
        check(probe != null, "Probe must not be null");
        int passed = 0;

        checkIntermediateChaos(probe, 0.5f);
        System.out.println("PASS positive intermediate chaos mixes layer and independent choices");
        passed++;

        checkIntermediateChaos(probe, -0.5f);
        System.out.println("PASS negative intermediate chaos mixes column and independent choices");
        passed++;

        checkExtremeSeeds(probe);
        System.out.println("PASS extreme seeds preserve determinism and affect mixed world coordinates");
        passed++;

        checkNegativeSphere(probe);
        System.out.println("PASS radius-one sphere has exact positions at a negative world center");
        passed++;

        checkShapeTranslationAndBoundaries(probe);
        System.out.println("PASS shapes preserve translation and include exact boundaries through radius 32");
        passed++;

        checkHeightRanges(probe);
        System.out.println("PASS height limits are inclusive and discard ranges outside the brush");
        passed++;

        return passed;
    }

    private static void checkIntermediateChaos(Probe probe, float chaos) {
        int matches = 0;
        int samples = 0;
        float groupedChaos = chaos > 0 ? 1 : -1;
        long seed = -832746192057364891L;
        for (int x = -30; x < 30; x++) {
            for (int y = -30; y < 30; y++) {
                for (int z = -30; z < 30; z++) {
                    int worldY = y + 61 * x;
                    String mixed = pick(probe, x, worldY, z, chaos, seed);
                    String grouped = pick(probe, x, worldY, z, groupedChaos, seed);
                    if (mixed.equals(grouped)) matches++;
                    samples++;
                }
            }
        }
        double actual = (double) matches / samples;
        double expected = 0.5 + 0.5 * (0.25 * 0.25 + 0.75 * 0.75);
        check(Math.abs(actual - expected) <= 0.03,
                "Chaos " + chaos + " group agreement must be " + expected + " +/- 0.03; got "
                        + actual + " from " + samples + " positions");
    }

    private static void checkExtremeSeeds(Probe probe) {
        int count = 4096;
        float[] chaosValues = {-1, -0.5f, 0, 0.5f, 1};
        String[][] minimumSeed = new String[chaosValues.length][count];
        String[][] maximumSeed = new String[chaosValues.length][count];
        for (int c = 0; c < chaosValues.length; c++) {
            float chaos = chaosValues[c];
            int differences = 0;
            for (int i = 0; i < count; i++) {
                Position position = samplePosition(i);
                minimumSeed[c][i] = pick(probe, position.x(), position.y(), position.z(), chaos, Long.MIN_VALUE);
                maximumSeed[c][i] = pick(probe, position.x(), position.y(), position.z(), chaos, Long.MAX_VALUE);
                if (!minimumSeed[c][i].equals(maximumSeed[c][i])) differences++;
            }
            check(differences > count / 10,
                    "Long.MIN_VALUE and Long.MAX_VALUE must affect picks at chaos " + chaos
                            + "; only " + differences + " of " + count + " positions differed");
        }
        for (int c = chaosValues.length - 1; c >= 0; c--) {
            float chaos = chaosValues[c];
            for (int i = count - 1; i >= 0; i--) {
                Position position = samplePosition(i);
                String maximum = pick(probe, position.x(), position.y(), position.z(), chaos, Long.MAX_VALUE);
                String minimum = pick(probe, position.x(), position.y(), position.z(), chaos, Long.MIN_VALUE);
                check(maximumSeed[c][i].equals(maximum) && minimumSeed[c][i].equals(minimum),
                        "Pick changed with call order at " + position + ", chaos " + chaos + ", extreme seeds");
            }
        }
    }

    private static Position samplePosition(int index) {
        return new Position(index * 37 - 75000, index % 257 - 128, 60000 - index * 29);
    }

    private static void checkNegativeSphere(Probe probe) {
        Position center = new Position(-17, -9, -31);
        Set<Position> expected = Set.of(center,
                new Position(-18, -9, -31), new Position(-16, -9, -31),
                new Position(-17, -10, -31), new Position(-17, -8, -31),
                new Position(-17, -9, -32), new Position(-17, -9, -30));
        Set<Position> actual = shape(probe, center, 1, false, false, 0, 0);
        checkSamePositions(expected, actual, "Radius-one sphere at " + center);
    }

    private static void checkShapeTranslationAndBoundaries(Probe probe) {
        Position origin = new Position(0, 0, 0);
        Position translatedCenter = new Position(-91, 47, -63);
        for (int radius : new int[]{2, 5, 32}) {
            for (boolean cylinder : new boolean[]{false, true}) {
                String context = (cylinder ? "Cylinder" : "Sphere") + " radius " + radius;
                Set<Position> original = shape(probe, origin, radius, cylinder, false, 0, 0);
                check(original.contains(origin), context + " must contain its center");
                for (Position boundary : List.of(
                        new Position(radius, 0, 0), new Position(-radius, 0, 0),
                        new Position(0, radius, 0), new Position(0, -radius, 0),
                        new Position(0, 0, radius), new Position(0, 0, -radius))) {
                    check(original.contains(boundary), context + " must include exact axial boundary " + boundary);
                }
                check(!original.contains(new Position(radius, 0, 1)),
                        context + " must exclude the horizontal diagonal beyond its radius");
                check(!original.contains(new Position(0, radius + 1, 0)),
                        context + " must exclude Y beyond its radius");
                check(original.contains(new Position(radius, radius, 0)) == cylinder,
                        context + " has incorrect membership at the radial and height corner");

                Set<Position> expected = new HashSet<>();
                for (Position position : original) {
                    expected.add(new Position(position.x() + translatedCenter.x(),
                            position.y() + translatedCenter.y(), position.z() + translatedCenter.z()));
                }
                Set<Position> translated = shape(probe, translatedCenter, radius, cylinder, false, 0, 0);
                checkSamePositions(expected, translated, context + " translated to " + translatedCenter);
            }
        }
    }

    private static void checkHeightRanges(Probe probe) {
        Position center = new Position(-23, -12, 41);
        int radius = 4;
        Set<Position> expectedCenterSlice = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    expectedCenterSlice.add(new Position(center.x() + dx, center.y(), center.z() + dz));
                }
            }
        }
        for (boolean cylinder : new boolean[]{false, true}) {
            String context = cylinder ? "Cylinder" : "Sphere";
            check(shape(probe, center, radius, cylinder, true, center.y() + radius + 1,
                            center.y() + radius + 4).isEmpty(),
                    context + " must be empty when the height range is entirely above it");
            check(shape(probe, center, radius, cylinder, true, center.y() - radius - 4,
                            center.y() - radius - 1).isEmpty(),
                    context + " must be empty when the height range is entirely below it");
            checkSamePositions(expectedCenterSlice,
                    shape(probe, center, radius, cylinder, true, center.y(), center.y()),
                    context + " inclusive single-Y center slice");

            Set<Position> bottomSlice = new HashSet<>();
            Set<Position> topSlice = new HashSet<>();
            if (cylinder) {
                for (Position position : expectedCenterSlice) {
                    bottomSlice.add(new Position(position.x(), center.y() - radius, position.z()));
                    topSlice.add(new Position(position.x(), center.y() + radius, position.z()));
                }
            } else {
                bottomSlice.add(new Position(center.x(), center.y() - radius, center.z()));
                topSlice.add(new Position(center.x(), center.y() + radius, center.z()));
            }
            checkSamePositions(bottomSlice,
                    shape(probe, center, radius, cylinder, true, center.y() - radius, center.y() - radius),
                    context + " inclusive single-Y bottom slice");
            checkSamePositions(topSlice,
                    shape(probe, center, radius, cylinder, true, center.y() + radius, center.y() + radius),
                    context + " inclusive single-Y top slice");
        }
    }

    private static String pick(Probe probe, int x, int y, int z, float chaos, long seed) {
        String value = probe.pick(x, y, z, chaos, seed);
        check("minecraft:quartz_block".equals(value) || "minecraft:white_wool".equals(value),
                "Unexpected replacement " + value + " at (" + x + ", " + y + ", " + z
                        + "), chaos " + chaos + ", seed " + seed);
        return value;
    }

    private static Set<Position> shape(Probe probe, Position center, int radius, boolean cylinder,
                                       boolean limitHeight, int minY, int maxY) {
        List<Position> positions = probe.shape(center.x(), center.y(), center.z(), radius, cylinder,
                limitHeight, minY, maxY);
        String context = (cylinder ? "Cylinder" : "Sphere") + " radius " + radius + " at " + center
                + ", limitHeight " + limitHeight + ", height range [" + minY + ", " + maxY + "]";
        check(positions != null, context + " returned a null list");
        Set<Position> unique = new HashSet<>(positions);
        check(!unique.contains(null), context + " contains a null position");
        check(unique.size() == positions.size(), context + " contains duplicate positions");
        return unique;
    }

    private static void checkSamePositions(Set<Position> expected, Set<Position> actual, String context) {
        if (expected.equals(actual)) return;
        Position missing = null;
        Position unexpected = null;
        for (Position position : expected) {
            if (!actual.contains(position)) { missing = position; break; }
        }
        for (Position position : actual) {
            if (!expected.contains(position)) { unexpected = position; break; }
        }
        throw new AssertionError(context + ": expected " + expected.size() + " positions, got " + actual.size()
                + "; first missing " + missing + "; first unexpected " + unexpected);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
