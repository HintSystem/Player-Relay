package dev.hintsystem.playerrelay.party;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Party {
    private final UUID partyId;
    public String partyName;
    public UUID leaderId;
    public final Set<UUID> members;

    public Party(UUID partyId, UUID leaderId) {
        this.partyId = partyId;
        this.leaderId = leaderId;
        this.members = new HashSet<>();

        members.add(leaderId);
    }

    public boolean isMember(UUID playerId) {
        return members.contains(playerId);
    }

    public boolean isLeader(UUID playerId) {
        return leaderId.equals(playerId);
    }

    public void setLeader(UUID newLeaderId) {
        if (members.contains(newLeaderId)) {
            this.leaderId = newLeaderId;
        }
    }

    public UUID getPartyId() { return partyId; }
    public int getMemberCount() { return members.size(); }
}
