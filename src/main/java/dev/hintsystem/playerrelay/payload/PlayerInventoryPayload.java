package dev.hintsystem.playerrelay.payload;

import dev.hintsystem.playerrelay.networking.message.P2PMessageType;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerInventoryPayload implements IPayload {
    private boolean isRequest = true;
    public final UUID playerId;
    public List<ItemStack> inventoryItems = new ArrayList<>();

    public PlayerInventoryPayload(UUID playerId) { this.playerId = playerId; }

    public static PlayerInventoryPayload request(UUID playerId) { return new PlayerInventoryPayload(playerId); }

    public PlayerInventoryPayload(PlayerEntity player) {
        this.isRequest = false;
        this.playerId = player.getUuid();

        for (int i = 0; i < PlayerInventory.MAIN_SIZE; i++) {
            ItemStack stack = player.getInventory().getStack(i);

            this.inventoryItems.add(stack.copy());
        }
    }

    public static PlayerInventoryPayload respond(PlayerEntity player) { return new PlayerInventoryPayload(player); }

    public PlayerInventoryPayload(RegistryByteBuf buf) {
        this.isRequest = buf.readBoolean();
        this.playerId = buf.readUuid();

        if (isResponse()) {
            this.inventoryItems = ItemStack.OPTIONAL_LIST_PACKET_CODEC.decode(buf);
        }
    }

    public boolean isRequest() { return this.isRequest; }
    public boolean isResponse() { return !this.isRequest; }

    @Override
    public P2PMessageType getMessageType() { return P2PMessageType.PLAYER_INVENTORY; }

    @Override
    public void write(RegistryByteBuf buf) {
        buf.writeBoolean(this.isRequest);
        buf.writeUuid(this.playerId);

        if (isResponse()) {
            ItemStack.OPTIONAL_LIST_PACKET_CODEC.encode(buf, inventoryItems);
        }
    }
}
