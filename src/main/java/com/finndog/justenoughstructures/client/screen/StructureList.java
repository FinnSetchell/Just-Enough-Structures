package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.client.Thumbnails;
import com.finndog.justenoughstructures.client.render.StructureViewport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The searchable list on the left: structures grouped by mod under headers that fold away, and,
 * when the search matches loot, the matching items at the top.
 */
final class StructureList {
    private static final int ROW = 18;
    private static final int HEADER = 14;
    private static final ItemStack ICON = new ItemStack(Items.FILLED_MAP);
    private static final String LOOT_HEADER = "";
    private static final Set<String> COLLAPSED = new HashSet<>();

    /** One line in the list: a mod header, a structure, or a loot item matching the search. */
    private record Row(String header, int count, StructureCatalog.Entry entry, String name, Item item, int top, int height) {
    }

    /** What a click landed on. */
    record Pick(StructureCatalog.Entry entry, ItemStack item) {
    }

    private List<StructureCatalog.Entry> all = List.of();
    private List<Row> rows = List.of();
    /** What the search lets through, in list order, folded mods included: for stepping through. */
    private List<StructureCatalog.Entry> ordered = List.of();
    private String query = "";
    private int contentHeight;
    private double scroll;
    private int x, y, width, height;
    private ResourceLocation selected;
    private Row hovered;

    void setEntries(List<StructureCatalog.Entry> entries) {
        this.all = entries;
        rebuild();
    }

    void setQuery(String query) {
        if (!query.equals(this.query)) {
            this.query = query;
            this.scroll = 0;
            rebuild();
        }
    }

    /** Re-runs the search, e.g. once the loot index arrives. */
    void refresh() {
        rebuild();
    }

    void setSelected(ResourceLocation id) {
        this.selected = id;
    }

