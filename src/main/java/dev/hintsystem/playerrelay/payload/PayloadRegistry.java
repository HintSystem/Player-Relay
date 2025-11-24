package dev.hintsystem.playerrelay.payload;

import net.minecraft.network.RegistryByteBuf;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class PayloadRegistry {
    private static final Map<Byte, PayloadType<?>> BY_ID = new HashMap<>();
    private static final Map<Class<? extends IPayload>, PayloadType<?>> BY_CLASS = new HashMap<>();
    private static byte nextId = 0;

    static {
        registerPeer(RelayVersionPayload.class, RelayVersionPayload::new);
        registerPeer(UdpHandshakePayload.class, UdpHandshakePayload::new);
        registerPeer(UdpPingPayload.class, UdpPingPayload::new);
        register(PlayerInfoPayload.class, PlayerInfoPayload::new);
        register(PlayerInventoryPayload.class, PlayerInventoryPayload::new);
        register(PlayerDisconnectPayload.class, PlayerDisconnectPayload::new);
        register(WaypointPayload.class, WaypointPayload::new);
        register(GenericPacketPayload.class, GenericPacketPayload::new);
    }

    public static class PayloadType<T extends IPayload> {
        private final byte id;
        private final Class<T> payloadClass;
        private final Function<RegistryByteBuf, T> factory;
        private final boolean shouldForward;

        private PayloadType(byte id, Class<T> payloadClass,
                            Function<RegistryByteBuf, T> factory, boolean shouldForward) {
            this.id = id;
            this.payloadClass = payloadClass;
            this.factory = factory;
            this.shouldForward = shouldForward;
        }

        public byte getId() { return id; }
        public Class<T> getPayloadClass() { return payloadClass; }
        public boolean shouldForward() { return shouldForward; }

        public T createPayload(RegistryByteBuf buf) {
            return factory.apply(buf);
        }

        @Override
        public String toString() {
            return "(id=" + id + ", class=" + payloadClass.getSimpleName() + ")";
        }
    }

    public static <T extends IPayload> PayloadType<T> register(
        Class<T> payloadClass,
        Function<RegistryByteBuf, T> factory
    ) {
        return register(payloadClass, factory, true);
    }

    public static <T extends IPayload> PayloadType<T> register(
        Class<T> payloadClass,
        Function<RegistryByteBuf, T> factory,
        boolean shouldForward
    ) {
        PayloadType<T> type = new PayloadType<>(nextId++, payloadClass, factory, shouldForward);

        registerInternal(type);
        return type;
    }

    /** Registers a payload type meant only for communication between 2 peers **/
    public static <T extends IPayload> PayloadType<T> registerPeer(
        Class<T> payloadClass,
        Function<RegistryByteBuf, T> factory
    ) {
        PayloadType<T> type = new PayloadType<>(nextId++, payloadClass, factory, false);

        registerInternal(type);
        return type;
    }

    private static void registerInternal(PayloadType<?> payloadType) {
        BY_ID.put(payloadType.id, payloadType);
        BY_CLASS.put(payloadType.payloadClass, payloadType);
    }

    @SuppressWarnings("unchecked")
    public static <T extends IPayload> PayloadType<T> getByClass(Class<T> payloadClass) {
        PayloadType<?> type = BY_CLASS.get(payloadClass);
        if (type == null) {
            throw new IllegalArgumentException("Unregistered payload class: " + payloadClass.getName());
        }
        return (PayloadType<T>) type;
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
