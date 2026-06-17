package dev.hintsystem.playerrelay.party;

import java.util.UUID;

public interface PartyMethods {
    void syncParty(UUID leaderId, Party syncParty);

    void createParty(Party party);
    void disbandParty(UUID leaderId);
    void leaveParty(UUID memberId);

    void kickMember(UUID leaderId, UUID memberId);
    void invitePlayer(PartyInvite invite);

    void acceptInvite(UUID inviteeId, UUID partyId);
    /** @return Declined invite */
    PartyInvite declineInvite(UUID inviteeId, UUID partyId);
}
