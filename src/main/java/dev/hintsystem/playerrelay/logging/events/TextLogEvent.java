package dev.hintsystem.playerrelay.logging.events;

import org.slf4j.event.Level;
import org.slf4j.helpers.MessageFormatter;

public class TextLogEvent extends AbstractLogEvent {
    protected final String title;
    protected final String message;

    protected TextLogEvent(AbstractBuilder<?, ?> builder, Level level) {
        super(builder, level);
        this.title = builder.title;
        this.message = builder.message;
    }

    @Override public String getTitle() { return title; }
    @Override public String getMessage() { return message; }

    public static class Builder
        extends AbstractBuilder<Builder, TextLogEvent>
        implements LevelCapableBuilder<TextLogEvent>
    {
        @Override public void setLevel(Level level) {this.level = level; }

        @Override protected Builder self() { return this; }
        @Override public TextLogEvent build() { return new TextLogEvent(this, level); }
    }

    public static abstract class AbstractBuilder<
        B extends AbstractBuilder<B, E>,
        E extends TextLogEvent>
        extends AbstractLogEvent.AbstractBuilder<B, E>
    {
        protected String title;
        protected String message;

        public B title(String title) {
            this.title = title;
            return self();
        }

        /** @see MessageFormatter */
        public B title(String title, Object... args) {
            return title(
                MessageFormatter.arrayFormat(title, args).getMessage()
            );
        }

        public B message(String message) {
            this.message = message;
            return self();
        }

        /** @see MessageFormatter */
        public B message(String message, Object... args) {
            return message(
                MessageFormatter.arrayFormat(message, args).getMessage()
            );
        }
    }
}
