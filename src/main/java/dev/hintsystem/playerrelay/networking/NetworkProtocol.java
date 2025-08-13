package dev.hintsystem.playerrelay.networking;

public enum NetworkProtocol {
    TCP(0),
    UDP(1);

    private final int id;

    NetworkProtocol(int id) {
        this.id = id;
    }

    public int getId() { return id; }

    public static NetworkProtocol fromId(int id) {
        for (NetworkProtocol protocol : values()) {
            if (protocol.id == id) {
                return protocol;
            }
        }
        throw new IllegalArgumentException("Unknown protocol id: " + id);
    }
}