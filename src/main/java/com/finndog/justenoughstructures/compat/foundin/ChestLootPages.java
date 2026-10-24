package com.finndog.justenoughstructures.compat.foundin;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.loot.LootIndex;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

/**
 * EMI Loot's chest loot pages show a loot table's items under the table's name, but not which
 * structures use it. The name stands for a stack of EMI Loot's own, and clicking it asks EMI for the
 * recipes that make that stack, so JES adds a table row for each structure using the table, made
 * for that stack. EMI Loot is only reached by reflection: without it nothing is made. Kept free of
 * the viewers' classes so it can be tested without them.
 */
public final class ChestLootPages {
    private static final String MOD = "emi_loot";
    private static final String STACK = "fzzyhmstrs.emi_loot.util.stack.ChestLootEmiStack";

    private ChestLootPages() {
    }

    /** A loot table, EMI Loot's stack for it, and a table row for each structure that uses it. */
    public record Page(ResourceLocation table, Object stack, List<FoundInRecipe> rows) {
    }

    /** Whether EMI Loot is installed. */
    public static boolean installed() {
        return JustEnoughStructures.modVersions().containsKey(MOD);
    }

    /**
     * A page for each loot table in the index. None without EMI Loot, or when the player's setting
     * leaves out JES's rows or hides EMI Loot's tab, as then there's nothing to link.
     */
    public static List<Page> pages(LootIndex index, Function<ResourceLocation, String> names) {
        if (index == null || !shown()) {
            return List.of();
        }
        Function<ResourceLocation, Object> stacks = stackMaker();
        return stacks == null ? List.of() : pages(index, names, stacks);
    }

    /** As {@link #pages(LootIndex, Function)}, with {@code stacks} making a table's stack, or null for none. Public for the game tests. */
    public static List<Page> pages(LootIndex index, Function<ResourceLocation, String> names, Function<ResourceLocation, Object> stacks) {
        if (index == null || !shown()) {
            return List.of();
        }
        List<Page> out = new ArrayList<>();
        FoundInRecipe.byTable(index, names).forEach((table, rows) -> {
            Object stack = stacks.apply(table);
            if (stack != null) {
                out.add(new Page(table, stack, rows));
            }
        });
        return out;
    }

    private static boolean shown() {
        return ChestLootTabs.showsOwn() && !ChestLootTabs.hidesOthers();
    }

    /** Makes EMI Loot's stack for a table, or is null without EMI Loot or if its class isn't as expected. */
    private static Function<ResourceLocation, Object> stackMaker() {
        if (!installed()) {
            return null;
        }
        Constructor<?> make;
        try {
            make = Class.forName(STACK).getConstructor(ResourceLocation.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            JesLog.warnOnce("emi-loot-stack", "Couldn't find EMI Loot's chest loot stack, so its pages don't list structures", e);
            return null;
        }
        return table -> {
            try {
                return make.newInstance(table);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                JesLog.warnOnce("emi-loot-stack-for", "Couldn't make EMI Loot's stack for {}, so its page doesn't list structures", table, e);
                return null;
            }
        };
    }
}
