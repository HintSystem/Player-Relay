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
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class RemoteContainerScreen extends Screen {
    private static final Identifier EQUIPMENT_TEXTURE = Identifier.of(PlayerRelay.MOD_ID, "textures/gui/container/remote_player_equipment.png");
    private static final Identifier SLOT_HIGHLIGHT_BACK_TEXTURE = Identifier.ofVanilla("container/slot_highlight_back");
    private static final Identifier SLOT_HIGHLIGHT_FRONT_TEXTURE = Identifier.ofVanilla("container/slot_highlight_front");

    public static final int EQUIPMENT_TEXTURE_HEIGHT = 45;
    public static final int EQUIPMENT_TEXTURE_WIDTH = 176;

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
    public final PlayerInfoPayload playerInfo;
    public final AbstractClientPlayerEntity playerEntity;

    private PlayerEquipmentData prevEquipment;

    public RemoteContainerScreen(Text title, PlayerInventoryPayload inventoryPayload) throws Exception {
        this(title, inventoryPayload, null);
    }

    public RemoteContainerScreen(Text title, PlayerInventoryPayload inventoryPayload, @Nullable PlayerInfoPayload playerPayload) throws Exception {
        super(title);

        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null) throw new Exception("Client not in a world");

        if (playerPayload == null) {
            playerPayload = PlayerRelay.getNetworkManager().connectedPlayers.get(inventoryPayload.playerId);
            if (playerPayload == null) throw new Exception("Player not connected to relay");
        }

        this.items = inventoryPayload.inventoryItems;
        this.playerInfo = playerPayload;
        this.playerEntity = new AbstractClientPlayerEntity(world, playerPayload.toGameProfile()) {};
    }

    private void updatePlayerEntityEquipment(PlayerEquipmentData equipmentData) {
        if (prevEquipment != null && !prevEquipment.hasChanged(equipmentData)) return;

        this.prevEquipment = equipmentData.copy();
        equipmentData.applyToPlayer(playerEntity);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderInGameBackground(context);
        renderContainer(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }

    protected abstract void renderContainer(DrawContext context, int mouseX, int mouseY, float delta);

    protected void renderPlayerEquipment(DrawContext context, int centerX, int topY, int mouseX, int mouseY) {
        PlayerEquipmentData equipmentData = playerInfo.getComponent(PlayerEquipmentData.class);
        if (equipmentData == null) return;

        int x = centerX - (EQUIPMENT_TEXTURE_WIDTH / 2);

        context.drawTexture(RenderPipelines.GUI_TEXTURED, EQUIPMENT_TEXTURE, x, topY, 0.0F, 0.0F, EQUIPMENT_TEXTURE_WIDTH, EQUIPMENT_TEXTURE_HEIGHT, 256, 256);

        updatePlayerEntityEquipment(equipmentData);
        InventoryScreen.drawEntity(context,
            x + 72, topY + 1,
            x + 104, topY + 44,
            22, 0.0625F, mouseX, mouseY, playerEntity);

        for (UIEquipmentSlot slot : UIEquipmentSlot.values()) {
            int slotX = x + slot.slotX;
            int slotY = topY + slot.slotY;
            ItemStack equippedStack = playerEntity.getEquippedStack(slot.equipmentSlot);

            renderSlot(context, equippedStack, slotX, slotY, mouseX, mouseY);
            if (equippedStack.isEmpty()) {
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, slot.emptySlotTexture, slotX, slotY, 16, 16);
            }
        }
    }

    protected void renderSlot(DrawContext context, ItemStack itemStack, int slotX, int slotY, int mouseX, int mouseY) {
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

    protected static boolean isMouseInSlot(int mouseX, int mouseY, int slotX, int slotY) {
        return mouseX >= slotX - 1 && mouseX < slotX + 17 && mouseY >= slotY - 1 && mouseY < slotY + 17 ;
    }

    protected static void drawSlotHighlightBack(DrawContext context, int slotX, int slotY) {
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_BACK_TEXTURE, slotX - 4, slotY - 4, 24, 24);
    }

    protected static void drawSlotHighlightFront(DrawContext context, int slotX, int slotY) {
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_FRONT_TEXTURE, slotX - 4, slotY - 4, 24, 24);
    }

    protected void drawItemTooltip(DrawContext context, ItemStack itemStack, int mouseX, int mouseY) {
        if (itemStack.isEmpty()) return;

        context.drawTooltip(this.textRenderer,
            getTooltipFromItem(MinecraftClient.getInstance(), itemStack),
            itemStack.getTooltipData(),
            mouseX, mouseY,
            itemStack.get(DataComponentTypes.TOOLTIP_STYLE)
        );
    }
}
