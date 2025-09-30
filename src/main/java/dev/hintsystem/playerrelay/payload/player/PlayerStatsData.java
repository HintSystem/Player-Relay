package dev.hintsystem.playerrelay.payload.player;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;

public class PlayerStatsData implements PlayerDataComponent {
    public float health, xp;
    public int hunger, armor;

    public PlayerStatsData() {}

    public PlayerStatsData(PlayerEntity player) {
        this.health = player.getHealth();
        this.xp = player.experienceLevel + player.experienceProgress;
        this.hunger = player.getHungerManager().getFoodLevel();
        this.armor = player.getArmor();
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeFloat(health);
        buf.writeFloat(xp);
        buf.writeInt(hunger);
        buf.writeInt(armor);
    }

    @Override
    public void read(PacketByteBuf buf) {
        this.health = buf.readFloat();
        this.xp = buf.readFloat();
        this.hunger = buf.readInt();
        this.armor = buf.readInt();
    }

    @Override
    public boolean hasChanged(PlayerDataComponent other) {
        if (!(other instanceof PlayerStatsData otherStats)) return true;

        return Float.compare(this.health, otherStats.health) != 0
            || Float.compare(this.xp, otherStats.xp) != 0
            || this.hunger != otherStats.hunger
            || this.armor != otherStats.armor;
    }

    @Override
    public PlayerStatsData copy() {
        PlayerStatsData copy = new PlayerStatsData();
        copy.health = this.health;
        copy.xp = this.xp;
        copy.hunger = this.hunger;
        copy.armor = this.armor;
        return copy;
    }
}
