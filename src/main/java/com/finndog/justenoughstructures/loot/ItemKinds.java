package com.finndog.justenoughstructures.loot;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.Regs;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
//? if >=1.21 {
/*import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.alchemy.PotionContents;
*///?} else {
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
//?}
//? if >=1.21 && <1.21.2 {
/*import net.minecraft.world.item.EnchantedBookItem;
*///?}

/**
 * The kinds an item comes in that players tell apart, and recipe viewers list one by one: a potion's
 * effect on potions, splash and lingering potions and tipped arrows, and each enchantment on books
 * and gear. Nothing else makes a kind, so a name, damage or another mod's data never does, as in JEI.
 * A kind is written "potion:" or "enchantment:" and the potion's or enchantment's id, the same keys
 * as the odds' variants.
 */
public final class ItemKinds {
    public static final String POTION = "potion:";
    public static final String ENCHANTMENT = "enchantment:";
    /** The items that carry a potion. */
    private static final Set<Item> POTION_ITEMS = Set.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.TIPPED_ARROW);

    private ItemKinds() {
    }

    /** Whether the item is one of the four that carry a potion. */
    public static boolean carriesPotion(Item item) {
        return POTION_ITEMS.contains(item);
    }

    /** The kinds a stack is, each with a level: its potion (level 0), and each enchantment it has, at its level. */
    public static Map<String, Integer> of(ItemStack stack) {
        Map<String, Integer> out = new LinkedHashMap<>();
        //? if >=1.21 {
        /*PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null) {
            potion.potion().flatMap(Holder::unwrapKey).ifPresent(key -> out.put(POTION + Ids.of(key), 0));
        }
        for (Object2IntMap.Entry<Holder<Enchantment>> e : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) {
            e.getKey().unwrapKey().ifPresent(key -> out.merge(ENCHANTMENT + Ids.of(key), e.getIntValue(), Math::max));
        }
        *///?} else {
        Potion potion = PotionUtils.getPotion(stack);
        if (potion != Potions.EMPTY) {
            out.put(POTION + BuiltInRegistries.POTION.getKey(potion), 0);
        }
        EnchantmentHelper.getEnchantments(stack).forEach((enchantment, level) ->
                out.merge(ENCHANTMENT + BuiltInRegistries.ENCHANTMENT.getKey(enchantment), level, Math::max));
        //?}
        return out;
    }

    /**
     * Stacks of the item in each of these kinds, for recipe viewers to match a looked-up stack
     * against: one per potion on the items that carry one, and on an enchanted book one book per
     * enchantment at each of its levels, as the viewers list books. Kinds that don't suit the item,
     * or that this game doesn't have, give nothing. {@code registries} are the world's, which hold
     * the enchantments from 1.21; without them there are no books.
     */
    public static List<ItemStack> stacks(Item item, Collection<String> kinds, RegistryAccess registries) {
        List<ItemStack> out = new ArrayList<>();
        for (String kind : kinds) {
            if (kind.startsWith(POTION) && carriesPotion(item)) {
                ItemStack stack = potionStack(item, ResourceLocation.tryParse(kind.substring(POTION.length())));
                if (stack != null) {
                    out.add(stack);
                }
            } else if (kind.startsWith(ENCHANTMENT) && item == Items.ENCHANTED_BOOK) {
                out.addAll(books(ResourceLocation.tryParse(kind.substring(ENCHANTMENT.length())), registries));
            }
        }
        return out;
    }

    /** An enchanted book of the enchantment at each of its levels, or none if the game doesn't have it. */
    private static List<ItemStack> books(ResourceLocation id, RegistryAccess registries) {
        List<ItemStack> out = new ArrayList<>();
        if (id == null) {
            return out;
        }
        //? if >=1.21 {
        /*if (registries == null) {
            return out;
        }
        Optional<? extends Holder<Enchantment>> found = Regs.holder(registries.registryOrThrow(Registries.ENCHANTMENT), id);
        if (found.isEmpty()) {
            return out;
        }
        Holder<Enchantment> enchantment = found.get();
        for (int level = 1; level <= enchantment.value().getMaxLevel(); level++) {
            out.add(book(new EnchantmentInstance(enchantment, level)));
        }
        *///?} else {
        Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.containsKey(id) ? BuiltInRegistries.ENCHANTMENT.get(id) : null;
        if (enchantment == null) {
            return out;
        }
        for (int level = 1; level <= enchantment.getMaxLevel(); level++) {
            out.add(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, level)));
        }
        //?}
        return out;
    }

    //? if >=1.21.2 {
    /*private static ItemStack book(EnchantmentInstance enchantment) {
        return EnchantmentHelper.createBook(enchantment);
    }
    *///?} else if >=1.21 {
    /*private static ItemStack book(EnchantmentInstance enchantment) {
        return EnchantedBookItem.createForEnchantment(enchantment);
    }
    *///?}

    private static ItemStack potionStack(Item item, ResourceLocation id) {
        if (id == null) {
            return null;
        }
        //? if >=1.21 {
        /*return Regs.holder(BuiltInRegistries.POTION, id).map(potion -> PotionContents.createItemStack(item, potion)).orElse(null);
        *///?} else {
        Potion potion = Regs.value(BuiltInRegistries.POTION, id);
        return potion == null || potion == Potions.EMPTY || !BuiltInRegistries.POTION.containsKey(id) ? null : PotionUtils.setPotion(new ItemStack(item), potion);
        //?}
    }
}
