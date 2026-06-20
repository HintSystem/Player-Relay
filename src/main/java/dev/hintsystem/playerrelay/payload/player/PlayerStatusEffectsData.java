package dev.hintsystem.playerrelay.payload.player;

import dev.hintsystem.playerrelay.CommonCore;
import dev.hintsystem.playerrelay.payload.FlagHolder;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

import com.google.common.collect.Ordering;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PlayerStatusEffectsData extends FlagHolder<PlayerStatusEffectsData.FLAGS>
    implements PlayerDataComponent {
    // Maximum difference in remaining milliseconds before considering an effect duration "changed"
    private static final int MAX_REMAINING_MS_DIF = 500;

    public enum FLAGS { FROZEN, ON_FIRE }

    private long timestamp;
    private final List<StatusEffectEntry> effects = new ArrayList<>();

    public record StatusEffectEntry(Holder<MobEffect> statusEffect, int amplifier, int duration) {
        public boolean isInfinite() { return duration == -1; }
    }

    public PlayerStatusEffectsData() {}

    public PlayerStatusEffectsData(Player player) {
        this.timestamp = System.currentTimeMillis();
        setFlag(FLAGS.FROZEN, player.isFullyFrozen());
        setFlag(FLAGS.ON_FIRE, player.isOnFire());

        for (MobEffectInstance effectInstance : Ordering.natural().reverse().sortedCopy(player.getActiveEffects())) {
            if (effectInstance == null) continue;
            if (effects.size() >= 255) break;

            effects.add(new StatusEffectEntry(
                effectInstance.getEffect(),
                effectInstance.getAmplifier(),
                effectInstance.getDuration()
            ));
        }
    }

    public boolean isFrozen() { return hasFlag(FLAGS.FROZEN); }
    public boolean isOnFire() { return hasFlag(FLAGS.ON_FIRE); }

    public long getEffectRemainingMs(StatusEffectEntry effect) {
        return getEffectRemainingMs(effect, System.currentTimeMillis());
    }

    public long getEffectRemainingMs(StatusEffectEntry effect, long currentTime) {
        if (effect.isInfinite()) return Long.MAX_VALUE;

        long effectDurationMs = CommonCore.ticksToMs(effect.duration);
        long effectEndTime = timestamp + effectDurationMs;
        return Math.max(0, effectEndTime - currentTime);
    }

    public boolean hasStatusEffect(Holder<MobEffect> effect) {
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
    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeLong(timestamp);
        writeFlags(buf, 1);

        buf.writeByte(effects.size()); // max 255
        for (StatusEffectEntry e : effects) {
            buf.writeVarInt(BuiltInRegistries.MOB_EFFECT.getId(e.statusEffect.value()));
            buf.writeByte(e.amplifier() & 0xFF);
            buf.writeInt(e.duration());
        }
    }

    @Override
    public void read(RegistryFriendlyByteBuf buf) {
        this.timestamp = buf.readLong();
        readFlags(buf, 1);

        effects.clear();
        int count = buf.readUnsignedByte();
        for (int i = 0; i < count; i++) {
            Optional<Holder.Reference<MobEffect>> effectType = BuiltInRegistries.MOB_EFFECT.get(buf.readVarInt());
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

        if (!equalsFlags(otherStatus)) return true;

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
        copy.setFlags(this.getFlags());
        copy.effects.addAll(this.effects);
        return copy;
    }
}
