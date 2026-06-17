package dev.hintsystem.playerrelay.payload;

import dev.hintsystem.playerrelay.party.Party;
import dev.hintsystem.playerrelay.party.PartyInvite;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;

import org.jetbrains.annotations.Nullable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PartyPayload implements Payload {
    public abstract static class ActionListener {
        public void onSync(PartyPayload party, SyncAction syncAction) {}

        public void onCreate(PartyPayload party, CreateAction createAction) {}
        public void onDisband(PartyPayload party) {}
        public void onLeave(PartyPayload party) {}

        public void onKick(PartyPayload party, KickAction kickAction) {}
        public void onInvite(PartyPayload party, InviteAction inviteAction) {}

        public void onAcceptInvite(PartyPayload party) {}
        public void onDeclineInvite(PartyPayload party) {}

        public void onFail(PartyPayload party, FailAction failAction) {}
    }

    public interface ActionData {
        void write(PacketByteBuf buf);
        void handle(ActionListener listener, PartyPayload payload);
    }

    public enum Action {
        SYNC(SyncAction::read),

        CREATE(CreateAction::read),
        DISBAND((p) -> NoDataAction.DISBAND),
        LEAVE((p) -> NoDataAction.LEAVE),

        KICK(KickAction::read),
        INVITE(InviteAction::read),

        ACCEPT_INVITE((p) -> NoDataAction.ACCEPT_INVITE),
        DECLINE_INVITE((p) -> NoDataAction.DECLINE_INVITE),

        FAIL(FailAction::read);

        interface ActionReader {
            ActionData read(PacketByteBuf buf);
        }

        private final ActionReader reader;

        Action(ActionReader reader) {
            this.reader = reader;
        }

        @Nullable
        public ActionData readData(PacketByteBuf buf) {
            if (reader == null) return null;
            return reader.read(buf);
        }
    }

    public final UUID partyId;
    public final UUID actorId;
    public final Action action;
    private final ActionData data;

    private PartyPayload(UUID partyId, UUID actorId, Action action, ActionData data) {
        this.action = action;
        this.partyId = partyId;
        this.actorId = actorId;
        this.data = data;
    }

    public void handleAction(ActionListener listener) {
        data.handle(listener, this);
    }

    public PartyPayload withActorId(UUID actorId) {
        return new PartyPayload(this.partyId, actorId, this.action, this.data);
    }

    /** Returns a new {@link FailAction} that describes an error meant for sending back to the client */
    public PartyPayload fail(String message) {
        return new PartyPayload(
            this.partyId, this.actorId,
            Action.FAIL, new FailAction(this.action, message)
        );
    }

    protected ActionData getData() { return data; }

    @Override
    public PayloadRegistry.PayloadType<PartyPayload> getPayloadType() { return PayloadRegistry.PARTY; }

    public record SyncAction(UUID leaderId, Set<UUID> members, String partyName) implements ActionData {
        public void handle(ActionListener listener, PartyPayload payload) {
            listener.onSync(payload, this);
        }

        public void applyToParty(Party partyToSync) {
            partyToSync.partyName = partyName;
            partyToSync.leaderId = leaderId;

            partyToSync.members.clear();
            partyToSync.members.addAll(members);
        }

        public Party asParty(PartyPayload partyPayload) {
            Party syncedParty = new Party(partyPayload.partyId, partyPayload.actorId, partyName);
            applyToParty(syncedParty);

            return syncedParty;
        }

        public void write(PacketByteBuf buf) {
            buf.writeUuid(this.leaderId);
            buf.writeString(this.partyName, Party.MAX_PARTY_NAME_LENGTH + 1);

            buf.writeInt(this.members.size());
            for (UUID member : this.members) {
                buf.writeUuid(member);
            }
        }

        public static SyncAction read(PacketByteBuf buf) {
            UUID leaderId = buf.readUuid();
            String partyName = buf.readString(Party.MAX_PARTY_NAME_LENGTH + 1);

            int memberCount = buf.readInt();
            Set<UUID> members = new HashSet<>(memberCount);
            for (int i = 0; i < memberCount; i++) {
                members.add(buf.readUuid());
            }

            return new SyncAction(leaderId, members, partyName);
        }
    }

    public record CreateAction(String partyName) implements ActionData {
        public void handle(ActionListener listener, PartyPayload payload) {
            listener.onCreate(payload, this);
        }

        public Party asParty(PartyPayload partyPayload) {
            return new Party(partyPayload.partyId, partyPayload.actorId, partyName);
        }

        public void write(PacketByteBuf buf) {
            buf.writeString(this.partyName);
        }

        public static CreateAction read(PacketByteBuf buf) {
            return new CreateAction(buf.readString());
        }
    }

    public record InviteAction(UUID inviteeId, Instant expiresAt) implements ActionData {
        public void handle(ActionListener listener, PartyPayload payload) {
            listener.onInvite(payload, this);
        }

        public PartyInvite asPartyInvite(PartyPayload partyPayload) {
            PartyInvite invite = new PartyInvite(partyPayload.partyId, partyPayload.actorId, inviteeId, expiresAt);
            invite.setReceived();
            return invite;
        }

        public void write(PacketByteBuf buf) {
            buf.writeUuid(inviteeId);
            buf.writeLong(expiresAt.toEpochMilli());
        }

        public static InviteAction read(PacketByteBuf buf) {
            return new InviteAction(buf.readUuid(), Instant.ofEpochMilli(buf.readLong()));
        }
    }

    public record KickAction(UUID memberId) implements ActionData {
        public void handle(ActionListener listener, PartyPayload payload) {
            listener.onKick(payload, this);
        }

        public void write(PacketByteBuf buf) { buf.writeUuid(memberId); }

        public static KickAction read(PacketByteBuf buf) {
            return new KickAction(buf.readUuid());
        }
    }

    public record FailAction(Action failedAction, String message) implements ActionData {
        public void handle(ActionListener listener, PartyPayload payload) {
            listener.onFail(payload, this);
        }

        public String getTitle() {
            return switch (failedAction) {
                case SYNC -> "Party sync failed";
                case CREATE -> "Party creation failed";
                case DISBAND -> "Party disband failed";
                case LEAVE -> "Failed to leave party";
                case KICK -> "Failed to kick party";
                case INVITE -> "Failed to invite player";
                case ACCEPT_INVITE -> "Failed to accept invite";
                case DECLINE_INVITE -> "Failed to decline invite";
                case FAIL -> "";
            };
        }

        public void write(PacketByteBuf buf) {
            buf.writeByte(failedAction.ordinal());
            buf.writeString(message);
        }

        public static FailAction read(PacketByteBuf buf) {
            return new FailAction(Action.values()[buf.readByte()], buf.readString());
        }
    }

    public enum NoDataAction implements ActionData {
        DISBAND {
            public void handle(ActionListener l, PartyPayload p) { l.onDisband(p); }
        },
        LEAVE {
            public void handle(ActionListener l, PartyPayload p) { l.onLeave(p); }
        },
        ACCEPT_INVITE {
            public void handle(ActionListener l, PartyPayload p) { l.onAcceptInvite(p); }
        },
        DECLINE_INVITE {
            public void handle(ActionListener l, PartyPayload p) { l.onDeclineInvite(p); }
        };

        public void write(PacketByteBuf buf) {}
    }

    public PartyPayload(PacketByteBuf buf) {
        this.action = Action.values()[buf.readByte()];
        this.partyId = buf.readUuid();
        this.actorId = buf.readUuid();
        this.data = action.readData(buf);
    }

    @Override
    public void write(RegistryByteBuf buf) {
        buf.writeByte(action.ordinal());
        buf.writeUuid(partyId);
        buf.writeUuid(actorId);

        if (data != null) data.write(buf);
    }

    public static class Builder {
        private final UUID partyId;
        private final UUID actorId;

        public Builder(UUID partyId, UUID actorId) {
            this.partyId = partyId;
            this.actorId = actorId;
        }

        public PartyPayload sync(Party syncedParty) {
            return sync(new SyncAction(syncedParty.leaderId, syncedParty.members, syncedParty.partyName));
        }

        public PartyPayload sync(SyncAction syncAction) {
            return new PartyPayload(partyId, actorId, Action.SYNC, syncAction);
        }

        public PartyPayload create(Party createdParty) {
            return create(new CreateAction(createdParty.partyName));
        }

        public PartyPayload create(CreateAction createAction) {
            return new PartyPayload(partyId, actorId, Action.CREATE, createAction);
        }

        public PartyPayload disband() {
            return new PartyPayload(partyId, actorId, Action.DISBAND, NoDataAction.DISBAND);
        }

        public PartyPayload leave() {
            return new PartyPayload(partyId, actorId, Action.LEAVE, NoDataAction.LEAVE);
        }

        public PartyPayload kick(KickAction kickAction) {
            return new PartyPayload(partyId, actorId, Action.KICK, kickAction);
        }

        public PartyPayload invite(InviteAction inviteAction) {
            return new PartyPayload(partyId, actorId, Action.INVITE, inviteAction);
        }

        public PartyPayload acceptInvite() {
            return new PartyPayload(partyId, actorId, Action.ACCEPT_INVITE, NoDataAction.ACCEPT_INVITE);
        }

        public PartyPayload declineInvite() {
            return new PartyPayload(partyId, actorId, Action.DECLINE_INVITE, NoDataAction.DECLINE_INVITE);
        }
    }
}
