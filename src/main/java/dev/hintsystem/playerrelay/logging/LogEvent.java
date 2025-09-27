package dev.hintsystem.playerrelay.logging;

import org.slf4j.event.Level;
import org.slf4j.helpers.MessageFormatter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class LogEvent {
    private final Level level;
    private final LogLocation location;
    private final LogEventTypes type;
    private final String title;
    private final String message;
    private final String description;
    private final Throwable exception;
    private final long timestamp;
    private final Thread thread;
    private final Map<String, Object> context;

    private LogEvent(Builder builder) {
        this.level = builder.level;
        this.location = builder.location;
        this.type = builder.type;
        this.title = builder.title;
        this.message = builder.message;
        this.description = builder.description;
        this.exception = builder.exception;
        this.timestamp = System.currentTimeMillis();
        this.thread = Thread.currentThread();
        this.context = new HashMap<>(builder.context);
    }

    // Getters
    public Level getLevel() { return level; }
    public LogLocation getLocation() { return location; }
    public LogEventTypes getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getDescription() { return description; }
    public Throwable getException() { return exception; }
    public long getTimestamp() { return timestamp; }
    public Thread getThread() { return thread; }
    public Map<String, Object> getContext() { return Collections.unmodifiableMap(context); }

    public static class Builder {
        private final Level level;
        private final LogLocation location;
        private LogEventTypes type;
        private String title;
        private String message;
        private String description;
        private Throwable exception;
        private final Map<String, Object> context = new HashMap<>();

        public Builder(Level level, LogLocation location) {
            this.level = level;
            this.location = location;
        }

        public Builder type(LogEventTypes type) {
            this.type = type;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder title(String format, Object ... arguments) {
            this.title = MessageFormatter.arrayFormat(format, arguments).getMessage();
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder message(String format, Object... arguments) {
            this.message = MessageFormatter.arrayFormat(format, arguments).getMessage();
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder description(String format, Object ... arguments) {
            this.description = MessageFormatter.arrayFormat(format, arguments).getMessage();
            return this;
        }

        public Builder exception(Throwable exception) {
            this.exception = exception;
            return this;
        }

        public Builder context(String key, Object value) {
            this.context.put(key, value);
            return this;
        }

        public Builder context(Map<String, Object> context) {
            this.context.putAll(context);
            return this;
        }

        public LogEvent build() { return new LogEvent(this); }
    }
}
