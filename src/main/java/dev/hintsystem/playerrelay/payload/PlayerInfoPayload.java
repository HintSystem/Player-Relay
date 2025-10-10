package dev.hintsystem.playerrelay.payload;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.networking.message.P2PMessageType;
import dev.hintsystem.playerrelay.payload.player.*;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.function.Supplier;

public class PlayerInfoPayload implements IPayload {
    private static final byte FLAG_NEW_CONNECTION = 1 << 0;
    private static final int MAX_FLAGS = 8;
    private static final int RESERVED_FLAGS = 1;

    private static final LinkedHashMap<Class<? extends PlayerDataComponent>, ComponentInfo<? extends PlayerDataComponent>>
        COMPONENT_REGISTRY = new LinkedHashMap<>();

    static {
        registerComponent(PlayerBasicData.class, PlayerBasicData::new);
        registerComponent(PlayerWorldData.class, PlayerWorldData::new);
        registerComponent(PlayerPositionData.class, PlayerPositionData::new);
        registerComponent(PlayerStatsData.class, PlayerStatsData::new);
        registerComponent(PlayerEquipmentData.class, PlayerEquipmentData::new);
        registerComponent(PlayerStatusEffectsData.class, PlayerStatusEffectsData::new);
    }

    private record ComponentInfo<T extends PlayerDataComponent>(
        Class<T> componentClass,
        Supplier<T> constructor,
        byte flag
    ) {}

    private static <T extends PlayerDataComponent> void registerComponent(Class<T> componentClass, Supplier<T> constructor) {
        int nextFlagPosition = COMPONENT_REGISTRY.size() + RESERVED_FLAGS;
        if (nextFlagPosition >= MAX_FLAGS) throw new IllegalStateException("Too many components registered (max " + MAX_FLAGS + " for byte flags)");

        byte flag = (byte) (1 << nextFlagPosition);
        ComponentInfo<T> info = new ComponentInfo<>(componentClass, constructor, flag);

        COMPONENT_REGISTRY.put(componentClass, info);
    }

    
    public final UUID playerId;
    private byte flags = 0;

    private final PlayerDataComponent[] components = new PlayerDataComponent[MAX_FLAGS];

    public PlayerInfoPayload(UUID playerId) {
        this.playerId = playerId;
    }

    public PlayerInfoPayload(RegistryByteBuf buf) {
        int idx = buf.readerIndex();
        this.playerId = buf.readUuid();
        buf.readerIndex(idx);

        read(buf);
    }

    public PlayerListEntry toPlayerListEntry() { return new PlayerListEntry(toGameProfile(), false); }
    public GameProfile toGameProfile() { return new GameProfile(this.playerId, getName()); }

    @Override
    public P2PMessageType getMessageType() { return P2PMessageType.PLAYER_INFO; }

    @SuppressWarnings("unchecked")
    private <T extends PlayerDataComponent> ComponentInfo<T> getComponentInfo(Class<T> componentClass) {
        ComponentInfo<?> info = COMPONENT_REGISTRY.get(componentClass);
        if (info == null) throw new IllegalArgumentException("Unknown component class: " + componentClass);

        return (ComponentInfo<T>) info;
    }

    public <T extends PlayerDataComponent> boolean hasComponent(Class<T> componentClass) {
        byte flag = getComponentInfo(componentClass).flag;
        return (flags & flag) != 0;
    }

    private int getComponentIndex(byte flag) { return Integer.numberOfTrailingZeros(flag & 0xFF); }

    @Nullable
    public <T extends PlayerDataComponent> T getComponent(Class<T> componentClass) {
        byte flag = getComponentInfo(componentClass).flag;
        int index = getComponentIndex(flag);

        return componentClass.cast(components[index]);
    }

    public <T extends PlayerDataComponent> T getComponentOrEmpty(Class<T> componentClass) {
        T component = getComponent(componentClass);
        return (component != null) ? component : getComponentInfo(componentClass).constructor.get();
    }

    public <T extends PlayerDataComponent> PlayerInfoPayload setComponent(T component) {
        byte flag = getComponentInfo(component.getClass()).flag;
        int index = getComponentIndex(flag);
        components[index] = component;

        this.flags |= flag;
        return this;
    }

    public <T extends PlayerDataComponent> boolean hasComponentChanged(T newComponent) {
        PlayerDataComponent currentData = getComponent(newComponent.getClass());
        return (currentData == null) || newComponent.hasChanged(currentData);
    }

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
        return (basicData != null) ? basicData.name : this.playerId.toString();
    }

    @Nullable
    public RegistryKey<World> getDimension() {
        PlayerWorldData worldData = getComponent(PlayerWorldData.class);
        return (worldData != null) ? worldData.dimension : null;
    }

    public boolean hasAnyInfo() { return flags != 0; }
    public boolean hasNewConnectionFlag() { return (flags & FLAG_NEW_CONNECTION) != 0; }

    public void merge(PlayerInfoPayload other) {
        this.flags |= other.flags;

        for (int i = 0; i < MAX_FLAGS; i++) {
            if (other.components[i] != null) {
                this.components[i] = other.components[i].copy();
            }
        }
    }

    @Override
    public void write(RegistryByteBuf buf) {
        buf.writeUuid(playerId);
        buf.writeByte(flags);

        for (ComponentInfo<?> info : COMPONENT_REGISTRY.values()) {
            if ((flags & info.flag) != 0) {
                int index = getComponentIndex(info.flag);
                PlayerDataComponent component = components[index];
                if (component != null) component.write(buf);
            }
        }
    }

    public void read(RegistryByteBuf buf) {
        int beforePayload = buf.readerIndex();

        buf.readUuid(); // playerId already read in constructor
        this.flags = buf.readByte();

        StringBuilder componentLog = PlayerRelay.isDevelopment ? new StringBuilder() : null;

        for (ComponentInfo<?> info : COMPONENT_REGISTRY.values()) {
            if ((flags & info.flag) != 0) {
                int beforeComponent = componentLog != null ? buf.readerIndex() : 0;

                PlayerDataComponent component = info.constructor.get();
                component.read(buf);

                int index = getComponentIndex(info.flag);
                components[index] = component;

                if (componentLog != null) {
                    int bytesRead = buf.readerIndex() - beforeComponent;
                    componentLog.append("\n  ")
                        .append(info.componentClass.getSimpleName())
                        .append(" (").append(bytesRead).append(" bytes)");
                }
            }
        }

        if (componentLog != null) {
            PlayerRelay.LOGGER.info("PlayerInfoPayload of {} bytes:{}",
                buf.readerIndex() - beforePayload, componentLog);
        }
    }

    public static Set<Class<? extends PlayerDataComponent>> getRegisteredComponents() {
        return Collections.unmodifiableSet(COMPONENT_REGISTRY.keySet());
    }
}
