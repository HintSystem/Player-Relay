package dev.hintsystem.playerrelay.mods.xaero;

import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import dev.hintsystem.playerrelay.payload.player.PlayerPositionData;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class RelayTrackedPlayerReader {
    public UUID getId(PlayerInfoPayload player) {
        return player.playerId;
    }

    public ResourceKey<Level> getDimension(PlayerInfoPayload player) {
        return player.getDimension();
    }

    public double getX(PlayerInfoPayload player) {
        return getPos(player).x();
    }

    public double getY(PlayerInfoPayload player) {
        return getPos(player).y();
    }

    public double getZ(PlayerInfoPayload player) {
        return getPos(player).z();
    }

    public Vec3 getPos(PlayerInfoPayload player) {
        PlayerPositionData pos = player.getComponent(PlayerPositionData.class);
        return pos != null ? pos.coords : null;
    }
}
