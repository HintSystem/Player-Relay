package dev.hintsystem.playerrelay.mixin;

import dev.hintsystem.playerrelay.mods.SupportPingWheel;
import dev.hintsystem.playerrelay.networking.message.P2PMessage;
import dev.hintsystem.playerrelay.networking.P2PNetworkManager;
import dev.hintsystem.playerrelay.PlayerRelay;

import net.minecraft.client.MinecraftClient;
import nx.pingwheel.common.network.IPacket;
import nx.pingwheel.common.network.PingLocationC2SPacket;
import nx.pingwheel.common.network.PingLocationS2CPacket;

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
            P2PNetworkManager networkManager = PlayerRelay.getNetworkManager();
            MinecraftClient mc = MinecraftClient.getInstance();

            if (PlayerRelay.isNetworkActive() && mc.player != null) {
                try {
                    P2PMessage pingMessage = new P2PMessage(PingLocationS2CPacket.fromClientPacket(pingPacket, mc.player.getUuid()));

                    networkManager.broadcastMessage(pingMessage);
                    supportPingWheel.handlePacket(pingMessage, mc.getNetworkHandler(), mc); // Process same packet on client to see ping
                } catch (Exception e) {
                    PlayerRelay.LOGGER.error("Failed to relay Ping Wheel packet over P2P: {}", e.getMessage());
                }
            }
        }
	}
}