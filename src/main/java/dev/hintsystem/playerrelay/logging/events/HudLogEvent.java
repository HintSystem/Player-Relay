package dev.hintsystem.playerrelay.logging.events;

import net.minecraft.network.chat.Component;

import org.slf4j.event.Level;

public class HudLogEvent extends AbstractLogEvent {
    protected final Component title;
    protected final Component message;
    protected final Component description;

    protected HudLogEvent(AbstractBuilder<?, ?> builder, Level level) {
        super(builder, level);
        this.title = builder.title;
        this.message = builder.message;
        this.description = builder.description;
    }

    @Override public String getTitle() { return title.getString(); }
    @Override public String getMessage() { return message.getString(); }
    public String getDescription() { return description.getString(); }

    public Component getTitleComponent() { return title; }
    public Component getMessageComponent() { return message; }
    public Component getDescriptionComponent() { return description; }

    public static class Builder
        extends AbstractBuilder<Builder, HudLogEvent>
        implements LevelCapableBuilder<HudLogEvent>
    {
        @Override public void setLevel(Level level) { this.level = level; }

        @Override protected Builder self() { return this; }
        @Override public HudLogEvent build() { return new HudLogEvent(this, level); }
    }

    public static abstract class AbstractBuilder<
        B extends AbstractBuilder<B, E>,
        E extends HudLogEvent>
        extends AbstractLogEvent.AbstractBuilder<B, E>
    {
        protected Component title;
        protected Component message;
        protected Component description;

        public B title(Component title) {
            this.title = title;
            return self();
        }

        public B message(Component message) {
            this.message = message;
            return self();
        }

        public B description(Component description) {
            this.description = description;
            return self();
        }
    }
}
