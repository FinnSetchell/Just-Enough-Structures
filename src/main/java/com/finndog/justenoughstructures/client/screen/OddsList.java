package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.Regs;
import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.loot.ItemKinds;
import com.finndog.justenoughstructures.loot.LootOdds;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
//? if >=1.21 {
/*import com.finndog.justenoughstructures.Ids;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
*///?}

/**
 * Rows of items with how likely each is: its icon and name, then a line with how many come, a bar
 * and the chance.
 */
public final class OddsList {
    static final int ROW = 20;
    /** Below this chance an item counts as rare and stands out. */
    static final float RARE_BELOW = 0.1f;
    static int rare() {
        return Gui.ink(0xFF8A5A00, Gui.WARN_ON_DARK);
    }
    private static final int RARE_BAR = 0xFFC08A20;

    private OddsList() {
    }

    /** How tall so many rows are, drawn a pixel apart: the gap comes between rows, not after the last. */
    static int height(int rows) {
        return Math.max(0, rows * (ROW + 1) - 1);
    }

    /** What shows for odds with no items: that the table never gives anything, or that none of its rolls worked. */
    public static Component noItems(LootOdds odds) {
        return Component.translatable(odds.unrollable() ? "screen.justenoughstructures.cant_roll" : "screen.justenoughstructures.always_empty");
    }

    /** A table's rows, most likely first, or rarest first. */
    static List<LootOdds.Row> sorted(LootOdds odds, boolean rarestFirst) {
        List<LootOdds.Row> rows = new ArrayList<>(odds.rows());
        if (rarestFirst) {
            rows.sort(Comparator.comparingInt(LootOdds.Row::hits));
        }
        return rows;
    }

    /**
     * Names for rows, with the item's id made readable where two rows would read the same, like two
     * music discs. A row of several kinds is named after the item ("Potion"), and one of a single kind
     * after its stack ("Potion of Healing").
     */
    public static Map<LootOdds.Row, String> names(List<LootOdds.Row> rows) {
        Map<String, Integer> counts = new HashMap<>();
        rows.forEach(r -> counts.merge(name(r), 1, Integer::sum));
        Map<LootOdds.Row, String> out = new HashMap<>();
        for (LootOdds.Row row : rows) {
            String name = name(row);
            out.put(row, counts.get(name) > 1 ? StructureNames.pretty(BuiltInRegistries.ITEM.getKey(row.example().getItem()).getPath()) : name);
        }
        return out;
    }

    private static String name(LootOdds.Row row) {
        return row.kinds().size() > 1 ? StructureNames.plainItem(row.example().getItem()).getString() : row.example().getHoverName().getString();
    }

    /** The stack a row shows now. */
    public static ItemStack icon(LootOdds.Row row) {
        return icon(row, System.currentTimeMillis());
    }

    /**
     * The stack a row shows at this time, in milliseconds: with several kinds, each in turn for a
     * second, most likely first, every row turning at the same moment, as EMI's lists do.
     */
    public static ItemStack icon(LootOdds.Row row, long millis) {
        if (row.kinds().size() < 2) {
            return row.kinds().isEmpty() ? row.example() : row.kinds().values().iterator().next().example();
        }
        List<LootOdds.Row.Kind> kinds = new ArrayList<>(row.kinds().values());
        kinds.sort(Comparator.comparingInt((LootOdds.Row.Kind k) -> -k.hits()));
        return cycle(kinds.stream().map(LootOdds.Row.Kind::example).toList(), row.example(), millis);
    }

    /** Each stack in turn for a second, or {@code fallback} with none. */
    static ItemStack cycle(List<ItemStack> stacks, ItemStack fallback, long millis) {
        return stacks.isEmpty() ? fallback : stacks.get((int) (Math.floorMod(millis / 1000, (long) stacks.size())));
    }

    /** One kind of an item and its chance in each container. */
    public record KindChance(String key, ItemStack example, double chance) {
    }

    /** A row's kinds, each with its chance in each container, where {@code chance} is the item's. */
    public static List<KindChance> kinds(LootOdds.Row row, float chance) {
        List<KindChance> out = new ArrayList<>();
        row.kinds().forEach((key, kind) -> out.add(new KindChance(key, kind.example(), (double) chance * kind.hits() / Math.max(1, row.hits()))));
        return out;
    }

