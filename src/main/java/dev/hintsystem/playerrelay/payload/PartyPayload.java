package dev.hintsystem.playerrelay.payload;

import dev.hintsystem.playerrelay.party.Party;
import dev.hintsystem.playerrelay.party.PartyInvite;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;

import org.jetbrains.annotations.Nullable;
import java.time.Instant;
import java.util.UUID;

public class PartyPayload implements Payload {

    public enum Action {
        SYNC(ActionListener::onSync, SyncAction::read),

        CREATE(ActionListener::onCreate, CreateAction::read),
        DISBAND(ActionListener::onDisband),
        LEAVE(ActionListener::onLeave),

        KICK(ActionListener::onKick, KickAction::read),
        INVITE(ActionListener::onInvite, InviteAction::read),

        ACCEPT_INVITE(ActionListener::onAcceptInvite),
        DECLINE_INVITE(ActionListener::onDeclineInvite);

        interface ActionReader<T extends ActionData> {
            T read(PartyPayload payload, PacketByteBuf buf);
        }

        interface ActionDataHandler<T extends ActionData> {
            void handle(ActionListener listener, PartyPayload party, T actionData);
        }

        interface NullActionDataHandler {
            void handle(ActionListener listener, PartyPayload party);
        }

        private final ActionReader<? extends ActionData> reader;
        private final ActionDataHandler<? extends ActionData> dataHandler;
        private final NullActionDataHandler nullHandler;

        Action(NullActionDataHandler handler) {
            this.reader = null;
            this.dataHandler = null;
            this.nullHandler = handler;
        }

        <T extends ActionData> Action(ActionDataHandler<T> handler, ActionReader<T> reader) {
            this.reader = reader;
            this.dataHandler = handler;
            this.nullHandler = null;
        }

        @Nullable
        public ActionData readData(PartyPayload partyPayload, PacketByteBuf buf) {
            if (reader == null) return null;
            return reader.read(partyPayload, buf);
        }

        @SuppressWarnings("unchecked")
        public void handle(ActionListener listener, PartyPayload party) {
            if (dataHandler != null) {
                ((ActionDataHandler<ActionData>) dataHandler)
                    .handle(listener, party, party.getData());
            } else if (nullHandler != null) {
                nullHandler
                    .handle(listener, party);
            }
        }
    }

    public final Action action;
    public final UUID partyId;
    public final UUID actorId;
    private final ActionData data;

    PartyPayload(Action action, UUID partyId, UUID actorId, ActionData data) {
        this.action = action;
        this.partyId = partyId;
        this.actorId = actorId;
        this.data = data;
    }

    public abstract static class ActionListener {
        public void onSync(PartyPayload party, SyncAction syncAction) {}

        public void onCreate(PartyPayload party, CreateAction createAction) {}
        public void onDisband(PartyPayload party) {}
        public void onLeave(PartyPayload party) {}

        public void onKick(PartyPayload party, KickAction kickAction) {}
        public void onInvite(PartyPayload party, InviteAction inviteAction) {}

        public void onAcceptInvite(PartyPayload party) {}
        public void onDeclineInvite(PartyPayload party) {}
    }

    public void handle(ActionListener listener) { action.handle(listener, this); }

    protected ActionData getData() { return data; }

    @Override
    public PayloadRegistry.PayloadType<PartyPayload> getPayloadType() { return PayloadRegistry.PARTY; }

    public interface ActionData {
        void write(PacketByteBuf buf);
    }

    public static class SyncAction extends Party implements ActionData {
        SyncAction(UUID partyId, UUID leaderId) { super(partyId, leaderId); }

        public void write(PacketByteBuf buf) {
            buf.writeUuid(this.leaderId);

            buf.writeInt(this.getMemberCount());
            for (UUID member : this.members) {
                buf.writeUuid(member);
            }
        }

        public static SyncAction read(PartyPayload partyPayload, PacketByteBuf buf) {
            SyncAction syncParty = new SyncAction(partyPayload.partyId, buf.readUuid());

            int memberCount = buf.readInt();
            for (int i = 0; i < memberCount; i++) {
                syncParty.members.add(buf.readUuid());
            }

            return syncParty;
        }
    }

    public record CreateAction(String partyName) implements ActionData {
        public void write(PacketByteBuf buf) {
            buf.writeString(this.partyName);
        }

        public static CreateAction read(PartyPayload partyPayload, PacketByteBuf buf) {
            return new CreateAction(buf.readString());
        }
    }

    public static class InviteAction extends PartyInvite implements ActionData {
        InviteAction(UUID partyId, UUID inviterId, UUID inviteeId, Instant expiresAt) { super(partyId, inviterId, inviteeId, expiresAt); }

        public void write(PacketByteBuf buf) {
            buf.writeUuid(inviteeId);
            buf.writeLong(expiresAt.toEpochMilli());
        }

        public static InviteAction read(PartyPayload partyPayload, PacketByteBuf buf) {
            return new InviteAction(partyPayload.partyId, partyPayload.actorId, buf.readUuid(), Instant.ofEpochMilli(buf.readLong()));
        }
    }

    public record KickAction(UUID targetId) implements ActionData {
        public void write(PacketByteBuf buf) { buf.writeUuid(targetId); }

        public static KickAction read(PartyPayload partyPayload, PacketByteBuf buf) {
            return new KickAction(buf.readUuid());
        }
    }

    public PartyPayload(PacketByteBuf buf) {
        this.action = Action.values()[buf.readByte()];
        this.partyId = buf.readUuid();
        this.actorId = buf.readUuid();
        this.data = action.readData(this, buf);
    }

    @Override
    public void write(RegistryByteBuf buf) {
        buf.writeByte(action.ordinal());
        buf.writeUuid(partyId);
        buf.writeUuid(actorId);

        if (data != null) data.write(buf);
    }
}
