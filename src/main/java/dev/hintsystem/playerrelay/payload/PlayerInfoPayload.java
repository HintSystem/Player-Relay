package dev.hintsystem.playerrelay.payload;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.networking.message.P2PMessageType;

import com.mojang.authlib.GameProfile;
import dev.hintsystem.playerrelay.payload.player.*;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.PacketByteBuf;

import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.function.Supplier;

public class PlayerInfoPayload implements IPayload {
    private static final byte FLAG_NEW_CONNECTION = 1 << 0;
    private static final int RESERVED_FLAGS = 1;

    private static final LinkedHashMap<Class<? extends PlayerDataComponent>, ComponentInfo<? extends PlayerDataComponent>>
        COMPONENT_REGISTRY = new LinkedHashMap<>();

    static {
        registerComponent(PlayerBasicData.class, PlayerBasicData::new);
        registerComponent(PlayerWorldData.class, PlayerWorldData::new);
        registerComponent(PlayerPositionData.class, PlayerPositionData::new);
        registerComponent(PlayerStatsData.class, PlayerStatsData::new);
        registerComponent(PlayerStatusEffectsData.class, PlayerStatusEffectsData::new);
    }

    private record ComponentInfo<T extends PlayerDataComponent>(
        Class<T> componentClass,
        Supplier<T> constructor,
        byte flag
    ) {}

    private static <T extends PlayerDataComponent> void registerComponent(Class<T> componentClass, Supplier<T> constructor) {
        int nextFlagPosition = COMPONENT_REGISTRY.size() + RESERVED_FLAGS;
        if (nextFlagPosition >= 8) throw new IllegalStateException("Too many components registered (max 8 for byte flags)");

        byte flag = (byte) (1 << nextFlagPosition);
        ComponentInfo<T> info = new ComponentInfo<>(componentClass, constructor, flag);

        COMPONENT_REGISTRY.put(componentClass, info);
    }

    
    public final UUID playerId;
    private byte flags = 0;

    private final Map<Byte, PlayerDataComponent> components = new HashMap<>();

    public PlayerInfoPayload(UUID playerId) {
        this.playerId = playerId;
    }

    public PlayerInfoPayload(PacketByteBuf buf) {
        int idx = buf.readerIndex();
        this.playerId = buf.readUuid();
        buf.readerIndex(idx);

        read(buf);
    }

    @Override
    public P2PMessageType getMessageType() { return P2PMessageType.PLAYER_INFO; }

    @SuppressWarnings("unchecked")
    private <T extends PlayerDataComponent> ComponentInfo<T> getComponentInfo(Class<T> componentClass) {
        ComponentInfo<?> info = COMPONENT_REGISTRY.get(componentClass);
        if (info == null) throw new IllegalArgumentException("Unknown component class: " + componentClass);

        return (ComponentInfo<T>) info;
    }

    @Nullable
    public <T extends PlayerDataComponent> T getComponent(Class<T> componentClass) {
        PlayerDataComponent c = components.get(getComponentInfo(componentClass).flag);
        return componentClass.cast(c);
    }

    public <T extends PlayerDataComponent> T getComponentOrEmpty(Class<T> componentClass) {
        T component = getComponent(componentClass);
        return (component != null) ? component : getComponentInfo(componentClass).constructor.get();
    }

    public <T extends PlayerDataComponent> PlayerInfoPayload setComponent(T component) {
        ComponentInfo<?> info = COMPONENT_REGISTRY.get(component.getClass());
        if (info == null) throw new IllegalArgumentException("Unknown component class: " + component.getClass());

        components.put(info.flag, component);
        this.flags |= info.flag;
        return this;
    }

    public <T extends PlayerDataComponent> boolean hasComponentChanged(T newComponent) {
        PlayerDataComponent currentData = getComponent(newComponent.getClass());
        return (currentData == null) || newComponent.hasChanged(currentData);
    }

    public PlayerListEntry toPlayerListEntry() { return new PlayerListEntry(toGameProfile(), false); }
    public GameProfile toGameProfile() { return new GameProfile(this.playerId, getName()); }

    public PlayerInfoPayload setName(String name) {
        return setComponent(new PlayerBasicData(name));
    }

    public PlayerInfoPayload setNewConnectionFlag(boolean isNewConnection) {
        if (isNewConnection) {
            this.flags |= FLAG_NEW_CONNECTION;
        } else {
            this.flags &= ~FLAG_NEW_CONNECTION;
        }

        return this;
    }

    public String getName() {
        PlayerBasicData basicData = getComponent(PlayerBasicData.class);
        return (basicData == null) ? this.playerId.toString() : basicData.name;
    }

    public boolean hasAnyInfo() { return flags != 0; }
    public boolean hasNewConnectionFlag() { return (flags & FLAG_NEW_CONNECTION) != 0; }

    public void merge(PlayerInfoPayload other) {
        for (Map.Entry<Byte, PlayerDataComponent> entry : other.components.entrySet()) {
            this.flags |= entry.getKey();
            this.components.put(entry.getKey(), entry.getValue().copy());
        }
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeUuid(playerId);
        buf.writeByte(flags);

        for (ComponentInfo<?> info : COMPONENT_REGISTRY.values()) {
            if ((flags & info.flag) != 0) {
                PlayerDataComponent component = components.get(info.flag);
                if (component != null) component.write(buf);
            }
        }
    }

    public void read(PacketByteBuf buf) {
        buf.readUuid(); // playerId already read in constructor
        this.flags = buf.readByte();

        for (ComponentInfo<?> info : COMPONENT_REGISTRY.values()) {
            if ((flags & info.flag) != 0) {
                PlayerDataComponent component = info.constructor.get();
                component.read(buf);
                components.put(info.flag, component);
            }
        }
    }

    public static Set<Class<? extends PlayerDataComponent>> getRegisteredComponents() {
        return Collections.unmodifiableSet(COMPONENT_REGISTRY.keySet());
    }
}
