package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.Regs;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.overrides.SpawnerPatches;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Picks the mob for one spawner in a structure, or none: any mob the game has, modded ones too.
 * Like the loot table picker, the change is saved on the server as a patch to the spawner's
 * template and applies from the next /reload.
 */
public final class MobPickerScreen extends BackdropScreen implements Nav.Page {
    private static final int PAD = 6;
    private static final int TOP = NavBar.TOP;
    private static final int ROW = 24;
    /** The widest the list gets. */
    private static final int MOST_WIDTH = 640;
    /** The row for no mob at all. */
    private static final String NONE = "";

    private final Screen parent;
    private final TablePickerScreen.Used onUsed;
    private final ResourceLocation template;
    private final BlockPos pos;
    private final String current;
    private final NavBar navBar = new NavBar(this);

    private EditBox search;
    private Button use;
    private Button useReload;
    private List<String> shown = List.of();
    private String picked;
    /** Why the server turned the last pick down, shown until something else is picked. */
    private Component problem;
    private double scroll;

    MobPickerScreen(Screen parent, ResourceLocation template, BlockPos pos, String current, TablePickerScreen.Used onUsed) {
        super(Component.translatable("screen.justenoughstructures.mob_picker.title"));
        this.parent = parent;
        this.onUsed = onUsed;
        this.template = template;
        this.pos = pos;
        this.current = current;
    }

    /** Where the picker is, for Back and Forward: which spawner it's picking for. */
    private record PickerLayer(ResourceLocation template, BlockPos pos, String current) implements Nav.Layer {
        @Override
        public Object key() {
            return List.of("mob_picker", template, pos);
        }

        @Override
        public Component label() {
            return Component.translatable("screen.justenoughstructures.nav.mob_picker");
        }

        @Override
        public Screen open(Screen below) {
            return new MobPickerScreen(below, template, pos, current, TablePickerScreen.saysSo(below));
        }

        @Override
        public boolean sameScreen(Nav.Layer other) {
            return key().equals(other.key());
        }
    }

    @Override
    public Nav.Layer layer() {
        return new PickerLayer(template, pos, current);
    }

    @Override
    public Screen below() {
        return parent;
    }

    @Override
    protected void init() {
        int left = left();
        int right = right();
        String text = search == null ? "" : search.getValue();
        search = addRenderableWidget(new EditBox(font, left + 1, TOP + 34, right - left - 2, 16, Component.translatable("screen.justenoughstructures.mob_picker.search")));
        search.setMaxLength(256);
        search.setHint(Component.translatable("screen.justenoughstructures.mob_picker.search"));
        search.setValue(text);
        search.setResponder(value -> refilter());
        setInitialFocus(search);

        int y = height - PAD - 26;
        int backWidth = font.width(Component.translatable("screen.justenoughstructures.editor.back")) + 12;
        addRenderableWidget(Button.builder(Component.translatable("screen.justenoughstructures.editor.back"), b -> onClose())
                .bounds(right - backWidth, y, backWidth, 20).build());
        int x = right - backWidth;
        useReload = null;
        if (ClientRequests.canUsePackTools()) {
            int reloadWidth = font.width(Component.translatable("screen.justenoughstructures.picker.use_reload")) + 12;
            x -= reloadWidth + 4;
            useReload = addRenderableWidget(Button.builder(Component.translatable("screen.justenoughstructures.picker.use_reload"), b -> apply(true))
                    .bounds(x, y, reloadWidth, 20).build());
        }
        int useWidth = font.width(Component.translatable("screen.justenoughstructures.picker.use")) + 12;
        use = addRenderableWidget(Button.builder(Component.translatable("screen.justenoughstructures.picker.use"), b -> apply(false))
                .bounds(x - 4 - useWidth, y, useWidth, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.justenoughstructures.mob_picker.use_hint"))).build());
        refilter();
    }

    /** Every mob a spawner can make, by mod and then by name, with no mob at all first. */
    private static List<String> mobs() {
        List<EntityType<?>> types = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (SpawnerPatches.spawnable(type)) {
                types.add(type);
            }
        }
        types.sort(Comparator.comparing((EntityType<?> type) -> !BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("minecraft"))
                .thenComparing(type -> StructureNames.mod(BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace()))
                .thenComparing(type -> type.getDescription().getString()));
        List<String> out = new ArrayList<>();
        out.add(NONE);
        types.forEach(type -> out.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()));
        return out;
    }

