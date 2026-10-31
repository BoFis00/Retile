package com.retile.network;

import com.retile.Retile;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RedoPayload() implements CustomPacketPayload {
    public static final Type<RedoPayload> TYPE = new Type<>(Retile.id("redo"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, RedoPayload> CODEC =
            StreamCodec.unit(new RedoPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
