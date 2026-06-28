package dev.hintsystem.playerrelay.logging.handler;

import dev.hintsystem.playerrelay.logging.LogContext;
import dev.hintsystem.playerrelay.network.logging.LogEventTypes;
import dev.hintsystem.playerrelay.network.logging.events.HandshakeFailEvent;
import dev.hintsystem.playerrelay.network.logging.events.NetworkLogEvent;
import dev.hintsystem.playerrelay.logging.events.HudLogEvent;
import dev.hintsystem.playerrelay.payload.RelayVersionPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Transforms a {@link NetworkLogEvent} into a formatted {@link HudLogEvent} */
public class ClientNetworkLogTransformer extends TypedLogMiddleware<NetworkLogEvent> {
    public ClientNetworkLogTransformer() {
        super(NetworkLogEvent.class);
    }

    @Override
    protected void handleTyped(LogContext ctx, NetworkLogEvent event) {
        LogEventTypes eventType = event.getType();
        if (eventType == null) {
            ctx.next(event);
            return;
        }

        switch (eventType) {
            case HANDSHAKE_FAIL -> {
                if (!(event instanceof  HandshakeFailEvent handshakeFail)) return;

                if (handshakeFail.variant == HandshakeFailEvent.FailVariant.BAD_VERSION) {
                    RelayVersionPayload handshake = handshakeFail.handshake;
                    if (handshake == null) return;

                    ctx.next(eventBuilder(event)
                        .title(Component.literal("Version mismatch detected"))
                        .description(Component.empty()
                            .append(Component.literal("\n\nHost requires: ")
                                .withStyle(ChatFormatting.GRAY))
                            .append(Component.literal("mod version " + handshake.versionString + ", network v" + handshake.networkVersion)
                                .withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal("\n"))
                            .append(Component.literal("Your client: ")
                                .withStyle(ChatFormatting.GRAY))
                            .append(Component.literal("mod version " + RelayVersionPayload.VERSION_STRING + ", network v" + RelayVersionPayload.NETWORK_VERSION)
                                .withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal("\n\n⚠ Please install the matching mod version.")
                                .withStyle(ChatFormatting.GOLD)))
                        .build());
                } else if (handshakeFail.variant == HandshakeFailEvent.FailVariant.TIMEOUT) {
                    ctx.next(eventBuilder(event)
                        .description(Component.empty()
                            .append(Component.literal("No relay version received.\n")
                                .withStyle(ChatFormatting.RED))
                            .append(Component.literal("This could mean either the relay you are connecting to, or your client is outdated.\n"))
                            .append(Component.literal("Consider updating the mod.")
                                .withStyle(ChatFormatting.GOLD)))
                        .build());
                }
            }

            case UPNP_FAIL -> ctx.next(eventBuilder(event)
                .description(Component.empty()
                    .append(Component.literal("Could not discover a UPnP gateway.\n")
                        .withStyle(ChatFormatting.RED))
                    .append(Component.literal("The server will continue running, however you will have:\n"))
                    .append(Component.literal("• No automatic port forwarding\n")
                        .withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("• No detection of your local/external IP")
                        .withStyle(ChatFormatting.GOLD)))
                .build());

            case PORT_MAP_FAIL -> ctx.next(eventBuilder(event)
                .description(Component.empty()
                    .append(Component.literal("UPnP port mapping failed.\n")
                        .withStyle(ChatFormatting.RED))
                    .append(Component.literal("Clients outside your network will not be able to connect.\n"))
                    .append(Component.literal("Try hosting again. If it continues to fail, you may need to manually forward this port in your router settings.\n")
                        .withStyle(ChatFormatting.GOLD)))
                .build());

            default -> ctx.next(event);
        }
    }

    protected HudLogEvent.Builder eventBuilder(NetworkLogEvent event) {
        HudLogEvent.Builder builder = new HudLogEvent.Builder();
        builder.setLevel(event.getLevel());

        if (event.getTitle() != null) {
            builder.title(Component.literal(event.getTitle()));
        }

        if (event.getMessage() != null) {
            builder.message(Component.literal(event.getMessage()));
        }

        return builder;
    }
}
