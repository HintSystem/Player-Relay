package dev.hintsystem.playerrelay.logging.handler;

import dev.hintsystem.playerrelay.logging.LogContext;
import dev.hintsystem.playerrelay.logging.events.AbstractLogEvent;

public abstract class TypedLogMiddleware<E extends AbstractLogEvent> implements LogMiddleware {
    private final Class<E> inputType;

    public TypedLogMiddleware(Class<E> inputType) {
        this.inputType = inputType;
    }

    public final Class<E> inputType() { return inputType; }

    @Override
    public final boolean accepts(AbstractLogEvent event) {
        return inputType.isInstance(event) && acceptsTyped(inputType.cast(event));
    }

    protected boolean acceptsTyped(E event) { return true; }

    @Override
    public final void handle(LogContext ctx, AbstractLogEvent event) {
        handleTyped(ctx, inputType.cast(event));
    }

    protected abstract void handleTyped(LogContext ctx, E event);
}
