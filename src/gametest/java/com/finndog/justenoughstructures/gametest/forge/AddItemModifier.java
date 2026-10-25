package com.finndog.justenoughstructures.gametest.forge;

import com.finndog.justenoughstructures.Regs;
import com.mojang.datafixers.kinds.App;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
//? if >=26.1 {
/*import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.eventbus.api.bus.BusGroup;
*///?} else if >=1.21 {
/*import com.mojang.serialization.MapCodec;
import net.minecraftforge.eventbus.api.IEventBus;
*///?} else {
import com.mojang.serialization.Codec;
import net.minecraftforge.eventbus.api.IEventBus;
//?}

/**
 * A loot modifier that adds an item to everything one loot table gives, the way Forge mods add their
 * items to vanilla chests. The test mod registers it for the game test of what Found in lists.
 */
public record AddItemModifier(ResourceLocation table, ResourceLocation item) implements IGlobalLootModifier {
    //? if >=1.21 {
    /*private static final MapCodec<AddItemModifier> CODEC = RecordCodecBuilder.mapCodec(AddItemModifier::fields);
    *///?} else {
    private static final Codec<AddItemModifier> CODEC = RecordCodecBuilder.create(AddItemModifier::fields);
    //?}

    private static App<RecordCodecBuilder.Mu<AddItemModifier>, AddItemModifier> fields(RecordCodecBuilder.Instance<AddItemModifier> instance) {
        return instance.group(
                ResourceLocation.CODEC.fieldOf("table").forGetter(AddItemModifier::table),
                ResourceLocation.CODEC.fieldOf("item").forGetter(AddItemModifier::item)).apply(instance, AddItemModifier::new);
    }

    /** Registers the modifier as justenoughstructures_gametest:add_item. */
    //? if >=26.1 {
    /*static void register(BusGroup modBus) {
    *///?} else {
    static void register(IEventBus modBus) {
    //?}
        var codecs = DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, "justenoughstructures_gametest");
        codecs.register("add_item", () -> CODEC);
        codecs.register(modBus);
    }

    //? if >=26.1 {
    /*@Override
    public ObjectArrayList<ItemStack> apply(LootTable lootTable, ObjectArrayList<ItemStack> loot, LootContext context) {
        return add(loot, context);
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
    *///?} else if >=1.21 {
    /*@Override
    public ObjectArrayList<ItemStack> apply(ObjectArrayList<ItemStack> loot, LootContext context) {
        return add(loot, context);
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
    *///?} else {
    @Override
    public ObjectArrayList<ItemStack> apply(ObjectArrayList<ItemStack> loot, LootContext context) {
        return add(loot, context);
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
    //?}

    private ObjectArrayList<ItemStack> add(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (table.equals(context.getQueriedLootTableId())) {
            loot.add(new ItemStack(Regs.value(BuiltInRegistries.ITEM, item)));
        }
        return loot;
    }
}
