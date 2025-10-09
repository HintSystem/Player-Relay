package dev.hintsystem.playerrelay.payload.player;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;

public class PlayerStatsData implements PlayerDataComponent {
    public float health, absorptionAmount, xp;
    public short hunger, armor;

    public PlayerStatsData() {}

    public PlayerStatsData(PlayerEntity player) {
        this.health = player.getHealth();
        this.absorptionAmount = player.getAbsorptionAmount();
        this.xp = player.experienceLevel + player.experienceProgress;
        this.hunger = (short) Math.min(player.getHungerManager().getFoodLevel(), Short.MAX_VALUE);
        this.armor = (short) Math.min(player.getArmor(), Short.MAX_VALUE);
    }

    @Override
    public void write(RegistryByteBuf buf) {
        buf.writeFloat(health);
        buf.writeFloat(absorptionAmount);
        buf.writeFloat(xp);
        buf.writeShort(hunger);
        buf.writeShort(armor);
    }

    @Override
    public void read(RegistryByteBuf buf) {
        this.health = buf.readFloat();
        this.absorptionAmount = buf.readFloat();
        this.xp = buf.readFloat();
        this.hunger = buf.readShort();
        this.armor = buf.readShort();
    }

    @Override
    public boolean hasChanged(PlayerDataComponent other) {
        if (!(other instanceof PlayerStatsData otherStats)) return true;

        return Float.compare(this.health, otherStats.health) != 0
            || Float.compare(this.absorptionAmount, otherStats.absorptionAmount) != 0
            || Float.compare(this.xp, otherStats.xp) != 0
            || this.hunger != otherStats.hunger
            || this.armor != otherStats.armor;
    }

    @Override
    public PlayerStatsData copy() {
        PlayerStatsData copy = new PlayerStatsData();
        copy.health = this.health;
        copy.absorptionAmount = this.absorptionAmount;
        copy.xp = this.xp;
        copy.hunger = this.hunger;
        copy.armor = this.armor;
        return copy;
    }
}
