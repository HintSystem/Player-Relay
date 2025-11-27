package dev.hintsystem.playerrelay.mixin.pingwheel;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.CommonCore;
import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.mods.SupportPingWheel;
import dev.hintsystem.playerrelay.payload.GenericPacketPayload;

import nx.pingwheel.common.network.IPacket;
import nx.pingwheel.common.network.PingLocationC2SPacket;
import nx.pingwheel.common.network.PingLocationS2CPacket;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.MinecraftClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = nx.pingwheel.fabric.platform.PlatformNetworkServiceImpl.class, remap = false)
public class PingWheelNetworkMixin {
    @Unique
    private static final SupportPingWheel supportPingWheel = new SupportPingWheel();

	@Inject(method = "sendToServer", at = @At("HEAD"))
	public void onPingLocationPacket(IPacket packet, CallbackInfo ci) {
        if (packet instanceof PingLocationC2SPacket pingPacket) {
            MinecraftClient client = MinecraftClient.getInstance();

            if (ClientCore.isP2PNetworkActive() && client.player != null) {
                GenericPacketPayload pingPayload = new GenericPacketPayload(
                    PingLocationS2CPacket.fromClientPacket(pingPacket, client.player.getUuid())
                );

                try {
                    CommonCore.getP2PNetworkManager().broadcastMessage(pingPayload.message());
                    if (!ClientPlayNetworking.canSend(packet.getId())) {
                        supportPingWheel.handlePacket(pingPayload, client.getNetworkHandler(), client); // Process same packet on client to see ping, when Ping Wheel isn't on current server
                    }
                } catch (Exception e) {
                    PlayerRelay.LOGGER.error("Failed to relay Ping Wheel packet over P2P: {}", e.getMessage());
                }
            }
        }
	}
}