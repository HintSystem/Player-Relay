package dev.hintsystem.playerrelay.gui;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import dev.hintsystem.playerrelay.payload.PlayerInventoryPayload;
import dev.hintsystem.playerrelay.payload.player.PlayerEquipmentData;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;

import java.util.List;

public class RemoteInventoryScreen extends Screen {
    private static final Identifier BACKGROUND_TEXTURE = Identifier.ofVanilla("textures/gui/container/generic_54.png");
    private static final Identifier EQUIPMENT_TEXTURE = Identifier.of(PlayerRelay.MOD_ID, "textures/gui/container/remote_player_equipment.png");
    private static final Identifier SLOT_HIGHLIGHT_BACK_TEXTURE = Identifier.ofVanilla("container/slot_highlight_back");
    private static final Identifier SLOT_HIGHLIGHT_FRONT_TEXTURE = Identifier.ofVanilla("container/slot_highlight_front");

    public static final int backgroundWidth = 176;
    public static final int backgroundHeight = 114;
    public static final int equipmentHeight = 45;

    public static final int inventoryX = 8;
    public static final int inventoryY = 18;
    public static final int hotbarY = inventoryY + (3 * 18) + 4;

    public enum UIEquipmentSlot {
        OFFHAND(EquipmentSlot.OFFHAND, 34, 15, Identifier.ofVanilla("container/slot/shield")),
        FEET(EquipmentSlot.FEET, 107, 28, Identifier.ofVanilla("container/slot/boots")),
        LEGS(EquipmentSlot.LEGS, 107, 1, Identifier.ofVanilla("container/slot/leggings")),
        CHEST(EquipmentSlot.CHEST, 53, 28, Identifier.ofVanilla("container/slot/chestplate")),
        HEAD(EquipmentSlot.HEAD, 53, 1, Identifier.ofVanilla("container/slot/helmet"));

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
    public final PlayerInfoPayload player;
    public final AbstractClientPlayerEntity playerEntity;

    private PlayerEquipmentData prevEquipment;

    public RemoteInventoryScreen(PlayerInventoryPayload inventoryPayload) throws Exception {
        super(Text.literal("Inventory peek"));

        ClientWorld clientWorld = MinecraftClient.getInstance().world;
        if (clientWorld == null) throw new Exception("Client is not in a world");
        PlayerInfoPayload playerPayload = PlayerRelay.getNetworkManager().connectedPlayers.get(inventoryPayload.playerId);
        if (playerPayload == null) throw new Exception("Player is not connected to relay");

        this.items = inventoryPayload.inventoryItems;
        this.player = playerPayload;
        this.playerEntity = new AbstractClientPlayerEntity(clientWorld, playerPayload.toGameProfile()) {};
    }

    @Override
    protected void init() {
        super.init();
    }

    private void updatePlayerEntityEquipment(PlayerEquipmentData equipmentData) {
        if (prevEquipment != null && !prevEquipment.hasChanged(equipmentData)) return;

        this.prevEquipment = equipmentData.copy();
        equipmentData.applyToPlayer(playerEntity);

    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderInGameBackground(context);

        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE, x, y, 0.0F, 0.0F, backgroundWidth, 17, 256, 256);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE, x, y + 17, 0.0F, 139.0F, backgroundWidth, 83, 256, 256);

        // Render player and equipment
        PlayerEquipmentData equipmentData = player.getComponent(PlayerEquipmentData.class);
        if (equipmentData != null) {
            int equipmentY = y - equipmentHeight - 5;
            context.drawTexture(RenderPipelines.GUI_TEXTURED, EQUIPMENT_TEXTURE, x, equipmentY, 0.0F, 0.0F, backgroundWidth, equipmentHeight, 256, 256);

            updatePlayerEntityEquipment(equipmentData);
            InventoryScreen.drawEntity(context,
                x + 72, equipmentY + 1,
                x + 104, equipmentY + 44,
                22, 0.0625F, mouseX, mouseY, playerEntity);

            for (UIEquipmentSlot slot : UIEquipmentSlot.values()) {
                ItemStack equippedStack = equipmentData.getEquippedStack(slot.equipmentSlot);

                renderSlot(context, equippedStack, x + slot.slotX, equipmentY + slot.slotY, mouseX, mouseY);
                if (equippedStack.isEmpty()) {
                    context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, slot.emptySlotTexture, x + slot.slotX, equipmentY + slot.slotY, 16, 16);
                }
            }
        }

        // Render player name
        context.drawText(this.textRenderer, "Viewing: " + player.getName(), x + 8, y + 7, Colors.DARK_GRAY, false);

        // Render hotbar
        for (int j = 0; j < 9; j++) {
            int slotX = x + inventoryX + j * 18;
            int slotY = y + hotbarY;

            renderSlot(context, items.get(j), slotX, slotY, mouseX, mouseY);
        }

        // Render inventory
        for (int i = 0; i < 3; i++) {
            int slotY = y + inventoryY + i * 18;
            for (int j = 0; j < 9; j++) {
                int slotX = x + inventoryX + j * 18;

                renderSlot(context, items.get(9 + (j + i * 9)), slotX, slotY, mouseX, mouseY);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderSlot(DrawContext context, ItemStack itemStack, int slotX, int slotY, int mouseX, int mouseY) {
        boolean highlighted = isMouseInSlot(mouseX, mouseY, slotX, slotY);

        if (highlighted) drawSlotHighlightBack(context, slotX, slotY);

        if (!itemStack.isEmpty()) {
            context.drawItem(itemStack, slotX, slotY);
            context.drawStackOverlay(this.textRenderer, itemStack, slotX, slotY);

            if (highlighted) {
                drawSlotHighlightFront(context, slotX, slotY);
                drawItemTooltip(context, itemStack, mouseX, mouseY);
            }
        }
    }

    private boolean isMouseInSlot(int mouseX, int mouseY, int slotX, int slotY) {
        return mouseX >= slotX - 1 && mouseX < slotX + 17 && mouseY >= slotY - 1 && mouseY < slotY + 17 ;
    }

    private void drawSlotHighlightBack(DrawContext context, int slotX, int slotY) {
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_BACK_TEXTURE, slotX - 4, slotY - 4, 24, 24);
    }

    private void drawSlotHighlightFront(DrawContext context, int slotX, int slotY) {
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_FRONT_TEXTURE, slotX - 4, slotY - 4, 24, 24);
    }

    private void drawItemTooltip(DrawContext context, ItemStack itemStack, int mouseX, int mouseY) {
        if (itemStack.isEmpty()) return;

        context.drawTooltip(this.textRenderer,
            getTooltipFromItem(MinecraftClient.getInstance(), itemStack),
            itemStack.getTooltipData(),
            mouseX, mouseY,
            itemStack.get(DataComponentTypes.TOOLTIP_STYLE)
        );
    }

    @Override
    public boolean shouldPause() { return false; }
}
