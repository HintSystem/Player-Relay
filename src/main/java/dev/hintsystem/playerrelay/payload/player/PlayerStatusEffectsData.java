package dev.hintsystem.playerrelay.payload.player;

import dev.hintsystem.playerrelay.ClientCore;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.*;

import com.google.common.collect.Ordering;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PlayerStatusEffectsData implements PlayerDataComponent {
    // Maximum difference in remaining milliseconds before considering an effect duration "changed"
    private static final int MAX_REMAINING_MS_DIF = 500;

    private long timestamp;
    public boolean isFrozen;
    private final List<StatusEffectEntry> effects = new ArrayList<>();

    public record StatusEffectEntry(RegistryEntry<StatusEffect> statusEffect, int amplifier, int duration) {
        public boolean isInfinite() { return duration == -1; }
    }

    public PlayerStatusEffectsData() {}

    public PlayerStatusEffectsData(PlayerEntity player) {
        this.timestamp = System.currentTimeMillis();
        this.isFrozen = player.isFrozen();

        for (StatusEffectInstance effectInstance : Ordering.natural().reverse().sortedCopy(player.getStatusEffects())) {
            if (effectInstance == null) continue;
            if (effects.size() >= 255) break;

            effects.add(new StatusEffectEntry(
                effectInstance.getEffectType(),
                effectInstance.getAmplifier(),
                effectInstance.getDuration()
            ));
        }
    }

    public long getEffectRemainingMs(StatusEffectEntry effect) {
        return getEffectRemainingMs(effect, System.currentTimeMillis());
    }

    public long getEffectRemainingMs(StatusEffectEntry effect, long currentTime) {
        if (effect.isInfinite()) return Long.MAX_VALUE;

        long effectDurationMs = ClientCore.ticksToMs(effect.duration);
        long effectEndTime = timestamp + effectDurationMs;
        return Math.max(0, effectEndTime - currentTime);
    }

    public boolean hasStatusEffect(RegistryEntry<StatusEffect> effect) {
        long currentTime = System.currentTimeMillis();
        return effects.stream()
            .filter(entry -> entry.statusEffect().equals(effect))
            .anyMatch(entry -> entry.isInfinite() || getEffectRemainingMs(entry, currentTime) > 0);
    }

    public List<StatusEffectEntry> getActiveStatusEffects() {
        long currentTime = System.currentTimeMillis();
        return effects.stream()
            .filter(entry -> entry.isInfinite() || getEffectRemainingMs(entry, currentTime) > 0)
            .toList();
    }

    public List<StatusEffectEntry> getAllEffects() { return new ArrayList<>(effects); }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeLong(timestamp);
        buf.writeBoolean(isFrozen);

        buf.writeByte(effects.size()); // max 255
        for (StatusEffectEntry e : effects) {
            buf.writeVarInt(Registries.STATUS_EFFECT.getRawId(e.statusEffect.value()));
            buf.writeByte(e.amplifier() & 0xFF);
            buf.writeInt(e.duration());
        }
    }

    @Override
    public void read(PacketByteBuf buf) {
        this.timestamp = buf.readLong();
        this.isFrozen = buf.readBoolean();

        effects.clear();
        int count = buf.readUnsignedByte();
        for (int i = 0; i < count; i++) {
            Optional<RegistryEntry.Reference<StatusEffect>> effectType = Registries.STATUS_EFFECT.getEntry(buf.readVarInt());
            if (effectType.isEmpty()) continue;

            effects.add(new StatusEffectEntry(
                effectType.get(),
                buf.readUnsignedByte(),
                buf.readInt()
            ));
        }
    }

    @Override
    public boolean hasChanged(PlayerDataComponent other) {
        if (!(other instanceof PlayerStatusEffectsData otherStatus)) return true;

        if (this.isFrozen != otherStatus.isFrozen) return true;

        if (this.effects.size() != otherStatus.effects.size()) return true;
        for (int i = 0; i < this.effects.size(); i++) {
            StatusEffectEntry thisEffect = this.effects.get(i);
            StatusEffectEntry otherEffect = otherStatus.effects.get(i);

            if (!Objects.equals(thisEffect.statusEffect(), otherEffect.statusEffect())
                || thisEffect.amplifier() != otherEffect.amplifier()
                || Math.abs(getEffectRemainingMs(thisEffect) - otherStatus.getEffectRemainingMs(otherEffect)) > MAX_REMAINING_MS_DIF) {
                return true;
            }
        }

        return false;
    }

    @Override
    public PlayerStatusEffectsData copy() {
        PlayerStatusEffectsData copy = new PlayerStatusEffectsData();
        copy.timestamp = this.timestamp;
        copy.isFrozen = this.isFrozen;
        copy.effects.addAll(this.effects);
        return copy;
    }
}
