package dev.hintsystem.playerrelay.party;

import dev.hintsystem.playerrelay.PlayerUpdateTracker;
import dev.hintsystem.playerrelay.ServerCore;
import dev.hintsystem.playerrelay.payload.PartyPayload;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Handles server-side party actions,
 * validates user actions with {@link dev.hintsystem.playerrelay.party.PartyValidator.ServerPartyValidator},
 * uses {@link PartyManager} to update party data and broadcasts successful actions through {@link PartyNetworkEventHandler}
 */
public class ServerPartyService implements PartyMethods {
    private final ServerCore server;
    private final PartyManager manager;
    private final PartyValidator.ServerPartyValidator validator;
    private final PartyNetworkEventHandler handler = new PartyNetworkEventHandler(this);

    public static class PartyNetworkEventHandler {
        private final ServerPartyService partyService;

        PartyNetworkEventHandler(ServerPartyService partyService) {
            this.partyService = partyService;
        }

        void beforeSync(UUID actorId, Party currentParty, Party syncedParty) {
            Set<UUID> informedPlayers = new HashSet<>(currentParty.members);
            informedPlayers.addAll(syncedParty.members);

            ServerCore.broadcastGlobalPayload(
                new PartyPayload.Builder(currentParty.partyId, actorId)
                    .sync(syncedParty).packet(),
                informedPlayers
            );
        }

        void onPartyCreate(Party createdParty) {
            ServerCore.broadcastGlobalPayload(
                new PartyPayload.Builder(createdParty.partyId, createdParty.leaderId)
                    .create(createdParty).packet(),
                createdParty.members
            );
        }
        void beforePartyDisband(UUID actorId, Party disbandedParty) {
            ServerCore.broadcastGlobalPayload(
                new PartyPayload.Builder(disbandedParty.partyId, actorId)
                    .disband().packet(),
                disbandedParty.members
            );
        }

        void onPartyMemberLeave(Party leftParty, UUID leftMemberId) {
            Set<UUID> informedPlayers = new HashSet<>(leftParty.members);
            informedPlayers.add(leftMemberId);

            ServerCore.broadcastGlobalPayload(
                new PartyPayload.Builder(leftParty.partyId, leftMemberId)
                    .leave().packet(),
                informedPlayers
            );
        }
        void onPartyMemberKick(UUID actorId, Party kickedParty, UUID kickedMemberId) {
            Set<UUID> informedPlayers = new HashSet<>(kickedParty.members);
            informedPlayers.add(kickedMemberId);

            ServerCore.broadcastGlobalPayload(
                new PartyPayload.Builder(kickedParty.partyId, actorId)
                    .kick(new PartyPayload.KickAction(kickedMemberId)).packet(),
                informedPlayers
            );
        }

        void onPlayerInvited(Party invitedParty, PartyInvite invite) {
            Set<UUID> informedPlayers = new HashSet<>(invitedParty.members);
            informedPlayers.add(invite.inviteeId);

            ServerCore.broadcastGlobalPayload(
                new PartyPayload.Builder(invite.partyId, invite.inviterId)
                    .invite(new PartyPayload.InviteAction(invite.inviteeId, invite.expiresAt)).packet(),
                informedPlayers
            );
        }
        void onPlayerAcceptInvite(Party acceptedParty, PartyInvite invite) {
            Set<UUID> informedPlayers = new HashSet<>(acceptedParty.members);
            informedPlayers.add(invite.inviteeId);

            PartyPayload.Builder payloadBuilder = new PartyPayload.Builder(invite.partyId, invite.inviteeId);
            ServerCore.broadcastGlobalPayload(
                payloadBuilder.acceptInvite().packet(),
                informedPlayers
            );

            partyService.syncPartyWithPlayer(invite.inviteeId, acceptedParty);
        }
        void onPlayerDeclineInvite(PartyInvite invite) {
            ServerCore.broadcastGlobalPayload(
                new PartyPayload.Builder(invite.partyId, invite.inviteeId)
                    .declineInvite().packet(),
                Set.of(invite.inviterId, invite.inviteeId)
            );
        }
    }

    public ServerPartyService(ServerCore server, PartyManager manager) {
        this.server = server;
        this.manager = manager;
        this.validator = new PartyValidator.ServerPartyValidator(manager);
    }

