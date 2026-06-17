package dev.hintsystem.playerrelay.command;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
            sendError(Text.literal(e.getMessage()));
            return 0;
        }
    }

    public static void sendFeedback(Text message) {
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(message);
    }

    public static void sendError(Text message) {
        sendFeedback(
            Text.empty().append(message).formatted(Formatting.RED)
        );
    }
}
