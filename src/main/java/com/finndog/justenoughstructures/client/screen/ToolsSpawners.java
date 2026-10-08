package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.Regs;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.capture.TrialSpawners;
import com.finndog.justenoughstructures.overrides.SpawnerPatches;
import com.finndog.justenoughstructures.server.PackToolsState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Spawners: picking one in the browser to change, those changed so far, and for the one picked,
 * where it is, what kind of spawner it is, its mob and what it's changed from.
 */
final class ToolsSpawners extends ToolsSection {
    private static final ItemStack SPAWNER = new ItemStack(Items.SPAWNER);

    /**
     * A spawner Pack tools shows: one clicked in the browser, with the structure and layout it's in,
     * or one changed before, known only by its template.
     *
     * @param structure   the structure it was clicked in, or null
     * @param pos         where it is in that layout, or null
     * @param mob         the mob it makes, or "" for none
     * @param others      how many other mobs it makes as well
     * @param block       the block it is in its template, before any change made it the other kind of spawner
     * @param template    the template it's in, or null when the structure's code places it or picks its mob
     * @param templatePos where it is in that template, or null
     * @param patchedFrom the mob it had before it was changed, when it had been in that layout, or null
     */
    record SpawnerRef(ResourceLocation structure, long seed, BlockPos pos, String mob, int others, ResourceLocation block,
                      ResourceLocation template, BlockPos templatePos, String patchedFrom) {
        static SpawnerRef of(SpawnerPatches.Patch patch) {
            return new SpawnerRef(null, 0, null, patch.mob(), 0, patch.block(), patch.template(), patch.pos(), null);
        }

        static SpawnerRef of(ResourceLocation structure, long seed, StructureSnapshot.Spawner spawner, boolean trial) {
            StructureSnapshot.Source source = spawner.source();
            ResourceLocation block = source != null ? source.block() : trial ? TrialSpawners.BLOCK : SpawnerPatches.SPAWNER;
            return new SpawnerRef(structure, seed, spawner.pos(), spawner.mob(), spawner.others(), block,
                    source == null ? null : source.template(), source == null ? null : source.pos(), source == null ? null : source.patchedFrom());
        }

        /** Its mob as things stand: the patch's, or with no patch any more, its own. */
        String mobNow(SpawnerPatches.Patch patch) {
            return patch != null ? patch.mob() : patchedFrom != null ? patchedFrom : mob;
        }

        int othersNow(SpawnerPatches.Patch patch) {
            return patch != null || patchedFrom != null ? 0 : others;
        }

        /** The block it is as things stand: what the patch makes it, or with no patch, what it is in its template. */
        ResourceLocation blockNow(SpawnerPatches.Patch patch) {
            return patch != null ? patch.target() : block;
        }

        /** Only spawners whose mob their template decides can be given another. */
        boolean byCode() {
            return template == null;
        }

        boolean same(SpawnerPatches.Patch patch) {
            return template != null && template.equals(patch.template()) && templatePos.equals(patch.pos());
        }
    }

    private final Scroller list = new Scroller();
    private final Scroller detail = new Scroller();
    /** Whether the list and what's picked were last shown one at a time, rather than side by side. */
    private boolean single;
    private SpawnerRef selected;

    ToolsSpawners(PackToolsScreen screen) {
        super(screen);
    }

    @Override
    String count() {
        PackToolsState state = screen.state();
        return state == null || state.spawners().isEmpty() ? "" : String.valueOf(state.spawners().size());
    }

    @Override
    Object selection() {
        return selected;
    }

    @Override
    void select(Object selection) {
        selected = selection instanceof SpawnerRef ref ? ref : null;
        detail.reset();
    }

