package dev.hintsystem.playerrelay.network.handler;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.CommonCore;
import dev.hintsystem.playerrelay.command.PlayerRelayCommands;
import dev.hintsystem.playerrelay.logging.NetworkLogger;
import dev.hintsystem.playerrelay.network.connection.ServerConnectionCollector;
import dev.hintsystem.playerrelay.party.Party;
import dev.hintsystem.playerrelay.party.PartyPayloadHandler;
import dev.hintsystem.playerrelay.payload.*;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** Handles messages received from the server on the client */
public class S2CMessageHandler extends ClientMessageHandler<Void> {
    public final ServerConnectionCollector connection;

    private final PartyPayloadHandler partyPayloadHandler = new PartyPayloadHandler(CommonCore.partyManager) {
        @Override
        public void onCreate(PartyPayload party, PartyPayload.CreateAction createAction) {
            super.onCreate(party, createAction);
            ClientCore.addHudMessage(
                Component.empty()
                    .append(Component.literal("Sucessfully created party \"%s\"".formatted(createAction.partyName())))
                    .withStyle(ChatFormatting.GREEN)
            );
        }

        @Override
        public void onDisband(PartyPayload party) {
            super.onDisband(party);

            Party disbandedParty = CommonCore.partyManager.getParty(party.partyId);
            if (disbandedParty == null) return;

            ClientCore.addHudMessage(
                Component.empty()
                    .append(Component.literal("Party \"%s\" was disbanded".formatted(disbandedParty.partyName)))
                    .withStyle(ChatFormatting.RED)
            );
        }

        @Override
        public void onLeave(PartyPayload party) {
            super.onLeave(party);

            if (!party.actorId.equals(ClientCore.getClientUuid())) {
                ClientCore.addHudMessage(
                    Component.empty()
                        .append(ClientCore.getPlayerDisplayName(party.actorId)
                            .withStyle(ChatFormatting.BOLD))
                        .append(Component.literal(" left the party"))
                        .withStyle(ChatFormatting.RED)
                );
                return;
            }

            ClientCore.addHudMessage(
                Component.empty()
                    .append(Component.literal("You left the party"))
                    .withStyle(ChatFormatting.RED)
            );
        }

        @Override
        public void onInvite(PartyPayload party, PartyPayload.InviteAction inviteAction) {
            super.onInvite(party, inviteAction);

            if (inviteAction.inviteeId().equals(ClientCore.getClientUuid())) {
                ClientCore.addHudMessage(
                    Component.empty()
                        .append(ClientCore.getPlayerDisplayName(party.actorId)
                            .withStyle(ChatFormatting.BOLD))
                        .append(Component.literal(" invited you to their party\n"))
                        .append(Component.literal("[Accept]").setStyle(Style.EMPTY
                            .applyFormats(ChatFormatting.DARK_GREEN, ChatFormatting.UNDERLINE)
                            .withClickEvent(new ClickEvent.RunCommand(
                                PlayerRelayCommands.acceptInviteCommand(party.partyId)
                            ))
                        ))
                        .append("   ")
                        .append(Component.literal("[Decline]").setStyle(Style.EMPTY
                            .applyFormats(ChatFormatting.RED, ChatFormatting.UNDERLINE)
                            .withClickEvent(new ClickEvent.RunCommand(
                                PlayerRelayCommands.declineInviteCommand(party.partyId)
                            ))
                        ))
                        .withStyle(ChatFormatting.GREEN)
                );
                return;
            }

            ClientCore.addHudMessage(
                Component.empty().append(ClientCore.getPlayerDisplayName(party.actorId))
                    .append(Component.literal(" invited "))
                    .append(ClientCore.getPlayerDisplayName(inviteAction.inviteeId())
                        .withStyle(ChatFormatting.BOLD))
                    .append(Component.literal(" to this party"))
                    .withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public void onAcceptInvite(PartyPayload party) {
            super.onAcceptInvite(party);

            if (!party.actorId.equals(ClientCore.getClientUuid())) {
                ClientCore.addHudMessage(
                    Component.empty()
                        .append(ClientCore.getPlayerDisplayName(party.actorId)
                            .withStyle(ChatFormatting.BOLD))
                        .append(Component.literal(" joined this party"))
                        .withStyle(ChatFormatting.GREEN)
                );
            }
        }

        @Override
        public void onDeclineInvite(PartyPayload party) {
            super.onDeclineInvite(party);

            if (!party.actorId.equals(ClientCore.getClientUuid())) {
                ClientCore.addHudMessage(
                    Component.empty()
                        .append(ClientCore.getPlayerDisplayName(party.actorId)
                            .withStyle(ChatFormatting.BOLD))
                        .append(Component.literal(" declined the invite to this party"))
                        .withStyle(ChatFormatting.RED)
                );
                return;
            }

            ClientCore.addHudMessage(
                Component.empty()
                    .append(Component.literal("Declined invite to party"))
                    .withStyle(ChatFormatting.RED)
            );
        }

        @Override
        public void onFail(PartyPayload party, PartyPayload.FailAction failAction) {
            ClientCore.addHudMessage(
                Component.empty()
                    .append(Component.literal(failAction.getTitle() + ":\n")
                            .withStyle(ChatFormatting.BOLD))
                    .append(Component.literal(failAction.message()))
                    .withStyle(ChatFormatting.RED)
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
