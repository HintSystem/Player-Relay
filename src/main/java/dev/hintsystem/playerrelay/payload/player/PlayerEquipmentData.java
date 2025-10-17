package dev.hintsystem.playerrelay.payload.player;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.util.collection.DefaultedList;

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

    public final DefaultedList<ItemStack> equipment = DefaultedList.ofSize(EQUIPMENT_SLOT_ORDER.length, ItemStack.EMPTY);

    public PlayerEquipmentData() {}

    public PlayerEquipmentData(PlayerEntity player) {
        for (int i = 0; i < EQUIPMENT_SLOT_ORDER.length; i++) {
            EquipmentSlot slot = EQUIPMENT_SLOT_ORDER[i];
            ItemStack stack = player.getEquippedStack(slot);
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
    public void applyToPlayer(PlayerEntity player) {
        for (int i = 0; i < EQUIPMENT_SLOT_ORDER.length; i++) {
            player.equipStack(EQUIPMENT_SLOT_ORDER[i], equipment.get(i));
        }
    }

    @Override
    public void write(RegistryByteBuf buf) {
        for (int i = 0; i < EQUIPMENT_SLOT_ORDER.length; i++) {
            ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, equipment.get(i));
        }
    }

    @Override
    public void read(RegistryByteBuf buf) {
        for (int i = 0; i < EQUIPMENT_SLOT_ORDER.length; i++) {
            ItemStack stack = ItemStack.OPTIONAL_PACKET_CODEC.decode(buf);
            this.equipment.set(i, stack);
        }
    }

    @Override
    public boolean hasChanged(PlayerDataComponent other) {
        if (!(other instanceof PlayerEquipmentData otherEquipment)) return true;

        for (int i = 0; i < EQUIPMENT_SLOT_ORDER.length; i++) {
            if (!ItemStack.areEqual(this.equipment.get(i), otherEquipment.equipment.get(i))) {
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
