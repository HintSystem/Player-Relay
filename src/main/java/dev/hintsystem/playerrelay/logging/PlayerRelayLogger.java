package dev.hintsystem.playerrelay.logging;

import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;

public class PlayerRelayLogger {
    private final List<LogHandler> logHandlers;

    private final LogLocation location;

    public PlayerRelayLogger() { this(LogLocation.UNDEFINED); }

    public PlayerRelayLogger(LogLocation location) { this(location, new ArrayList<>()); }

    public PlayerRelayLogger(LogLocation location, List<LogHandler> logHandlers) {
        this.logHandlers = logHandlers;
        this.location = location;
    }

    public PlayerRelayLogger withLocation(LogLocation location) {
        return new PlayerRelayLogger(location, this.logHandlers);
    }

    public PlayerRelayLogger addLogHandler(LogHandler handler) {
        synchronized (logHandlers) { logHandlers.add(handler); }
        return this;
    }

    public PlayerRelayLogger removeLogHandler(LogHandler handler) {
        synchronized (logHandlers) { logHandlers.remove(handler); }
        return this;
    }

    public void log(LogEvent event) {
        for (LogHandler handler : logHandlers) {
            if (!handler.isEnabled(event.getLevel())) continue;

            try {
                handler.handle(event);
            } catch (Exception e) {
                System.out.print("Log handler failed " + e);
            }
        }
    }

    public static class LoggingBuilder extends LogEvent.Builder {
        private final PlayerRelayLogger logger;

        public LoggingBuilder(PlayerRelayLogger logger, Level level) {
            super(level, logger.location);
            this.logger = logger;
        }

        @Override
        public LogEvent build() {
            LogEvent event = super.build();
            logger.log(event);
            return event;
        }
    }

    public LoggingBuilder debug() { return new LoggingBuilder(this, Level.DEBUG); }
    public LoggingBuilder info() { return new LoggingBuilder(this, Level.INFO); }
    public LoggingBuilder warn() { return new LoggingBuilder(this, Level.WARN); }
    public LoggingBuilder error() { return new LoggingBuilder(this, Level.ERROR); }
}
