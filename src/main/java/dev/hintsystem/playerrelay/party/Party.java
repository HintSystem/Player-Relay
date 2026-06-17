package dev.hintsystem.playerrelay.party;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Party {
    public static final int MAX_PARTY_NAME_LENGTH = 40;

    public final UUID partyId;
    public String partyName;
    public UUID leaderId;
    public final Set<UUID> members;

    public Party(UUID partyId, UUID leaderId, String partyName) {
        this.partyId = partyId;
        this.leaderId = leaderId;
        this.partyName = partyName.trim().substring(0, Math.min(partyName.length(), MAX_PARTY_NAME_LENGTH));
        this.members = new HashSet<>();

        members.add(leaderId);
    }

    public static Party copyOf(Party party) {
        Party copiedParty = new Party(party.partyId, party.leaderId, party.partyName);
        copiedParty.members.addAll(party.members);

        return copiedParty;
    }

    public boolean isMember(UUID playerId) {
        return members.contains(playerId);
    }

    public boolean isLeader(UUID playerId) {
        return leaderId.equals(playerId);
    }

    public int getMemberCount() { return members.size(); }
}
