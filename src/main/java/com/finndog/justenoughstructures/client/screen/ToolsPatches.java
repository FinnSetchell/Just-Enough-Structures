package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.server.PackToolsState;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * What the Chests and Spawners sections share: picking one in the browser to change, the list of
 * those changed so far, and the one picked beside it, or on its own where there isn't room for both.
 */
abstract class ToolsPatches<P, R extends ToolsPatches.Ref<P>> extends ToolsSection {
    /** A container or spawner Pack tools shows: one picked in the browser, or one changed before, known only by its template. */
    interface Ref<P> {
        /** The structure it was picked in, or null. */
        ResourceLocation structure();

        /** The template it's in, or null when the structure's code places it. */
        ResourceLocation template();

        /** Where it is in that template, or null. */
        BlockPos templatePos();

        /** Whether this is the one the patch changes. */
        boolean same(P patch);

        /** Only one in a template can be changed; one a structure's code places can't. */
        default boolean byCode() {
            return template() == null;
        }
    }

    /** What a row of the list shows: its icon, the block it's for, and below that, what it has or how it changed. */
    record Line(ItemStack icon, Component what, String detail) {
    }

    private final Class<R> refs;
    private final Scroller list = new Scroller();
    private final Scroller detail = new Scroller();
    /** Whether the list and what's picked were last shown one at a time, rather than side by side. */
    private boolean single;
    protected R selected;

    ToolsPatches(PackToolsScreen screen, Class<R> refs) {
        super(screen);
        this.refs = refs;
    }

    /** The patches saved so far. */
    abstract List<P> patches(PackToolsState state);

    abstract R refOf(P patch);

    abstract ResourceLocation templateOf(P patch);

    abstract BlockPos posOf(P patch);

    /** "chest" or "spawner": which pick button, list heading and waiting changes are this section's. */
    abstract String kind();

    /** Starts picking one in the browser. */
    abstract void pickInBrowser();

    /** Whether a change to the one at a spot in a template waits for a /reload. */
    abstract boolean waiting(ResourceLocation template, BlockPos pos);

    /** The row for one picked in the browser that hasn't been changed. */
    abstract Line browserLine(R ref);

    /** The row for one changed so far. */
    abstract Line patchLine(P patch);

    /** What a change here does, while nothing's picked. Returns the y below it. */
    abstract int intro(GuiGraphics g, int x, int y, int w);

    /** The one picked. Returns the y below it. */
    abstract int detail(GuiGraphics g, ToolsUi ui, int x, int y, int w, R ref);

    @Override
    String count() {
        PackToolsState state = screen.state();
        return state == null || patches(state).isEmpty() ? "" : String.valueOf(patches(state).size());
    }

    @Override
    Object selection() {
        return selected;
    }

    @Override
    void select(Object selection) {
        selected = refs.isInstance(selection) ? refs.cast(selection) : null;
        detail.reset();
    }

    @Override
    Object parse(String text) {
        PackToolsState state = screen.state();
        if (state == null) {
            return null;
        }
        for (P patch : patches(state)) {
            if (templateOf(patch).toString().equals(text)) {
                return refOf(patch);
            }
        }
        return null;
    }

    @Override
    boolean scroll(double mouseX, double mouseY, double delta) {
        // Shown one at a time, only the one on show scrolls.
        return (!single || selected == null) && list.scroll(mouseX, mouseY, delta)
                || (!single || selected != null) && detail.scroll(mouseX, mouseY, delta);
    }

    /** The patch for the one picked, if it has one. */
    P patchOf(R ref) {
        if (ref == null || screen.state() == null) {
            return null;
        }
        for (P patch : patches(screen.state())) {
            if (ref.same(patch)) {
                return patch;
            }
        }
        return null;
    }

