package com.retile;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import com.retile.brush.BrushConstants;
import com.retile.brush.BrushSettings;
import com.retile.brush.BrushShapeType;
import com.retile.network.ApplyBrushPayload;
import com.retile.network.StrokeEndPayload;
import com.retile.server.BrushServer;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

import java.util.List;

/** Checks the stroke wire format and per-packet validation without a client or server world. */
public final class BrushNetworkTests {
    private static int passed;

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        test("all stroke phases round trip with complete settings and extreme seeds", () -> {
            for (ApplyBrushPayload.Phase phase : ApplyBrushPayload.Phase.values()) {
                for (long seed : new long[]{0, Long.MIN_VALUE, Long.MAX_VALUE}) {
                    BrushSettings settings = settings();
                    settings.radius = BrushConstants.MAX_RADIUS;
                    settings.shape = BrushShapeType.CYLINDER;
                    settings.includeAir = true;
                    settings.filters.add(id("air"));
                    settings.filters.add(id("stone"));
                    settings.chaos = -0.75f;
                    settings.limitHeight = true;
                    settings.minY = 120;
                    settings.maxY = -32;
                    ApplyBrushPayload original = ApplyBrushPayload.from(new BlockPos(-71, 62, 19), settings, seed, phase);
                    RegistryFriendlyByteBuf buffer = buffer();
                    try {
                        ApplyBrushPayload.CODEC.encode(buffer, original);
                        ApplyBrushPayload decoded = ApplyBrushPayload.CODEC.decode(buffer);
                        check(original.equals(decoded), "Wire round trip lost settings or phase " + phase);
                        check(!buffer.isReadable(), "Decoder must consume the entire application");
                        check(decoded.minY() == -32 && decoded.maxY() == 120, "Height bounds must remain normalized");
                        check(decoded.settings().seed == seed && BrushServer.valid(decoded), "Decoded packet must remain valid");
                    } finally { buffer.release(); }
                }
            }
        });

        test("single defaults and payload lists remain independent of edited settings", () -> {
            BrushSettings settings = settings();
            settings.filters.add(id("stone"));
            ApplyBrushPayload single = ApplyBrushPayload.from(BlockPos.ZERO, settings, 9);
            check(single.phase() == ApplyBrushPayload.Phase.SINGLE, "Existing factory must default to SINGLE");
            ApplyBrushPayload compatible = new ApplyBrushPayload(single.center(), single.radius(), single.shape(),
                    single.filters(), single.includeAir(), single.replacements(), single.chaos(), single.limitHeight(),
                    single.minY(), single.maxY(), single.seed());
            check(single.equals(compatible), "Existing constructor must default to SINGLE");
            settings.filters.clear();
            settings.replacements.clear();
            check(single.filters().equals(List.of(id("stone"))) && single.replacements().size() == 2,
                    "Editing client settings must not change an already queued packet");
        });

        test("empty stroke end codec does not consume following application bytes", () -> {
            RegistryFriendlyByteBuf buffer = buffer();
            try {
                StrokeEndPayload.CODEC.encode(buffer, new StrokeEndPayload());
                check(buffer.writerIndex() == 0, "StrokeEnd must have no body");
                ApplyBrushPayload apply = ApplyBrushPayload.from(BlockPos.ZERO, settings(), 37, ApplyBrushPayload.Phase.START);
                ApplyBrushPayload.CODEC.encode(buffer, apply);
                check(StrokeEndPayload.CODEC.decode(buffer).type().equals(StrokeEndPayload.TYPE), "End payload type");
                check(buffer.readerIndex() == 0 && apply.equals(ApplyBrushPayload.CODEC.decode(buffer)),
                        "Empty end codec must not shift the following application");
            } finally { buffer.release(); }
        });

        test("unknown wire phases are rejected for every application", () -> {
            for (int ordinal : new int[]{3, 127, 255}) {
                RegistryFriendlyByteBuf buffer = buffer();
                try {
                    ApplyBrushPayload.CODEC.encode(buffer,
                            ApplyBrushPayload.from(BlockPos.ZERO, settings(), 7, ApplyBrushPayload.Phase.CONTINUE));
                    buffer.setByte(buffer.writerIndex() - 1, ordinal);
                    ApplyBrushPayload decoded = ApplyBrushPayload.CODEC.decode(buffer);
                    check(decoded.phase() == null && !BrushServer.valid(decoded), "Invalid phase must never apply");
                } finally { buffer.release(); }
            }
        });

