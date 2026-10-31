package com.retile.network;
import com.retile.Retile;
import com.retile.brush.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.*;

public record ApplyBrushPayload(BlockPos center, int radius, BrushShapeType shape,
        List<Identifier> filters, boolean includeAir, List<BrushSettings.WeightedEntry> replacements,
        float chaos, boolean limitHeight, int minY, int maxY, long seed, Phase phase) implements CustomPacketPayload {
    public enum Phase { SINGLE, START, CONTINUE }

    public ApplyBrushPayload(BlockPos center, int radius, BrushShapeType shape,
            List<Identifier> filters, boolean includeAir, List<BrushSettings.WeightedEntry> replacements,
            float chaos, boolean limitHeight, int minY, int maxY, long seed) {
        this(center, radius, shape, filters, includeAir, replacements, chaos, limitHeight, minY, maxY, seed, Phase.SINGLE);
    }

    public static final Type<ApplyBrushPayload> TYPE = new Type<>(Retile.id("apply_brush"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ApplyBrushPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeBlockPos(p.center); buf.writeVarInt(p.radius);
            buf.writeVarInt(p.shape.ordinal()); buf.writeVarInt(p.filters.size());
            p.filters.forEach(buf::writeIdentifier); buf.writeBoolean(p.includeAir);
            buf.writeVarInt(p.replacements.size());
            p.replacements.forEach(e -> { buf.writeIdentifier(e.id()); buf.writeVarInt(e.weight()); });
            buf.writeFloat(p.chaos); buf.writeBoolean(p.limitHeight);
            buf.writeInt(p.minY); buf.writeInt(p.maxY); buf.writeLong(p.seed);
            buf.writeByte(p.phase.ordinal());
        }, buf -> {
            BlockPos center = buf.readBlockPos(); int radius = buf.readVarInt();
            int ordinal = buf.readVarInt();
            BrushShapeType shape = ordinal >= 0 && ordinal < BrushShapeType.values().length ? BrushShapeType.values()[ordinal] : null;
            int count = boundedCount(buf, BrushConstants.MAX_FILTER);
            List<Identifier> filters = new ArrayList<>(count);
            for (int i = 0; i < count; i++) filters.add(buf.readIdentifier());
            boolean air = buf.readBoolean(); count = boundedCount(buf, BrushConstants.MAX_REPLACEMENTS);
            List<BrushSettings.WeightedEntry> replacements = new ArrayList<>(count);
            for (int i = 0; i < count; i++) replacements.add(new BrushSettings.WeightedEntry(buf.readIdentifier(), buf.readVarInt()));
            float chaos = buf.readFloat(); boolean height = buf.readBoolean();
            int minY = buf.readInt(), maxY = buf.readInt(); long seed = buf.readLong();
            int phaseOrdinal = buf.readUnsignedByte();
            Phase phase = phaseOrdinal < Phase.values().length ? Phase.values()[phaseOrdinal] : null;
            return new ApplyBrushPayload(center, radius, shape, filters, air, replacements, chaos, height, minY, maxY, seed, phase);
        });
    private static int boundedCount(RegistryFriendlyByteBuf buf, int max) {
        int count = buf.readVarInt();
        if (count < 0 || count > max) throw new io.netty.handler.codec.DecoderException("Brush list exceeds limit");
        return count;
    }
    public static ApplyBrushPayload from(BlockPos center, BrushSettings s, long seed) {
        return from(center, s, seed, Phase.SINGLE);
    }
    public static ApplyBrushPayload from(BlockPos center, BrushSettings s, long seed, Phase phase) {
        return new ApplyBrushPayload(center, s.radius, s.shape, List.copyOf(s.filters), s.includeAir, List.copyOf(s.replacements), s.chaos, s.limitHeight, s.normalizedMinY(), s.normalizedMaxY(), seed, phase);
    }
    public BrushSettings settings() {
        BrushSettings s = new BrushSettings();
        s.radius = radius; s.shape = shape; s.filters = filters; s.includeAir = includeAir;
        s.replacements = replacements; s.chaos = chaos; s.limitHeight = limitHeight;
        s.minY = Math.min(minY, maxY); s.maxY = Math.max(minY, maxY); s.seed = seed; return s;
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
