package dev.hintsystem.playerrelay.gui.screen;

import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import dev.hintsystem.playerrelay.payload.PlayerInventoryPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.CommonColors;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

public class RemoteEnderChestScreen extends RemoteContainerScreen {
    private static final ResourceLocation BACKGROUND_TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public static final int backgroundWidth = 176;

    public static final int inventoryX = 8;
    public static final int inventoryY = 18;

    public RemoteEnderChestScreen(PlayerInventoryPayload inventoryPayload) throws Exception {
        this(inventoryPayload, null);
    }

    public RemoteEnderChestScreen(PlayerInventoryPayload inventoryPayload, @Nullable PlayerInfoPayload playerPayload) throws Exception {
        super(Component.literal("Ender chest peek"), inventoryPayload, playerPayload);
    }

    @Override
    public void renderContainer(GuiGraphics context, int mouseX, int mouseY, float delta) {
        int containerHeight = 3 * 18 + 17;
        int borderHeight = 8;
        int backgroundHeight = borderHeight + containerHeight;

        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        context.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE, x, y, 0.0F, 0.0F, backgroundWidth, containerHeight, 256, 256);
        context.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE, x, y + containerHeight, 0.0F, 215.0F, backgroundWidth, borderHeight, 256, 256);

        renderPlayerEquipment(context, x + backgroundWidth / 2, y - EQUIPMENT_TEXTURE_HEIGHT - 5, mouseX, mouseY);

        context.drawString(this.font, "Ender chest: " + playerInfo.getName(), x + 8, y + 7, CommonColors.DARK_GRAY, false);

        // Render inventory
        for (int i = 0; i < 3; i++) {
            int slotY = y + inventoryY + i * 18;
            for (int j = 0; j < 9; j++) {
                int slotX = x + inventoryX + j * 18;
                int slotIndex = j + i * 9;

                renderSlot(context, (slotIndex < items.size()) ? items.get(slotIndex) : ItemStack.EMPTY,
                    slotX, slotY, mouseX, mouseY);
            }
        }
    }
}
