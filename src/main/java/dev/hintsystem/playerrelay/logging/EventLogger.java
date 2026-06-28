package dev.hintsystem.playerrelay.logging;

import dev.hintsystem.playerrelay.logging.events.AbstractLogEvent;
import dev.hintsystem.playerrelay.logging.events.HudLogEvent;
import dev.hintsystem.playerrelay.logging.events.TextLogEvent;

import org.slf4j.event.Level;

public class EventLogger implements Logger {
    private final LogPipeline pipeline;

    public EventLogger(LogPipeline pipeline) {
        this.pipeline = pipeline;
    }

    public void debug(String message) { textLog().message(message).log(Level.DEBUG); }
    public void info(String message) { textLog().message(message).log(Level.INFO); }
    public void warn(String message) { textLog().message(message).log(Level.WARN); }
    public void error(String message) { textLog().message(message).log(Level.ERROR); }

    private TextLogEvent.Builder textLog() { return new TextLogEvent.Builder().logger(pipeline); }

    public HudLogEvent.Builder builder() {
        return builder(new HudLogEvent.Builder());
    }

    /** Configures a log event builder with the properties required for logging */
    public <B extends AbstractLogEvent.AbstractBuilder<B, ?>>
    B builder(B builder) {
        return builder
            .logger(this);
    }

    @Override
    public void log(AbstractLogEvent event) { pipeline.log(event); }
}
