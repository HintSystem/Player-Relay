package dev.hintsystem.playerrelay.party;

import java.time.Instant;
import java.util.UUID;

public class PartyInvite {
    public final UUID partyId;
    public final UUID inviterId;
    public final UUID inviteeId;
    public final Instant expiresAt;

    protected PartyInvite(UUID partyId, UUID inviterId, UUID inviteeId, Instant expiresAt) {
        this.partyId = partyId;
        this.inviterId = inviterId;
        this.inviteeId = inviteeId;
        this.expiresAt = expiresAt;
    }

    public PartyInvite(UUID partyId, UUID inviterId, UUID inviteeId, long timeoutMs) {
        this(partyId, inviterId, inviteeId, Instant.now().plusMillis(timeoutMs));
    }

    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
}
