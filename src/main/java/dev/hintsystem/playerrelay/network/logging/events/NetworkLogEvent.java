package dev.hintsystem.playerrelay.network.logging.events;

import dev.hintsystem.playerrelay.logging.events.TextLogEvent;

import dev.hintsystem.playerrelay.network.logging.LogEventLocation;
import dev.hintsystem.playerrelay.network.logging.LogEventTypes;

import org.slf4j.event.Level;

public class NetworkLogEvent extends TextLogEvent {
    protected final LogEventLocation location;
    protected final LogEventTypes type;

    protected NetworkLogEvent(AbstractBuilder<?, ?> builder, Level level, LogEventTypes type) {
        super(builder, level);

        this.location = builder.location != null ? builder.location : LogEventLocation.UNDEFINED;
        this.type = type;
    }

    public LogEventLocation getLocation() { return location; }
    public LogEventTypes getType() { return type; }

    public static class Builder
        extends AbstractBuilder<Builder, NetworkLogEvent>
        implements LevelCapableBuilder<NetworkLogEvent>
    {
        protected LogEventTypes type;

        public Builder type(LogEventTypes type) {
            this.type = type;
            return self();
        }

        @Override public void setLevel(Level level) { this.level = level; }

        @Override protected Builder self() { return this; }
        @Override public NetworkLogEvent build() { return new NetworkLogEvent(this, level, type); }
    }

    public static abstract class AbstractBuilder<
        B extends AbstractBuilder<B, E>,
        E extends NetworkLogEvent>
        extends TextLogEvent.AbstractBuilder<B, E>
    {
        protected LogEventLocation location;

        public B location(LogEventLocation location) {
            this.location = location;
            return self();
        }
    }
}
