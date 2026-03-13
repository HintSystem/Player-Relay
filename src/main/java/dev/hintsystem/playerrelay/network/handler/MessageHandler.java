package dev.hintsystem.playerrelay.network.handler;

import dev.hintsystem.playerrelay.network.PayloadMessage;
import org.jetbrains.annotations.NotNull;

public interface MessageHandler<C> {
    void handleMessage(@NotNull PayloadMessage message, C context);
}
