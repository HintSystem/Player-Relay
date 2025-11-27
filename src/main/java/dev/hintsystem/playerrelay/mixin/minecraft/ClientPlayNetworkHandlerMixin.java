package dev.hintsystem.playerrelay.mixin.minecraft;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.CommonCore;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(net.minecraft.client.network.ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(
        method = "getPlayerListEntry(Ljava/util/UUID;)Lnet/minecraft/client/network/PlayerListEntry;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void playerListEntryFallback(UUID uuid, CallbackInfoReturnable<PlayerListEntry> cir) {
        if (cir.getReturnValue() == null && ClientCore.isP2PNetworkActive()) {
            PlayerInfoPayload fallback = CommonCore.p2pPlayers.get(uuid);

            if (fallback != null) { cir.setReturnValue(fallback.toPlayerListEntry()); }
        }
    }

    @Inject(method = "onPlayerRemove", at = @At("HEAD"))
    private void onPlayerRemove(PlayerRemoveS2CPacket packet, CallbackInfo ci) {
        for (UUID uuid : packet.profileIds()) {
            CommonCore.serverPlayers.remove(uuid);
        }
    }
}
