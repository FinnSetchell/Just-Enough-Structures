package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.loot.LootOdds;
import java.util.ArrayList;
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
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.enchantment.Enchantment;
//? if >=1.21 {
/*import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
*///?}

/**
 * Rows of items with how likely each is: its icon and name, then a line with how many come, a bar
 * and the chance. The same rows in the details panel, the chest popup and Pack tools.
 */
final class OddsList {
    static final int ROW = 20;
    /** Below this chance an item counts as rare and stands out. */
    static final float RARE_BELOW = 0.1f;
    static final int RARE = 0xFF8A5A00;
    private static final int RARE_BAR = 0xFFC08A20;

    private OddsList() {
    }

    /** A table's rows, most likely first, or rarest first. */
    static List<LootOdds.Row> sorted(LootOdds odds, boolean rarestFirst) {
        List<LootOdds.Row> rows = new ArrayList<>(odds.rows());
        if (rarestFirst) {
            rows.sort(Comparator.comparingInt(LootOdds.Row::hits));
        }
        return rows;
    }

    /** Names for rows, with the item's id made readable where two rows would read the same, like two music discs. */
    static Map<LootOdds.Row, String> names(List<LootOdds.Row> rows) {
        Map<String, Integer> counts = new HashMap<>();
        rows.forEach(r -> counts.merge(r.example().getHoverName().getString(), 1, Integer::sum));
        Map<LootOdds.Row, String> out = new HashMap<>();
        for (LootOdds.Row row : rows) {
            String name = row.example().getHoverName().getString();
            out.put(row, counts.get(name) > 1 ? StructureNames.pretty(BuiltInRegistries.ITEM.getKey(row.example().getItem()).getPath()) : name);
        }
        return out;
    }

    static String percent(float chance) {
        return chance >= 0.1f ? Math.round(chance * 100) + "%" : String.format("%.1f%%", chance * 100);
    }

    /** How many come in a container: "2-9 (avg 5)", or "3 at a time" when it's always the same. */
    static String counts(LootOdds.Row row) {
        float average = (float) row.total() / row.hits();
        return row.min() == row.max()
                ? Component.translatable("screen.justenoughstructures.count_exact", row.min()).getString()
                : Component.translatable("screen.justenoughstructures.count_range", row.min(), row.max(), Math.round(average)).getString();
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
            g.fill(x, y, right, y + ROW, Gui.ROW_HOVER);
        }
        Gui.slot(g, x + 2, y);
        g.renderItem(stack, x + 3, y + 1);
        int textX = x + 24;
        Gui.fitted(g, font, name, textX, y + 1, right - 2 - textX, rare ? RARE : Gui.LABEL);
        int lineY = y + 11;
        String pct = percent(chance);
        int pctX = right - 2 - font.width(pct);
        g.drawString(font, pct, pctX, lineY, rare ? RARE : Gui.LABEL, false);
        int detailY = lineY + (font.lineHeight - 1 - Gui.fineLine(font)) / 2;
        Gui.fineClipped(g, font, detail, textX, detailY, pctX - 4 - textX, Gui.LABEL_SOFT);
        int barLeft = textX + (detailRoom >= 0 ? detailRoom : Gui.fineWidth(font, detail)) + 4;
        int barRight = pctX - 4;
        if (barRight - barLeft >= 4) {
            g.fill(barLeft, lineY + 2, barRight, lineY + 6, Gui.BAR_BACK);
            g.fill(barLeft, lineY + 2, barLeft + Math.max(1, (int) ((barRight - barLeft) * chance)), lineY + 6, rare ? RARE_BAR : Gui.BAR);
        }
    }

    /** The tooltip for an item in one table: its chance in each container, in all of them, and what it can come with. */
    static List<Component> tooltip(LootOdds.Row row, float chance, int containers, String containerName) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.justenoughstructures.tip_chance", containerName, String.format("%.1f%%", chance * 100)).withStyle(ChatFormatting.GRAY));
        if (containers > 1) {
            double atLeastOne = 1 - Math.pow(1 - chance, containers);
            lines.add(Component.translatable("screen.justenoughstructures.tip_total", containers, containerName,
                    String.format("%.0f%%", atLeastOne * 100)).withStyle(ChatFormatting.GRAY));
        }
        lines.addAll(variantLines(row.variants()));
        return lines;
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
                    connection.registryAccess().registry(Registries.ENCHANTMENT).flatMap(registry -> registry.getHolder(id))
                            .ifPresent(enchantment -> variants.add(Enchantment.getFullname(enchantment, e.getValue())));
                }
                *///?} else {
                Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(ResourceLocation.tryParse(key.substring(12)));
                if (enchantment != null) {
                    variants.add(enchantment.getFullname(e.getValue()));
                }
                //?}
            } else if (key.startsWith("potion:")) {
                //? if >=1.21 {
                /*ResourceLocation id = ResourceLocation.tryParse(key.substring(7));
                Optional<Holder<Potion>> potion = id == null ? Optional.empty() : BuiltInRegistries.POTION.getHolder(id).map(holder -> holder);
                variants.add(Component.translatable(Potion.getName(potion, "item.minecraft.potion.effect.")));
                *///?} else {
                Potion potion = BuiltInRegistries.POTION.get(ResourceLocation.tryParse(key.substring(7)));
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
