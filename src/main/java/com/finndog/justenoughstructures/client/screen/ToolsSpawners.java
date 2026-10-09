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
final class ToolsSpawners extends ToolsPatches<SpawnerPatches.Patch, ToolsSpawners.SpawnerRef> {
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
                      ResourceLocation template, BlockPos templatePos, String patchedFrom) implements Ref<SpawnerPatches.Patch> {
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

        @Override
        public boolean same(SpawnerPatches.Patch patch) {
            return template != null && template.equals(patch.template()) && templatePos.equals(patch.pos());
        }
    }

    ToolsSpawners(PackToolsScreen screen) {
        super(screen, SpawnerRef.class);
    }

    @Override
    List<SpawnerPatches.Patch> patches(PackToolsState state) {
        return state.spawners();
    }

    @Override
    SpawnerRef refOf(SpawnerPatches.Patch patch) {
        return SpawnerRef.of(patch);
    }

    @Override
    ResourceLocation templateOf(SpawnerPatches.Patch patch) {
        return patch.template();
    }

    @Override
    BlockPos posOf(SpawnerPatches.Patch patch) {
        return patch.pos();
    }

    @Override
    String kind() {
        return "spawner";
    }

    @Override
    void pickInBrowser() {
        screen.pickSpawner();
    }

    @Override
    boolean waiting(ResourceLocation template, BlockPos pos) {
        return screen.waiting(PackToolsState.spawnerKey(template, pos));
    }

    @Override
    Line browserLine(SpawnerRef ref) {
        return new Line(mobIcon(ref.mobNow(null)), blockName(ref.blockNow(null)), mobName(ref.mobNow(null), ref.othersNow(null)).getString());
    }

    @Override
    Line patchLine(SpawnerPatches.Patch patch) {
        return new Line(mobIcon(patch.mob()), blockName(patch.target()), changed(patch));
    }

    @Override
    int intro(GuiGraphics g, int x, int y, int w) {
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

    @Override
    int detail(GuiGraphics g, ToolsUi ui, int x, int y, int w, SpawnerRef ref) {
        SpawnerPatches.Patch patch = patchOf(ref);
        String mob = ref.mobNow(patch);
        int others = ref.othersNow(patch);
        ResourceLocation blockNow = ref.blockNow(patch);
        boolean trial = TrialSpawners.isBlock(blockNow);
        Component block = blockName(blockNow);
        String name = ref.template() == null ? block.getString()
                : Component.translatable("screen.justenoughstructures.tools.chest_in", block, templateName(ref.template())).getString();
        int cy = y + row(g, ui, x, y, w, Icon.item(blockIcon(blockNow)), name, null, 0, where(ref), List.of(), null, 0, false);
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
            Component changed = Component.translatable(changedFromKey(patch, waiting(patch.template(), patch.pos())), mobName(patch.original(), patch.others()));
            cy = undoBar(g, ui, changed, x, cy, w, () -> screen.undoSpawner(patch.template(), patch.pos()));
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
}
