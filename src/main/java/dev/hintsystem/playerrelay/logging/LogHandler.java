package dev.hintsystem.playerrelay.logging;

import org.slf4j.event.Level;

public interface LogHandler {
    void handle(LogEvent event);
    boolean isEnabled(Level level);
}
