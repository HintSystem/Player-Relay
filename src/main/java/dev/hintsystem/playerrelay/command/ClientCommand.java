package dev.hintsystem.playerrelay.command;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public abstract class ClientCommand {
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static int tryOrSendError(ThrowingRunnable action) {
        try {
            action.run();
            return 1;
        } catch (Exception e) {
            sendError(Component.literal(e.getMessage()));
            return 0;
        }
    }

    public static void sendFeedback(Component message) {
        Minecraft.getInstance().gui.getChat().addMessage(message);
    }

    public static void sendError(Component message) {
        sendFeedback(
            Component.empty().append(message).withStyle(ChatFormatting.RED)
        );
    }
}
