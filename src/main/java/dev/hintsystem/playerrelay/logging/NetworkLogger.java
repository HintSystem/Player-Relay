package dev.hintsystem.playerrelay.logging;

import dev.hintsystem.playerrelay.payload.RelayVersionPayload;

import org.slf4j.event.Level;
import java.util.ArrayList;
import java.util.List;

public class NetworkLogger {
    private final List<LogHandler> logHandlers;

    private final LogLocation location;

    public NetworkLogger() { this(LogLocation.UNDEFINED); }

    public NetworkLogger(LogLocation location) { this(location, new ArrayList<>()); }

    public NetworkLogger(LogLocation location, List<LogHandler> logHandlers) {
        this.logHandlers = logHandlers;
        this.location = location;
    }

    public NetworkLogger withLocation(LogLocation location) {
        return new NetworkLogger(location, this.logHandlers);
    }

    public NetworkLogger addLogHandler(LogHandler handler) {
        synchronized (logHandlers) { logHandlers.add(handler); }
        return this;
    }

    public NetworkLogger removeLogHandler(LogHandler handler) {
        synchronized (logHandlers) { logHandlers.remove(handler); }
        return this;
    }

    public LoggingBuilder versionMismatch(RelayVersionPayload versionPayload) {
        return (LoggingBuilder) error().type(LogEventTypes.VERSION_FAIL)
            .title("Network version mismatch")
            .message("relay={}, client={}", versionPayload.networkVersion, RelayVersionPayload.NETWORK_VERSION)
            .context("version", versionPayload);
    }

    public void debug(String message) { new LoggingBuilder(this, Level.DEBUG).message(message).build(); }
    public void info(String message) { new LoggingBuilder(this, Level.INFO).message(message).build(); }
    public void warn(String message) { new LoggingBuilder(this, Level.WARN).message(message).build(); }
    public void error(String message) { new LoggingBuilder(this, Level.ERROR).message(message).build(); }

    public LoggingBuilder debug() { return new LoggingBuilder(this, Level.DEBUG); }
    public LoggingBuilder info() { return new LoggingBuilder(this, Level.INFO); }
    public LoggingBuilder warn() { return new LoggingBuilder(this, Level.WARN); }
    public LoggingBuilder error() { return new LoggingBuilder(this, Level.ERROR); }

    public static class LoggingBuilder extends LogEvent.Builder {
        private final NetworkLogger logger;

        public LoggingBuilder(NetworkLogger logger, Level level) {
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

    private void log(LogEvent event) {
        for (LogHandler handler : logHandlers) {
            if (!handler.isEnabled(event.getLevel())) continue;

            try {
                handler.handle(event);
            } catch (Exception e) {
                System.out.print("Log handler failed " + e);
            }
        }
    }
}
