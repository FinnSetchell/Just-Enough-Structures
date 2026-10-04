package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.overrides.ContainerPatches;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.finndog.justenoughstructures.overrides.SpawnerPatches;
import com.finndog.justenoughstructures.server.PackToolsState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Pack tools' first page: how much has been changed, anything that needs looking at, ways to start,
 * and everything changed so far.
 */
final class ToolsOverview extends ToolsSection {
    /** The widest the page gets, so its counts and rows don't stretch across a wide screen. */
    private static final int WIDEST = 640;
    private final Scroller scroller = new Scroller();

    ToolsOverview(PackToolsScreen screen) {
        super(screen);
    }

    @Override
    String count() {
        PackToolsState state = screen.state();
        return state == null || state.waiting() == 0 ? "" : String.valueOf(state.waiting());
    }

    @Override
    boolean scroll(double mouseX, double mouseY, double delta) {
        return scroller.scroll(mouseX, mouseY, delta);
    }

    @Override
    void render(GuiGraphics g, ToolsUi ui, int x, int y, int w, int h, int mouseX, int mouseY) {
        PackToolsState state = screen.state();
        int top = scroller.begin(g, ui, x, y, Math.min(w, WIDEST), h);
        int cw = scroller.width();
        int cy = top + cards(g, ui, state, x, top, cw);

        List<Map.Entry<ResourceLocation, LootOverrides.Status>> needs = new ArrayList<>();
        for (Map.Entry<ResourceLocation, LootOverrides.Status> e : state.overrides().entrySet()) {
            if (e.getValue() == LootOverrides.Status.ORIGINAL_CHANGED || e.getValue() == LootOverrides.Status.BROKEN) {
                needs.add(e);
            }
        }
        if (!needs.isEmpty()) {
            cy = ui.heading(g, Component.translatable("screen.justenoughstructures.tools.needs_look"), x, cy, cw);
            for (Map.Entry<ResourceLocation, LootOverrides.Status> e : needs) {
                ResourceLocation table = e.getKey();
                List<RowButton> buttons = e.getValue() == LootOverrides.Status.BROKEN
                        ? List.of(RowButton.of("tools.edit", () -> screen.openEditor(table, false)))
                        : List.of(RowButton.of("editor.see_changes", () -> screen.showChanges(table)),
                        RowButton.of("editor.merge", () -> screen.openEditor(table, true)),
                        RowButton.of("editor.keep", () -> screen.keep(table)));
                cy += row(g, ui, x, cy, cw, Icon.item(CHEST), StructureNames.lootTable(table.toString()), null, 0,
                        Component.translatable("screen.justenoughstructures.editor.status." + e.getValue().name().toLowerCase(java.util.Locale.ROOT)).getString(),
                        buttons, null, 0xFFF1DCAE, false);
            }
            cy += 4;
        }

        cy = ui.heading(g, Component.translatable("screen.justenoughstructures.tools.start"), x, cy, cw);
        int bx = x;
        String[][] starts = {{"edit", "LOOT"}, {"chest", "CHESTS"}, {"spawner", "SPAWNERS"}, {"new", null}, {"hide", "STRUCTURES"}, {"notes", "STRUCTURES"}};
        for (String[] start : starts) {
            Component label = Component.translatable("screen.justenoughstructures.tools.start." + start[0]);
            int bw = ui.buttonWidth(label) + 6;
            if (bx > x && bx + bw > x + cw) {
                bx = x;
                cy += ToolsUi.BUTTON + 3;
            }
            Runnable action = start[1] == null ? screen::newTable
                    : start[0].equals("chest") ? screen::pickChest
                    : start[0].equals("spawner") ? screen::pickSpawner
                    : () -> screen.go(PackToolsScreen.Section.valueOf(start[1]), null);
            bx += ui.button(g, label, bx, cy, bw, ToolsUi.BUTTON + 2, true, action) + 3;
        }
        cy += ToolsUi.BUTTON + 8;

        cy = ui.heading(g, Component.translatable("screen.justenoughstructures.tools.recent"), x, cy, cw);
        if (state.overrides().isEmpty()) {
            Gui.fine(g, font, Component.translatable("screen.justenoughstructures.tools.no_tables").getString(), x + 2, cy, Gui.LABEL_SOFT);
            cy += Gui.fineLine(font) + 4;
        }
        for (Map.Entry<ResourceLocation, LootOverrides.Status> e : state.overrides().entrySet()) {
            cy += tableRow(g, ui, x, cy, cw, e.getKey(), e.getValue());
        }
        if (state.patches().isEmpty()) {
            Gui.fine(g, font, Component.translatable("screen.justenoughstructures.tools.no_chests").getString(), x + 2, cy, Gui.LABEL_SOFT);
            cy += Gui.fineLine(font) + 4;
        }
        for (ContainerPatches.Patch patch : state.patches()) {
            cy += chestRow(g, ui, x, cy, cw, patch);
        }
        for (SpawnerPatches.Patch patch : state.spawners()) {
            cy += spawnerRow(g, ui, x, cy, cw, patch);
        }
        scroller.end(g, ui, cy - top + 2);
    }

