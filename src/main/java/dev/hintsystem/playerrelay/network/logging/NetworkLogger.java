package dev.hintsystem.playerrelay.network.logging;

import dev.hintsystem.playerrelay.logging.LogPipeline;
import dev.hintsystem.playerrelay.logging.Logger;
import dev.hintsystem.playerrelay.logging.events.AbstractLogEvent;
import dev.hintsystem.playerrelay.network.logging.events.NetworkLogEvent;

public class NetworkLogger implements Logger {
    private final LogPipeline pipeline;
    private final LogEventLocation location;

    public NetworkLogger(LogPipeline pipeline) { this(pipeline, LogEventLocation.UNDEFINED); }

    NetworkLogger(LogPipeline pipeline, LogEventLocation location) {
        this.pipeline = pipeline;
        this.location = location;
    }

    public NetworkLogger withLocation(LogEventLocation location) {
        return new NetworkLogger(pipeline, location);
    }

    public NetworkLogEvent.Builder builder() {
        return builder(new NetworkLogEvent.Builder());
    }

    /** Configures a log event builder with the properties required for logging */
    public <B extends NetworkLogEvent.AbstractBuilder<B, ?>>
    B builder(B builder) {
        return builder
            .logger(pipeline)
            .location(location);
    }

    /** Applies this logger's configuration to the builder, builds the event, and logs it. */
    public <E extends NetworkLogEvent, B extends NetworkLogEvent.AbstractBuilder<B, E>>
    E log(B builder) {
        E event = this.builder(builder)
            .build();

        log(event);
        return event;
    }

    @Override
    public void log(AbstractLogEvent event) { pipeline.log(event); }
}
