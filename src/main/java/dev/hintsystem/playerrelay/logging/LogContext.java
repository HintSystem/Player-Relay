package dev.hintsystem.playerrelay.logging;

import dev.hintsystem.playerrelay.logging.events.AbstractLogEvent;
import dev.hintsystem.playerrelay.logging.handler.LogHandler;
import dev.hintsystem.playerrelay.logging.handler.LogMiddleware;

import java.util.Iterator;
import java.util.List;

public final class LogContext {
    private final Iterator<LogMiddleware> iterator;
    private final List<LogHandler> handlers;

    public LogContext(Iterator<LogMiddleware> middlewareIterator, List<LogHandler> handlers) {
        this.iterator = middlewareIterator;
        this.handlers = handlers;
    }

    /** Calling this will continue the handler chain */
    public void next(AbstractLogEvent event) {
        while (iterator.hasNext()) {
            LogMiddleware middleware = iterator.next();

            if (!middleware.accepts(event)) {
                continue;
            }

            try {
                middleware.handle(this, event);
            } catch (Exception e) {
                StringBuilder stackTrace = new StringBuilder();
                for (StackTraceElement trace : e.getStackTrace()) {
                    stackTrace.append(trace);
                }

                System.out.println("Log middleware (" + middleware + ") failed:\n" + stackTrace);
            }

            return;
        }

        dispatch(event);
    }

    private void dispatch(AbstractLogEvent event) {
        for (LogHandler handler : handlers) {
            try {
                handler.handle(event);
            } catch (Exception e) {
                System.out.println("Log handler (" + handler + ") failed:\n" + e);
            }
        }
    }
}
