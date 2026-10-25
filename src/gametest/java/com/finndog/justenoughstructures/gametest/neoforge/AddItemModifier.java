package com.finndog.justenoughstructures.gametest.neoforge;

import com.finndog.justenoughstructures.Regs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * A loot modifier that adds an item to everything one loot table gives, the way NeoForge mods add
 * their items to vanilla chests. The test mod registers it for the game test of what Found in lists.
 */
public record AddItemModifier(ResourceLocation table, ResourceLocation item) implements IGlobalLootModifier {
    private static final MapCodec<AddItemModifier> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("table").forGetter(AddItemModifier::table),
            ResourceLocation.CODEC.fieldOf("item").forGetter(AddItemModifier::item)).apply(instance, AddItemModifier::new));

    /** Registers the modifier as justenoughstructures_gametest:add_item. */
    static void register(IEventBus modBus) {
        DeferredRegister<MapCodec<? extends IGlobalLootModifier>> codecs = DeferredRegister.create(
                NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, "justenoughstructures_gametest");
        codecs.register("add_item", () -> CODEC);
        codecs.register(modBus);
    }

    @Override
    public ObjectArrayList<ItemStack> apply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (table.equals(context.getQueriedLootTableId())) {
            loot.add(new ItemStack(Regs.value(BuiltInRegistries.ITEM, item)));
        }
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    //? if >=26.1 {
    /*@Override
    public int priority() {
        return DEFAULT_PRIORITY;
    }
    *///?}
}
