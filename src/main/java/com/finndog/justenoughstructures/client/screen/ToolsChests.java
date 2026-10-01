package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.overrides.ContainerPatches;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.finndog.justenoughstructures.server.PackToolsState;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Containers: picking one in the browser to change, those changed so far, and for the one picked,
 * where it is, its loot table and what it's changed from, and one possible roll of it.
 */
final class ToolsChests extends ToolsSection {
    private static final int SLOT = 18;

    /**
     * A container Pack tools shows: one opened from the browser, with the structure and layout it's
     * in, or one changed before, known only by its template.
     *
     * @param structure   the structure it was opened in, or null
     * @param pos         where it is in that layout, or null
     * @param block       what it is, a block or an entity id
     * @param template    the template it's in, or null when the structure's code placed it
     * @param templatePos where it is in that template, or null
     * @param table       the loot table it has, or null for one saved with its items
     */
    record ChestRef(ResourceLocation structure, long seed, BlockPos pos, boolean entity, String block, ResourceLocation template,
                    BlockPos templatePos, String table, Component title, int size) {
        static ChestRef of(ContainerPatches.Patch patch) {
            Block block = BuiltInRegistries.BLOCK.get(patch.block());
            return new ChestRef(null, 0, null, false, patch.block().toString(), patch.template(), patch.pos(), patch.table().toString(),
                    block.getName(), sizeOf(block));
        }

        static ChestRef of(ResourceLocation structure, long seed, StructureSnapshot.Container c, Component title, int size) {
            StructureSnapshot.Source source = c.source();
            return new ChestRef(structure, seed, c.pos(), c.entity(), c.id(), source == null ? null : source.template(),
                    source == null ? null : source.pos(), c.lootTable(), title, size);
        }

        /** Only containers in a template can be pointed at another table; those a structure's code places can't. */
        boolean byCode() {
            return template == null;
        }

        boolean same(ContainerPatches.Patch patch) {
            return template != null && template.equals(patch.template()) && templatePos.equals(patch.pos());
        }
    }

    /** How many slots a container block has, or 27 if it can't be told. */
    static int sizeOf(Block block) {
        try {
            if (block instanceof EntityBlock entityBlock) {
                BlockEntity be = entityBlock.newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
                if (be instanceof Container container) {
                    return container.getContainerSize();
                }
                return 1;
            }
        } catch (RuntimeException e) {
            // Some block entities want a level to be made; a chest's size is the best guess then.
        }
        return 27;
    }

    private final Scroller list = new Scroller();
    private final Scroller detail = new Scroller();
    private ChestRef selected;
    private List<ItemStack> roll;
    private String rollFor;
    private long seed = ThreadLocalRandom.current().nextLong();
    private ItemStack hovered = ItemStack.EMPTY;

    ToolsChests(PackToolsScreen screen) {
        super(screen);
    }

    @Override
    String count() {
        PackToolsState state = screen.state();
        return state == null || state.patches().isEmpty() ? "" : String.valueOf(state.patches().size());
    }

    @Override
    Object selection() {
        return selected;
    }

    @Override
    void select(Object selection) {
        selected = selection instanceof ChestRef ref ? ref : null;
        detail.reset();
        rollFor = null;
    }

    @Override
    Object parse(String text) {
        PackToolsState state = screen.state();
        if (state == null) {
            return null;
        }
        for (ContainerPatches.Patch patch : state.patches()) {
            if (patch.template().toString().equals(text)) {
                return ChestRef.of(patch);
            }
        }
        return null;
    }

    @Override
    boolean scroll(double mouseX, double mouseY, double delta) {
        return list.scroll(mouseX, mouseY, delta) || detail.scroll(mouseX, mouseY, delta);
    }

    /** The patch for the picked container, if it has one. */
    private ContainerPatches.Patch patchOf(ChestRef ref) {
        if (ref == null || screen.state() == null) {
            return null;
        }
        for (ContainerPatches.Patch patch : screen.state().patches()) {
            if (ref.same(patch)) {
                return patch;
            }
        }
        return null;
    }

