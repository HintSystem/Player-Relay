package dev.hintsystem.playerrelay.party;

import java.time.Instant;
import java.util.UUID;

public class PartyInvite {
    public final UUID partyId;
    public final UUID inviterId;
    public final UUID inviteeId;
    public final Instant expiresAt;

    public Instant receivedAt;
    public boolean declined = false;

    public PartyInvite(UUID partyId, UUID inviterId, UUID inviteeId, Instant expiresAt) {
        this.partyId = partyId;
        this.inviterId = inviterId;
        this.inviteeId = inviteeId;
        this.expiresAt = expiresAt;
    }

    public void decline() { declined = true; }

    public void setReceived() { receivedAt = Instant.now(); }

    public boolean isReceivedFor(long durationMs) {
        if (receivedAt == null) {
            receivedAt = Instant.now();
            return false;
        }
        return Instant.now().isAfter(receivedAt.plusMillis(durationMs));
    }

    public boolean isExpired() { return Instant.now().isAfter(expiresAt) || declined; }
}
