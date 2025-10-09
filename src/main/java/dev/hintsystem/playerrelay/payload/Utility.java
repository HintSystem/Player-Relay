package dev.hintsystem.playerrelay.payload;

import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.world.World;

public class Utility {
    public static DynamicRegistryManager getRegistryManager() {
        World world = MinecraftClient.getInstance().world;
        return (world != null) ? world.getRegistryManager() : DynamicRegistryManager.EMPTY;
    }
}
