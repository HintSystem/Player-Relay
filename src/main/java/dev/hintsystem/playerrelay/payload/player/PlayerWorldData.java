package dev.hintsystem.playerrelay.payload.player;

import dev.hintsystem.playerrelay.payload.FlagHolder;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldProperties;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class PlayerWorldData extends FlagHolder<PlayerWorldData.FLAGS>
    implements PlayerDataComponent {
    public enum FLAGS { IN_WORLD, HARDCORE }

    @Nullable
    public RegistryKey<World> dimension;
    @Nullable
    public Difficulty difficulty;

    public PlayerWorldData() {
        setFlag(FLAGS.IN_WORLD, false);
        this.dimension = null;
        this.difficulty = null;
    }

    public PlayerWorldData(@Nullable PlayerEntity player) {
        if (player == null) { return; }

        setFlag(FLAGS.IN_WORLD, true);
        WorldProperties worldProperties = player.getWorld().getLevelProperties();

        setFlag(FLAGS.HARDCORE, worldProperties.isHardcore());
        this.dimension = player.getWorld().getRegistryKey();
        this.difficulty = worldProperties.getDifficulty();
    }

    public boolean isInWorld() { return hasFlag(FLAGS.IN_WORLD); }

    public boolean isHardcore() { return hasFlag(FLAGS.HARDCORE); }

    @Override
    public void write(RegistryByteBuf buf) {
        writeFlags(buf, 1);

        if (hasFlag(FLAGS.IN_WORLD)) {
            assert dimension != null && difficulty != null : "dimension and difficulty must be set when IN_WORLD is true";
            buf.writeIdentifier(dimension.getValue());
            Difficulty.PACKET_CODEC.encode(buf, difficulty);
        }
    }

    @Override
    public void read(RegistryByteBuf buf) {
        readFlags(buf, 1);

        if (hasFlag(FLAGS.IN_WORLD)) {
            this.dimension = RegistryKey.of(RegistryKeys.WORLD, buf.readIdentifier());
            this.difficulty = Difficulty.PACKET_CODEC.decode(buf);
        }
    }

    @Override
    public boolean hasChanged(PlayerDataComponent other) {
        if (!(other instanceof PlayerWorldData otherWorld)) return true;

        return !equalsFlags(otherWorld)
            || !Objects.equals(this.dimension, otherWorld.dimension)
            || !Objects.equals(this.difficulty, otherWorld.difficulty);
    }

    @Override
    public PlayerWorldData copy() {
        PlayerWorldData copy = new PlayerWorldData();
        copy.setFlags(this.getFlags());
        copy.dimension = this.dimension;
        copy.difficulty = this.difficulty;
        return copy;
    }
}
