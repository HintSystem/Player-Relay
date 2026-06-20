package dev.hintsystem.playerrelay.payload;

import dev.hintsystem.playerrelay.EnderChestTracker;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerInventoryPayload extends FlagHolder<PlayerInventoryPayload.FLAGS>
    implements Payload {

    public enum FLAGS { IS_REQUEST, IS_ENDER_CHEST, PLAYER_HAS_DATA }

    public final UUID playerId;
    public List<ItemStack> inventoryItems = new ArrayList<>();

    public PlayerInventoryPayload(UUID playerId) {
        this.playerId = playerId;
    }

    public PlayerInventoryPayload(Player player, boolean isEnderChest) {
        this.playerId = player.getUUID();
        this.setFlag(FLAGS.IS_ENDER_CHEST, isEnderChest);

        if (isEnderChest) {
            if (player instanceof ServerPlayer serverPlayer) {
                this.setFlag(FLAGS.PLAYER_HAS_DATA, true);
                PlayerEnderChestContainer enderChest = serverPlayer.getEnderChestInventory();

                for (ItemStack stack : enderChest.getItems()) {
                    this.inventoryItems.add(stack.copy());
                }
            } else {
                this.setFlag(FLAGS.PLAYER_HAS_DATA, EnderChestTracker.hasEnderChestInventory());

                if (EnderChestTracker.hasEnderChestInventory()) {
                    this.inventoryItems = EnderChestTracker.getEnderChestInventory();
                }
            }
            return;
        }

        this.setFlag(FLAGS.PLAYER_HAS_DATA, true);
        Inventory inventory = player.getInventory();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);

            this.inventoryItems.add(stack.copy());
        }
    }

    public boolean isRequest() { return hasFlag(FLAGS.IS_REQUEST); }
    public boolean isResponse() { return !hasFlag(FLAGS.IS_REQUEST); }

    public boolean hasData() { return hasFlag(FLAGS.PLAYER_HAS_DATA); }
    public boolean isEnderChest() { return hasFlag(FLAGS.IS_ENDER_CHEST); }

    public static PlayerInventoryPayload request(UUID playerId, boolean isEnderChest) {
        PlayerInventoryPayload payload = new PlayerInventoryPayload(playerId);
        payload.setFlag(FLAGS.IS_REQUEST, true);
        payload.setFlag(FLAGS.IS_ENDER_CHEST, isEnderChest);
        return payload;
    }

    public static PlayerInventoryPayload respond(Player player, boolean isEnderChest) {
        return new PlayerInventoryPayload(player, isEnderChest);
    }

    @Override
    public PayloadRegistry.PayloadType<PlayerInventoryPayload> getPayloadType() { return PayloadRegistry.PLAYER_INVENTORY; }

    public PlayerInventoryPayload(RegistryFriendlyByteBuf buf) {
        this.playerId = buf.readUUID();
        readFlags(buf, 1);

        if (isResponse() && hasData()) {
            this.inventoryItems = ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf);
        }
    }

    @Override
    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(this.playerId);
        writeFlags(buf, 1);

        if (isResponse() && hasData()) {
            ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, inventoryItems);
        }
    }
}
