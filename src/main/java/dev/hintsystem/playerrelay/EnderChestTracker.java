package dev.hintsystem.playerrelay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class EnderChestTracker {
    private static final String ENDER_CHEST_NAME_KEY = "container.enderchest";

    private static final Map<String, List<ItemStack>> enderChestCache = new HashMap<>();
    private static String currentWorldId = null;

    /**
     * Updates the ender chest inventory cache when the player has an ender chest screen open.
     * <p>
     * This method should be called every client tick. It detects when a {@link ContainerScreen}
     * is open with the ender chest title, then copies all slots into a cache mapped by world ID.
     * The cached inventory persists after the screen is closed and can be retrieved later.
     *
     * @see #getEnderChestInventory()
     * @see #hasEnderChestInventory()
     * @see net.minecraft.world.level.block.EnderChestBlock#CONTAINER_TITLE
     */
    public static void tick() {
        Minecraft client = Minecraft.getInstance();
        updateCurrentWorldId(client);
        updateViaScreenReading(client);
    }

    private static void updateViaScreenReading(Minecraft client) {
        if (currentWorldId == null) return;
        if (!(client.screen instanceof ContainerScreen containerScreen)) return;

        ComponentContents titleContent = containerScreen.getTitle().getContents();
        if (!(titleContent instanceof TranslatableContents translatableText)) return;

        if (!translatableText.getKey().equals(ENDER_CHEST_NAME_KEY)) return;

        int slots = containerScreen.getMenu().getRowCount() * 9;

        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < slots; i++) {
            ItemStack stack = containerScreen.getMenu().slots.get(i).getItem();
            items.add(stack.copy());
        }

        enderChestCache.put(currentWorldId, items);
    }

    public static void update(List<ItemStack> items) {
        if (currentWorldId == null) return;

        List<ItemStack> copy = items.stream().map(ItemStack::copy).toList();
        enderChestCache.put(currentWorldId, copy);
    }

    public static void updateCurrentWorldId(Minecraft client) {
        if (client.player == null || client.level == null) {
            currentWorldId = null;
            return;
        }

        StringBuilder worldId = new StringBuilder();

        if (client.getCurrentServer() != null) {
            worldId.append("server:");
            worldId.append(client.getCurrentServer().ip);
            worldId.append(":");
        } else {
            worldId.append("world:");
            if (client.getSingleplayerServer() != null) {
                worldId.append(client.getSingleplayerServer().getWorldData().getLevelName());
                worldId.append(":");
            }
        }

        worldId.append(client.player.getStringUUID());
        currentWorldId = worldId.toString();
    }

    public static boolean hasEnderChestInventory() {
        return currentWorldId != null && enderChestCache.containsKey(currentWorldId);
    }

    public static List<ItemStack> getEnderChestInventory() {
        if (currentWorldId == null) return Collections.emptyList();

        List<ItemStack> cached = enderChestCache.get(currentWorldId);
        if (cached == null) return Collections.emptyList();

        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack stack : cached) copy.add(stack.copy());

        return copy;
    }
}
