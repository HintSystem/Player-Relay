package dev.hintsystem.playerrelay.gui;

import net.minecraft.client.gui.DrawContext;

public enum AnchorPoint {
    TOP_LEFT(0, 0),
    TOP_RIGHT(1, 0),
    BOTTOM_LEFT(0, 1),
    BOTTOM_RIGHT(1, 1);

    public final float x;
    public final float y;

    AnchorPoint(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public int[] resolve(DrawContext context, int elementWidth, int elementHeight) {
        int x = (int) (this.x * (context.getScaledWindowWidth() - elementWidth));
        int y = (int) (this.y * (context.getScaledWindowHeight() - elementHeight));

        return new int[] { x, y };
    }
}
