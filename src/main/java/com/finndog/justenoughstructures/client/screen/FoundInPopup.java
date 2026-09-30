package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.client.Thumbnails;
import com.finndog.justenoughstructures.client.render.StructureViewport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Lists the structures whose loot can give an item, like pressing U on an item in JEI. */
final class FoundInPopup {
    static final int WIDTH = 230;
    private static final int ROW = 22;
    private static final int VISIBLE_ROWS = 7;
    private static final ItemStack STRUCTURE_ICON = new ItemStack(Items.FILLED_MAP);

    record Row(ResourceLocation structure, Set<ResourceLocation> tables) {
    }

    final ItemStack item;
    private List<Row> rows;
    private boolean sorted;
    private int scroll;
    int x;
    int y;

    FoundInPopup(ItemStack item) {
        this.item = item.copyWithCount(1);
    }

    int height() {
        return 30 + VISIBLE_ROWS * ROW + 14;
    }

    void place(int screenWidth, int screenHeight) {
        x = (screenWidth - WIDTH) / 2;
        y = Math.max(4, (screenHeight - height()) / 2);
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + height();
    }

    private List<Row> rows() {
        if (rows == null && FoundIn.ready()) {
            rows = new ArrayList<>();
            for (Map.Entry<ResourceLocation, Set<ResourceLocation>> e : FoundIn.structuresFor(item.getItem()).entrySet()) {
                rows.add(new Row(e.getKey(), e.getValue()));
                e.getValue().forEach(ClientRequests::odds);
            }
            rows.sort(Comparator.comparing(r -> StructureNames.structure(r.structure())));
        }
        if (rows != null && !sorted && rows.stream().allMatch(r -> chance(r) >= 0)) {
            // Best chance first, once every chance is known, so the list only moves once.
            rows.sort(Comparator.comparingDouble((Row r) -> -chance(r)).thenComparing(r -> StructureNames.structure(r.structure())));
            sorted = true;
        }
        return rows;
    }

    private double chance(Row row) {
        return FoundIn.chance(item.getItem(), row.tables());
    }

    void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        Gui.panel(g, x, y, WIDTH, height());
        Gui.slot(g, x + 7, y + 6);
        g.renderItem(item, x + 8, y + 7);
        g.drawString(font, Gui.clip(font, item.getHoverName().getString(), WIDTH - 40), x + 30, y + 7, Gui.LABEL, false);

        List<Row> list = rows();
        int top = y + 30;
        if (list == null) {
            float progress = ClientRequests.indexProgress();
            Component text = progress < 0
                    ? Component.translatable("screen.justenoughstructures.indexing")
                    : Component.translatable("screen.justenoughstructures.indexing_progress", Math.round(progress * 100));
            Gui.wrapped(g, font, text, x + 8, top + 4, WIDTH - 16, Gui.LABEL_SOFT);
            return;
        }
        Gui.small(g, font, Component.translatable("screen.justenoughstructures.found_in_chance", list.size()).getString(), x + 30, y + 17, Gui.LABEL_SOFT);
        Gui.inset(g, x + 6, top - 1, WIDTH - 12, VISIBLE_ROWS * ROW + 2, 0xFFB9B9B9);
        if (list.isEmpty()) {
            Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.found_nowhere"), x + 10, top + 4, WIDTH - 20, Gui.LABEL_SOFT);
        }
        for (int i = 0; i < VISIBLE_ROWS && scroll + i < list.size(); i++) {
            Row row = list.get(scroll + i);
            int ry = top + i * ROW;
            boolean hovered = mouseX >= x + 7 && mouseX < x + WIDTH - 7 && mouseY >= ry && mouseY < ry + ROW;
            if (hovered) {
                g.fill(x + 7, ry, x + WIDTH - 7, ry + ROW, Gui.ROW_HOVER);
            }
            int thumbnail = Thumbnails.textureId(row.structure());
            if (thumbnail >= 0) {
                StructureViewport.drawTexture(g, thumbnail, x + 9, ry + 2, 18, 18);
            } else {
                g.renderItem(STRUCTURE_ICON, x + 10, ry + 3);
            }
            double chance = chance(row);
            String pct = FoundIn.percent(chance);
            Gui.fitted(g, font, StructureNames.structure(row.structure()), x + 30, ry + 3, WIDTH - 50 - font.width(pct), 0xFF202020);
            g.drawString(font, pct, x + WIDTH - 10 - font.width(pct), ry + 3, 0xFF202020, false);
            // Which of its loot tables, unless that only repeats the structure's name.
            String name = StructureNames.structure(row.structure());
            String tables = row.tables().stream().map(t -> StructureNames.lootTable(t.toString())).distinct()
                    .filter(t -> !t.equals(name)).collect(Collectors.joining(", "));
            Gui.small(g, font, Gui.clip(font, tables, (int) ((WIDTH - 40) / 0.75f)), x + 30, ry + 13, Gui.LABEL_SOFT);
        }
        if (list.size() > VISIBLE_ROWS) {
            String more = (scroll + 1) + "-" + Math.min(list.size(), scroll + VISIBLE_ROWS) + " / " + list.size();
            Gui.small(g, font, more, x + WIDTH - 8 - (int) (font.width(more) * 0.75f), y + height() - 11, Gui.LABEL_SOFT);
        }
        Gui.small(g, font, Component.translatable("screen.justenoughstructures.found_hint").getString(), x + 8, y + height() - 11, Gui.LABEL_SOFT);
    }

    Optional<Row> click(double mouseX, double mouseY) {
        List<Row> list = rows();
        if (list == null) {
            return Optional.empty();
        }
        int top = y + 30;
        for (int i = 0; i < VISIBLE_ROWS && scroll + i < list.size(); i++) {
            int ry = top + i * ROW;
            if (mouseX >= x + 7 && mouseX < x + WIDTH - 7 && mouseY >= ry && mouseY < ry + ROW) {
                return Optional.of(list.get(scroll + i));
            }
        }
        return Optional.empty();
    }

    /** The structures showing right now, to make pictures for. */
    List<ResourceLocation> visibleStructures() {
        List<Row> list = rows();
        if (list == null) {
            return List.of();
        }
        return list.subList(scroll, Math.min(list.size(), scroll + VISIBLE_ROWS)).stream().map(Row::structure).toList();
    }

    void scroll(double delta) {
        List<Row> list = rows();
        if (list != null) {
            scroll = Math.max(0, Math.min(scroll - (int) Math.signum(delta), Math.max(0, list.size() - VISIBLE_ROWS)));
        }
    }
}
