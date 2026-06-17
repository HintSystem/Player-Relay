package dev.hintsystem.playerrelay.party;

import dev.hintsystem.playerrelay.payload.PartyPayload;

public class PartyPayloadHandler extends PartyPayload.ActionListener {
    protected final PartyMethods manager;

    public PartyPayloadHandler(PartyMethods manager) {
        this.manager = manager;
    }

    @Override
    public void onSync(PartyPayload party, PartyPayload.SyncAction syncAction) {
        manager.syncParty(party.actorId, syncAction.asParty(party));
    }

    @Override
    public void onCreate(PartyPayload party, PartyPayload.CreateAction createAction) {
        manager.createParty(createAction.asParty(party));
    }

    @Override
    public void onDisband(PartyPayload party) {
        manager.disbandParty(party.actorId);
    }

    @Override
    public void onLeave(PartyPayload party) {
        manager.leaveParty(party.actorId);
    }

    @Override
    public void onKick(PartyPayload party, PartyPayload.KickAction kickAction) {
        manager.kickMember(party.actorId, kickAction.memberId());
    }

    @Override
    public void onInvite(PartyPayload party, PartyPayload.InviteAction inviteAction) {
        manager.invitePlayer(inviteAction.asPartyInvite(party));
    }

    @Override
    public void onAcceptInvite(PartyPayload party) {
        manager.acceptInvite(party.actorId, party.partyId);
    }

    @Override
    public void onDeclineInvite(PartyPayload party) {
        manager.declineInvite(party.actorId, party.partyId);
    }
}
