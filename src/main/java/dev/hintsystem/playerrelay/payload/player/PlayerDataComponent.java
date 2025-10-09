package dev.hintsystem.playerrelay.payload.player;

import net.minecraft.network.RegistryByteBuf;

public interface PlayerDataComponent {
    void write(RegistryByteBuf buf);
    void read(RegistryByteBuf buf);
    boolean hasChanged(PlayerDataComponent other);
    PlayerDataComponent copy();
}