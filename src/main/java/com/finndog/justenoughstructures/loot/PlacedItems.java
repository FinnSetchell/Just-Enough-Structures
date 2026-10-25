package com.finndog.justenoughstructures.loot;

import com.finndog.justenoughstructures.Nbt;
import com.finndog.justenoughstructures.Regs;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

/**
 * Items a structure places itself instead of rolling them from a loot table: what an item frame or
 * an armour stand holds, and what a container saved with items and no loot table holds. Read from
 * block entity and entity NBT, which name an item by its id under "id" on every version. What mobs
 * hold isn't counted, as it isn't there to take.
 */
public final class PlacedItems {
    private static final Set<String> HOLDERS = Set.of("minecraft:item_frame", "minecraft:glow_item_frame", "minecraft:armor_stand");

    private PlacedItems() {
    }

    /**
     * Adds what a block entity holds, if it has no loot table: a container's items, or a decorated
     * pot's one. Fuel is left out, as so many structures leave coal in a furnace that it would crowd
     * out what they really place.
     */
    public static void fromBlockEntity(CompoundTag tag, Set<ResourceLocation> out) {
        if (Nbt.hasString(tag, "LootTable")) {
            return;
        }
        int fuel = fuelSlot(tag);
        for (Tag item : Nbt.list(tag, "Items", Tag.TAG_COMPOUND)) {
            if (!(item instanceof CompoundTag slot && Nbt.hasNumber(slot, "Slot") && Nbt.getInt(slot, "Slot") == fuel)) {
                add(item, out);
            }
        }
        add(Nbt.compound(tag, "item"), out);
    }

    /**
     * The slot a furnace, smoker or blast furnace burns from, or a brewing stand's blaze powder slot,
     * or -1. Told apart by the keys only they save, before and after 1.21.4, as a template's block
     * entity may leave its id out.
     */
    private static int fuelSlot(CompoundTag tag) {
        if (Nbt.hasNumber(tag, "BurnTime") || Nbt.hasNumber(tag, "lit_time_remaining")) {
            return 1;
        }
        return Nbt.hasNumber(tag, "BrewTime") ? 4 : -1;
    }

    /** Adds what an item frame or armour stand holds, or what a minecart or boat with a chest holds if it has no loot table. */
    static void fromEntity(CompoundTag tag, Set<ResourceLocation> out) {
        if (!Nbt.hasString(tag, "LootTable")) {
            for (Tag item : Nbt.list(tag, "Items", Tag.TAG_COMPOUND)) {
                add(item, out);
            }
        }
        if (!HOLDERS.contains(Nbt.string(tag, "id"))) {
            return;
        }
        add(Nbt.compound(tag, "Item"), out);
        // An armour stand's armour and hands: two lists before 1.21.5, one compound by slot from it.
        for (String key : List.of("ArmorItems", "HandItems")) {
            for (Tag item : Nbt.list(tag, key, Tag.TAG_COMPOUND)) {
                add(item, out);
            }
        }
        CompoundTag equipment = Nbt.compound(tag, "equipment");
        for (String slot : Nbt.keys(equipment)) {
            add(equipment.get(slot), out);
        }
    }

    /** Adds an item's id, unless the stack is empty or the item isn't one the game has. */
    private static void add(Tag tag, Set<ResourceLocation> out) {
        if (!(tag instanceof CompoundTag item) || !Nbt.hasString(item, "id")) {
            return;
        }
        // Count before 1.20.5, count from it, which is left out when it's 1.
        String count = Nbt.hasNumber(item, "Count") ? "Count" : "count";
        if (Nbt.hasNumber(item, count) && Nbt.getInt(item, count) <= 0) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(Nbt.string(item, "id"));
        if (id != null && Regs.value(BuiltInRegistries.ITEM, id) != Items.AIR) {
            out.add(id);
        }
    }
}
