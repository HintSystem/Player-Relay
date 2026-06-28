package dev.hintsystem.playerrelay.logging.handler;

import dev.hintsystem.playerrelay.logging.events.AbstractLogEvent;

public interface LogHandler {
    void handle(AbstractLogEvent event);
}
