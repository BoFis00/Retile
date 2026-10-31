package com.retile.network;

import com.retile.Retile;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record StrokeEndPayload() implements CustomPacketPayload {
    public static final Type<StrokeEndPayload> TYPE = new Type<>(Retile.id("stroke_end"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StrokeEndPayload> CODEC =
            StreamCodec.unit(new StrokeEndPayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
