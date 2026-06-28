package dev.hintsystem.playerrelay.logging.events;

import dev.hintsystem.playerrelay.logging.Logger;

import org.slf4j.event.Level;

public abstract class AbstractLogEvent {
    protected final Level level;
    protected final Throwable cause;
    protected final long timestamp;

    protected AbstractLogEvent(AbstractBuilder<?, ?> builder, Level level) {
        this.level = level;
        this.cause = builder.cause;
        this.timestamp = System.currentTimeMillis();
    }

    public Level getLevel() { return level; }
    public abstract String getTitle();
    public abstract String getMessage();
    public Throwable getCause() { return cause; }
    public long getTimestamp() { return timestamp; }

    public static abstract class AbstractBuilder<
        B extends AbstractBuilder<B, E>,
        E extends AbstractLogEvent>
    {
        protected Logger logger;
        protected Level level = Level.DEBUG;
        protected Throwable cause;

        protected abstract B self();

        public B logger(Logger logger) {
            this.logger = logger;
            return self();
        }

        public B cause(Throwable cause) {
            this.cause = cause;
            return self();
        }

        /** Builds the event without logging it */
        public abstract E build();

        /** Builds the event, logs it, and returns the built event */
        public E log() {
            E event = build();

            if (logger != null) logger.log(event);
            return event;
        }
    }

    public interface LevelCapableBuilder<E extends AbstractLogEvent> {
        E build();
        E log();

        void setLevel(Level level);

        default E build(Level level) {
            setLevel(level);
            return build();
        }

        /** Builds the event, logs it, and returns the built event */
        default E log(Level level) {
            setLevel(level);
            return log();
        }

        /**
         * Equivalent to {@code log(Level.DEBUG)}
         * @see #log
         */
        default E debug() { return log(Level.DEBUG); }
        /**
         * Equivalent to {@code log(Level.INFO)}
         * @see #log
         */
        default E info() { return log(Level.INFO); }
        /**
         * Equivalent to {@code log(Level.WARN)}
         * @see #log
         */
        default E warn() { return log(Level.WARN); }
        /**
         * Equivalent to {@code log(Level.ERROR)}
         * @see #log
         */
        default E error() { return log(Level.ERROR); }
    }
}
