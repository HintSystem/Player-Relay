package dev.hintsystem.playerrelay.payload.player;

import net.minecraft.network.PacketByteBuf;

public class PlayerStatsData implements PlayerDataComponent {
    public float health, xp;
    public int hunger, armor;

    public  PlayerStatsData() {}

    public PlayerStatsData(float health, float xp, int hunger, int armor) {
        this.health = health;
        this.xp = xp;
        this.hunger = hunger;
        this.armor = armor;
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

        return Float.compare(this.health, otherStats.health) != 0 ||
            Float.compare(this.xp, otherStats.xp) != 0 ||
            this.hunger != otherStats.hunger ||
            this.armor != otherStats.armor;
    }

    @Override
    public PlayerDataComponent copy() {
        return new PlayerStatsData(
            this.health,
            this.xp,
            this.hunger,
            this.armor
        );
    }
}
