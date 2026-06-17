package dev.hintsystem.playerrelay.party;

import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PartyManager implements PartyMethods {
    final Map<UUID, Party> parties = new ConcurrentHashMap<>();
    final Map<UUID, UUID> playerToParty = new ConcurrentHashMap<>();
    final Map<UUID, List<PartyInvite>> pendingInvites = new ConcurrentHashMap<>();

    /** How long it takes before a player can be invited to the same party again */
    public static final long INVITE_TIMEOUT_MS = 60000;

    public void reset() {
        parties.clear();
        playerToParty.clear();
        pendingInvites.clear();
    }

    public void addMember(Party party, UUID memberId) {
        Party currentParty = getPlayerParty(memberId);
        // Ideally shouldn't happen, but remove member from previous party if it wasn't handled by the manager
        if (currentParty != null) {
            currentParty.members.remove(memberId);
            if (currentParty.members.isEmpty()) {
                parties.remove(memberId);
            }
        }

        party.members.add(memberId);
        playerToParty.put(memberId, party.partyId);
    }

    public void removeMember(Party party, UUID memberId) {
        UUID currentPartyId = getPlayerPartyId(memberId);
        // Ideally shouldn't happen, but don't remove party association if this member somehow belonged to multiple parties
        if (party.partyId.equals(currentPartyId))
            playerToParty.remove(memberId);

        party.members.remove(memberId);
    }

    private void internalSyncPartySettings(Party currentParty, Party syncParty) {
        currentParty.leaderId = syncParty.leaderId;
        currentParty.partyName = syncParty.partyName;
    }

    public Party syncPartySettings(Party syncParty) {
        Party currentParty = parties.get(syncParty.partyId);
        if (currentParty != null) internalSyncPartySettings(currentParty, syncParty);

        return currentParty;
    }

    public void syncParty(UUID leaderId, Party syncParty) {
        Party currentParty = parties.get(syncParty.partyId);
        if (currentParty == null) {
            parties.put(syncParty.partyId, syncParty);
            for (UUID memberId : syncParty.members) {
                addMember(syncParty, memberId);
            }

            return;
        }

        internalSyncPartySettings(currentParty, syncParty);

        Set<UUID> currentMembers = new HashSet<>(currentParty.members);
        for (UUID member : currentMembers) {
            removeMember(currentParty, member);
        }
        for (UUID member : syncParty.members) {
            addMember(currentParty, member);
        }
    }

    public void createParty(Party createdParty) {
        Party existingParty = parties.get(createdParty.partyId);
        if (existingParty != null) {
            disbandParty(existingParty);
        }

        parties.put(createdParty.partyId, createdParty);
        addMember(createdParty, createdParty.leaderId);
    }

    public void disbandParty(Party party) {
        // Remove all members
        for (UUID memberId : party.members) {
            removeMember(party, memberId);
        }
        parties.remove(party.partyId);
    }

    public void disbandParty(UUID leaderId) {
        Party party = getPlayerParty(leaderId);
        if (party != null) disbandParty(party);
    }

    public void leaveParty(UUID memberId) {
        Party party = getPlayerParty(memberId);
        if (party != null) removeMember(party, memberId);
    }

    public void kickMember(UUID leaderId, UUID memberId) { leaveParty(memberId); }

    public void invitePlayer(PartyInvite invite) {
        List<PartyInvite> invites = pendingInvites.computeIfAbsent(invite.inviteeId, k -> new ArrayList<>());

        // Remove all existing invites for this party, no duplicates allowed
        invites.removeIf(inv -> inv.partyId.equals(invite.partyId));

        invites.add(invite);
    }

    public void acceptInvite(UUID inviteeId, UUID partyId) {
        Party party = parties.get(partyId);
        if (party == null) return;

        addMember(party, inviteeId);

        // Clear all invites for this player
        pendingInvites.remove(inviteeId);
    }

    @Nullable
    public PartyInvite declineInvite(UUID inviteeId, UUID partyId) {
        List<PartyInvite> invites = getPendingInvites(inviteeId);

        // Set invite as declined, but do not remove so invites can be checked for timeout
        for (PartyInvite invite : invites) {
            if (invite.partyId.equals(partyId) && !invite.declined) {
                invite.decline();
                return invite;
            }
        }

        return null;
    }

    @Nullable
    public Party getParty(UUID partyId) { return parties.get(partyId); }

    @Nullable
    public UUID getPlayerPartyId(UUID playerId) { return playerToParty.get(playerId); }

    @Nullable
    public Party getPlayerParty(UUID playerId) {
        UUID partyId = playerToParty.get(playerId);
        return partyId != null ? parties.get(partyId) : null;
    }

    public List<PartyInvite> getPendingInvites(UUID playerId) {
        List<PartyInvite> invites = pendingInvites.get(playerId);
        if (invites == null) return Collections.emptyList();

        // Clean up expired invites
        invites.removeIf((invite) ->
            invite.isExpired() && invite.isReceivedFor(INVITE_TIMEOUT_MS));

        if (invites.isEmpty())
            pendingInvites.remove(playerId);

        return new ArrayList<>(invites);
    }

    public boolean areInSameParty(UUID player1, UUID player2) {
        UUID party1 = playerToParty.get(player1);
        UUID party2 = playerToParty.get(player2);
        return party1 != null && party1.equals(party2);
    }
}
