package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** The tabs on the right: what the structure is, its loot, its blocks and its entities. */
final class InfoPanel {
    enum Tab {
        OVERVIEW("overview"), LOOT("loot"), BLOCKS("blocks"), ENTITIES("entities");

        final String key;

        Tab(String key) {
            this.key = key;
        }

        Component label() {
            return Component.translatable("screen.justenoughstructures.tab." + key);
        }
    }

    private static final int TAB_HEIGHT = 16;
    private static final int PAD = 5;

    private final Font font;
    private final Consumer<String> onSelectTable;
    private final Consumer<StructureSnapshot.Container> onOpenContainer;
    private final Consumer<ItemStack> onItemClicked;

    private Tab tab = Tab.OVERVIEW;
    private int x, y, width, height;
    private double scroll;
    private int contentHeight;

    private StructureCatalog.Entry entry;
    private CaptureResult result;
    private String selectedTable;
    private LootOdds odds;

    private final List<Hotspot> hotspots = new ArrayList<>();
    private ItemStack hoveredStack = ItemStack.EMPTY;
    private List<Component> hoveredText = List.of();

    private record Hotspot(int x, int y, int w, int h, Runnable action) {
    }

    InfoPanel(Font font, Consumer<String> onSelectTable, Consumer<StructureSnapshot.Container> onOpenContainer,
              Consumer<ItemStack> onItemClicked) {
        this.font = font;
        this.onSelectTable = onSelectTable;
        this.onOpenContainer = onOpenContainer;
        this.onItemClicked = onItemClicked;
    }

    void layout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    void setEntry(StructureCatalog.Entry entry) {
        this.entry = entry;
        this.result = null;
        this.selectedTable = null;
        this.odds = null;
        this.scroll = 0;
    }

    void setResult(CaptureResult result) {
        this.result = result;
        this.selectedTable = null;
        this.odds = null;
    }

    void setSelectedTable(String table) {
        this.selectedTable = table;
        this.odds = null;
    }

    void setOdds(LootOdds odds) {
        if (odds != null && odds.tableId().toString().equals(selectedTable)) {
            this.odds = odds;
        }
    }

    Tab tab() {
        return tab;
    }

    void setTab(Tab tab) {
        this.tab = tab;
        this.scroll = 0;
    }

    ItemStack hoveredStack() {
        return hoveredStack;
    }

