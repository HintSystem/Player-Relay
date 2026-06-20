package dev.hintsystem.playerrelay.payload.player;

import dev.hintsystem.playerrelay.payload.FlagHolder;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;

import org.jetbrains.annotations.Nullable;
import java.util.Objects;

public class PlayerWorldData extends FlagHolder<PlayerWorldData.FLAGS>
    implements PlayerDataComponent {
    public enum FLAGS { IN_WORLD, HARDCORE }

    @Nullable
    public ResourceKey<Level> dimension = null;
    @Nullable
    public Difficulty difficulty = null;

    public PlayerWorldData() {}

    public PlayerWorldData(@Nullable Player player) {
        if (player == null) { return; }

        setFlag(FLAGS.IN_WORLD, true);
        LevelData worldProperties = player.level().getLevelData();

        setFlag(FLAGS.HARDCORE, worldProperties.isHardcore());
        this.dimension = player.level().dimension();
        this.difficulty = worldProperties.getDifficulty();
    }

    public boolean isInWorld() { return hasFlag(FLAGS.IN_WORLD); }

    public boolean isHardcore() { return hasFlag(FLAGS.HARDCORE); }

    @Override
    public void write(RegistryFriendlyByteBuf buf) {
        writeFlags(buf, 1);

        if (hasFlag(FLAGS.IN_WORLD)) {
            assert dimension != null && difficulty != null : "dimension and difficulty must be set when IN_WORLD is true";
            buf.writeResourceLocation(dimension.location());
            Difficulty.STREAM_CODEC.encode(buf, difficulty);
        }
    }

    @Override
    public void read(RegistryFriendlyByteBuf buf) {
        readFlags(buf, 1);

        if (hasFlag(FLAGS.IN_WORLD)) {
            this.dimension = ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
            this.difficulty = Difficulty.STREAM_CODEC.decode(buf);
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
