package dev.hintsystem.playerrelay.logging;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.payload.RelayVersionPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import org.slf4j.event.Level;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class ClientLogHandler implements LogHandler {
    @Override
    public void handle(LogEvent event) {
        if (event.getType() == null) return;

        switch (event.getType()) {
            case UPNP_FAIL -> ClientCore.addHudMessage(formatMessage(event, Component.empty()
                    .append(Component.literal("Could not discover a UPnP gateway.\n")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)))
                    .append(Component.literal("The server will continue running, however you will have:\n"))
                    .append(Component.literal("• No automatic port forwarding\n")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)))
                    .append(Component.literal("• No detection of your local/external IP")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)))
            ));
            case PORT_MAP_FAIL -> ClientCore.addHudMessage(formatMessage(event, Component.empty()
                    .append(Component.literal("UPnP port mapping failed.\n")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)))
                    .append(Component.literal("Clients outside your network will not be able to connect.\n"))
                    .append(Component.literal("Try hosting again. If it continues to fail, you may need to manually forward this port in your router settings.\n")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)))
            ));
            case VERSION_FAIL -> {
                Object version = event.getContext().get("version");

                if (version instanceof RelayVersionPayload versionPayload) {
                    ClientCore.addHudMessage(Component.empty()
                        .append(formatTitle("Version mismatch detected", LevelFormat.ERROR))
                        .append(Component.literal("\n\n"))
                        .append(Component.literal("Host requires: ")
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)))
                        .append(Component.literal("mod version " + versionPayload.versionString + ", network v" + versionPayload.networkVersion)
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)))
                        .append(Component.literal("\n"))
                        .append(Component.literal("Your client: ")
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)))
                        .append(Component.literal("mod version " + RelayVersionPayload.VERSION_STRING + ", network v" + RelayVersionPayload.NETWORK_VERSION)
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)))
                        .append(Component.literal("\n\n"))
                        .append(Component.literal("⚠ Please install the matching mod version.")
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)))
                    );
                } else {
                    ClientCore.addHudMessage(formatMessage(event, Component.empty()
                        .append(Component.literal("No relay version received.\n")
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)))
                        .append(Component.literal("This could mean either the relay you are connecting to, or your client is outdated.\n"))
                        .append(Component.literal("Consider updating the mod.")
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)))
                    ));
                }
            }
        }
    }

    private enum LevelFormat {
        DEFAULT(null, "", ChatFormatting.GRAY),
        WARN(Level.WARN, "⚠", ChatFormatting.GOLD),
        ERROR(Level.ERROR, "❌", ChatFormatting.RED);

        private final Level level;
        public final String icon;
        public final ChatFormatting color;

        LevelFormat(Level level, String icon, ChatFormatting color) {
            this.level = level;
            this.icon = icon;
            this.color = color;
        }

        private static final Map<Level, LevelFormat> FORMAT_MAP = Arrays.stream(values())
            .filter(lf -> lf.level != null)
            .collect(Collectors.toMap(lf -> lf.level, lf -> lf));

        public static LevelFormat fromLevel(Level level) {
            return FORMAT_MAP.getOrDefault(level, DEFAULT);
        }
    }

    private MutableComponent formatTitle(String title, LevelFormat format) {
        return Component.literal(format.icon + " " + title)
            .setStyle(Style.EMPTY.withColor(format.color).withBold(true));
    }

    private MutableComponent formatMessage(LogEvent event) { return formatMessage(event, event.getLevel(), null); }

    private MutableComponent formatMessage(LogEvent event, Component overrideDescription) { return formatMessage(event, event.getLevel(), overrideDescription); }

    private MutableComponent formatMessage(LogEvent event, Level level, Component overrideDescription) {
        LevelFormat format = LevelFormat.fromLevel(level);
        MutableComponent msg = Component.empty();

        String message = event.getMessage();
        if (message == null && event.getException() != null) {
            message = event.getException().getMessage();
        }

        String title = event.getTitle() != null
            ? (message != null ? event.getTitle() + ":\n" : event.getTitle())
            : "";

        msg.append(formatTitle(title, format));
        if (message != null) {
            msg.append(Component.literal(message)
                .setStyle(Style.EMPTY.withColor(format.color)));
        }

        Component description = (event.getDescription() != null) ? Component.literal(event.getDescription()) : overrideDescription;
        if (description != null) {
            msg.withStyle(style -> style.withHoverEvent(
                new HoverEvent.ShowText(description)
            ));
        }

        return msg;
    }

    @Override
    public boolean isEnabled(Level level) {
        return level.compareTo(Level.WARN) <= 0;
    }
}
