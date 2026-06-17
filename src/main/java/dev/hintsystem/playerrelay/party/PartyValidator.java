package dev.hintsystem.playerrelay.party;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class PartyValidator {
    protected final PartyManager manager;

    public PartyValidator(PartyManager partyManager) {
        this.manager = partyManager;
    }

    public void validateSync(UUID leaderId, Party syncedParty) throws IllegalStateException {
        Party currentParty = manager.getParty(syncedParty.partyId);
        if (currentParty == null) {
            throw new IllegalStateException("Cannot synchronize a party that doesn't exist");
        }

        if (!currentParty.isLeader(leaderId)) {
            throw new IllegalStateException("Only leader can change party settings");
        }

        if (!syncedParty.isMember(syncedParty.leaderId)) {
            throw new IllegalStateException("A player must be a member to make them the leader");
        }
    }

    /** @return The current party the actor is already in, null if not in one */
    @Nullable
    public Party validateCreate(Party createdParty) throws IllegalStateException {
        if (manager.parties.containsKey(createdParty.partyId)) {
            throw new IllegalStateException("Cannot create a new party when another with the same identifier already exists");
        }

        Party currentParty = manager.getPlayerParty(createdParty.leaderId);
        if (currentParty != null) {
            if (currentParty.isLeader(createdParty.leaderId)) {
                throw new IllegalStateException("Cannot create a new party when already leading another party");
            }

            return currentParty;
        }

        return null;
    }

    /** @return The party to disband */
    public Party validateDisband(UUID leaderId) throws IllegalStateException {
        Party party = manager.getPlayerParty(leaderId);
        if (party == null || !party.isLeader(leaderId)) {
            throw new IllegalStateException("Only leader can disband the party");
        }

        return party;
    }

    /** @return The party to leave */
    public Party validateLeave(UUID memberId) throws IllegalStateException {
        Party party = manager.getPlayerParty(memberId);
        if (party == null) {
            throw new IllegalStateException("Cannot leave party when not in one");
        }

        return party;
    }

    /** @return The party to kick from */
    public Party validateKick(UUID leaderId, UUID memberId) throws IllegalStateException {
        Party party = manager.getPlayerParty(leaderId);
        if (party == null || !party.isLeader(leaderId)) {
            throw new IllegalStateException("Only party leader can kick members");
        }

        if (leaderId.equals(memberId)) {
            throw new IllegalStateException("Cannot kick yourself");
        }

        if (memberId.equals(leaderId)) {
            throw new IllegalStateException("Cannot kick leader");
        }

        if (!party.isMember(memberId)) {
            throw new IllegalStateException("Cannot kick a player that is not a member");
        }

        return party;
    }

    /** @return The party the invite is for */
    public Party validateInvite(PartyInvite invite) throws IllegalStateException {
        Party inviterParty = manager.getPlayerParty(invite.inviterId);
        if (inviterParty == null) {
            throw new IllegalStateException("Inviter is not in a party");
        }

        if (!invite.partyId.equals(inviterParty.partyId)) {
            throw new IllegalStateException("Inviter is not in the party they are inviting to");
        }

        return inviterParty;
    }

    public static class ServerPartyValidator extends PartyValidator {
        public ServerPartyValidator(PartyManager partyManager) { super(partyManager); }

        @Override
        public Party validateInvite(PartyInvite invite) throws IllegalStateException {
            Party invitedParty = super.validateInvite(invite);

            UUID inviteePartyId = manager.getPlayerPartyId(invite.inviteeId);
            if (inviteePartyId != null) {
                if (invite.partyId.equals(inviteePartyId)) {
                    throw new IllegalStateException("Invitee is already in your party");
                }
                throw new IllegalStateException("Invitee is already in a party");
            }

            PartyInvite timeoutInvite = manager.getPendingInvites(invite.inviteeId).stream()
                .filter(i -> i.partyId.equals(invite.partyId) && !i.isReceivedFor(PartyManager.INVITE_TIMEOUT_MS))
                .findFirst().orElse(null);

            if (timeoutInvite != null) {
                Instant timeoutEnd = timeoutInvite.receivedAt.plusMillis(PartyManager.INVITE_TIMEOUT_MS);
                long secondsLeft = Duration.between(Instant.now(), timeoutEnd).toSeconds();

                throw new IllegalStateException("Invite timeout " + secondsLeft + "s");
            }

            return invitedParty;
        }

        /** @return A valid invite */
        public PartyInvite validateAcceptInvite(UUID inviteeId, UUID partyId) throws IllegalStateException {
            List<PartyInvite> invites = manager.getPendingInvites(inviteeId);
            if (invites.isEmpty()) {
                throw new IllegalStateException("Invitee has no invites");
            }

            PartyInvite invite = invites.stream()
                .filter(i -> i.partyId.equals(partyId) && !i.isExpired())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Invite not found or expired"));

            Party party = manager.getParty(partyId);
            if (party == null) {
                throw new IllegalStateException("Party no longer exists");
            }

            return invite;
        }
    }
}