    private void syncPartyWithPlayer(UUID playerId, Party party) {
        PartyPayload.Builder payloadBuilder = new PartyPayload.Builder(party.partyId, playerId);

        server.sendToClient(payloadBuilder.sync(party).packet(), playerId);
        for (UUID memberId : party.members) {
            PlayerUpdateTracker memberTracker = server.playerUpdateTrackers.get(memberId);
            PlayerUpdateTracker playerTracker = server.playerUpdateTrackers.get(playerId);
            if (memberTracker != null) server.sendToClient(memberTracker.getCurrentState().packet(), playerId);
            if (playerTracker != null) server.sendToClient(playerTracker.getCurrentState().packet(), memberId);
        }
    }

    private void internalSyncParty(UUID actorId, Party syncParty) {
        Party currentParty = manager.getParty(actorId);
        if (currentParty == null) return;

        handler.beforeSync(actorId, currentParty, syncParty);
        manager.syncPartySettings(syncParty);
    }

    public void syncParty(UUID actorId, Party syncParty) throws IllegalStateException {
        validator.validateSync(actorId, syncParty);
        internalSyncParty(actorId, syncParty);
    }

    public void createParty(Party createdParty) throws IllegalStateException {
        // Server assigns the partyId itself
        Party serverParty = new Party(UUID.randomUUID(), createdParty.leaderId, createdParty.partyName);
        Party memberParty = validator.validateCreate(serverParty);

        if (memberParty != null) {
            // Leave existing party if regular member
            internalLeaveParty(memberParty, serverParty.leaderId);
        }

        manager.parties.put(serverParty.partyId, serverParty);
        manager.addMember(serverParty, serverParty.leaderId);

        handler.onPartyCreate(serverParty);
    }

    private void internalDisbandParty(UUID actorId, Party disbandedParty) {
        handler.beforePartyDisband(actorId, disbandedParty);
        manager.disbandParty(disbandedParty);
    }

    public void disbandParty(UUID actorId) throws IllegalStateException {
        internalDisbandParty(actorId, validator.validateDisband(actorId));
    }

    private void internalLeaveParty(Party leftParty, UUID memberId) {
        manager.removeMember(leftParty, memberId);

        // Disband empty party
        if (leftParty.getMemberCount() == 0) {
            internalDisbandParty(memberId, leftParty); return;
        }

        handler.onPartyMemberLeave(leftParty, memberId);

        // If leader left, transfer leadership to next member
        if (leftParty.isLeader(memberId)) {
            Party newLeaderParty = Party.copyOf(leftParty);
            newLeaderParty.leaderId = leftParty.members.iterator().next();
            internalSyncParty(memberId, newLeaderParty);
        }
    }

    public void leaveParty(UUID memberId) throws IllegalStateException {
        internalLeaveParty(validator.validateLeave(memberId), memberId);
    }

    public void kickMember(UUID actorId, UUID memberId) throws IllegalStateException {
        Party kickedParty = validator.validateKick(actorId, memberId);

        manager.removeMember(kickedParty, memberId);
        handler.onPartyMemberKick(actorId, kickedParty, memberId);
    }

    public void invitePlayer(PartyInvite invite) throws IllegalStateException {
        Party invitedParty = validator.validateInvite(invite);

        manager.invitePlayer(invite);
        handler.onPlayerInvited(invitedParty, invite);
    }

    public void acceptInvite(UUID inviteeId, UUID partyId) throws IllegalStateException {
        PartyInvite validInvite = validator.validateAcceptInvite(inviteeId, partyId);

        // Force invitee to leave existing party
        Party currentParty = manager.getPlayerParty(inviteeId);
        if (currentParty != null) internalLeaveParty(currentParty, inviteeId);

        Party acceptedParty = manager.getParty(partyId);
        if (acceptedParty == null) return;

        manager.acceptInvite(inviteeId, partyId);
        handler.onPlayerAcceptInvite(acceptedParty, validInvite);
    }

    public PartyInvite declineInvite(UUID inviteeId, UUID partyId) {
        PartyInvite declinedInvite = manager.declineInvite(inviteeId, partyId);

        if (declinedInvite != null) {
            handler.onPlayerDeclineInvite(declinedInvite);
        }

        return declinedInvite;
    }
}
