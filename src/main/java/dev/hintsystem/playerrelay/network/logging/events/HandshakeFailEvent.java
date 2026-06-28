package dev.hintsystem.playerrelay.network.logging.events;

import dev.hintsystem.playerrelay.network.logging.LogEventTypes;
import dev.hintsystem.playerrelay.payload.RelayVersionPayload;

import org.jetbrains.annotations.Nullable;
import org.slf4j.event.Level;

public class HandshakeFailEvent extends NetworkLogEvent {
    public static final String BAD_VERSION_TITLE = "Incorrect handshake network version";

    public final FailVariant variant;
    public final RelayVersionPayload handshake;

    public enum FailVariant {
        BAD_VERSION,
        TIMEOUT
    }

    private HandshakeFailEvent(Builder builder) {
        super(builder, Level.ERROR, LogEventTypes.HANDSHAKE_FAIL);
        this.variant = builder.variant;
        this.handshake = builder.handshake;
    }

    public static IllegalStateException exception() {
        return new IllegalStateException(BAD_VERSION_TITLE);
    }

    public static class Builder
        extends NetworkLogEvent.AbstractBuilder<Builder, HandshakeFailEvent>
    {
        private final FailVariant variant;
        @Nullable private final RelayVersionPayload handshake;

        private Builder(FailVariant variant, @Nullable RelayVersionPayload handshake) {
            this.variant = variant;
            this.handshake = handshake;
        }

        public static Builder badVersion(RelayVersionPayload handshake) {
            return new Builder(FailVariant.BAD_VERSION, handshake)
                .title(BAD_VERSION_TITLE);
        }

        public static Builder timeout() {
            return new Builder(FailVariant.TIMEOUT, null)
                .title("Handshake timeout");
        }

        @Override protected Builder self() { return this; }
        @Override public HandshakeFailEvent build() { return new HandshakeFailEvent(this); }
    }
}
