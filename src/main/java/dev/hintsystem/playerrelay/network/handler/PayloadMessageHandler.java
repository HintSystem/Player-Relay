package dev.hintsystem.playerrelay.network.handler;

import dev.hintsystem.playerrelay.network.PayloadMessage;
import dev.hintsystem.playerrelay.payload.*;

import org.jetbrains.annotations.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public abstract class PayloadMessageHandler<C> implements MessageHandler<C> {
    private final Map<Byte, BiConsumer<? extends Payload, C>> payloadHandlers = new HashMap<>();

    public void handleMessage(@NotNull PayloadMessage message, C context) {
        if (!handlePayload(message.getPayload(), context)) {
            onMessagePass(message, context);
        }
    }

    /** @return true if payload was processed by a payload handler */
    protected boolean handlePayload(@NotNull Payload payload, C context) {
        @SuppressWarnings("unchecked")
        BiConsumer<Payload, C> payloadHandler = (BiConsumer<Payload, C>) payloadHandlers.get(payload.getPayloadType().getId());

        if (payloadHandler != null) {
            payloadHandler.accept(payload, context);
            return true;
        }
        return false;
    }

    /**
     * Initialize handlers. Called in constructor.
     * Should register all payload handlers using {@link #register(PayloadRegistry.PayloadType, BiConsumer)}
     */
    protected abstract void init();

    /**
     * Called when a message is not handled by any payload handler.
     * Override to implement custom handling of unhandled messages.
     */
    protected void onMessagePass(PayloadMessage message, C context) {}

    /**
     * Registers a handler for a specific payload type.
     *
     * @throws IllegalStateException if a handler is already registered for this payload type
     * @throws IllegalArgumentException if the payload class is not registered in PayloadRegistry
     */
    public <T extends Payload> void register(@NotNull PayloadRegistry.PayloadType<T> payloadType, @NotNull BiConsumer<T, C> payloadHandler) {
        if (payloadHandlers.containsKey(payloadType.getId())) {
            throw new IllegalStateException("Handler already registered for " + payloadType);
        }

        payloadHandlers.put(payloadType.getId(), payloadHandler);
    }

    /**
     * Unregisters a handler for a specific payload type.
     *
     * @return true if a handler was removed, false if none was registered
     */
    public boolean unregister(PayloadRegistry.PayloadType<? extends Payload> payloadType) {
        return payloadHandlers.remove(payloadType.getId()) != null;
    }

    public void clearAllHandlers() { payloadHandlers.clear(); }
}
