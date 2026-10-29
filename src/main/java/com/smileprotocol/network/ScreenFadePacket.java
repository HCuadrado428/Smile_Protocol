package com.smileprotocol.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S -> C: fundido a negro (entrada, negro mantenido y salida, en ticks). README 6.2. */
public record ScreenFadePacket(int fadeIn, int hold, int fadeOut) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(fadeIn);
        buf.writeVarInt(hold);
        buf.writeVarInt(fadeOut);
    }

    public static ScreenFadePacket decode(FriendlyByteBuf buf) {
        return new ScreenFadePacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.onFade(this));
    }
}
