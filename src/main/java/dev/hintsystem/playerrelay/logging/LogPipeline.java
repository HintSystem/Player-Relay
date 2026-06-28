package dev.hintsystem.playerrelay.logging;

import dev.hintsystem.playerrelay.logging.events.AbstractLogEvent;
import dev.hintsystem.playerrelay.logging.handler.LogHandler;
import dev.hintsystem.playerrelay.logging.handler.LogMiddleware;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LogPipeline implements Logger {
    private final List<LogMiddleware> middlewares = new CopyOnWriteArrayList<>();
    private final List<LogHandler> handlers = new CopyOnWriteArrayList<>();

    /** Adds middleware that runs <i>before</i> existing middleware */
    public LogPipeline useFirst(LogMiddleware middleware) {
        middlewares.addFirst(middleware);
        return this;
    }

    /** Adds middleware that runs <i>after</i> existing middleware */
    public LogPipeline useLast(LogMiddleware middleware) {
        middlewares.addLast(middleware);
        return this;
    }

    public LogPipeline addHandler(LogHandler handler) {
        handlers.add(handler);
        return this;
    }

    public void log(AbstractLogEvent event) {
        LogContext ctx = new LogContext(middlewares.iterator(), handlers);
        ctx.next(event);
    }
}
