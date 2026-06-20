package dev.hintsystem.playerrelay.mixin.minecraft;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.CommonCore;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(net.minecraft.client.multiplayer.ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(
        method = "getPlayerInfo(Ljava/util/UUID;)Lnet/minecraft/client/multiplayer/PlayerInfo;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void playerListEntryFallback(UUID uuid, CallbackInfoReturnable<PlayerInfo> cir) {
        if (cir.getReturnValue() == null && ClientCore.isP2PNetworkActive()) {
            PlayerInfoPayload fallback = CommonCore.peerConnections.getPlayer(uuid);

            if (fallback != null) { cir.setReturnValue(fallback.toPlayerListEntry()); }
        }
    }

    @Inject(method = "handlePlayerInfoRemove", at = @At("HEAD"))
    private void onPlayerRemove(ClientboundPlayerInfoRemovePacket packet, CallbackInfo ci) {
        for (UUID uuid : packet.profileIds()) {
            CommonCore.serverConnection.removeAnnouncedPlayer(uuid);
        }
    }
}
