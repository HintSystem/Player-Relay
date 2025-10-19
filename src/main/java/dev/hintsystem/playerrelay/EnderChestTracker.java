package dev.hintsystem.playerrelay;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.TextContent;
import net.minecraft.text.TranslatableTextContent;

import java.util.*;

public class EnderChestTracker {
    private static final String ENDER_CHEST_NAME_KEY = "container.enderchest";

    private static final Map<String, List<ItemStack>> enderChestCache = new HashMap<>();
    private static String currentWorldId = null;

    /**
     * Updates the ender chest inventory cache when the player has an ender chest screen open.
     * <p>
     * This method should be called every client tick. It detects when a {@link GenericContainerScreen}
     * is open with the ender chest title, then copies all slots into a cache mapped by world ID.
     * The cached inventory persists after the screen is closed and can be retrieved later.
     *
     * @see #getEnderChestInventory()
     * @see #hasEnderChestInventory()
     * @see net.minecraft.block.EnderChestBlock#CONTAINER_NAME
     */
    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        updateCurrentWorldId(client);

        if (currentWorldId == null) return;
        if (client.currentScreen instanceof GenericContainerScreen containerScreen) {
            TextContent titleContent = containerScreen.getTitle().getContent();
            if (!(titleContent instanceof TranslatableTextContent translatableText)) return;

            if (translatableText.getKey().equals(ENDER_CHEST_NAME_KEY)) {
                List<ItemStack> items = new ArrayList<>();
                int slots = containerScreen.getScreenHandler().getRows() * 9;

                for (int i = 0; i < slots; i++) {
                    ItemStack stack = containerScreen.getScreenHandler().slots.get(i).getStack();
                    items.add(stack.copy());
                }

                enderChestCache.put(currentWorldId, items);
            }
        }
    }

    private static void updateCurrentWorldId(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            currentWorldId = null;
            return;
        }

        StringBuilder worldId = new StringBuilder();

        if (client.getCurrentServerEntry() != null) {
            worldId.append("server:");
            worldId.append(client.getCurrentServerEntry().address);
            worldId.append(":");
        } else {
            worldId.append("world:");
            if (client.getServer() != null && client.getServer().getSaveProperties() != null) {
                worldId.append(client.getServer().getSaveProperties().getLevelName());
                worldId.append(":");
            }
        }

        worldId.append(client.player.getUuidAsString());

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
