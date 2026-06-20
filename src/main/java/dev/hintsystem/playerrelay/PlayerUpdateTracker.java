package dev.hintsystem.playerrelay;

import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import dev.hintsystem.playerrelay.payload.player.*;

import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/** Tracks player state changes and builds delta payloads */
public class PlayerUpdateTracker {
    public UUID playerId;
    private long lastCommitTime = 0;
    private PlayerInfoPayload lastCommitState;

    public PlayerUpdateTracker(UUID playerId) {
        this(new PlayerInfoPayload(playerId));
    }

    public PlayerUpdateTracker(PlayerInfoPayload playerInfo) {
        this.playerId = playerInfo.playerId;
        this.lastCommitState = playerInfo;
    }

    /** Builder for constructing player delta payloads */
    public static class DeltaBuilder {
        @Nullable
        private final PlayerInfoPayload baseline;
        private final PlayerInfoPayload delta;
        private boolean hasChanges = false;

        private DeltaBuilder(UUID playerId, @Nullable PlayerInfoPayload baseline) {
            this.delta = new PlayerInfoPayload(playerId);
            this.baseline = baseline;
        }

        /**
         * Adds all player info components that have a shared implementation
         * <p>
         * Excludes {@link PlayerBasicData} and {@link PlayerPositionData}
         */
        public DeltaBuilder withCommon(@Nullable Player player) {
            if (player != null) {
                this.with(new PlayerStatsData(player))
                    .with(new PlayerEquipmentData(player))
                    .with(new PlayerStatusEffectsData(player));
            }

            return this.with(new PlayerWorldData(player)); // Send empty world payload when a player is not in a world
        }

        /**
         * Add a component to the delta. Only included if it has changed from baseline
         * @param component The component to potentially add
         * @return this builder for chaining
         */
        public <T extends PlayerDataComponent> DeltaBuilder with(T component) {
            if (delta.updateComponent(baseline, component)) {
                hasChanges = true;
            }
            return this;
        }

        /**
         * Set a flag value
         * @param flag The flag to set
         * @param value The value to set it to
         * @return this builder for chaining
         */
        public DeltaBuilder withFlag(PlayerInfoPayload.FLAGS flag, boolean value) {
            if (baseline == null || baseline.hasFlag(flag) != value) {
                delta.setFlag(flag, value);
                hasChanges = true;
            }
            return this;
        }

        /**
         * @return The delta payload, or null if no changes detected
         */
        @Nullable
        public PlayerInfoPayload build() { return (hasChanges || baseline == null) ? delta : null; }

        public boolean hasChanges() { return hasChanges; }
    }

    /** Begin building an update against the last known state */
    public DeltaBuilder beginDelta() {
        return new DeltaBuilder(playerId, lastCommitState);
    }

    /**
     * Begin building a complete snapshot without change detection.
     * All components added will be included regardless of whether they changed
     */
    public DeltaBuilder beginSnapshot() { return new DeltaBuilder(playerId, null); }

    /** Commit the changes from a delta to the tracked state */
    public void commitDelta(PlayerInfoPayload delta) {
        lastCommitTime = System.currentTimeMillis();
        lastCommitState.merge(delta);
    }

    @NotNull
    public PlayerInfoPayload getCurrentState() { return lastCommitState; }

    /** @return time in milliseconds from epoch when the last commit was made, returns 0 if no commit was made */
    public long lastCommitTime() { return lastCommitTime; }

    /** @return time in milliseconds since the last delta commit was made, returns time since epoch if no commit was made */
    public long timeSinceLastCommit() { return System.currentTimeMillis() - lastCommitTime; }

    public void reset() { lastCommitState = new PlayerInfoPayload(playerId); }
}