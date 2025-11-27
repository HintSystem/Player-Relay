package dev.hintsystem.playerrelay.networking.handler;

import dev.hintsystem.playerrelay.networking.PayloadMessage;
import org.jetbrains.annotations.NotNull;

public interface MessageHandler<C> {
    void handleMessage(@NotNull PayloadMessage message, C context);
}