    /** The four counts along the top, each opening its section. Returns their height. */
    private int cards(GuiGraphics g, ToolsUi ui, PackToolsState state, int x, int y, int w) {
        int notes = 0;
        for (PackToolsState.Written written : state.structures().values()) {
            if (written.fromPack() && written.info().notes() != null) {
                notes++;
            }
        }
        int[] counts = {state.overrides().size(), state.patches().size(),
                state.settings().hiddenStructures().size() + state.settings().hiddenMods().size(), notes};
        String[] keys = {"tables", "chests", "hidden", "notes"};
        PackToolsScreen.Section[] goes = {PackToolsScreen.Section.LOOT, PackToolsScreen.Section.CHESTS,
                PackToolsScreen.Section.STRUCTURES, PackToolsScreen.Section.STRUCTURES};
        String[] labels = new String[4];
        int widest = 0;
        for (int i = 0; i < 4; i++) {
            labels[i] = Component.translatable("screen.justenoughstructures.tools.count." + keys[i]).getString();
            widest = Math.max(widest, Gui.fineWidth(font, labels[i]));
        }
        // Four across only when every label fits on one line; otherwise two, their labels wrapped if need be.
        int columns = (w - 12) / 4 - 10 >= widest ? 4 : 2;
        int cardW = (w - (columns - 1) * 4) / columns;
        int lines = 1;
        for (String label : labels) {
            lines = Math.max(lines, font.split(Component.literal(label), (int) ((cardW - 10) / Gui.fineScale())).size());
        }
        int lineH = Gui.fineLine(font) + 1;
        int cardH = 32 + (lines - 1) * lineH;
        for (int i = 0; i < 4; i++) {
            int cx = x + (i % columns) * (cardW + 4);
            int cy = y + (i / columns) * (cardH + 4);
            Gui.card(g, cx, cy, cardW, cardH);
            if (ui.hovered(cx, cy, cardW, cardH)) {
                g.fill(cx + 1, cy + 1, cx + cardW - 1, cy + cardH - 1, 0x40FFFFFF);
            }
            g.pose().pushPose();
            g.pose().translate(cx + 5, cy + 4, 0);
            g.pose().scale(2, 2, 1);
            g.drawString(font, String.valueOf(counts[i]), 0, 0, ToolsUi.TEXT, false);
            g.pose().popPose();
            Gui.fineWrapped(g, font, Component.literal(labels[i]), cx + 5, cy + cardH - 3 - lines * lineH, cardW - 10, Gui.LABEL_SOFT);
            PackToolsScreen.Section to = goes[i];
            ui.spot(cx, cy, cardW, cardH, () -> screen.go(to, null));
        }
        return (4 / columns) * (cardH + 4) + 4;
    }

