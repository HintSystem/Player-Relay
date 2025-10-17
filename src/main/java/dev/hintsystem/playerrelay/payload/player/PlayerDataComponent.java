package dev.hintsystem.playerrelay.payload.player;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;

public interface PlayerDataComponent {
    default void applyToPlayer(PlayerEntity player) {}
    void write(RegistryByteBuf buf);
    void read(RegistryByteBuf buf);
    boolean hasChanged(PlayerDataComponent other);
    PlayerDataComponent copy();
}