package dev.hintsystem.playerrelay.payload.player;

import net.minecraft.network.PacketByteBuf;

import java.awt.Color;
import java.util.Objects;

public class PlayerBasicData implements PlayerDataComponent {
    public String name;
    public int nameColor;

    public PlayerBasicData() {}

    public PlayerBasicData(String name) {
        this(name, Color.WHITE);
    }

    public PlayerBasicData(String name, Color nameColor) {
        this(name, nameColor.getRGB());
    }

    public PlayerBasicData(String name, int nameColor) {
        this.name = name;
        this.nameColor = getOpaqueColor(nameColor);
    }

    private int getOpaqueColor(int color) { return 0xFF000000 | (color & 0x00FFFFFF); }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeString(name);
        buf.writeInt(nameColor);
    }

    @Override
    public void read(PacketByteBuf buf) {
        this.name = buf.readString();
        this.nameColor = getOpaqueColor(buf.readInt());
    }

    @Override
    public boolean hasChanged(PlayerDataComponent other) {
        if (!(other instanceof PlayerBasicData otherInfo)) return true;

        return !Objects.equals(this.name, otherInfo.name)
            || !Objects.equals(this.nameColor, otherInfo.nameColor);
    }

    @Override
    public PlayerBasicData copy() {
        return new PlayerBasicData(
            this.name,
            this.nameColor
        );
    }
}
