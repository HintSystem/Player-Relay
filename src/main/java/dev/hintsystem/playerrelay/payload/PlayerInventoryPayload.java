package dev.hintsystem.playerrelay.payload;

import dev.hintsystem.playerrelay.EnderChestTracker;
import dev.hintsystem.playerrelay.networking.message.P2PMessageType;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerInventoryPayload extends FlagHolder<PlayerInventoryPayload.FLAGS>
    implements IPayload {
    public enum FLAGS { IS_REQUEST, IS_ENDER_CHEST, PLAYER_HAS_DATA }

    public final UUID playerId;
    public List<ItemStack> inventoryItems = new ArrayList<>();

    public PlayerInventoryPayload(UUID playerId) {
        this.playerId = playerId;
    }

    public PlayerInventoryPayload(PlayerEntity player, boolean isEnderChest) {
        this.playerId = player.getUuid();
        this.setFlag(FLAGS.IS_ENDER_CHEST, isEnderChest);

        if (isEnderChest) {
            this.setFlag(FLAGS.PLAYER_HAS_DATA, EnderChestTracker.hasEnderChestInventory());
            if (EnderChestTracker.hasEnderChestInventory()) {
                this.inventoryItems = EnderChestTracker.getEnderChestInventory();
            }
            return;
        }

        this.setFlag(FLAGS.PLAYER_HAS_DATA, true);
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);

            this.inventoryItems.add(stack.copy());
        }
    }

    public static PlayerInventoryPayload request(UUID playerId, boolean isEnderChest) {
        PlayerInventoryPayload payload = new PlayerInventoryPayload(playerId);
        payload.setFlag(FLAGS.IS_REQUEST, true);
        payload.setFlag(FLAGS.IS_ENDER_CHEST, isEnderChest);
        return payload;
    }
    public static PlayerInventoryPayload respond(PlayerEntity player, boolean isEnderChest) { return new PlayerInventoryPayload(player, isEnderChest); }

    public PlayerInventoryPayload(RegistryByteBuf buf) {
        this.playerId = buf.readUuid();
        readFlags(buf, 1);

        if (isResponse() && hasData()) {
            this.inventoryItems = ItemStack.OPTIONAL_LIST_PACKET_CODEC.decode(buf);
        }
    }

    public boolean isRequest() { return hasFlag(FLAGS.IS_REQUEST); }
    public boolean isResponse() { return !hasFlag(FLAGS.IS_REQUEST); }

    public boolean hasData() { return hasFlag(FLAGS.PLAYER_HAS_DATA); }
    public boolean isEnderChest() { return hasFlag(FLAGS.IS_ENDER_CHEST); }

    @Override
    public P2PMessageType getMessageType() { return P2PMessageType.PLAYER_INVENTORY; }

    @Override
    public void write(RegistryByteBuf buf) {
        buf.writeUuid(this.playerId);
        writeFlags(buf, 1);

        if (isResponse() && hasData()) {
            ItemStack.OPTIONAL_LIST_PACKET_CODEC.encode(buf, inventoryItems);
        }
    }
}
