package dev.hintsystem.playerrelay.payload.player;

import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class PlayerEquipmentData implements PlayerDataComponent {
    public static final EquipmentSlot[] EQUIPMENT_SLOT_ORDER = new EquipmentSlot[] {
        EquipmentSlot.MAINHAND,
        EquipmentSlot.OFFHAND,
        EquipmentSlot.FEET,
        EquipmentSlot.LEGS,
        EquipmentSlot.CHEST,
        EquipmentSlot.HEAD,
    };

    private static final Map<EquipmentSlot, Integer> SLOT_TO_INDEX = new HashMap<>();
    static {
        for (int i = 0; i < EQUIPMENT_SLOT_ORDER.length; i++) {
            SLOT_TO_INDEX.put(EQUIPMENT_SLOT_ORDER[i], i);
        }
    }

    public final NonNullList<ItemStack> equipment = NonNullList.withSize(EQUIPMENT_SLOT_ORDER.length, ItemStack.EMPTY);

    public PlayerEquipmentData() {}

    public PlayerEquipmentData(Player player) {
        for (int i = 0; i < EQUIPMENT_SLOT_ORDER.length; i++) {
            EquipmentSlot slot = EQUIPMENT_SLOT_ORDER[i];
            ItemStack stack = player.getItemBySlot(slot);
            this.equipment.set(i, stack.copy());
        }
    }

    public void forEach(BiConsumer<EquipmentSlot, ItemStack> consumer) {
        for (int i = 0; i < EQUIPMENT_SLOT_ORDER.length; i++) {
            consumer.accept(EQUIPMENT_SLOT_ORDER[i], equipment.get(i));
        }
    }

    public ItemStack getEquippedStack(EquipmentSlot slot) {
        Integer index = SLOT_TO_INDEX.get(slot);
        return (index != null) ? equipment.get(index) : ItemStack.EMPTY;
    }

    @Override
    public void applyToPlayer(Player player) {
        for (int i = 0; i < EQUIPMENT_SLOT_ORDER.length; i++) {
            player.setItemSlot(EQUIPMENT_SLOT_ORDER[i], equipment.get(i));
        }
    }

    @Override
    public void write(RegistryFriendlyByteBuf buf) {
        for (int i = 0; i < EQUIPMENT_SLOT_ORDER.length; i++) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, equipment.get(i));
        }
    }

    @Override
    public void read(RegistryFriendlyByteBuf buf) {
        for (int i = 0; i < EQUIPMENT_SLOT_ORDER.length; i++) {
            ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            this.equipment.set(i, stack);
        }
    }

    @Override
    public boolean hasChanged(PlayerDataComponent other) {
        if (!(other instanceof PlayerEquipmentData otherEquipment)) return true;

        for (int i = 0; i < EQUIPMENT_SLOT_ORDER.length; i++) {
            if (!ItemStack.matches(this.equipment.get(i), otherEquipment.equipment.get(i))) {
                return true;
            }
        }

        return false;
    }

    @Override
    public PlayerEquipmentData copy() {
        PlayerEquipmentData copy = new PlayerEquipmentData();
        for (int i = 0; i < this.equipment.size(); i++) {
            copy.equipment.set(i, this.equipment.get(i).copy());
        }
        return copy;
    }
}
