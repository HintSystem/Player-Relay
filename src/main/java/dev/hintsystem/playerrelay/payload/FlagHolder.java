package dev.hintsystem.playerrelay.payload;

import io.netty.buffer.ByteBuf;

public abstract class FlagHolder<E extends Enum<E>> {
    protected int flags;

    /** Returns true if the given flag is set */
    public boolean hasFlag(E flag) {
        int mask = 1 << flag.ordinal();
        return (flags & mask) != 0;
    }

    /** Sets or clears the given flag based on value */
    public void setFlag(E flag, boolean value) {
        int mask = 1 << flag.ordinal();
        if (value) {
            flags |= mask;
        } else {
            flags &= ~mask;
        }
    }

    /**
     * Writes the flags to a ByteBuf.
     * @param buf The ByteBuf to write to.
     * @param bytes The number of bytes to write (1, 2, or 4).
     */
    public void writeFlags(ByteBuf buf, int bytes) {
        switch (bytes) {
            case 1 -> buf.writeByte(flags & 0xFF);
            case 2 -> buf.writeShort(flags & 0xFFFF);
            case 4 -> buf.writeInt(flags);
            default -> throw new IllegalArgumentException("Unsupported flag size: " + bytes);
        }
    }

    /**
     * Reads the flags from a ByteBuf.
     * @param buf The ByteBuf to read from.
     * @param bytes The number of bytes to read (1, 2, or 4).
     */
    public void readFlags(ByteBuf buf, int bytes) {
        switch (bytes) {
            case 1 -> flags = buf.readUnsignedByte();
            case 2 -> flags = buf.readUnsignedShort();
            case 4 -> flags = buf.readInt();
            default -> throw new IllegalArgumentException("Unsupported flag size: " + bytes);
        }
    }

    /** Returns true if both flag sets are identical. */
    public boolean equalsFlags(FlagHolder<?> other) {
        return other != null && this.flags == other.flags;
    }

    public int getFlags() { return flags; }

    public void setFlags(int flags) { this.flags = flags; }
}