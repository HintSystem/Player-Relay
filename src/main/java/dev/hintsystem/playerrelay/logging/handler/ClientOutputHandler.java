package dev.hintsystem.playerrelay.logging.handler;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.logging.events.AbstractLogEvent;
import dev.hintsystem.playerrelay.logging.events.HudLogEvent;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import org.slf4j.event.Level;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class ClientOutputHandler implements LogHandler {
    public void handle(AbstractLogEvent event) {
        if (!(event instanceof HudLogEvent hudLog)) return;

        Minecraft.getInstance().schedule(() ->
            ClientCore.addHudMessage(formatMessage(hudLog)));
    }

    private enum LevelFormat {
        DEFAULT(null, " ℹ ", ChatFormatting.GRAY),
        WARN(Level.WARN, " ⚠ ", ChatFormatting.GOLD),
        ERROR(Level.ERROR, " ❌ ", ChatFormatting.RED);

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

    private MutableComponent relayIcon() {
        return Component.literal("[")
            .append(Component.literal("@")
                .setStyle(ClientCore.getIconStyle()))
            .append(Component.literal("]"))
            .setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)
                .withHoverEvent(new HoverEvent.ShowText(
                    Component.literal("Sent by Player Relay")
                )));
    }

    private MutableComponent formatTitle(Component title, LevelFormat format) {
        return Component.empty()
            .append(relayIcon())
            .append(Component.literal(format.icon).append(title)
                .setStyle(Style.EMPTY.withColor(format.color).withBold(true)));
    }

    private MutableComponent formatMessage(HudLogEvent event) {
        LevelFormat format = LevelFormat.fromLevel(event.getLevel());
        MutableComponent msg = Component.empty();

        Component title = event.getTitleComponent();
        Component message = event.getMessageComponent();
        if (message == null && event.getCause() != null) {
            message = Component.literal(event.getCause().getLocalizedMessage());
        }

        if (title != null && message != null) {
            title = title.copy().append(Component.literal(":\n"));
        }

        msg.append(formatTitle(title != null ? title : Component.empty(), format));
        if (message != null) {
            msg.append(message.copy()
                .withStyle(format.color));
        }

        Component description = event.getDescriptionComponent();
        if (description != null) {
            msg.withStyle(style -> style.withHoverEvent(
                new HoverEvent.ShowText(description)
            ));
        }

        return msg;
    }
}