        test("all phases preserve radius chaos weight and block safety validation", () -> {
            for (ApplyBrushPayload.Phase phase : ApplyBrushPayload.Phase.values()) {
                BrushSettings settings = settings();
                check(valid(settings, phase), "Fixture must be valid for " + phase);
                settings.radius = 0;
                check(!valid(settings, phase), "Zero radius must be rejected for " + phase);
                settings.radius = BrushConstants.MAX_RADIUS + 1;
                check(!valid(settings, phase), "Large radius must be rejected for " + phase);
                settings.radius = 3;
                for (float chaos : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1.01f, 1.01f}) {
                    settings.chaos = chaos;
                    check(!valid(settings, phase), "Invalid chaos must be rejected for " + phase);
                }
                settings.chaos = 0;
                for (int weight : new int[]{0, BrushConstants.MAX_WEIGHT + 1}) {
                    settings.replacements = List.of(new BrushSettings.WeightedEntry(id("stone"), weight));
                    check(!valid(settings, phase), "Invalid weight must be rejected for " + phase);
                }
                for (String forbidden : new String[]{"air", "water", "chest", "does_not_exist"}) {
                    settings.replacements = List.of(new BrushSettings.WeightedEntry(id(forbidden), 1));
                    check(!valid(settings, phase), "Forbidden replacement must be rejected for " + phase);
                }
                settings.replacements = settings().replacements;
                for (String forbidden : new String[]{"water", "chest", "does_not_exist"}) {
                    settings.filters = List.of(id(forbidden));
                    check(!valid(settings, phase), "Forbidden filter must be rejected for " + phase);
                }
                settings.filters = java.util.Collections.nCopies(BrushConstants.MAX_FILTER + 1, id("stone"));
                check(!valid(settings, phase), "Too many filters must be rejected for " + phase);
                settings.filters = List.of();
                settings.replacements = java.util.Collections.nCopies(BrushConstants.MAX_REPLACEMENTS + 1,
                        new BrushSettings.WeightedEntry(id("stone"), 1));
                check(!valid(settings, phase), "Too many replacements must be rejected for " + phase);
                settings.replacements = List.of();
                check(!valid(settings, phase), "Empty replacements must be rejected for " + phase);
            }
        });

        test("codec bounds list lengths before allocating entries", () -> {
            for (boolean replacements : new boolean[]{false, true}) {
                int limit = replacements ? BrushConstants.MAX_REPLACEMENTS : BrushConstants.MAX_FILTER;
                for (int count : new int[]{-1, limit + 1, Integer.MAX_VALUE}) {
                    RegistryFriendlyByteBuf buffer = buffer();
                    try {
                        buffer.writeBlockPos(BlockPos.ZERO);
                        buffer.writeVarInt(1);
                        buffer.writeVarInt(BrushShapeType.SPHERE.ordinal());
                        if (replacements) { buffer.writeVarInt(0); buffer.writeBoolean(false); }
                        buffer.writeVarInt(count);
                        try {
                            ApplyBrushPayload.CODEC.decode(buffer);
                            throw new AssertionError("Oversized list was accepted");
                        } catch (DecoderException expected) {
                            check(expected.getMessage().contains("Brush list exceeds limit"), "Expected bounded-list rejection");
                        }
                    } finally { buffer.release(); }
                }
            }
        });

        System.out.println(passed + " tests passed");
    }

    private static boolean valid(BrushSettings settings, ApplyBrushPayload.Phase phase) {
        return BrushServer.valid(ApplyBrushPayload.from(BlockPos.ZERO, settings, 0, phase));
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    private static BrushSettings settings() {
        BrushSettings settings = new BrushSettings();
        settings.replacements.add(new BrushSettings.WeightedEntry(id("quartz_block"), 1));
        settings.replacements.add(new BrushSettings.WeightedEntry(id("white_wool"), BrushConstants.MAX_WEIGHT));
        return settings;
    }

    private static Identifier id(String path) { return Identifier.withDefaultNamespace(path); }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void test(String name, Runnable body) {
        body.run();
        passed++;
        System.out.println("PASS " + name);
    }
}
