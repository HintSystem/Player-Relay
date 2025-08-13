package dev.hintsystem.playerrelay.mixin;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import net.minecraft.client.network.PlayerListEntry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
        if (cir.getReturnValue() == null && PlayerRelay.isNetworkActive()) {
            PlayerInfoPayload fallback = PlayerRelay.getNetworkManager().connectedPlayers.get(uuid);

            if (fallback != null) { cir.setReturnValue(fallback.toPlayerListEntry()); }
        }
    }
}
