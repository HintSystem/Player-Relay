package dev.hintsystem.playerrelay.gui.screen;

import dev.hintsystem.playerrelay.payload.PlayerInventoryPayload;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;

public class RemoteInventoryScreen extends RemoteContainerScreen {
    private static final Identifier BACKGROUND_TEXTURE = Identifier.ofVanilla("textures/gui/container/generic_54.png");

    public static final int backgroundWidth = 176;

    public static final int inventoryX = 8;
    public static final int inventoryY = 18;
    public static final int hotbarY = inventoryY + (3 * 18) + 4;

    public RemoteInventoryScreen(PlayerInventoryPayload inventoryPayload) throws Exception {
        super(Text.literal("Inventory peek"), inventoryPayload);
    }

    @Override
    public void renderContainer(DrawContext context, int mouseX, int mouseY, float delta) {
        int borderHeight = 17;
        int containerHeight = 83;
        int backgroundHeight = borderHeight + containerHeight;

        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE, x, y, 0.0F, 0.0F, backgroundWidth, borderHeight, 256, 256);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE, x, y + borderHeight, 0.0F, 139.0F, backgroundWidth, containerHeight, 256, 256);

        renderPlayerEquipment(context, x + backgroundWidth / 2, y - EQUIPMENT_TEXTURE_HEIGHT - 5, mouseX, mouseY);

        context.drawText(this.textRenderer, "Inventory: " + playerInfo.getName(), x + 8, y + 7, Colors.DARK_GRAY, false);

        // Render hotbar
        for (int j = 0; j < 9; j++) {
            int slotX = x + inventoryX + j * 18;
            int slotY = y + hotbarY;

            renderSlot(context, (j < items.size()) ? items.get(j) : ItemStack.EMPTY,
                slotX, slotY, mouseX, mouseY);
        }

        // Render inventory
        for (int i = 0; i < 3; i++) {
            int slotY = y + inventoryY + i * 18;
            for (int j = 0; j < 9; j++) {
                int slotX = x + inventoryX + j * 18;
                int slotIndex = 9 + (j + i * 9);

                renderSlot(context, (slotIndex < items.size()) ? items.get(slotIndex) : ItemStack.EMPTY,
                    slotX, slotY, mouseX, mouseY);
            }
        }
    }
}
