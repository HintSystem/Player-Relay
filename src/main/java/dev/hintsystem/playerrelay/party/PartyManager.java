package dev.hintsystem.playerrelay.party;

import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PartyManager {
    private final Map<UUID, Party> parties = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToParty = new ConcurrentHashMap<>();
    private final Map<UUID, List<PartyInvite>> pendingInvites = new ConcurrentHashMap<>();

    private static final long INVITE_TIMEOUT_MS = 60000;

    public Party createParty(UUID leaderId) {
        // Leave existing party if in one
        leaveParty(leaderId);

        UUID partyId = UUID.randomUUID();
        Party party = new Party(partyId, leaderId);

        parties.put(partyId, party);
        playerToParty.put(leaderId, partyId);

        return party;
    }

    public PartyInvite invitePlayer(UUID inviterId, UUID inviteeId) {
        UUID partyId = playerToParty.get(inviterId);
        if (partyId == null) {
            throw new IllegalStateException("Inviter is not in a party");
        }

        Party party = parties.get(partyId);
        if (party == null) {
            throw new IllegalStateException("Party not found");
        }

        // Check if invitee is already in a party
        if (playerToParty.containsKey(inviteeId)) {
            throw new IllegalStateException("Invitee is already in a party");
        }

        PartyInvite invite = new PartyInvite(partyId, inviterId, inviteeId, INVITE_TIMEOUT_MS);
        pendingInvites.computeIfAbsent(inviteeId, k -> new ArrayList<>()).add(invite);

        return invite;
    }

    public Party acceptInvite(UUID inviteeId, UUID partyId) {
        List<PartyInvite> invites = pendingInvites.get(inviteeId);
        if (invites == null) {
            throw new IllegalStateException("No pending invites");
        }

        PartyInvite invite = invites.stream()
            .filter(i -> i.partyId.equals(partyId) && !i.isExpired())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Invite not found or expired"));

        Party party = parties.get(partyId);
        if (party == null) {
            throw new IllegalStateException("Party no longer exists");
        }

        // Leave current party if in one
        leaveParty(inviteeId);

        // Join the party
        party.members.add(inviteeId);
        playerToParty.put(inviteeId, partyId);

        // Clear all invites for this player
        pendingInvites.remove(inviteeId);

        return party;
    }

    public void declineInvite(UUID inviteeId, UUID partyId) {
        List<PartyInvite> invites = pendingInvites.get(inviteeId);
        if (invites != null) {
            invites.removeIf(i -> i.partyId.equals(partyId));
            if (invites.isEmpty()) {
                pendingInvites.remove(inviteeId);
            }
        }
    }

    public void leaveParty(UUID playerId) {
        UUID partyId = playerToParty.remove(playerId);
        if (partyId == null) return;

        Party party = parties.get(partyId);
        if (party == null) return;

        party.members.remove(playerId);

        // Disband empty party
        if (party.getMemberCount() == 0) {
            parties.remove(partyId);
            return;
        }

        // If leader left, transfer leadership or disband
        if (party.isLeader(playerId)) {
            // Transfer to next member
            UUID newLeader = party.members.iterator().next();
            party.setLeader(newLeader);
        }
    }

    public void disbandParty(UUID leaderId) {
        UUID partyId = playerToParty.get(leaderId);
        if (partyId == null) return;

        Party party = parties.get(partyId);
        if (party == null || !party.isLeader(leaderId)) {
            throw new IllegalStateException("Only party leader can disband");
        }

        // Remove all members
        for (UUID memberId : party.members) {
            playerToParty.remove(memberId);
        }

        parties.remove(partyId);
    }

    public void kickMember(UUID leaderId, UUID memberId) {
        UUID partyId = playerToParty.get(leaderId);
        if (partyId == null) return;

        Party party = parties.get(partyId);
        if (party == null || !party.isLeader(leaderId)) {
            throw new IllegalStateException("Only party leader can kick members");
        }

        if (memberId.equals(leaderId)) {
            throw new IllegalStateException("Leader cannot kick themselves");
        }

        party.members.remove(memberId);
        playerToParty.remove(memberId);
    }

    @Nullable
    public Party getPlayerParty(UUID playerId) {
        UUID partyId = playerToParty.get(playerId);
        return partyId != null ? parties.get(partyId) : null;
    }

    @Nullable
    public Party getParty(UUID partyId) {
        return parties.get(partyId);
    }

    public List<PartyInvite> getPendingInvites(UUID playerId) {
        List<PartyInvite> invites = pendingInvites.get(playerId);
        if (invites == null) return Collections.emptyList();

        // Clean up expired invites
        invites.removeIf(PartyInvite::isExpired);
        if (invites.isEmpty()) {
            pendingInvites.remove(playerId);
        }

        return new ArrayList<>(invites);
    }

    public boolean areInSameParty(UUID player1, UUID player2) {
        UUID party1 = playerToParty.get(player1);
        UUID party2 = playerToParty.get(player2);
        return party1 != null && party1.equals(party2);
    }

    public Set<UUID> getPartyMembers(UUID playerId) {
        Party party = getPlayerParty(playerId);
        return party != null ? party.members : Collections.singleton(playerId);
    }

    public void cleanupExpiredInvites() {
        pendingInvites.values().forEach(invites -> invites.removeIf(PartyInvite::isExpired));
        pendingInvites.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}