    /** An edited table: what it is, where it's used, and what can be done with it. */
    int tableRow(GuiGraphics g, ToolsUi ui, int x, int y, int w, ResourceLocation table, LootOverrides.Status status) {
        List<RowButton> buttons = new ArrayList<>();
        buttons.add(RowButton.of("tools.edit", () -> screen.openEditor(table, false)));
        if (status == LootOverrides.Status.ORIGINAL_CHANGED) {
            buttons.add(RowButton.of("editor.see_changes", () -> screen.showChanges(table)));
            buttons.add(RowButton.of("editor.merge", () -> screen.openEditor(table, true)));
            buttons.add(RowButton.of("editor.keep", () -> screen.keep(table)));
        }
        buttons.add(new RowButton(Component.translatable("screen.justenoughstructures.editor.remove"), () -> screen.removeEdit(table),
                Component.translatable("screen.justenoughstructures.editor.remove_hint")));
        Component mark = editedMark(status);
        if (screen.waiting(PackToolsState.tableKey(table))) {
            mark = Component.translatable("screen.justenoughstructures.tools.after_reload");
        }
        return row(g, ui, x, y, w, Icon.item(CHEST), StructureNames.lootTable(table.toString()), mark,
                screen.waiting(PackToolsState.tableKey(table)) ? ToolsUi.CHANGED : markColour(status),
                table + " · " + usedBy(table), buttons, null, 0, false);
    }

    /** A changed container: where it is, what it was and is now, and a way to open or undo it. */
    int chestRow(GuiGraphics g, ToolsUi ui, int x, int y, int w, ContainerPatches.Patch patch) {
        String name = Component.translatable("screen.justenoughstructures.tools.chest_name", templateName(patch.template()),
                BuiltInRegistries.BLOCK.get(patch.block()).getName()).getString();
        boolean waiting = screen.waiting(PackToolsState.chestKey(patch.template(), patch.pos()));
        String detail = Component.translatable("screen.justenoughstructures.tools.changed", tableName(patch.original()),
                StructureNames.lootTable(patch.table().toString())).getString();
        List<RowButton> buttons = List.of(
                RowButton.of("tools.open", () -> screen.go(PackToolsScreen.Section.CHESTS, ToolsChests.ChestRef.of(patch))),
                new RowButton(Component.translatable("screen.justenoughstructures.container.undo"), () -> screen.undoChest(patch.template(), patch.pos()),
                        Component.translatable("screen.justenoughstructures.container.undo_hint")));
        return row(g, ui, x, y, w, Icon.item(new net.minecraft.world.item.ItemStack(BuiltInRegistries.BLOCK.get(patch.block()))), name,
                waiting ? Component.translatable("screen.justenoughstructures.tools.after_reload") : null, ToolsUi.CHANGED, detail, buttons, null, 0, false);
    }

    /** A changed spawner: where it is, what it made and makes now, and a way to open or undo it. */
    private int spawnerRow(GuiGraphics g, ToolsUi ui, int x, int y, int w, SpawnerPatches.Patch patch) {
        String name = Component.translatable("screen.justenoughstructures.tools.chest_name", templateName(patch.template()),
                BuiltInRegistries.BLOCK.get(patch.block()).getName()).getString();
        boolean waiting = screen.waiting(PackToolsState.spawnerKey(patch.template(), patch.pos()));
        List<RowButton> buttons = List.of(
                RowButton.of("tools.open", () -> screen.go(PackToolsScreen.Section.SPAWNERS, ToolsSpawners.SpawnerRef.of(patch))),
                new RowButton(Component.translatable("screen.justenoughstructures.container.undo"), () -> screen.undoSpawner(patch.template(), patch.pos()),
                        Component.translatable("screen.justenoughstructures.spawner.undo_hint")));
        return row(g, ui, x, y, w, Icon.item(ToolsSpawners.mobIcon(patch.mob())), name,
                waiting ? Component.translatable("screen.justenoughstructures.tools.after_reload") : null, ToolsUi.CHANGED,
                ToolsSpawners.changed(patch), buttons, null, 0, false);
    }

    static String tableName(String table) {
        return table == null || table.isEmpty() ? Component.translatable("screen.justenoughstructures.tools.no_table").getString()
                : StructureNames.lootTable(table);
    }
}