    @Override
    void render(GuiGraphics g, ToolsUi ui, int x, int y, int w, int h, int mouseX, int mouseY) {
        int leftW = Math.max(130, Math.min(220, w * 38 / 100));
        ui.button(g, Component.translatable("screen.justenoughstructures.tools.pick_chest"), x, y, leftW, 18, screen.browser() != null, screen::pickChest,
                Component.translatable("screen.justenoughstructures.tools.pick_chest_hint"));
        int listTop = y + 22;
        Gui.inset(g, x, listTop, leftW, y + h - listTop, Gui.PANEL);
        int top = list.begin(g, ui, x + 1, listTop + 1, leftW - 2, y + h - listTop - 2);
        int rw = list.width();
        int cy = top + 2;
        PackToolsState state = screen.state();
        if (selected != null && selected.structure() != null && patchOf(selected) == null) {
            Gui.fine(g, font, Component.translatable("screen.justenoughstructures.tools.from_browser").getString(), x + 4, cy, Gui.LABEL_SOFT);
            cy += Gui.fineLine(font) + 2;
            String name = Component.translatable("screen.justenoughstructures.tools.chest_name", StructureNames.structure(selected.structure()),
                    selected.title()).getString();
            cy += row(g, ui, x + 1, cy, rw, Icon.item(icon(selected.block())), name, null, 0,
                    selected.table() == null ? Component.translatable("screen.justenoughstructures.popup_saved_items").getString()
                            : StructureNames.lootTable(selected.table()), List.of(), null, 0, true) + 4;
        }
        Gui.fine(g, font, Component.translatable("screen.justenoughstructures.tools.changed_chests").getString(), x + 4, cy, Gui.LABEL_SOFT);
        cy += Gui.fineLine(font) + 2;
        if (state.patches().isEmpty()) {
            Gui.fine(g, font, Component.translatable("screen.justenoughstructures.tools.none_yet").getString(), x + 4, cy, Gui.LABEL_SOFT);
            cy += Gui.fineLine(font) + 4;
        }
        for (ContainerPatches.Patch patch : state.patches()) {
            ChestRef ref = ChestRef.of(patch);
            boolean isSelected = selected != null && selected.same(patch);
            String name = Component.translatable("screen.justenoughstructures.tools.chest_name", templateName(patch.template()),
                    BuiltInRegistries.BLOCK.get(patch.block()).getName()).getString();
            String detailText = Component.translatable("screen.justenoughstructures.tools.changed", ToolsOverview.tableName(patch.original()),
                    StructureNames.lootTable(patch.table().toString())).getString();
            if (screen.waiting(PackToolsState.chestKey(patch.template(), patch.pos()))) {
                detailText += " " + Component.translatable("screen.justenoughstructures.tools.after_reload_brackets").getString();
            }
            cy += row(g, ui, x + 1, cy, rw, Icon.item(icon(patch.block().toString())), name, null, 0, detailText, List.of(),
                    () -> screen.pick(ref), 0, isSelected);
        }
        list.end(g, ui, cy - top + 2);

        int dx = x + leftW + 6;
        int dw = w - leftW - 6;
        hovered = ItemStack.EMPTY;
        if (selected == null) {
            int ty = Gui.fineWrapped(g, font, Component.translatable("screen.justenoughstructures.tools.chests_intro"), dx, y + 4, dw, Gui.LABEL_SOFT);
            Gui.fineWrapped(g, font, Component.translatable("screen.justenoughstructures.tools.chests_intro_more"), dx, ty + 4, dw, Gui.LABEL_SOFT);
            return;
        }
        int dTop = detail.begin(g, ui, dx, y, dw, h);
        int end = detail(g, ui, dx, dTop, detail.width(), selected);
        detail.end(g, ui, end - dTop);
    }

