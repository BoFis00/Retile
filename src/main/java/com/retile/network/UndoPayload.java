package com.retile.network;

import com.retile.Retile;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record UndoPayload() implements CustomPacketPayload {
    public static final Type<UndoPayload> TYPE = new Type<>(Retile.id("undo"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, UndoPayload> CODEC =
            StreamCodec.unit(new UndoPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