    /** A chance as a short percentage, with a decimal place for rare things, and {@code <0.1%} for the rarest ({@link FoundIn#tenthPercent}). */
    public static String percent(float chance) {
        return chance >= 0.1f ? Math.round(chance * 100) + "%" : FoundIn.tenthPercent(chance);
    }

    /**
     * How many come in a container: "2-9 (avg 5)", or "3 at a time" when it's always the same. Not how
     * many kinds a row stands for: every row's bar starts after the widest of these, and in a chest's
     * narrow list that would leave no room for bars; the picture and the tooltip show the kinds.
     */
    static String counts(LootOdds.Row row) {
        float average = (float) row.total() / row.hits();
        return row.min() == row.max()
                ? Component.translatable("screen.justenoughstructures.count_exact", row.min()).getString()
                : Component.translatable("screen.justenoughstructures.count_range", row.min(), row.max(), Math.round(average)).getString();
    }

    /**
     * Whether kinds are enchantments on something other than a book, like a sword, which can have
     * several at once and is said to come with them, where a potion or a book comes as one.
     */
    private static boolean enchantedGear(ItemStack example, Collection<String> kinds) {
        return !example.is(Items.ENCHANTED_BOOK) && !kinds.isEmpty() && kinds.stream().allMatch(k -> k.startsWith(ItemKinds.ENCHANTMENT));
    }

    /**
     * One row: the item, its name, {@code detail} under it with a bar, and the chance at the right.
     * Rare ones are drawn in amber.
     */
    static void drawRow(GuiGraphics g, Font font, ItemStack stack, String name, String detail, float chance, int x, int y, int right, boolean hovered) {
        drawRow(g, font, stack, name, detail, chance, x, y, right, hovered, -1);
    }

    /** How wide the widest of a list's details is, so every row's bar can start at the same place. */
    static int detailRoom(Font font, Iterable<String> details) {
        int widest = 0;
        for (String detail : details) {
            widest = Math.max(widest, Gui.fineWidth(font, detail));
        }
        return widest;
    }

    /** With {@code detailRoom} from {@link #detailRoom}, the bar starts after it rather than after this row's own detail. */
    static void drawRow(GuiGraphics g, Font font, ItemStack stack, String name, String detail, float chance, int x, int y, int right, boolean hovered,
                        int detailRoom) {
        boolean rare = chance < RARE_BELOW;
        if (hovered) {
            g.fill(x, y, right, y + ROW, Gui.rowHover());
        }
        Gui.slot(g, x + 2, y);
        g.renderItem(stack, x + 3, y + 1);
        int textX = x + 24;
        Gui.fitted(g, font, name, textX, y + 1, right - 2 - textX, rare ? rare() : Gui.label());
        int lineY = y + 11;
        String pct = percent(chance);
        int pctX = right - 2 - font.width(pct);
        g.drawString(font, pct, pctX, lineY, rare ? rare() : Gui.label(), false);
        int detailY = lineY + (font.lineHeight - 1 - Gui.fineLine(font)) / 2;
        Gui.fineClipped(g, font, detail, textX, detailY, pctX - 4 - textX, Gui.labelSoft());
        int barLeft = textX + (detailRoom >= 0 ? detailRoom : Gui.fineWidth(font, detail)) + 4;
        int barRight = pctX - 4;
        if (barRight - barLeft >= 4) {
            g.fill(barLeft, lineY + 2, barRight, lineY + 6, Gui.BAR_BACK);
            g.fill(barLeft, lineY + 2, barLeft + Math.max(1, (int) ((barRight - barLeft) * chance)), lineY + 6, rare ? RARE_BAR : Gui.BAR);
        }
    }

