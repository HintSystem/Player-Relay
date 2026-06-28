package dev.hintsystem.playerrelay.logging.handler;

import dev.hintsystem.playerrelay.logging.LogContext;
import dev.hintsystem.playerrelay.logging.events.AbstractLogEvent;

public interface LogMiddleware {
    boolean accepts(AbstractLogEvent event);
    void handle(LogContext ctx, AbstractLogEvent event);
}
