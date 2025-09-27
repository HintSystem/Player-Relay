package dev.hintsystem.playerrelay.logging;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.payload.RelayVersionPayload;

import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import org.slf4j.event.Level;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class ClientLogHandler implements LogHandler {

    @Override
    public void handle(LogEvent event) {
        if (event.getType() == null) return;

        switch (event.getType()) {
            case UPNP_FAIL -> ClientCore.sendClientMessage(formatMessage(event, Text.empty()
                    .append(Text.literal("Could not discover a UPnP gateway.\n")
                        .setStyle(Style.EMPTY.withColor(Formatting.RED)))
                    .append(Text.literal("The server will continue running, however you will have:\n"))
                    .append(Text.literal("• No automatic port forwarding\n")
                        .setStyle(Style.EMPTY.withColor(Formatting.GOLD)))
                    .append(Text.literal("• No detection of your local/external IP")
                        .setStyle(Style.EMPTY.withColor(Formatting.GOLD)))
            ));
            case PORT_MAP_FAIL -> ClientCore.sendClientMessage(formatMessage(event, Text.empty()
                    .append(Text.literal("UPnP port mapping failed.\n")
                        .setStyle(Style.EMPTY.withColor(Formatting.RED)))
                    .append(Text.literal("Clients outside your network will not be able to connect.\n"))
                    .append(Text.literal("Try hosting again. If it continues to fail, you may need to manually forward this port in your router settings.\n")
                        .setStyle(Style.EMPTY.withColor(Formatting.GOLD)))
            ));
            case VERSION_FAIL -> {
                Object version = event.getContext().get("version");

                if (version instanceof RelayVersionPayload versionPayload) {
                    ClientCore.sendClientMessage(Text.empty()
                        .append(formatTitle("Version mismatch detected", LevelFormat.ERROR))
                        .append(Text.literal("\n\n"))
                        .append(Text.literal("Host requires: ")
                            .setStyle(Style.EMPTY.withColor(Formatting.GRAY)))
                        .append(Text.literal("mod version " + versionPayload.modVersion + ", network v" + versionPayload.networkVersion)
                            .setStyle(Style.EMPTY.withColor(Formatting.YELLOW)))
                        .append(Text.literal("\n"))
                        .append(Text.literal("Your client: ")
                            .setStyle(Style.EMPTY.withColor(Formatting.GRAY)))
                        .append(Text.literal("mod version " + PlayerRelay.VERSION + ", network v" + PlayerRelay.NETWORK_VERSION)
                            .setStyle(Style.EMPTY.withColor(Formatting.YELLOW)))
                        .append(Text.literal("\n\n"))
                        .append(Text.literal("⚠ Please install the matching mod version.")
                            .setStyle(Style.EMPTY.withColor(Formatting.GOLD)))
                    );
                } else {
                    ClientCore.sendClientMessage(formatMessage(event, Text.empty()
                        .append(Text.literal("No relay version received.\n")
                            .setStyle(Style.EMPTY.withColor(Formatting.RED)))
                        .append(Text.literal("This could mean either the relay you are connecting to, or your client is outdated.\n"))
                        .append(Text.literal("Consider updating the mod.")
                            .setStyle(Style.EMPTY.withColor(Formatting.GOLD)))
                    ));
                }
            }
        }
    }

    private enum LevelFormat {
        DEFAULT(null, "", Formatting.GRAY),
        WARN(Level.WARN, "⚠", Formatting.GOLD),
        ERROR(Level.ERROR, "❌", Formatting.RED);

        private final Level level;
        public final String icon;
        public final Formatting color;

        LevelFormat(Level level, String icon, Formatting color) {
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

    private MutableText formatTitle(String title, LevelFormat format) {
        return Text.literal(format.icon + " " + title)
            .setStyle(Style.EMPTY.withColor(format.color).withBold(true));
    }

    private MutableText formatMessage(LogEvent event) { return formatMessage(event, event.getLevel(), null); }

    private MutableText formatMessage(LogEvent event, Text overrideDescription) { return formatMessage(event, event.getLevel(), overrideDescription); }

    private MutableText formatMessage(LogEvent event, Level level, Text overrideDescription) {
        LevelFormat format = LevelFormat.fromLevel(level);
        MutableText msg = Text.empty();

        String message = event.getMessage();
        if (message == null && event.getException() != null) {
            message = event.getException().getMessage();
        }

        String title = event.getTitle() != null
            ? (message != null ? event.getTitle() + ":\n" : event.getTitle())
            : "";

        msg.append(formatTitle(title, format));
        if (message != null) {
            msg.append(Text.literal(message)
                .setStyle(Style.EMPTY.withColor(format.color)));
        }

        Text description = (event.getDescription() != null) ? Text.literal(event.getDescription()) : overrideDescription;
        if (description != null) {
            msg.styled(style -> style.withHoverEvent(
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