    /** The tooltip for an item in one table: its chance in each container, in all of them, and what it can come with. */
    public static List<Component> tooltip(LootOdds.Row row, float chance, int containers, String containerName) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.justenoughstructures.tip_chance", containerName, FoundIn.tenthPercent(chance)).withStyle(ChatFormatting.GRAY));
        if (containers > 1) {
            double atLeastOne = 1 - Math.pow(1 - chance, containers);
            lines.add(Component.translatable("screen.justenoughstructures.tip_total", FoundIn.wholePercent(atLeastOne), containers)
                    .withStyle(ChatFormatting.GRAY));
        }
        // A row from a server from before kinds has only the enchantments and potions it came with.
        lines.addAll(row.kinds().isEmpty() ? variantLines(row.variants()) : kindLines(kinds(row, chance), row.variants()));
        return lines;
    }

    /** How many kind names a tooltip lists before "and N more". */
    private static final int MOST_NAMES = 8;
    /** The rolls players' chances come from, which say how far apart two chances must be to tell them apart. */
    private static final double ROLLS = 2000;

    /**
     * "Comes as:" and each kind with its chance, most likely first. Kinds about as likely as each
     * other, within what the rolls can tell apart, share one "Each about" line with their names under
     * it, in order of name. {@code levels} are the highest level each enchantment came with.
     */
    public static List<Component> kindLines(List<KindChance> kinds, Map<String, Integer> levels) {
        List<KindChance> sorted = new ArrayList<>(kinds);
        sorted.sort(Comparator.comparingDouble((KindChance k) -> -k.chance()).thenComparing(KindChance::key));
        // Each group starts with its most likely kind and takes the next while it's within the rolls' noise of the group's average.
        List<List<KindChance>> groups = new ArrayList<>();
        for (KindChance kind : sorted) {
            List<KindChance> last = groups.isEmpty() ? null : groups.get(groups.size() - 1);
            if (last != null && Math.abs(kind.chance() - average(last)) <= noise(average(last))) {
                last.add(kind);
            } else {
                groups.add(new ArrayList<>(List.of(kind)));
            }
        }
        List<Component> lines = new ArrayList<>();
        if (groups.isEmpty()) {
            return lines;
        }
        boolean alike = groups.size() == 1 && sorted.size() > 1;
        boolean gear = enchantedGear(sorted.get(0).example(), sorted.stream().map(KindChance::key).toList());
        String header = gear ? (alike ? "screen.justenoughstructures.tip_kinds_with_alike" : "screen.justenoughstructures.tip_variants")
                : (alike ? "screen.justenoughstructures.tip_kinds_alike" : "screen.justenoughstructures.tip_kinds");
        lines.add((alike ? Component.translatable(header, percent((float) average(groups.get(0)))) : Component.translatable(header))
                .withStyle(ChatFormatting.GRAY));
        int shown = 0;
        int left = sorted.size();
        for (List<KindChance> group : groups) {
            if (shown >= MOST_NAMES) {
                break;
            }
            if (group.size() == 1) {
                KindChance kind = group.get(0);
                lines.add(Component.literal("  ").append(Component.translatable("screen.justenoughstructures.tip_kind",
                        kindName(kind, levels).copy().withStyle(ChatFormatting.DARK_AQUA),
                        Component.literal(percent((float) kind.chance())).withStyle(ChatFormatting.GRAY))));
                shown++;
                left--;
                continue;
            }
            String indent = "  ";
            if (!alike) {
                lines.add(Component.literal("  ").append(Component.translatable("screen.justenoughstructures.tip_kinds_group",
                        percent((float) average(group))).withStyle(ChatFormatting.GRAY)));
                indent = "    ";
            }
            List<Component> names = new ArrayList<>(group.stream().map(k -> kindName(k, levels)).toList());
            names.sort(Comparator.comparing(Component::getString));
            for (Component name : names) {
                if (shown >= MOST_NAMES) {
                    break;
                }
                lines.add(Component.literal(indent).append(name.copy().withStyle(ChatFormatting.DARK_AQUA)));
                shown++;
                left--;
            }
        }
        if (left > 0) {
            lines.add(Component.translatable("screen.justenoughstructures.and_more", left).withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    private static double average(List<KindChance> group) {
        return group.stream().mapToDouble(KindChance::chance).average().orElse(0);
    }

    /** How far from {@code chance} a chance from the rolls can land by luck: three standard deviations, and two rolls. */
    private static double noise(double chance) {
        return 3 * Math.sqrt(Math.max(0, chance * (1 - chance)) / ROLLS) + 2 / ROLLS;
    }

    /** A kind as players know it: its stack's name for a potion ("Arrow of Poison"), and an enchantment's name at the highest level it came with. */
    private static Component kindName(KindChance kind, Map<String, Integer> levels) {
        if (kind.key().startsWith(ItemKinds.ENCHANTMENT)) {
            Component name = enchantmentName(kind.example(), kind.key(), levels.getOrDefault(kind.key(), 0));
            if (name != null) {
                return name;
            }
        }
        return kind.example().getHoverName();
    }

    /**
     * The name of the enchantment of this kind on a stack that has it, like "Sharpness V", at its level
     * there or {@code level} if that's higher; null if the stack doesn't have it. Read from the stack,
     * which has the enchantment itself, so it needs nothing else from the world.
     */
    private static Component enchantmentName(ItemStack stack, String key, int level) {
        //? if >=1.21 {
        /*for (Object2IntMap.Entry<Holder<Enchantment>> e : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) {
            if (e.getKey().unwrapKey().map(k -> key.equals(ItemKinds.ENCHANTMENT + Ids.of(k))).orElse(false)) {
                return Enchantment.getFullname(e.getKey(), Math.max(level, e.getIntValue()));
            }
        }
        *///?} else {
        for (Map.Entry<Enchantment, Integer> e : EnchantmentHelper.getEnchantments(stack).entrySet()) {
            if (key.equals(ItemKinds.ENCHANTMENT + BuiltInRegistries.ENCHANTMENT.getKey(e.getKey()))) {
                return e.getKey().getFullname(Math.max(level, e.getValue()));
            }
        }
        //?}
        return null;
    }

    /** "Can come with:" and the enchantments and potions an item turned up with, if any. */
    static List<Component> variantLines(Map<String, Integer> found) {
        List<Component> variants = new ArrayList<>();
        for (Map.Entry<String, Integer> e : found.entrySet()) {
            String key = e.getKey();
            if (key.startsWith("enchantment:")) {
                //? if >=1.21 {
                /*// Enchantments are the server's own since 1.21, sent over with the world.
                ResourceLocation id = ResourceLocation.tryParse(key.substring(12));
                ClientPacketListener connection = Minecraft.getInstance().getConnection();
                if (id != null && connection != null) {
                    connection.registryAccess().registry(Registries.ENCHANTMENT).flatMap(registry -> Regs.holder(registry, id))
                            .ifPresent(enchantment -> variants.add(Enchantment.getFullname(enchantment, e.getValue())));
                }
                *///?} else {
                Enchantment enchantment = Regs.value(BuiltInRegistries.ENCHANTMENT, ResourceLocation.tryParse(key.substring(12)));
                if (enchantment != null) {
                    variants.add(enchantment.getFullname(e.getValue()));
                }
                //?}
            } else if (key.startsWith("potion:")) {
                //? if >=1.21 {
                /*ResourceLocation id = ResourceLocation.tryParse(key.substring(7));
                Optional<Holder<Potion>> potion = id == null ? Optional.empty() : Regs.holder(BuiltInRegistries.POTION, id).map(holder -> holder);
                variants.add(potion.map(StructureNames::potion).orElse(Component.translatable("item.minecraft.potion.effect.empty")));
                *///?} else {
                Potion potion = Regs.value(BuiltInRegistries.POTION, ResourceLocation.tryParse(key.substring(7)));
                variants.add(Component.translatable(potion.getName("item.minecraft.potion.effect.")));
                //?}
            }
        }
        List<Component> lines = new ArrayList<>();
        if (!variants.isEmpty()) {
            lines.add(Component.translatable("screen.justenoughstructures.tip_variants").withStyle(ChatFormatting.GRAY));
            int shown = Math.min(8, variants.size());
            for (int i = 0; i < shown; i++) {
                lines.add(Component.literal("  ").append(variants.get(i).copy().withStyle(ChatFormatting.DARK_AQUA)));
            }
            if (variants.size() > shown) {
                lines.add(Component.translatable("screen.justenoughstructures.and_more", variants.size() - shown).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return lines;
    }
}
