package dev.hintsystem.playerrelay.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.Level;

import io.netty.buffer.ByteBuf;

public class PayloadUtils {
    public static RegistryAccess getRegistryManager() {
        Level world = Minecraft.getInstance().level;
        return (world != null) ? world.registryAccess() : RegistryAccess.EMPTY;
    }

    public static byte[] bytesFromByteBuf(ByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(0, bytes);
        buf.release();

        return bytes;
    }
}