    @Override
    Object parse(String text) {
        PackToolsState state = screen.state();
        if (state == null) {
            return null;
        }
        for (SpawnerPatches.Patch patch : state.spawners()) {
            if (patch.template().toString().equals(text)) {
                return SpawnerRef.of(patch);
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

    /** The patch for the picked spawner, if it has one. */
    private SpawnerPatches.Patch patchOf(SpawnerRef ref) {
        if (ref == null || screen.state() == null) {
            return null;
        }
        for (SpawnerPatches.Patch patch : screen.state().spawners()) {
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
        Component pick = Component.translatable("screen.justenoughstructures.tools.pick_spawner");
        int pickW = Math.min(w, Math.max(leftW, ui.buttonWidth(pick)));
        ui.button(g, pick, x, y, pickW, 18, screen.browser() != null, screen::pickSpawner,
                Component.translatable("screen.justenoughstructures.tools.pick_spawner_hint"));
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
            String name = Component.translatable("screen.justenoughstructures.tools.chest_name", StructureNames.structure(selected.structure()),
                    blockName(selected.blockNow(null))).getString();
            cy += row(g, ui, x + 1, cy, rw, Icon.item(mobIcon(selected.mobNow(null))), name, null, 0,
                    mobName(selected.mobNow(null), selected.othersNow(null)).getString(), List.of(), null, 0, true) + 4;
        }
        Gui.fine(g, font, Component.translatable("screen.justenoughstructures.tools.changed_spawners").getString(), x + 4, cy, Gui.LABEL_SOFT);
        cy += Gui.fineLine(font) + 2;
        if (state.spawners().isEmpty()) {
            Gui.fine(g, font, Component.translatable("screen.justenoughstructures.tools.none_yet").getString(), x + 4, cy, Gui.LABEL_SOFT);
            cy += Gui.fineLine(font) + 4;
        }
        for (SpawnerPatches.Patch patch : state.spawners()) {
            SpawnerRef ref = SpawnerRef.of(patch);
            boolean isSelected = selected != null && selected.same(patch);
            String name = Component.translatable("screen.justenoughstructures.tools.chest_name", templateName(patch.template()),
                    blockName(patch.target())).getString();
            String detailText = changed(patch);
            if (screen.waiting(PackToolsState.spawnerKey(patch.template(), patch.pos()))) {
                detailText += " " + Component.translatable("screen.justenoughstructures.tools.after_reload_brackets").getString();
            }
            cy += row(g, ui, x + 1, cy, rw, Icon.item(mobIcon(patch.mob())), name, null, 0, detailText, List.of(),
                    () -> screen.pick(ref), 0, isSelected);
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

    /** What this section is for, while nothing's picked. Returns the y below it. */
    private int intro(GuiGraphics g, int x, int y, int w) {
        int ty = Gui.fineWrapped(g, font, Component.translatable("screen.justenoughstructures.tools.spawners_intro"), x, y, w, Gui.LABEL_SOFT);
        ty = Gui.fineWrapped(g, font, Component.translatable("screen.justenoughstructures.tools.spawners_intro_more"), x, ty + 4, w, Gui.LABEL_SOFT);
        return TrialSpawners.exist()
                ? Gui.fineWrapped(g, font, Component.translatable("screen.justenoughstructures.tools.spawners_intro_trial"), x, ty + 4, w, Gui.LABEL_SOFT)
                : ty;
    }

    /**
     * "Magma Cube > Husk", for a changed spawner's row, saying so when it's been made the other kind,
     * or only that when its mob is the same.
     */
    static String changed(SpawnerPatches.Patch patch) {
        if (patch.to() == null) {
            return Component.translatable("screen.justenoughstructures.tools.changed", mobName(patch.original(), patch.others()),
                    StructureNames.mob(patch.mob())).getString();
        }
        String kind = TrialSpawners.isBlock(patch.to()) ? "trial" : "spawner";
        if (patch.mob().equals(patch.original()) && patch.others() == 0) {
            return Component.translatable("screen.justenoughstructures.tools.made_" + kind, StructureNames.mob(patch.mob())).getString();
        }
        return Component.translatable("screen.justenoughstructures.tools.changed_to_" + kind, mobName(patch.original(), patch.others()),
                StructureNames.mob(patch.mob())).getString();
    }

    private static Component blockName(ResourceLocation block) {
        return Regs.value(BuiltInRegistries.BLOCK, block).getName();
    }

    private static ItemStack blockIcon(ResourceLocation block) {
        ItemStack icon = new ItemStack(Regs.value(BuiltInRegistries.BLOCK, block).asItem());
        return icon.isEmpty() ? SPAWNER : icon;
    }

    /** The mob's egg or other icon, or an empty spawner for none. */
    static ItemStack mobIcon(String mob) {
        ResourceLocation id = mob.isEmpty() ? null : ResourceLocation.tryParse(mob);
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return SPAWNER;
        }
        ItemStack icon = InfoPanel.entityIcon(Regs.value(BuiltInRegistries.ENTITY_TYPE, id));
        return icon.isEmpty() ? SPAWNER : icon;
    }

    /** A mob's name, or "Zombie and 2 more" for a spawner that makes a mix. */
    static Component mobName(String mob, int others) {
        Component name = StructureNames.mob(mob);
        return others > 0 ? Component.translatable("screen.justenoughstructures.tools.mob_mix", name, others) : name;
    }

    /** The picked spawner. Returns the y below it. */
    private int detail(GuiGraphics g, ToolsUi ui, int x, int y, int w, SpawnerRef ref) {
        SpawnerPatches.Patch patch = patchOf(ref);
        String mob = ref.mobNow(patch);
        int others = ref.othersNow(patch);
        ResourceLocation blockNow = ref.blockNow(patch);
        boolean trial = TrialSpawners.isBlock(blockNow);
        Component block = blockName(blockNow);
        String name = ref.template() == null ? block.getString()
                : Component.translatable("screen.justenoughstructures.tools.chest_in", block, templateName(ref.template())).getString();
        String where = ref.structure() == null ? ref.template().toString()
                : StructureNames.structure(ref.structure()) + " · " + StructureNames.mod(ref.structure().getNamespace());
        int cy = y + row(g, ui, x, y, w, Icon.item(blockIcon(blockNow)), name, null, 0, where, List.of(), null, 0, false);
        Component from = ref.byCode() ? Component.translatable("screen.justenoughstructures.tools.spawner_by_code")
                : Component.translatable("screen.justenoughstructures.tools.from_template", ref.template().toString(), ref.templatePos().toShortString());
        cy = Gui.fineWrapped(g, font, from, x, cy + 2, w, Gui.LABEL_SOFT) + 2;

        if (!ref.byCode() && TrialSpawners.exist()) {
            // Made the other kind with the mob it has now. Back to what it was in its template, with its own mob, is undoing it.
            ResourceLocation other = TrialSpawners.isBlock(ref.block()) != trial ? ref.block() : trial ? SpawnerPatches.SPAWNER : TrialSpawners.BLOCK;
            boolean undoes = patch != null && other.equals(patch.block()) && mob.equals(patch.original());
            String make = trial ? "make_spawner" : "make_trial";
            List<RowButton> buttons = List.of(new RowButton(Component.translatable("screen.justenoughstructures.tools." + make),
                    () -> screen.switchSpawner(ref, mob, other, undoes), Component.translatable("screen.justenoughstructures.tools." + make + "_hint")));
            cy = ui.heading(g, Component.translatable("screen.justenoughstructures.tools.spawner_kind"), x, cy + 2, w);
            cy += row(g, ui, x, cy, w, Icon.item(blockIcon(blockNow)), block.getString(), null, 0,
                    Component.translatable("screen.justenoughstructures.tools." + (trial ? "kind_trial" : "kind_spawner")).getString(), buttons, null, 0, false) + 2;
        }

        cy = ui.heading(g, Component.translatable("screen.justenoughstructures.tools.mob"), x, cy + 2, w);
        List<RowButton> buttons = ref.byCode() ? List.of() : List.of(new RowButton(Component.translatable("screen.justenoughstructures.container.change"),
                () -> screen.changeSpawner(ref, mob, blockNow)));
        String id = mob.isEmpty() ? Component.translatable("screen.justenoughstructures.hover_spawns_nothing").getString() : mob;
        cy += row(g, ui, x, cy, w, Icon.item(mobIcon(mob)), mobName(mob, others).getString(), null, 0, id, buttons, null, 0, false) + 2;

        if (patch != null) {
            boolean waiting = screen.waiting(PackToolsState.spawnerKey(patch.template(), patch.pos()));
            Component changed = Component.translatable(changedFromKey(patch, waiting), mobName(patch.original(), patch.others()));
            Component undo = Component.translatable("screen.justenoughstructures.container.undo");
            int undoW = ui.buttonWidth(undo);
            int barH = ui.status(g, changed, x, cy, w - undoW - 4, 2);
            g.fill(x + w - undoW - 4, cy, x + w, cy + barH, 0xFFF1DCAE);
            ui.button(g, undo, x + w - undoW - 2, cy + (barH - ToolsUi.BUTTON) / 2, true, () -> screen.undoSpawner(patch.template(), patch.pos()),
                    Component.translatable("screen.justenoughstructures.spawner.undo_hint"));
            cy += Math.max(barH, ToolsUi.BUTTON) + 3;
        } else if (ref.structure() != null && waitingUndo(ref)) {
            cy += ui.status(g, Component.translatable("screen.justenoughstructures.tools.spawner_undone_next"), x, cy, w, 2) + 3;
        }
        List<Component> notes = new ArrayList<>();
        if (ref.byCode()) {
            notes.add(Component.translatable("screen.justenoughstructures.tools.spawner_code_note"));
        } else {
            if (others > 0) {
                notes.add(Component.translatable("screen.justenoughstructures.tools.spawner_mix_note"));
            }
            if (trial) {
                notes.add(Component.translatable("screen.justenoughstructures.tools.trial_note"));
            }
        }
        for (Component note : notes) {
            cy = Gui.fineWrapped(g, font, note, x, cy, w, Gui.LABEL_SOFT) + 3;
        }
        return cy;
    }

    /** "Changed from Zombie", or from a spawner or trial spawner making it when it's been made the other kind. */
    private static String changedFromKey(SpawnerPatches.Patch patch, boolean waiting) {
        if (patch.to() == null) {
            return waiting ? "screen.justenoughstructures.tools.changed_from_next" : "screen.justenoughstructures.container.changed_from";
        }
        String was = TrialSpawners.isBlock(patch.block()) ? "was_trial" : "was_spawner";
        return waiting ? "screen.justenoughstructures.tools." + was + "_next" : "screen.justenoughstructures.spawner." + was;
    }

    /** A spawner clicked in the browser whose change was undone, waiting for the /reload that puts it back. */
    private boolean waitingUndo(SpawnerRef ref) {
        return ref.template() != null && screen.waiting(PackToolsState.spawnerKey(ref.template(), ref.templatePos()));
    }
}
