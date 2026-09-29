package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.catalog.StructureCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The searchable list of structures on the left, grouped by mod. */
final class StructureList {
    private static final int ROW = 20;
    private static final int HEADER = 14;
    private static final ItemStack ICON = new ItemStack(Items.STRUCTURE_BLOCK);

    private record Row(String header, int count, StructureCatalog.Entry entry, String name, int top, int height) {
    }

    private List<StructureCatalog.Entry> all = List.of();
    private List<Row> rows = List.of();
    private String query = "";
    private int contentHeight;
    private double scroll;
    private int x, y, width, height;
    private ResourceLocation selected;

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

    int shown() {
        return (int) rows.stream().filter(r -> r.entry() != null).count();
    }

    Optional<StructureCatalog.Entry> firstShown() {
        return rows.stream().filter(r -> r.entry() != null).map(Row::entry).findFirst();
    }

    private void rebuild() {
        String[] tokens = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        Map<String, List<StructureCatalog.Entry>> byMod = new TreeMap<>(Comparator
                .comparing((String ns) -> !ns.equals("minecraft"))
                .thenComparing(ns -> StructureNames.mod(ns).toLowerCase(Locale.ROOT)));
        for (StructureCatalog.Entry entry : all) {
            if (matches(entry, tokens)) {
                byMod.computeIfAbsent(entry.id().getNamespace(), k -> new ArrayList<>()).add(entry);
            }
        }
        List<Row> out = new ArrayList<>();
        int top = 0;
        for (Map.Entry<String, List<StructureCatalog.Entry>> mod : byMod.entrySet()) {
            List<StructureCatalog.Entry> entries = mod.getValue();
            entries.sort(Comparator.comparing(e -> StructureNames.structure(e.id())));
            out.add(new Row(StructureNames.mod(mod.getKey()), entries.size(), null, null, top, HEADER));
            top += HEADER;
            for (StructureCatalog.Entry entry : entries) {
                out.add(new Row(null, 0, entry, StructureNames.structure(entry.id()), top, ROW));
                top += ROW;
            }
        }
        rows = out;
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
            if (token.startsWith("@")) {
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
        Gui.inset(g, x, y, width, height, 0xFFB9B9B9);
        if (rows.isEmpty()) {
            Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.no_matches"),
                    x + 5, y + 5, width - 10, Gui.LABEL_SOFT);
            return;
        }
        g.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        int offset = y + 1 - (int) scroll;
        for (Row row : rows) {
            int top = offset + row.top();
            if (top + row.height() < y || top > y + height) {
                continue;
            }
            if (row.entry() == null) {
                g.drawString(font, Gui.clip(font, row.header(), width - 34), x + 4, top + 4, Gui.LABEL, false);
                String count = String.valueOf(row.count());
                g.drawString(font, count, x + width - 6 - font.width(count), top + 4, Gui.LABEL_SOFT, false);
                continue;
            }
            boolean isSelected = row.entry().id().equals(selected);
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= top && mouseY < top + ROW
                    && mouseY >= y && mouseY < y + height;
            if (isSelected) {
                g.fill(x + 1, top, x + width - 1, top + ROW, Gui.ROW_SELECTED);
            } else if (hovered) {
                g.fill(x + 1, top, x + width - 1, top + ROW, Gui.ROW_HOVER);
            }
            g.renderItem(ICON, x + 3, top + 2);
            g.drawString(font, Gui.clip(font, row.name(), width - 28), x + 22, top + 2, 0xFF202020, false);
            Gui.small(g, font, Gui.clip(font, row.entry().id().toString(), (int) ((width - 28) / 0.75f)), x + 22, top + 12, Gui.LABEL_SOFT);
        }
        g.disableScissor();

        if (contentHeight > height) {
            int barHeight = Math.max(12, height * height / contentHeight);
            int barTop = y + (int) ((height - barHeight) * (scroll / (contentHeight - height)));
            g.fill(x + width - 4, barTop, x + width - 1, barTop + barHeight, 0xFF6F6F6F);
        }
    }

    Optional<StructureCatalog.Entry> click(double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return Optional.empty();
        }
        double local = mouseY - y - 1 + scroll;
        for (Row row : rows) {
            if (row.entry() != null && local >= row.top() && local < row.top() + row.height()) {
                return Optional.of(row.entry());
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
