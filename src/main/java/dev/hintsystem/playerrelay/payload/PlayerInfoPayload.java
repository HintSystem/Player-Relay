package dev.hintsystem.playerrelay.payload;

import dev.hintsystem.playerrelay.networking.P2PMessageType;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

public class PlayerInfoPayload implements IPayload {
    public static final byte FLAG_NEWCONNECTION = 1 << 0;
    public static final byte FLAG_NAME = 1 << 1;
    public static final byte FLAG_POSITION      = 1 << 2;
    public static final byte FLAG_GENERALINFO   = 1 << 3;

    public final UUID playerId;
    private byte flags = 0;

    public PlayerPositionPayload pos;
    public String name;
    public float health, xp;
    public int hunger, armor;

    public PlayerInfoPayload(UUID playerId) {
        this.playerId = playerId;
    }

    public PlayerInfoPayload(PacketByteBuf buf) {
        int idx = buf.readerIndex();
        this.playerId = buf.readUuid();
        buf.readerIndex(idx);

        read(buf);
    }

    public PlayerListEntry toPlayerListEntry() {
        String profileName = (this.name != null ? this.name : this.playerId.toString());

        return new PlayerListEntry(new GameProfile(this.playerId, profileName), false);
    }

    @Override
    public P2PMessageType getMessageType() { return P2PMessageType.PLAYER_INFO; }

    public PlayerInfoPayload setNewConnectionFlag(boolean isNewConnection) {
        if (isNewConnection) {
            this.flags |= FLAG_NEWCONNECTION;
        } else {
            this.flags &= ~FLAG_NEWCONNECTION;
        }

        return this;
    }

    public PlayerInfoPayload setName(String name) {
        this.name = name;
        this.flags |= FLAG_NAME;

        return this;
    }

    public PlayerInfoPayload setPosition(PlayerPositionPayload positionPayload) {
        return setPosition(positionPayload.coords, positionPayload.dimension);
    }

    public PlayerInfoPayload setPosition(Vec3d pos, RegistryKey<World> dimension) {
        this.pos = new PlayerPositionPayload(this.playerId, pos, dimension);
        this.pos.includeUuid = false;
        this.flags |= FLAG_POSITION;

        return this;
    }

    public PlayerInfoPayload setGeneralInfo(float health, float xp, int hunger, int armor) {
        this.health = health;
        this.xp = xp;
        this.hunger = hunger;
        this.armor = armor;
        this.flags |= FLAG_GENERALINFO;

        return this;
    }

    public boolean hasAnyInfo()           { return flags != 0; }
    public boolean hasNewConnectionFlag() { return (flags & FLAG_NEWCONNECTION) != 0; }
    public boolean hasName()              { return (flags & FLAG_NAME) != 0; }
    public boolean hasPosition()          { return (flags & FLAG_POSITION) != 0; }
    public boolean hasGeneralInfo()       { return (flags & FLAG_GENERALINFO) != 0; }

    public void merge(PlayerInfoPayload reference) {
        if (reference.hasName()) {
            setName(reference.name);
        }
        if (reference.hasPosition()) {
            setPosition(reference.pos);
        }
        if (reference.hasGeneralInfo()) {
            setGeneralInfo(reference.health, reference.xp, reference.hunger, reference.armor);
        }
    }

    public void read(PacketByteBuf buf) {
        buf.readUuid();
        this.flags = buf.readByte();

        if (hasName()) {
            this.name = buf.readString();
        }
        if (hasPosition()) {
            this.pos = new PlayerPositionPayload(this.playerId, buf);
        }
        if (hasGeneralInfo()) {
            this.health = buf.readFloat();
            this.xp = buf.readFloat();
            this.hunger = buf.readInt();
            this.armor = buf.readInt();
        }
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeUuid(playerId);
        buf.writeByte(flags);

        if (hasName()) {
            buf.writeString(name);
        }
        if (hasPosition() && this.pos != null) {
            this.pos.includeUuid = false;
            this.pos.write(buf);
        }
        if (hasGeneralInfo()) {
            buf.writeFloat(health);
            buf.writeFloat(xp);
            buf.writeInt(hunger);
            buf.writeInt(armor);
        }
    }
}