    void layout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        clampScroll();
    }

    /**
     * The structure {@code dir} places before or after {@code current} in the list, wrapping round.
     * With {@code byMod} it's the first structure of the previous or next mod instead.
     */
    Optional<StructureCatalog.Entry> step(ResourceLocation current, int dir, boolean byMod) {
        if (ordered.isEmpty()) {
            return Optional.empty();
        }
        int at = 0;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).id().equals(current)) {
                at = i;
                break;
            }
        }
        if (!byMod) {
            return Optional.of(ordered.get(Math.floorMod(at + dir, ordered.size())));
        }
        List<String> mods = ordered.stream().map(e -> e.id().getNamespace()).distinct().toList();
        String next = mods.get(Math.floorMod(mods.indexOf(ordered.get(at).id().getNamespace()) + dir, mods.size()));
        return ordered.stream().filter(e -> e.id().getNamespace().equals(next)).findFirst();
    }

    Optional<StructureCatalog.Entry> firstShown() {
        return rows.stream().filter(r -> r.entry() != null).map(Row::entry).findFirst();
    }

    /** Structures whose rows are on screen right now, for loading their thumbnails. */
    List<ResourceLocation> visible() {
        List<ResourceLocation> out = new ArrayList<>();
        for (Row row : rows) {
            if (row.entry() != null && row.top() + ROW >= scroll && row.top() <= scroll + height) {
                out.add(row.entry().id());
            }
        }
        return out;
    }

    private void rebuild() {
        String lower = query.toLowerCase(Locale.ROOT).trim();
        String[] tokens = lower.split("\\s+");
        Map<String, List<StructureCatalog.Entry>> byMod = new TreeMap<>(Comparator
                .comparing((String ns) -> !ns.equals("minecraft"))
                .thenComparing(ns -> StructureNames.mod(ns).toLowerCase(Locale.ROOT)));
        for (StructureCatalog.Entry entry : all) {
            if (matches(entry, tokens)) {
                byMod.computeIfAbsent(entry.id().getNamespace(), k -> new ArrayList<>()).add(entry);
            }
        }
        List<Row> out = new ArrayList<>();
        List<StructureCatalog.Entry> inOrder = new ArrayList<>();
        int top = 0;

        // A plain search also looks through loot, so "diamond" finds where diamonds come from.
        if (!lower.isEmpty() && !lower.startsWith("@") && !lower.startsWith("$") && FoundIn.ready()) {
            List<Item> items = FoundIn.itemsMatching(lower, 3);
            if (!items.isEmpty()) {
                out.add(new Row(LOOT_HEADER, items.size(), null, null, null, top, HEADER));
                top += HEADER;
                for (Item item : items) {
                    out.add(new Row(null, FoundIn.structuresFor(item).size(), null, item.getDescription().getString(), item, top, ROW));
                    top += ROW;
                }
            }
        }

        for (Map.Entry<String, List<StructureCatalog.Entry>> mod : byMod.entrySet()) {
            List<StructureCatalog.Entry> entries = mod.getValue();
            entries.sort(Comparator.comparing(e -> StructureNames.structure(e.id())));
            inOrder.addAll(entries);
            out.add(new Row(mod.getKey(), entries.size(), null, StructureNames.mod(mod.getKey()), null, top, HEADER));
            top += HEADER;
            if (COLLAPSED.contains(mod.getKey()) && lower.isEmpty()) {
                continue;
            }
            for (StructureCatalog.Entry entry : entries) {
                out.add(new Row(null, 0, entry, StructureNames.structure(entry.id()), null, top, ROW));
                top += ROW;
            }
        }
        rows = out;
        ordered = inOrder;
        contentHeight = top;
        clampScroll();
    }

    private static boolean matches(StructureCatalog.Entry entry, String[] tokens) {
        String name = StructureNames.structure(entry.id()).toLowerCase(Locale.ROOT);
        String id = entry.id().toString();
        String namespace = entry.id().getNamespace();
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            if (token.startsWith("$")) {
                String item = token.substring(1);
                if (!item.isEmpty() && !FoundIn.structureHasItem(entry.id(), item)) {
                    return false;
                }
            } else if (token.startsWith("@")) {
                String mod = token.substring(1);
                if (!namespace.startsWith(mod) && !StructureNames.mod(namespace).toLowerCase(Locale.ROOT).contains(mod)) {
                    return false;
                }
            } else if (!name.contains(token) && !id.contains(token)) {
                return false;
            }
        }
        return true;
    }

    void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        hovered = null;
        if (rows.isEmpty()) {
            Component message = Component.translatable("screen.justenoughstructures.no_matches");
            if (query.contains("$") && !FoundIn.ready()) {
                float progress = ClientRequests.indexProgress();
                message = progress < 0 ? Component.translatable("screen.justenoughstructures.indexing")
                        : Component.translatable("screen.justenoughstructures.indexing_progress", Math.round(progress * 100));
            }
            Gui.wrapped(g, font, message, x + 5, y + 5, width - 10, Gui.LABEL_SOFT);
            return;
        }
        boolean scrolls = contentHeight > height;
        int rowRight = x + width - (scrolls ? 8 : 0);
        g.enableScissor(x, y, x + width, y + height);
        int offset = y - (int) scroll;
        for (Row row : rows) {
            int top = offset + row.top();
            if (top + row.height() < y || top > y + height) {
                continue;
            }
            boolean over = mouseX >= x && mouseX < rowRight && mouseY >= top && mouseY < top + row.height()
                    && mouseY >= y && mouseY < y + height;
            if (over) {
                hovered = row;
            }
            if (row.header() != null) {
                String label = row.header().equals(LOOT_HEADER)
                        ? Component.translatable("screen.justenoughstructures.loot_matches").getString()
                        : (COLLAPSED.contains(row.header()) && query.isBlank() ? "+ " : "- ") + row.name();
                // JEI's title rows: white text on a translucent dark band.
                g.fill(x, top, rowRight, top + HEADER - 1, over && !row.header().equals(LOOT_HEADER) ? 0x50000000 : Gui.BAND);
                String count = String.valueOf(row.count());
                g.drawString(font, Gui.clip(font, label, rowRight - x - 12 - font.width(count)), x + 3, top + 3, 0xFFFFFFFF, true);
                g.drawString(font, count, rowRight - 3 - font.width(count), top + 3, 0xFFE0E0E0, true);
                continue;
            }
            boolean isSelected = row.entry() != null && row.entry().id().equals(selected);
            if (isSelected) {
                g.fill(x, top, rowRight, top + ROW, Gui.ROW_SELECTED);
            } else if (over) {
                g.fill(x, top, rowRight, top + ROW, Gui.ROW_HOVER);
            }
            Gui.slot(g, x, top);
            if (row.item() != null) {
                g.renderItem(new ItemStack(row.item()), x + 1, top + 1);
                String count = "x" + row.count();
                Gui.fitted(g, font, row.name(), x + 21, top + 5, rowRight - x - 26 - font.width(count), Gui.LABEL);
                g.drawString(font, count, rowRight - 3 - font.width(count), top + 5, Gui.LABEL_SOFT, false);
                continue;
            }
            int thumbnail = Thumbnails.textureId(row.entry().id());
            if (thumbnail >= 0) {
                StructureViewport.drawTexture(g, thumbnail, x + 1, top + 1, 16, 16);
            } else {
                g.renderItem(ICON, x + 1, top + 1);
            }
            Gui.fitted(g, font, row.name(), x + 21, top + 5, rowRight - x - 24, Gui.LABEL);
        }
        g.disableScissor();

        if (scrolls) {
            Gui.scrollbar(g, x + width - 4, y, height, scroll, contentHeight - height);
        }
    }

    /** Tooltip for whatever row the mouse is over, or empty. */
    List<Component> tooltip() {
        if (hovered == null) {
            return List.of();
        }
        if (hovered.entry() != null) {
            ResourceLocation id = hovered.entry().id();
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(hovered.name()));
            // Ids only for people who asked for them with F3+H, the same as item tooltips.
            if (Minecraft.getInstance().options.advancedItemTooltips) {
                lines.add(Component.literal(id.toString()).withStyle(ChatFormatting.DARK_GRAY));
            }
            lines.add(Component.literal(StructureNames.mod(id.getNamespace())).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
            return lines;
        }
        if (hovered.item() != null) {
            return List.of(Component.literal(hovered.name()),
                    Component.translatable("screen.justenoughstructures.found_in", hovered.count()).withStyle(ChatFormatting.GRAY),
                    Component.translatable("screen.justenoughstructures.found_hint").withStyle(ChatFormatting.DARK_GRAY));
        }
        return List.of();
    }

    Optional<Pick> click(double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return Optional.empty();
        }
        double local = mouseY - y - 1 + scroll;
        for (Row row : rows) {
            if (local < row.top() || local >= row.top() + row.height()) {
                continue;
            }
            if (row.entry() != null) {
                return Optional.of(new Pick(row.entry(), ItemStack.EMPTY));
            }
            if (row.item() != null) {
                return Optional.of(new Pick(null, new ItemStack(row.item())));
            }
            if (row.header() != null && !row.header().equals(LOOT_HEADER) && query.isBlank()) {
                if (!COLLAPSED.remove(row.header())) {
                    COLLAPSED.add(row.header());
                }
                rebuild();
                return Optional.of(new Pick(null, ItemStack.EMPTY));
            }
        }
        return Optional.empty();
    }

    boolean scroll(double mouseX, double mouseY, double delta) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }
        scroll -= delta * ROW * 2;
        clampScroll();
        return true;
    }

    /** Centre of a structure's row on screen, scrolling it into view first. */
    Optional<int[]> rowCentre(ResourceLocation id) {
        for (Row row : rows) {
            if (row.entry() != null && row.entry().id().equals(id)) {
                if (row.top() < scroll || row.top() + ROW > scroll + height - 2) {
                    scroll = row.top() - height / 2.0;
                    clampScroll();
                }
                return Optional.of(new int[]{x + 60, y + 1 - (int) scroll + row.top() + ROW / 2});
            }
        }
        return Optional.empty();
    }

    /** Scrolls just enough to show the selected structure. */
    void revealSelected() {
        for (Row row : rows) {
            if (row.entry() != null && row.entry().id().equals(selected)) {
                if (row.top() < scroll) {
                    scroll = row.top();
                } else if (row.top() + ROW > scroll + height - 2) {
                    scroll = row.top() + ROW - height + 2;
                }
                clampScroll();
                return;
            }
        }
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - height + 2)));
    }
}
