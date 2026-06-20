package dev.hintsystem.playerrelay.payload.player;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;

public interface PlayerDataComponent {
    default void applyToPlayer(Player player) {}
    void write(RegistryFriendlyByteBuf buf);
    void read(RegistryFriendlyByteBuf buf);
    boolean hasChanged(PlayerDataComponent other);
    PlayerDataComponent copy();
}