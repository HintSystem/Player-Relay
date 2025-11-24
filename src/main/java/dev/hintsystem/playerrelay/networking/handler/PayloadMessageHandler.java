package dev.hintsystem.playerrelay.networking.handler;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.networking.PayloadMessage;
import dev.hintsystem.playerrelay.networking.TrackedPlayerList;
import dev.hintsystem.playerrelay.payload.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public abstract class PayloadMessageHandler<C> {
    private final Map<Class<? extends IPayload>, BiConsumer<? extends IPayload, C>> payloadHandlers = new HashMap<>();

    public void handleMessage(@NotNull PayloadMessage message, C context) {
        if (!handlePayload(message.getPayload(), context)) {
            onMessagePass(message, context);
        }
    }

    /** @return true if payload was processed by a payload handler */
    protected boolean handlePayload(@NotNull IPayload payload, C context) {
        @SuppressWarnings("unchecked")
        BiConsumer<IPayload, C> payloadHandler = (BiConsumer<IPayload, C>) payloadHandlers.get(payload.getClass());

        if (payloadHandler != null) {
            payloadHandler.accept(payload, context);
            return true;
        }
        return false;
    }

    /**
     * Initialize handlers. Called in constructor.
     * Should register all payload handlers using {@link #register(Class, BiConsumer)}
     */
    protected abstract void init();

    /**
     * Called when a message is not handled by any payload handler.
     * Override to implement custom handling of unhandled messages.
     */
    protected void onMessagePass(PayloadMessage message, C context) {}

    /** Updates the player list by adding or merging a new player info payload */
    protected void addPlayerInfo(TrackedPlayerList.Sublist playerList, PlayerInfoPayload playerInfo) {
        if (playerInfo.playerId.equals(ClientCore.getClientUuid())) return;

        PlayerInfoPayload existingPlayerInfo = playerList.putIfAbsent(playerInfo.playerId, playerInfo);
        if (existingPlayerInfo != null) existingPlayerInfo.merge(playerInfo);
    }

    /**
     * Registers a handler for a specific payload type.
     *
     * @throws IllegalStateException if a handler is already registered for this payload type
     * @throws IllegalArgumentException if the payload class is not registered in PayloadRegistry
     */
    public <T extends IPayload> void register(@NotNull Class<T> payloadClass, @NotNull BiConsumer<T, C> payloadHandler) {
        PayloadRegistry.getByClass(payloadClass);
        if (payloadHandlers.containsKey(payloadClass)) {
            throw new IllegalStateException("Handler already registered for " + payloadClass.getName());
        }

        payloadHandlers.put(payloadClass, payloadHandler);
    }

    /**
     * Unregisters a handler for a specific payload type.
     *
     * @return true if a handler was removed, false if none was registered
     */
    public boolean unregister(Class<? extends IPayload> payloadClass) {
        return payloadHandlers.remove(payloadClass) != null;
    }

    public void clearAllHandlers() { payloadHandlers.clear(); }
}
