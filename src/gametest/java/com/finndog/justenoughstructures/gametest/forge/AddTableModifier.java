package com.finndog.justenoughstructures.gametest.forge;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
//? if >=1.21 {
/*import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
*///?} else {
import com.mojang.serialization.Codec;
//?}

/**
 * A loot modifier that adds what one loot table gives to another, under its conditions, as NeoForge's
 * own add_table does: Forge has none of its own, so the test mod registers this one as
 * justenoughstructures_gametest:add_table.
 */
public final class AddTableModifier extends LootModifier {
    //? if >=1.21 {
    /*static final MapCodec<AddTableModifier> CODEC = RecordCodecBuilder.mapCodec(instance -> codecStart(instance)
            .and(ResourceLocation.CODEC.fieldOf("table").forGetter(m -> m.table)).apply(instance, AddTableModifier::new));
    *///?} else {
    static final Codec<AddTableModifier> CODEC = RecordCodecBuilder.create(instance -> codecStart(instance)
            .and(ResourceLocation.CODEC.fieldOf("table").forGetter(m -> m.table)).apply(instance, AddTableModifier::new));
    //?}

    private final ResourceLocation table;

    private AddTableModifier(LootItemCondition[] conditions, ResourceLocation table) {
        super(conditions);
        this.table = table;
    }

    //? if >=26.1 {
    /*@Override
    protected ObjectArrayList<ItemStack> doApply(LootTable rolled, ObjectArrayList<ItemStack> loot, LootContext context) {
        context.getResolver().lookupOrThrow(Registries.LOOT_TABLE).get(ResourceKey.create(Registries.LOOT_TABLE, table))
                .ifPresent(added -> added.value().getRandomItemsRaw(context, loot::add));
        return loot;
    }
    *///?} else if >=1.21 {
    /*@Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        context.getResolver().get(Registries.LOOT_TABLE, ResourceKey.create(Registries.LOOT_TABLE, table))
                .ifPresent(added -> added.value().getRandomItemsRaw(context, loot::add));
        return loot;
    }
    *///?} else {
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        context.getResolver().getLootTable(table).getRandomItemsRaw(context, loot::add);
        return loot;
    }
    //?}

    //? if >=1.21 {
    /*@Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
    *///?} else {
    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
    //?}
}
