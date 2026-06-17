package dev.hintsystem.playerrelay.network.handler;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.CommonCore;
import dev.hintsystem.playerrelay.command.PlayerRelayCommands;
import dev.hintsystem.playerrelay.logging.NetworkLogger;
import dev.hintsystem.playerrelay.network.connection.ServerConnectionCollector;
import dev.hintsystem.playerrelay.party.Party;
import dev.hintsystem.playerrelay.party.PartyPayloadHandler;
import dev.hintsystem.playerrelay.payload.*;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Handles messages received from the server on the client */
public class S2CMessageHandler extends ClientMessageHandler<Void> {
    public final ServerConnectionCollector connection;

    private final PartyPayloadHandler partyPayloadHandler = new PartyPayloadHandler(CommonCore.partyManager) {
        @Override
        public void onCreate(PartyPayload party, PartyPayload.CreateAction createAction) {
            super.onCreate(party, createAction);
            ClientCore.addHudMessage(
                Text.empty()
                    .append(Text.literal("Sucessfully created party \"%s\"".formatted(createAction.partyName())))
                    .formatted(Formatting.GREEN)
            );
        }

        @Override
        public void onDisband(PartyPayload party) {
            super.onDisband(party);

            Party disbandedParty = CommonCore.partyManager.getParty(party.partyId);
            if (disbandedParty == null) return;

            ClientCore.addHudMessage(
                Text.empty()
                    .append(Text.literal("Party \"%s\" was disbanded".formatted(disbandedParty.partyName)))
                    .formatted(Formatting.RED)
            );
        }

        @Override
        public void onLeave(PartyPayload party) {
            super.onLeave(party);

            if (!party.actorId.equals(ClientCore.getClientUuid())) {
                ClientCore.addHudMessage(
                    Text.empty()
                        .append(ClientCore.getPlayerDisplayName(party.actorId)
                            .formatted(Formatting.BOLD))
                        .append(Text.literal(" left the party"))
                        .formatted(Formatting.RED)
                );
                return;
            }

            ClientCore.addHudMessage(
                Text.empty()
                    .append(Text.literal("You left the party"))
                    .formatted(Formatting.RED)
            );
        }

        @Override
        public void onInvite(PartyPayload party, PartyPayload.InviteAction inviteAction) {
            super.onInvite(party, inviteAction);

            if (inviteAction.inviteeId().equals(ClientCore.getClientUuid())) {
                ClientCore.addHudMessage(
                    Text.empty()
                        .append(ClientCore.getPlayerDisplayName(party.actorId)
                            .formatted(Formatting.BOLD))
                        .append(Text.literal(" invited you to their party\n"))
                        .append(Text.literal("[Accept]").setStyle(Style.EMPTY
                            .withFormatting(Formatting.DARK_GREEN, Formatting.UNDERLINE)
                            .withClickEvent(new ClickEvent.RunCommand(
                                PlayerRelayCommands.acceptInviteCommand(party.partyId)
                            ))
                        ))
                        .append("   ")
                        .append(Text.literal("[Decline]").setStyle(Style.EMPTY
                            .withFormatting(Formatting.RED, Formatting.UNDERLINE)
                            .withClickEvent(new ClickEvent.RunCommand(
                                PlayerRelayCommands.declineInviteCommand(party.partyId)
                            ))
                        ))
                        .formatted(Formatting.GREEN)
                );
                return;
            }

            ClientCore.addHudMessage(
                Text.empty().append(ClientCore.getPlayerDisplayName(party.actorId))
                    .append(Text.literal(" invited "))
                    .append(ClientCore.getPlayerDisplayName(inviteAction.inviteeId())
                        .formatted(Formatting.BOLD))
                    .append(Text.literal(" to this party"))
                    .formatted(Formatting.GRAY)
            );
        }

        @Override
        public void onAcceptInvite(PartyPayload party) {
            super.onAcceptInvite(party);

            if (!party.actorId.equals(ClientCore.getClientUuid())) {
                ClientCore.addHudMessage(
                    Text.empty()
                        .append(ClientCore.getPlayerDisplayName(party.actorId)
                            .formatted(Formatting.BOLD))
                        .append(Text.literal(" joined this party"))
                        .formatted(Formatting.GREEN)
                );
            }
        }

        @Override
        public void onDeclineInvite(PartyPayload party) {
            super.onDeclineInvite(party);

            if (!party.actorId.equals(ClientCore.getClientUuid())) {
                ClientCore.addHudMessage(
                    Text.empty()
                        .append(ClientCore.getPlayerDisplayName(party.actorId)
                            .formatted(Formatting.BOLD))
                        .append(Text.literal(" declined the invite to this party"))
                        .formatted(Formatting.RED)
                );
                return;
            }

            ClientCore.addHudMessage(
                Text.empty()
                    .append(Text.literal("Declined invite to party"))
                    .formatted(Formatting.RED)
            );
        }

        @Override
        public void onFail(PartyPayload party, PartyPayload.FailAction failAction) {
            ClientCore.addHudMessage(
                Text.empty()
                    .append(Text.literal(failAction.getTitle() + ":\n")
                            .formatted(Formatting.BOLD))
                    .append(Text.literal(failAction.message()))
                    .formatted(Formatting.RED)
            );
        }
    };

    public S2CMessageHandler(NetworkLogger logger, ServerConnectionCollector connection) {
        super(logger);
        this.connection = connection;

        register(PayloadRegistry.RELAY_VERSION, this::onPlayerRelayVersion);
        register(PayloadRegistry.PARTY, this::onPartyPayload);
    }

    public void onPlayerRelayVersion(RelayVersionPayload version, Void unused) {
        connection.get().onVersionHandshake(version);
        if (version.networkVersion != RelayVersionPayload.NETWORK_VERSION) {
            logger.versionMismatch(version).build();
        }
    }

    public void onPartyPayload(PartyPayload party, Void unused) {
        party.handleAction(partyPayloadHandler);
    }

    @Override
    public void onPlayerInfo(PlayerInfoPayload playerInfo) {
        connection.updatePlayer(playerInfo, ClientCore.getClientUuid());
        super.onPlayerInfo(playerInfo);
    }

    @Override
    public void onPlayerDisconnect(PlayerDisconnectPayload disconnect) {
        super.onPlayerDisconnect(disconnect);
        connection.removeAnnouncedPlayer(disconnect.playerId());
    }
}