    @Override
    void render(GuiGraphics g, ToolsUi ui, int x, int y, int w, int h, int mouseX, int mouseY) {
        single = oneAtATime(w);
        if (single && selected != null) {
            int cy = backToList(g, ui, x, y);
            int dTop = detail.begin(g, ui, x, cy, w, y + h - cy);
            int end = detail(g, ui, x, dTop, detail.width(), selected);
            detail.end(g, ui, end - dTop);
            return;
        }
        int leftW = single ? w : Math.max(130, Math.min(300, w * 38 / 100));
        // As wide as the list, or as its label where that's wider, reaching over the column beside it.
        Component pick = Component.translatable("screen.justenoughstructures.tools.pick_" + kind());
        int pickW = Math.min(w, Math.max(leftW, ui.buttonWidth(pick)));
        ui.button(g, pick, x, y, pickW, 18, screen.browser() != null, this::pickInBrowser,
                Component.translatable("screen.justenoughstructures.tools.pick_" + kind() + "_hint"));
        int listTop = y + 22;
        Gui.inset(g, x, listTop, leftW, y + h - listTop, Gui.PANEL);
        int top = list.begin(g, ui, x + 1, listTop + 1, leftW - 2, y + h - listTop - 2);
        int rw = list.width();
        int cy = top + 2;
        if (single) {
            // No room beside the list, so what this is for goes above it.
            cy = intro(g, x + 4, cy, rw - 8) + 6;
        }
        PackToolsState state = screen.state();
        if (selected != null && selected.structure() != null && patchOf(selected) == null) {
            Gui.fine(g, font, Component.translatable("screen.justenoughstructures.tools.from_browser").getString(), x + 4, cy, Gui.LABEL_SOFT);
            cy += Gui.fineLine(font) + 2;
            Line line = browserLine(selected);
            String name = Component.translatable("screen.justenoughstructures.tools.chest_name", StructureNames.structure(selected.structure()), line.what())
                    .getString();
            cy += row(g, ui, x + 1, cy, rw, Icon.item(line.icon()), name, null, 0, line.detail(), List.of(), null, 0, true) + 4;
        }
        Gui.fine(g, font, Component.translatable("screen.justenoughstructures.tools.changed_" + kind() + "s").getString(), x + 4, cy, Gui.LABEL_SOFT);
        cy += Gui.fineLine(font) + 2;
        if (patches(state).isEmpty()) {
            Gui.fine(g, font, Component.translatable("screen.justenoughstructures.tools.none_yet").getString(), x + 4, cy, Gui.LABEL_SOFT);
            cy += Gui.fineLine(font) + 4;
        }
        for (P patch : patches(state)) {
            R ref = refOf(patch);
            boolean isSelected = selected != null && selected.same(patch);
            Line line = patchLine(patch);
            String name = Component.translatable("screen.justenoughstructures.tools.chest_name", templateName(templateOf(patch)), line.what()).getString();
            String detailText = line.detail();
            if (waiting(templateOf(patch), posOf(patch))) {
                detailText += " " + Component.translatable("screen.justenoughstructures.tools.after_reload_brackets").getString();
            }
            cy += row(g, ui, x + 1, cy, rw, Icon.item(line.icon()), name, null, 0, detailText, List.of(), () -> screen.pick(ref), 0, isSelected);
        }
        list.end(g, ui, cy - top + 2);
        if (single) {
            return;
        }

        int dx = x + leftW + 6;
        int dw = Math.min(w - leftW - 6, READABLE);
        int dy = pickW > leftW ? listTop : y;
        int dTop = detail.begin(g, ui, dx, dy, dw, y + h - dy);
        int end = selected == null ? intro(g, dx, dTop + 4, detail.width()) : detail(g, ui, dx, dTop, detail.width(), selected);
        detail.end(g, ui, end - dTop);
    }

    /** Where one is: its structure and mod when it was picked in one, or else its template. */
    static String where(Ref<?> ref) {
        return ref.structure() == null ? ref.template().toString()
                : StructureNames.structure(ref.structure()) + " · " + StructureNames.mod(ref.structure().getNamespace());
    }

    /** The bar under a changed one saying what it's changed from, with Undo. Returns the y below it. */
    int undoBar(GuiGraphics g, ToolsUi ui, Component changed, int x, int y, int w, Runnable undo) {
        Component label = Component.translatable("screen.justenoughstructures.container.undo");
        int undoW = ui.buttonWidth(label);
        int barH = ui.status(g, changed, x, y, w - undoW - 4, ToolsUi.STATUS_CHANGED);
        g.fill(x + w - undoW - 4, y, x + w, y + barH, ToolsUi.statusColours(ToolsUi.STATUS_CHANGED)[0]);
        ui.button(g, label, x + w - undoW - 2, y + (barH - ToolsUi.BUTTON) / 2, true, undo,
                Component.translatable("screen.justenoughstructures.container.undo_hint"));
        return y + Math.max(barH, ToolsUi.BUTTON) + 3;
    }

    /** One picked in the browser whose change was undone, waiting for the /reload that puts it back. */
    boolean waitingUndo(R ref) {
        return ref.template() != null && waiting(ref.template(), ref.templatePos());
    }
}
