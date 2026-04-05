package dev.hintsystem.playerrelay.payload;

import net.minecraft.network.RegistryByteBuf;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class PayloadRegistry {
    private static final Map<Byte, PayloadType<?>> BY_ID = new HashMap<>();
    private static byte nextId = 0;

    public static final PayloadType<RelayVersionPayload> RELAY_VERSION = registerPeer(RelayVersionPayload::new);
    public static final PayloadType<UdpHandshakePayload> UDP_HANDSHAKE = registerPeer(UdpHandshakePayload::new);
    public static final PayloadType<UdpPingPayload> UDP_PING = registerPeer(UdpPingPayload::new);

    public static final PayloadType<PlayerInfoPayload> PLAYER_INFO = register(PlayerInfoPayload::new);
    public static final PayloadType<PlayerInventoryPayload> PLAYER_INVENTORY = register(PlayerInventoryPayload::new);
    public static final PayloadType<PlayerDisconnectPayload> PLAYER_DISCONNECT = register(PlayerDisconnectPayload::new);
    public static final PayloadType<PartyPayload> PARTY = register(PartyPayload::new);
    public static final PayloadType<WaypointPayload> WAYPOINT = register(WaypointPayload::new);
    public static final PayloadType<GenericPacketPayload> GENERIC_PACKET = register(GenericPacketPayload::new);

    public static class PayloadType<T extends Payload> {
        private final byte id;
        private final Function<RegistryByteBuf, T> factory;
        private final boolean shouldForward;

        private PayloadType(byte id, Function<RegistryByteBuf, T> factory, boolean shouldForward) {
            this.id = id;
            this.factory = factory;
            this.shouldForward = shouldForward;
        }

        public byte getId() { return id; }
        public boolean shouldForward() { return shouldForward; }

        public T createPayload(RegistryByteBuf buf) {
            return factory.apply(buf);
        }

        @Override
        public String toString() {
            return "(PayloadType, id=" + id + ")";
        }
    }

    public static <T extends Payload> PayloadType<T> register(Function<RegistryByteBuf, T> factory) {
        PayloadType<T> type = new PayloadType<>(nextId++, factory, true);

        registerInternal(type);
        return type;
    }

    /** Registers a payload type meant only for communication between 2 peers **/
    public static <T extends Payload> PayloadType<T> registerPeer(Function<RegistryByteBuf, T> factory) {
        PayloadType<T> type = new PayloadType<>(nextId++, factory, false);

        registerInternal(type);
        return type;
    }

    private static void registerInternal(PayloadType<?> payloadType) {
        BY_ID.put(payloadType.getId(), payloadType);
    }

    public static PayloadType<?> getById(byte id) {
        PayloadType<?> type = BY_ID.get(id);
        if (type == null) {
            throw new IllegalArgumentException("Unknown payload type ID: " + id);
        }
        return type;
    }

    public static Collection<PayloadType<?>> getAllTypes() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    public static int size() { return BY_ID.size(); }
}
