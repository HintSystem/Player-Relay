package dev.hintsystem.playerrelay.gui.screen;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.CommonCore;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import dev.hintsystem.playerrelay.payload.PlayerInventoryPayload;
import dev.hintsystem.playerrelay.payload.player.PlayerEquipmentData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;
import java.util.List;

public abstract class RemoteContainerScreen extends Screen {
    private static final Identifier EQUIPMENT_TEXTURE = CommonCore.identifier("textures/gui/container/remote_player_equipment.png");
    private static final Identifier SLOT_HIGHLIGHT_BACK_TEXTURE = Identifier.withDefaultNamespace("container/slot_highlight_back");
    private static final Identifier SLOT_HIGHLIGHT_FRONT_TEXTURE = Identifier.withDefaultNamespace("container/slot_highlight_front");

    public static final int EQUIPMENT_TEXTURE_HEIGHT = 45;
    public static final int EQUIPMENT_TEXTURE_WIDTH = 176;

    public enum UIEquipmentSlot {
        OFFHAND(EquipmentSlot.OFFHAND, 34, 15, Identifier.withDefaultNamespace("container/slot/shield")),
        FEET(EquipmentSlot.FEET, 107, 28, Identifier.withDefaultNamespace("container/slot/boots")),
        LEGS(EquipmentSlot.LEGS, 107, 1, Identifier.withDefaultNamespace("container/slot/leggings")),
        CHEST(EquipmentSlot.CHEST, 53, 28, Identifier.withDefaultNamespace("container/slot/chestplate")),
        HEAD(EquipmentSlot.HEAD, 53, 1, Identifier.withDefaultNamespace("container/slot/helmet"));

        final EquipmentSlot equipmentSlot;
        final Identifier emptySlotTexture;
        final int slotX, slotY;

        UIEquipmentSlot(EquipmentSlot equipmentSlot, int slotX, int slotY, Identifier emptySlotTexture) {
            this.equipmentSlot = equipmentSlot;
            this.emptySlotTexture = emptySlotTexture;
            this.slotX = slotX; this.slotY = slotY;
        }
    }

    public final List<ItemStack> items;
    public final PlayerInfoPayload playerInfo;
    public final AbstractClientPlayer playerEntity;

    private PlayerEquipmentData prevEquipment;

    public RemoteContainerScreen(Component title, PlayerInventoryPayload inventoryPayload) throws Exception {
        this(title, inventoryPayload, null);
    }

    public RemoteContainerScreen(Component title, PlayerInventoryPayload inventoryPayload, @Nullable PlayerInfoPayload playerPayload) throws Exception {
        super(title);

        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (world == null) throw new Exception("Client not in a world");

        if (playerPayload == null) {
            playerPayload = ClientCore.getTrackedPlayer(inventoryPayload.playerId);
            if (playerPayload == null) throw new Exception("Player not connected to relay");
        }

        this.items = inventoryPayload.inventoryItems;
        this.playerInfo = playerPayload;
        this.playerEntity = new AbstractClientPlayer(world, playerPayload.toGameProfile()) {};
    }

    private void updatePlayerEntityEquipment(PlayerEquipmentData equipmentData) {
        if (prevEquipment != null && !prevEquipment.hasChanged(equipmentData)) return;

        this.prevEquipment = equipmentData.copy();
        equipmentData.applyToPlayer(playerEntity);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.renderTransparentBackground(context);
        renderContainer(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }

    protected abstract void renderContainer(GuiGraphics context, int mouseX, int mouseY, float delta);

    protected void renderPlayerEquipment(GuiGraphics context, int centerX, int topY, int mouseX, int mouseY) {
        PlayerEquipmentData equipmentData = playerInfo.getComponent(PlayerEquipmentData.class);
        if (equipmentData == null) return;

        int x = centerX - (EQUIPMENT_TEXTURE_WIDTH / 2);

        context.blit(RenderPipelines.GUI_TEXTURED, EQUIPMENT_TEXTURE, x, topY, 0.0F, 0.0F, EQUIPMENT_TEXTURE_WIDTH, EQUIPMENT_TEXTURE_HEIGHT, 256, 256);

        updatePlayerEntityEquipment(equipmentData);
        InventoryScreen.renderEntityInInventoryFollowsMouse(context,
            x + 72, topY + 1,
            x + 104, topY + 44,
            22, 0.0625F, mouseX, mouseY, playerEntity);

        for (UIEquipmentSlot slot : UIEquipmentSlot.values()) {
            int slotX = x + slot.slotX;
            int slotY = topY + slot.slotY;
            ItemStack equippedStack = playerEntity.getItemBySlot(slot.equipmentSlot);

            renderSlot(context, equippedStack, slotX, slotY, mouseX, mouseY);
            if (equippedStack.isEmpty()) {
                context.blitSprite(RenderPipelines.GUI_TEXTURED, slot.emptySlotTexture, slotX, slotY, 16, 16);
            }
        }
    }

    protected void renderSlot(GuiGraphics context, ItemStack itemStack, int slotX, int slotY, int mouseX, int mouseY) {
        boolean highlighted = isMouseInSlot(mouseX, mouseY, slotX, slotY);

        if (highlighted) drawSlotHighlightBack(context, slotX, slotY);

        if (!itemStack.isEmpty()) {
            context.renderItem(itemStack, slotX, slotY);
            context.renderItemDecorations(this.font, itemStack, slotX, slotY);

            if (highlighted) {
                drawSlotHighlightFront(context, slotX, slotY);
                drawItemTooltip(context, itemStack, mouseX, mouseY);
            }
        }
    }

    protected static boolean isMouseInSlot(int mouseX, int mouseY, int slotX, int slotY) {
        return mouseX >= slotX - 1 && mouseX < slotX + 17 && mouseY >= slotY - 1 && mouseY < slotY + 17 ;
    }

    protected static void drawSlotHighlightBack(GuiGraphics context, int slotX, int slotY) {
        context.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_BACK_TEXTURE, slotX - 4, slotY - 4, 24, 24);
    }

    protected static void drawSlotHighlightFront(GuiGraphics context, int slotX, int slotY) {
        context.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_FRONT_TEXTURE, slotX - 4, slotY - 4, 24, 24);
    }

    protected void drawItemTooltip(GuiGraphics context, ItemStack itemStack, int mouseX, int mouseY) {
        if (itemStack.isEmpty()) return;

        context.setTooltipForNextFrame(this.font,
            getTooltipFromItem(Minecraft.getInstance(), itemStack),
            itemStack.getTooltipImage(),
            mouseX, mouseY,
            itemStack.get(DataComponents.TOOLTIP_STYLE)
        );
    }
}
