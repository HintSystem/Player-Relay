package dev.hintsystem.playerrelay.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.world.World;

import io.netty.buffer.ByteBuf;

public class PayloadUtils {
    public static DynamicRegistryManager getRegistryManager() {
        World world = MinecraftClient.getInstance().world;
        return (world != null) ? world.getRegistryManager() : DynamicRegistryManager.EMPTY;
    }

    public static byte[] bytesFromByteBuf(ByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(0, bytes);
        buf.release();

        return bytes;
    }
}
