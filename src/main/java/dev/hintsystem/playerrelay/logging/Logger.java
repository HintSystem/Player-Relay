package dev.hintsystem.playerrelay.logging;

import dev.hintsystem.playerrelay.logging.events.AbstractLogEvent;

public interface Logger {
    void log(AbstractLogEvent event);
}