    List<Component> hoveredText() {
        return hoveredText;
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        hotspots.clear();
        hoveredStack = ItemStack.EMPTY;
        hoveredText = List.of();

        int tabWidth = width / Tab.values().length;
        for (Tab t : Tab.values()) {
            int tx = x + t.ordinal() * tabWidth;
            int tw = t.ordinal() == Tab.values().length - 1 ? width - tabWidth * (Tab.values().length - 1) : tabWidth;
            boolean active = t == tab;
            g.fill(tx, y, tx + tw, y + TAB_HEIGHT, Gui.EDGE);
            g.fill(tx + 1, y + 1, tx + tw - 1, y + TAB_HEIGHT - (active ? 0 : 1), active ? Gui.PANEL : 0xFF9C9C9C);
            String label = Gui.clip(font, t.label().getString(), tw - 4);
            g.drawString(font, label, tx + (tw - font.width(label)) / 2, y + 4, active ? 0xFF202020 : 0xFF404040, false);
        }

        int top = y + TAB_HEIGHT + 1;
        int bodyHeight = height - TAB_HEIGHT - 1;
        Gui.inset(g, x, top, width, bodyHeight, 0xFFBDBDBD);
        g.enableScissor(x + 1, top + 1, x + width - 1, top + bodyHeight - 1);
        int cursor = top + PAD - (int) scroll;
        int end = switch (tab) {
            case OVERVIEW -> overview(g, cursor);
            case LOOT -> loot(g, cursor, mouseX, mouseY, top, bodyHeight);
            case BLOCKS -> blocks(g, cursor, mouseX, mouseY, top, bodyHeight);
            case ENTITIES -> entities(g, cursor);
        };
        g.disableScissor();
        contentHeight = end - cursor + PAD;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - bodyHeight)));
    }

    boolean click(double mouseX, double mouseY) {
        if (mouseY >= y && mouseY < y + TAB_HEIGHT && mouseX >= x && mouseX < x + width) {
            int index = (int) ((mouseX - x) / (width / Tab.values().length));
            setTab(Tab.values()[Math.min(Tab.values().length - 1, Math.max(0, index))]);
            return true;
        }
        if (mouseX < x || mouseX >= x + width || mouseY < y + TAB_HEIGHT + 1 || mouseY >= y + height) {
            return false;
        }
        for (Hotspot h : hotspots) {
            if (mouseX >= h.x() && mouseX < h.x() + h.w() && mouseY >= h.y() && mouseY < h.y() + h.h()) {
                h.action().run();
                return true;
            }
        }
        return false;
    }

    boolean scroll(double mouseX, double mouseY, double delta) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }
        scroll -= delta * 20;
        return true;
    }

    // ------------------------------------------------------------------ overview

    private int overview(GuiGraphics g, int cy) {
        if (entry == null) {
            return cy;
        }
        JsonObject def = entry.definition();
        cy = field(g, cy, "type", entry.type() == null ? "?" : entry.type().toString());
        if (def != null) {
            cy = field(g, cy, "step", string(def.get("step")));
            cy = field(g, cy, "biomes", string(def.get("biomes")));
            if (def.has("start_pool")) {
                cy = field(g, cy, "start_pool", string(def.get("start_pool")));
            }
            if (def.has("size")) {
                cy = field(g, cy, "jigsaw_size", string(def.get("size")));
            }
            cy = field(g, cy, "terrain", def.has("terrain_adaptation") ? string(def.get("terrain_adaptation")) : "none");
        } else {
            cy = Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.no_definition"), x + PAD, cy, width - PAD * 2, Gui.LABEL_SOFT) + 4;
        }
        for (StructureCatalog.SetInfo set : entry.sets()) {
            JsonObject p = set.placement();
            String placement = p == null ? set.setId().toString() : string(p.get("type"));
            if (p != null && p.has("spacing")) {
                placement += "\n" + Component.translatable("screen.justenoughstructures.spacing", string(p.get("spacing")), string(p.get("separation"))).getString();
            }
            if (p != null && p.has("frequency")) {
                placement += "\n" + Component.translatable("screen.justenoughstructures.frequency", string(p.get("frequency"))).getString();
            }
            cy = field(g, cy, "placement", placement);
        }

        if (result != null && result.succeeded()) {
            StructureSnapshot s = result.snapshot();
            cy += 4;
            g.drawString(font, Component.translatable("screen.justenoughstructures.this_preview"), x + PAD, cy, 0xFF202020, false);
            cy += font.lineHeight + 4;
            cy = field(g, cy, "size", s.size().getX() + " x " + s.size().getY() + " x " + s.size().getZ());
            cy = field(g, cy, "blocks", String.format("%,d", s.blockCount()));
            cy = field(g, cy, "pieces", String.valueOf(s.pieceCount()));
            cy = field(g, cy, "generated_on", Component.translatable("screen.justenoughstructures.terrain." + s.terrain().name().toLowerCase(Locale.ROOT)).getString());
            cy = field(g, cy, "seed", Long.toHexString(s.seed()).toUpperCase(Locale.ROOT));
            cy = field(g, cy, "time", result.millis() + " ms");
        }
        return cy;
    }

    private int field(GuiGraphics g, int cy, String key, String value) {
        Gui.small(g, font, Component.translatable("screen.justenoughstructures.field." + key).getString(), x + PAD, cy, Gui.LABEL_SOFT);
        cy += 8;
        for (String line : value.split("\n")) {
            cy = Gui.wrapped(g, font, Component.literal(line), x + PAD, cy, width - PAD * 2, 0xFF202020);
        }
        return cy + 4;
    }

    private static String string(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return "?";
        }
        if (e.isJsonPrimitive()) {
            return e.getAsString();
        }
        if (e.isJsonArray()) {
            List<String> parts = new ArrayList<>();
            e.getAsJsonArray().forEach(el -> parts.add(string(el)));
            return String.join(", ", parts);
        }
        return e.toString();
    }

    // ------------------------------------------------------------------ loot

    private int loot(GuiGraphics g, int cy, int mouseX, int mouseY, int clipTop, int clipHeight) {
        if (result == null || !result.succeeded()) {
            return Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.loot_waiting"), x + PAD, cy, width - PAD * 2, Gui.LABEL_SOFT);
        }
        Map<String, List<StructureSnapshot.Container>> groups = new LinkedHashMap<>();
        for (StructureSnapshot.Container c : result.snapshot().containers()) {
            groups.computeIfAbsent(c.lootTable() == null ? "" : c.lootTable(), k -> new ArrayList<>()).add(c);
        }
        if (groups.isEmpty()) {
            return Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.no_loot"), x + PAD, cy, width - PAD * 2, Gui.LABEL_SOFT);
        }
        List<Map.Entry<String, List<StructureSnapshot.Container>>> sorted = new ArrayList<>(groups.entrySet());
        sorted.sort(Comparator.comparing((Map.Entry<String, List<StructureSnapshot.Container>> e) -> e.getKey().isEmpty())
                .thenComparing(e -> -e.getValue().size()));

        for (Map.Entry<String, List<StructureSnapshot.Container>> group : sorted) {
            String table = group.getKey();
            List<StructureSnapshot.Container> containers = group.getValue();
            int rowHeight = 22;
            boolean selected = table.equals(selectedTable);
            boolean hovered = inside(mouseX, mouseY, x + 2, cy, width - 4, rowHeight, clipTop, clipHeight);
            if (selected || hovered) {
                g.fill(x + 2, cy, x + width - 2, cy + rowHeight, selected ? Gui.ROW_SELECTED : Gui.ROW_HOVER);
            }
            Gui.slot(g, x + PAD - 1, cy + 2);
            g.renderItem(containerIcon(result.snapshot(), containers.get(0)), x + PAD, cy + 3);
            ItemStack icon = containerIcon(result.snapshot(), containers.get(0));
            String name = icon.getHoverName().getString() + " x" + containers.size();
            String detail = table.isEmpty() ? Component.translatable("screen.justenoughstructures.prefilled").getString() : table;
            int textWidth = width - PAD * 2 - 24;
            g.drawString(font, Gui.clip(font, name, textWidth), x + PAD + 21, cy + 3, 0xFF202020, false);
            Gui.small(g, font, Gui.clip(font, detail, (int) (textWidth / 0.75f)), x + PAD + 21, cy + 13, Gui.LABEL_SOFT);
            StructureSnapshot.Container first = containers.get(0);
            hotspots.add(new Hotspot(x + 2, cy, width - 4, rowHeight, () -> {
                onSelectTable.accept(table.isEmpty() ? null : table);
                onOpenContainer.accept(first);
            }));
            cy += rowHeight + 1;
        }

        if (selectedTable == null) {
            return cy;
        }
        cy += 5;
        g.drawString(font, Component.translatable("screen.justenoughstructures.odds"), x + PAD, cy, 0xFF202020, false);
        cy += font.lineHeight + 2;
        if (odds == null) {
            return Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.rolling"), x + PAD, cy, width - PAD * 2, Gui.LABEL_SOFT);
        }
        cy = Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.odds_note", String.format("%,d", odds.rolls())),
                x + PAD, cy, width - PAD * 2, Gui.LABEL_SOFT) + 3;
        if (odds.rows().isEmpty()) {
            return Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.always_empty"), x + PAD, cy, width - PAD * 2, Gui.LABEL_SOFT);
        }
        for (LootOdds.Row row : odds.rows()) {
            float chance = (float) row.hits() / odds.rolls();
            Gui.slot(g, x + PAD - 1, cy);
            g.renderItem(row.example(), x + PAD, cy + 1);
            int textX = x + PAD + 21;
            int textWidth = width - PAD * 2 - 21 - 30;
            g.drawString(font, Gui.clip(font, row.example().getHoverName().getString(), textWidth), textX, cy + 1, 0xFF202020, false);
            String pct = chance >= 0.1f ? Math.round(chance * 100) + "%" : String.format("%.1f%%", chance * 100);
            g.drawString(font, pct, x + width - PAD - font.width(pct), cy + 1, 0xFF202020, false);
            String counts = (row.min() == row.max() ? String.valueOf(row.min()) : row.min() + "-" + row.max())
                    + "  " + Component.translatable("screen.justenoughstructures.average", String.format("%.1f", (float) row.total() / row.hits())).getString();
            Gui.small(g, font, counts, textX, cy + 11, Gui.LABEL_SOFT);
            int barLeft = textX + (int) (font.width(counts) * 0.75f) + 4;
            int barRight = x + width - PAD;
            if (barRight - barLeft > 10) {
                g.fill(barLeft, cy + 12, barRight, cy + 16, Gui.BAR_BACK);
                g.fill(barLeft, cy + 12, barLeft + Math.max(1, (int) ((barRight - barLeft) * chance)), cy + 16, Gui.BAR);
            }
            if (inside(mouseX, mouseY, x + 2, cy, width - 4, 19, clipTop, clipHeight)) {
                hoveredStack = row.example();
            }
            ItemStack example = row.example();
            hotspots.add(new Hotspot(x + 2, cy, width - 4, 19, () -> onItemClicked.accept(example)));
            cy += 20;
        }
        if (odds.emptyRolls() > 0) {
            cy = Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.empty_rolls",
                    String.format("%.1f%%", 100f * odds.emptyRolls() / odds.rolls())), x + PAD, cy + 2, width - PAD * 2, Gui.LABEL_SOFT);
        }
        return cy;
    }

    /** The block's own item for block containers (so suspicious sand looks like sand), otherwise by id. */
    static ItemStack containerIcon(StructureSnapshot snapshot, StructureSnapshot.Container container) {
        if (!container.entity()) {
            BlockState state = snapshot.stateAt(container.pos());
            if (state != null && state.getBlock().asItem() != Items.AIR) {
                return new ItemStack(state.getBlock().asItem());
            }
        }
        ResourceLocation id = ResourceLocation.tryParse(container.id());
        Item item = id == null ? Items.CHEST : BuiltInRegistries.ITEM.get(id);
        return new ItemStack(item == Items.AIR ? Items.CHEST : item);
    }

    // ------------------------------------------------------------------ blocks

    private int blocks(GuiGraphics g, int cy, int mouseX, int mouseY, int clipTop, int clipHeight) {
        if (result == null || !result.succeeded()) {
            return Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.loot_waiting"), x + PAD, cy, width - PAD * 2, Gui.LABEL_SOFT);
        }
        StructureSnapshot s = result.snapshot();
        Map<Block, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < s.blockCount(); i++) {
            counts.merge(s.state(i).getBlock(), 1, Integer::sum);
        }
        List<Map.Entry<Block, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        g.drawString(font, Component.translatable("screen.justenoughstructures.block_types", sorted.size(), String.format("%,d", s.blockCount())),
                x + PAD, cy, 0xFF202020, false);
        cy += font.lineHeight + 4;
        int columns = Math.max(1, (width - PAD * 2) / 18);
        for (int i = 0; i < sorted.size(); i++) {
            int sx = x + PAD + (i % columns) * 18;
            int sy = cy + (i / columns) * 18;
            Block block = sorted.get(i).getKey();
            int count = sorted.get(i).getValue();
            Gui.slot(g, sx, sy);
            ItemStack stack = new ItemStack(block.asItem());
            if (stack.isEmpty()) {
                stack = new ItemStack(Items.BARRIER);
            }
            g.renderItem(stack, sx + 1, sy + 1);
            String label = count >= 10_000 ? (count / 1000) + "k" : count >= 1000 ? String.format("%.1fk", count / 1000f) : String.valueOf(count);
            g.renderItemDecorations(font, stack, sx + 1, sy + 1, label);
            if (inside(mouseX, mouseY, sx, sy, 18, 18, clipTop, clipHeight)) {
                hoveredText = List.of(block.getName(), Component.translatable("screen.justenoughstructures.placed", String.format("%,d", count)).withStyle(ChatFormatting.GRAY),
                        Component.literal(BuiltInRegistries.BLOCK.getKey(block).toString()).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return cy + ((sorted.size() + columns - 1) / columns) * 18;
    }

    // ------------------------------------------------------------------ entities

    private int entities(GuiGraphics g, int cy) {
        if (result == null || !result.succeeded()) {
            return Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.loot_waiting"), x + PAD, cy, width - PAD * 2, Gui.LABEL_SOFT);
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (CompoundTag tag : result.snapshot().entities()) {
            counts.merge(tag.getString("id"), 1, Integer::sum);
        }
        if (counts.isEmpty()) {
            cy = Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.no_entities"), x + PAD, cy, width - PAD * 2, Gui.LABEL_SOFT);
        }
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(e.getKey());
            Component name = id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)
                    ? BuiltInRegistries.ENTITY_TYPE.get(id).getDescription()
                    : Component.literal(e.getKey());
            g.fill(x + 2, cy - 1, x + width - 2, cy + font.lineHeight + 2, 0xFFB3B3B3);
            g.drawString(font, Gui.clip(font, name.getString(), width - PAD * 2 - 24), x + PAD, cy + 1, 0xFF202020, false);
            String count = "x" + e.getValue();
            g.drawString(font, count, x + width - PAD - font.width(count), cy + 1, Gui.LABEL_SOFT, false);
            cy += font.lineHeight + 5;
        }
        return Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.entities_note"), x + PAD, cy + 4, width - PAD * 2, Gui.LABEL_SOFT);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h, int clipTop, int clipHeight) {
        return mx >= x && mx < x + w && my >= y && my < y + h && my >= clipTop && my < clipTop + clipHeight;
    }
}
