package dev.hintsystem.playerrelay.networking;

public enum P2PMessageType {
    UDP_HANDSHAKE(0, false),
    UDP_PING(1, false),
    CHAT(2),
    PLAYER_INFO(3),
    PLAYER_POSITION(4),
    PLAYER_DISCONNECT(5),
    PACKET(6);

    private final byte id;
    private final boolean shouldForward;

    P2PMessageType(int id) {
        this.id = (byte) id;
        this.shouldForward = true;
    }

    P2PMessageType(int id, boolean shouldForward) {
        this.id = (byte) id;
        this.shouldForward = shouldForward;
    }

    public byte getId() {
        return id;
    }
    public boolean shouldForward() { return shouldForward; }

    public static P2PMessageType fromId(byte id) {
        for (P2PMessageType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown P2PMessageType id: " + id);
    }
}
