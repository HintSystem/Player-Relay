package dev.hintsystem.playerrelay.party;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.CommonCore;
import dev.hintsystem.playerrelay.payload.PartyPayload;

import java.time.Instant;
import java.util.UUID;

/**
 * Responsible for creating payloads and sending them to the server,
 * uses {@link dev.hintsystem.playerrelay.party.PartyValidator} to validate actions and throw client-side errors early.
 * Does not modify {@link PartyManager} parties directly, results are awaited from server
 */
public class ClientPartyService {
    private final PartyManager manager;
    private final PartyValidator validator;

    public ClientPartyService(PartyManager manager) {
        this.manager = manager;
        this.validator = new PartyValidator(manager);
    }

    private void requireConnection() {
        if (!CommonCore.serverConnection.get().isVersionValid()) {
            throw new IllegalStateException("Parties can only be used when connected to a server with Player Relay installed");
        }
    }

    public void createParty(String partyName) throws IllegalStateException {
        requireConnection();

        PartyPayload.CreateAction action = new PartyPayload.CreateAction(partyName);
        PartyPayload payload = new PartyPayload.Builder(
            new UUID(0, 0), ClientCore.getClientUuid() // Server will assign party id
        ).create(action);

        validator.validateCreate(action.asParty(payload));
        sendMessage(payload);
    }

    public void disbandParty() {
        requireConnection();

        UUID clientId = ClientCore.getClientUuid();
        Party disbandedParty = validator.validateDisband(clientId);

        sendMessage(
            new PartyPayload.Builder(
                disbandedParty.partyId, clientId
            ).disband()
        );
    }

    public void leaveParty() {
        requireConnection();

        UUID clientId = ClientCore.getClientUuid();
        Party leftParty = validator.validateLeave(clientId);

        sendMessage(
            new PartyPayload.Builder(
                leftParty.partyId, clientId
            ).leave()
        );
    }

    public void kickMember(UUID memberId) {
        requireConnection();

        UUID clientId = ClientCore.getClientUuid();
        Party kickedParty = validator.validateKick(clientId, memberId);

        sendMessage(
            new PartyPayload.Builder(
                kickedParty.partyId, clientId
            ).kick(new PartyPayload.KickAction(memberId))
        );
    }

    public void invitePlayer(UUID playerId) {
        requireConnection();

        UUID clientId = ClientCore.getClientUuid();
        PartyInvite invite = new PartyInvite(
            manager.getPlayerPartyId(clientId), clientId, playerId,
            Instant.now().plusMillis(PartyManager.INVITE_TIMEOUT_MS)
        );

        validator.validateInvite(invite);

        sendMessage(
            new PartyPayload.Builder(
                invite.partyId, clientId
            ).invite(new PartyPayload.InviteAction(invite.inviteeId, invite.expiresAt))
        );
    }

    public void acceptInvite(UUID partyId) {
        requireConnection();

        UUID clientId = ClientCore.getClientUuid();
        sendMessage(
            new PartyPayload.Builder(
                partyId, clientId
            ).acceptInvite()
        );
    }

    public void declineInvite(UUID partyId) {
        requireConnection();

        UUID clientId = ClientCore.getClientUuid();
        sendMessage(
            new PartyPayload.Builder(
                partyId, clientId
            ).declineInvite()
        );
    }

    private void sendMessage(PartyPayload payload) {
        CommonCore.serverConnection.get().sendMessage(payload.message());
    }
}
