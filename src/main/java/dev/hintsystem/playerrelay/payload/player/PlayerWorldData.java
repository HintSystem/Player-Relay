package dev.hintsystem.playerrelay.payload.player;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldProperties;

import java.util.Objects;

public class PlayerWorldData implements PlayerDataComponent {
    public RegistryKey<World> dimension;
    public Difficulty difficulty;
    public boolean hardcore;

    public PlayerWorldData() {}

    public PlayerWorldData(PlayerEntity player) {
        WorldProperties worldProperties = player.getWorld().getLevelProperties();

        this.dimension = player.getWorld().getRegistryKey();
        this.difficulty = worldProperties.getDifficulty();
        this.hardcore = worldProperties.isHardcore();
    }

    @Override
    public void write(RegistryByteBuf buf) {
        buf.writeIdentifier(dimension.getValue());
        Difficulty.PACKET_CODEC.encode(buf, difficulty);
        buf.writeBoolean(hardcore);
    }

    @Override
    public void read(RegistryByteBuf buf) {
        this.dimension = RegistryKey.of(RegistryKeys.WORLD, buf.readIdentifier());
        this.difficulty = Difficulty.PACKET_CODEC.decode(buf);
        this.hardcore = buf.readBoolean();
    }

    @Override
    public boolean hasChanged(PlayerDataComponent other) {
        if (!(other instanceof PlayerWorldData otherWorld)) return true;

        return !Objects.equals(this.dimension, otherWorld.dimension)
            || !Objects.equals(this.difficulty, otherWorld.difficulty)
            || this.hardcore != otherWorld.hardcore;
    }

    @Override
    public PlayerWorldData copy() {
        PlayerWorldData copy = new PlayerWorldData();
        copy.dimension = this.dimension;
        copy.difficulty = this.difficulty;
        copy.hardcore = this.hardcore;
        return copy;
    }
}