    private void refilter() {
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String id : mobs()) {
            String mod = id.isEmpty() ? "" : StructureNames.mod(ResourceLocation.tryParse(id).getNamespace());
            if (query.isEmpty() || id.contains(query) || name(id).toLowerCase(Locale.ROOT).contains(query) || mod.toLowerCase(Locale.ROOT).contains(query)) {
                out.add(id);
            }
        }
        shown = out;
        scroll = 0;
        if (picked != null && !shown.contains(picked)) {
            picked = null;
        }
        problem = null;
        updateUse();
    }

    private static String name(String id) {
        return id.isEmpty() ? Component.translatable("screen.justenoughstructures.mob_picker.nothing").getString() : StructureNames.mob(id).getString();
    }

    private void updateUse() {
        boolean active = picked != null && !picked.equals(current);
        if (use != null) {
            use.active = active;
        }
        if (useReload != null) {
            useReload.active = active;
        }
    }

    /** Asks the server to give the spawner the picked mob. If it can't, why stays here; if it can, it says so where the picker goes back to. */
    private void apply(boolean reload) {
        if (picked == null) {
            return;
        }
        String mob = picked;
        ClientRequests.spawnerAction(template, pos, mob).thenAccept(reply -> {
            if (!JesScreen.replyIs(reply.message(), "spawner.saved")) {
                problem = reply.message();
                return;
            }
            String name = name(mob);
            Screen next;
            if (reload) {
                ClientRequests.reloadServer();
                next = onUsed.used(Component.translatable("screen.justenoughstructures.container.saved_reloading", name), false);
            } else {
                next = onUsed.used(Component.translatable("screen.justenoughstructures.container.saved_named", name), true);
            }
            minecraft.setScreen(next != null ? next : parent);
        });
    }

    @Override
    public void tick() {
        super.tick();
        //? if <1.21 {
        search.tick();
        //?}
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    /** The list's left edge: the panel's, or in from it to keep the list a readable width on a very wide screen. */
    private int left() {
        return PAD + 6 + Math.max(0, (width - PAD * 2 - 12 - MOST_WIDTH) / 2);
    }

    private int right() {
        return width - left();
    }

    private int listTop() {
        return TOP + 56;
    }

    /** Above the buttons, with a line under the list for a note about the mob picked. */
    private int listBottom() {
        return height - PAD - 30 - font.lineHeight - 4;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (navBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int left = left();
        int right = right();
        if (mouseX >= left && mouseX < right && mouseY >= listTop() && mouseY < listBottom()) {
            int row = (int) ((mouseY - listTop() - 2 + scroll) / ROW);
            if (row >= 0 && row < shown.size()) {
                picked = shown.get(row);
                problem = null;
                updateUse();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Math.max(0, Math.min(scroll - delta * ROW, Math.max(0, shown.size() * ROW - (listBottom() - listTop() - 4))));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Gui.beginClipped();
        backdrop(g);
        Gui.panel(g, PAD, TOP, width - PAD * 2, height - TOP - PAD);
        int left = left();
        int right = right();
        int w = right - left;
        Gui.drawClipped(g, font, title.getString(), left + 2, TOP + 8, w, Gui.LABEL, false);
        String from = Component.translatable("screen.justenoughstructures.picker.from", template.toString()).getString();
        Gui.fineClipped(g, font, from, left + 2, TOP + 21, w, Gui.LABEL_SOFT);

        Gui.inset(g, left, listTop(), w, listBottom() - listTop(), Gui.PANEL);
        if (shown.isEmpty()) {
            Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.mob_picker.none"), left + 4, listTop() + 4, w - 8, Gui.LABEL_SOFT);
        }
        Gui.scissor(g, left + 1, listTop() + 1, right - 1, listBottom() - 1);
        int y = listTop() + 2 - (int) scroll;
        // Narrower rows when the list scrolls, leaving room for its bar.
        int rowRight = shown.size() * ROW > listBottom() - listTop() - 4 ? right - 8 : right;
        for (String id : shown) {
            if (y > listBottom()) {
                break;
            }
            if (y + ROW >= listTop()) {
                boolean selected = id.equals(picked);
                boolean over = mouseX >= left + 2 && mouseX < rowRight - 2 && mouseY >= y && mouseY < y + ROW - 1 && mouseY < listBottom();
                Gui.card(g, left + 2, y, rowRight - left - 4, ROW - 1);
                if (selected || over) {
                    g.fill(left + 3, y + 1, rowRight - 3, y + ROW - 2, selected ? Gui.ROW_SELECTED : Gui.ROW_HOVER);
                }
                g.renderItem(ToolsSpawners.mobIcon(id), left + 5, y + 3);
                String now = id.equals(current) ? Component.translatable("screen.justenoughstructures.picker.now").getString() : "";
                int nowWidth = now.isEmpty() ? 0 : Gui.fineWidth(font, now) + 4;
                Gui.fitted(g, font, name(id), left + 25, y + 3, rowRight - left - 33 - nowWidth, Gui.LABEL);
                String detail = id.isEmpty() ? Component.translatable("screen.justenoughstructures.hover_spawns_nothing").getString()
                        : id + " · " + StructureNames.mod(ResourceLocation.tryParse(id).getNamespace());
                Gui.fineClipped(g, font, detail, left + 25, y + 13, rowRight - left - 33 - nowWidth, Gui.LABEL_SOFT);
                if (!now.isEmpty()) {
                    Gui.fine(g, font, now, rowRight - 6 - nowWidth + 4, y + 8, 0xFF2E7D1F);
                }
            }
            y += ROW;
        }
        Gui.endScissor(g);
        Gui.scrollbar(g, right - 4, listTop(), listBottom() - listTop(), scroll, Math.max(0, shown.size() * ROW - (listBottom() - listTop() - 4)));
        Component note = note();
        if (note != null) {
            Gui.drawClipped(g, font, note.getString(), left + 2, listBottom() + 3, w, Gui.LABEL_SOFT, false);
        }
        if (problem != null) {
            // Over the top of the list, where it's seen, until something else is picked.
            List<FormattedCharSequence> lines = font.split(problem, w - 12);
            int boxH = Math.min(3, lines.size()) * (font.lineHeight + 1) + 6;
            g.fill(left + 2, listTop() + 2, right - 2, listTop() + 2 + boxH, 0xF0FFE4E4);
            for (int i = 0; i < Math.min(3, lines.size()); i++) {
                g.drawString(font, lines.get(i), left + 6, listTop() + 5 + i * (font.lineHeight + 1), 0xFFB02020, false);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
        navBar.render(g, font, mouseX, mouseY, partialTick);
        Gui.push(g);
        Gui.lift(g, 600);
        Gui.clippedTooltip(g, font, mouseX, mouseY);
        Gui.pop(g);
    }

    /** A word about the mob picked when it isn't a monster: spawners still check where it would normally spawn. */
    private Component note() {
        ResourceLocation id = picked == null || picked.isEmpty() ? null : ResourceLocation.tryParse(picked);
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return null;
        }
        EntityType<?> type = Regs.value(BuiltInRegistries.ENTITY_TYPE, id);
        return type.getCategory() == MobCategory.MONSTER ? null
                : Component.translatable("screen.justenoughstructures.mob_picker.not_monster", type.getDescription());
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return navBar.keyPressed(key, modifiers) || super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