    private static ItemStack icon(String block) {
        ResourceLocation id = ResourceLocation.tryParse(block);
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            return new ItemStack(BuiltInRegistries.ITEM.get(id));
        }
        return CHEST;
    }

    /** The picked container. Returns the y below it. */
    private int detail(GuiGraphics g, ToolsUi ui, int x, int y, int w, ChestRef ref) {
        ContainerPatches.Patch patch = patchOf(ref);
        String table = patch != null ? patch.table().toString() : ref.table();
        String name = ref.template() == null ? ref.title().getString()
                : Component.translatable("screen.justenoughstructures.tools.chest_in", ref.title(), templateName(ref.template())).getString();
        String where = ref.structure() == null ? ref.template().toString()
                : StructureNames.structure(ref.structure()) + " · " + StructureNames.mod(ref.structure().getNamespace());
        List<RowButton> show = ref.structure() == null ? List.of()
                : List.of(new RowButton(Component.translatable("screen.justenoughstructures.tools.show_in_browser"), screen.browser() == null ? null
                : () -> screen.showContainerInBrowser(ref), Component.translatable("screen.justenoughstructures.tools.show_in_browser_hint")));
        int cy = y + row(g, ui, x, y, w, Icon.item(icon(ref.block())), name, null, 0, where, show, null, 0, false);
        Component from = ref.byCode() ? Component.translatable("screen.justenoughstructures.tools.by_code")
                : Component.translatable("screen.justenoughstructures.tools.from_template", ref.template().toString(), ref.templatePos().toShortString());
        cy = Gui.fineWrapped(g, font, from, x, cy + 2, w, Gui.LABEL_SOFT) + 2;

        cy = ui.heading(g, Component.translatable("screen.justenoughstructures.tools.loot_table"), x, cy + 2, w);
        LootOverrides.Status status = table == null ? null : screen.state().overrides().get(ResourceLocation.tryParse(table));
        List<RowButton> buttons = new ArrayList<>();
        boolean canChange = !ref.byCode() && table != null;
        Component why = ref.byCode() ? Component.translatable("screen.justenoughstructures.tools.cant_change_code")
                : table == null ? Component.translatable("screen.justenoughstructures.tools.cant_change_items")
                : Component.translatable("screen.justenoughstructures.container.change_hint");
        buttons.add(new RowButton(Component.translatable("screen.justenoughstructures.container.change"), canChange ? () -> screen.changeChest(ref, table) : null, why));
        if (table != null && ResourceLocation.tryParse(table) != null) {
            ResourceLocation id = ResourceLocation.tryParse(table);
            buttons.add(RowButton.of("container.edit_table", () -> screen.openEditor(id, false)));
            buttons.add(new RowButton(Component.translatable("screen.justenoughstructures.tools.more"), () -> screen.go(PackToolsScreen.Section.LOOT, id),
                    Component.translatable("screen.justenoughstructures.tools.more_hint")));
        }
        cy += row(g, ui, x, cy, w, Icon.item(CHEST), table == null ? Component.translatable("screen.justenoughstructures.tools.no_table").getString()
                        : StructureNames.lootTable(table), editedMark(status), status == null ? 0 : markColour(status),
                table == null ? Component.translatable("screen.justenoughstructures.popup_saved_items").getString() : table, buttons, null, 0, false) + 2;

        if (patch != null) {
            boolean waiting = screen.waiting(PackToolsState.chestKey(patch.template(), patch.pos()));
            Component changed = Component.translatable(waiting ? "screen.justenoughstructures.tools.changed_from_next" : "screen.justenoughstructures.container.changed_from",
                    ToolsOverview.tableName(patch.original()));
            Component undo = Component.translatable("screen.justenoughstructures.container.undo");
            int undoW = ui.buttonWidth(undo);
            int barH = ui.status(g, changed, x, cy, w - undoW - 4, 2);
            g.fill(x + w - undoW - 4, cy, x + w, cy + barH, 0xFFF1DCAE);
            ui.button(g, undo, x + w - undoW - 2, cy + (barH - ToolsUi.BUTTON) / 2, true, () -> screen.undoChest(patch.template(), patch.pos()),
                    Component.translatable("screen.justenoughstructures.container.undo_hint"));
            cy += Math.max(barH, ToolsUi.BUTTON) + 3;
        } else if (ref.structure() != null && waitingUndo(ref)) {
            cy += ui.status(g, Component.translatable("screen.justenoughstructures.tools.undone_next"), x, cy, w, 2) + 3;
        }
        if (ref.byCode()) {
            cy = Gui.fineWrapped(g, font, Component.translatable("screen.justenoughstructures.tools.code_note"), x, cy, w, Gui.LABEL_SOFT) + 3;
        }

        cy = ui.heading(g, Component.translatable("screen.justenoughstructures.tools.one_roll"), x, cy + 2, w);
        String key = table + "|" + seed + "|" + ref.size();
        if (table != null && !key.equals(rollFor)) {
            rollFor = key;
            roll = null;
            ResourceLocation id = ResourceLocation.tryParse(table);
            if (id != null) {
                String wanted = key;
                ClientRequests.loot(id, seed, Math.max(1, ref.size())).thenAccept(items -> {
                    if (wanted.equals(rollFor)) {
                        roll = items;
                    }
                });
            }
        }
        int columns = Math.min(9, Math.max(1, ref.size()));
        int rows = Math.max(1, (ref.size() + 8) / 9);
        int gridW = columns * SLOT + 8;
        int gx = x + Math.max(0, (w - gridW) / 2);
        Gui.panel(g, gx - 2, cy, gridW + 4, rows * SLOT + 34);
        int sy = cy + 6;
        for (int i = 0; i < ref.size(); i++) {
            int sx = gx + 4 + (i % 9) * SLOT;
            int slotY = sy + (i / 9) * SLOT;
            Gui.slot(g, sx, slotY);
            ItemStack stack = roll != null && i < roll.size() ? roll.get(i) : ItemStack.EMPTY;
            if (!stack.isEmpty()) {
                g.renderItem(stack, sx + 1, slotY + 1);
                g.renderItemDecorations(font, stack, sx + 1, slotY + 1);
                if (ui.hovered(sx, slotY, SLOT, SLOT)) {
                    hovered = stack;
                }
            }
        }
        if (table != null && roll == null) {
            Gui.fine(g, font, Component.translatable("screen.justenoughstructures.rolling").getString(), gx + 6, sy + 4, Gui.LABEL_SOFT);
        }
        ui.button(g, Component.translatable("screen.justenoughstructures.reroll_loot"), gx + 2, sy + rows * SLOT + 4, gridW - 4, 20, table != null,
                () -> seed = ThreadLocalRandom.current().nextLong());
        return cy + rows * SLOT + 40;
    }

    /** A container opened from the browser whose change was undone, waiting for the /reload that puts it back. */
    private boolean waitingUndo(ChestRef ref) {
        return ref.template() != null && screen.waiting(PackToolsState.chestKey(ref.template(), ref.templatePos()));
    }

    @Override
    void renderOver(GuiGraphics g, ToolsUi ui, int mouseX, int mouseY) {
        if (!hovered.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 600);
            g.renderTooltip(font, hovered, mouseX, mouseY);
            g.pose().popPose();
        }
    }
}
