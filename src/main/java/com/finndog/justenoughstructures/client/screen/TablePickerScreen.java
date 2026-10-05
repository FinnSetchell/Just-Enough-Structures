package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.FoundIn;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Picks the loot table for one container in a structure: any table the structures use, one typed
 * by id, or a new one made and saved in the editor first. The change is saved on the server as a
 * patch to the container's template and applies from the next /reload.
 */
public final class TablePickerScreen extends BackdropScreen implements Nav.Page {
    private static final int PAD = 6;
    private static final int TOP = NavBar.TOP;
    private static final int ROW = 24;
    /** The widest the list gets. */
    private static final int MOST_WIDTH = 640;

    private final Screen parent;
    private final Used onUsed;
    private final StructureSnapshot.Source source;
    private final String current;
    private final Component containerName;
    private final NavBar navBar = new NavBar(this);

    private EditBox search;
    private Button use;
    private Button useReload;
    private List<ResourceLocation> shown = List.of();
    private ResourceLocation typed;
    private ResourceLocation picked;
    /** Why the server turned the last pick down, shown until something else is picked. */
    private Component problem;
    private double scroll;
    private boolean indexed;

    /**
     * What happens once a table is picked and saved: told what to say, and whether it waits for a
     * /reload, it gives the screen to go to, or null for the one the picker was opened from.
     */
    interface Used {
        Screen used(Component message, boolean untilReload);
    }

    TablePickerScreen(Screen parent, StructureSnapshot.Source source, String current, Component containerName, Used onUsed) {
        super(Component.translatable("screen.justenoughstructures.picker.title", containerName));
        this.parent = parent;
        this.onUsed = onUsed;
        this.source = source;
        this.current = current;
        this.containerName = containerName;
        ClientRequests.index();
    }

    /** Where the picker is, for Back and Forward: which container it's picking for. */
    private record PickerLayer(StructureSnapshot.Source source, String current, Component containerName) implements Nav.Layer {
        @Override
        public Object key() {
            return List.of("picker", source.template(), source.pos());
        }

        @Override
        public Component label() {
            return Component.translatable("screen.justenoughstructures.nav.picker");
        }

        @Override
        public Screen open(Screen below) {
            return new TablePickerScreen(below, source, current, containerName, saysSo(below));
        }

        @Override
        public boolean sameScreen(Nav.Layer other) {
            return key().equals(other.key());
        }
    }

    @Override
    public Nav.Layer layer() {
        return new PickerLayer(source, current, containerName);
    }

    @Override
    public Screen below() {
        return parent;
    }

    /** Says it's done where the picker goes back to: the browser's top line, or Pack tools' title row. */
    static Used saysSo(Screen parent) {
        return (message, untilReload) -> {
            if (parent instanceof JesScreen browser) {
                browser.showMessage(message, true, untilReload);
            } else if (parent instanceof PackToolsScreen tools) {
                tools.say(message, true);
                tools.refresh();
            }
            return null;
        };
    }

    /** A table just saved in the editor, typed in here, which picks it. */
    void useSaved(ResourceLocation id) {
        search.setValue(id.toString());
    }

    @Override
    protected void init() {
        int left = left();
        int right = right();
        String text = search == null ? "" : search.getValue();
        search = addRenderableWidget(new EditBox(font, left + 1, TOP + 34, right - left - 2, 16, Component.translatable("screen.justenoughstructures.picker.search")));
        search.setMaxLength(256);
        search.setHint(Component.translatable("screen.justenoughstructures.picker.search"));
        search.setValue(text);
        search.setResponder(value -> refilter());
        setInitialFocus(search);

        int y = height - PAD - 26;
        int newWidth = font.width(Component.translatable("screen.justenoughstructures.picker.new")) + 12;
        addRenderableWidget(Button.builder(Component.translatable("screen.justenoughstructures.picker.new"), b -> newTable())
                .bounds(left, y, newWidth, 20).tooltip(Tooltip.create(Component.translatable("screen.justenoughstructures.picker.new_hint"))).build());
        int backWidth = font.width(Component.translatable("screen.justenoughstructures.editor.back")) + 12;
        addRenderableWidget(Button.builder(Component.translatable("screen.justenoughstructures.editor.back"), b -> onClose())
                .bounds(right - backWidth, y, backWidth, 20).build());
        int x = right - backWidth;
        // Like the editor's Save & reload, for players who can run /reload: the change applies straight away.
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
                .tooltip(Tooltip.create(Component.translatable("screen.justenoughstructures.picker.use_hint"))).build());
        refilter();
    }

    private void refilter() {
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        List<ResourceLocation> all = new ArrayList<>(FoundIn.allTables());
        all.sort(Comparator.comparing((ResourceLocation id) -> StructureNames.lootTable(id.toString())).thenComparing(ResourceLocation::toString));
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation id : all) {
            if (query.isEmpty() || id.toString().contains(query) || StructureNames.lootTable(id.toString()).toLowerCase(Locale.ROOT).contains(query)) {
                out.add(id);
            }
        }
        // An id typed in full, like a table just made in the editor, can be picked even if no structure
        // uses it yet. Typing one in full is taken as picking it.
        ResourceLocation full = query.contains(":") ? ResourceLocation.tryParse(query) : null;
        boolean pickedTyped = picked != null && picked.equals(typed);
        typed = full != null && !all.contains(full) ? full : null;
        if (typed != null) {
            out.add(0, typed);
        }
        shown = out;
        scroll = 0;
        if (full != null && (picked == null || pickedTyped)) {
            picked = full;
        }
        if (picked != null && !shown.contains(picked)) {
            picked = null;
        }
        problem = null;
        updateUse();
    }

    private void updateUse() {
        boolean active = picked != null && !picked.toString().equals(current);
        if (use != null) {
            use.active = active;
        }
        if (useReload != null) {
            useReload.active = active;
        }
    }

    /**
     * Asks the server to point the container at the picked table. If it can't, why stays here to be
     * fixed; if it can, the browser says so until the change applies.
     */
    private void apply(boolean reload) {
        if (picked == null) {
            return;
        }
        ResourceLocation table = picked;
        ClientRequests.containerAction(source.template(), source.pos(), table).thenAccept(reply -> {
            if (!JesScreen.replyIs(reply.message(), "container.saved")) {
                problem = reply.message();
                return;
            }
            String name = StructureNames.lootTable(table.toString());
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

    /** Opens the editor on a new table, named as typed or after the container's template. Save it, then pick it here. */
    private void newTable() {
        ResourceLocation id = typed;
        if (id == null) {
            String path = source.template().getPath();
            id = Ids.of("justenoughstructures", "chests/" + path.substring(path.lastIndexOf('/') + 1));
        }
        search.setValue(id.toString());
        Nav.remember();
        // Saved under whatever id it ends up with, it's typed in here, which picks it.
        minecraft.setScreen(new LootEditorScreen(this, id, StructureNames.lootTable(id.toString()), this::useSaved));
    }

    @Override
    public void tick() {
        super.tick();
        //? if <1.21 {
        search.tick();
        //?}
        if (!indexed && FoundIn.ready()) {
            indexed = true;
            refilter();
        }
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

    private int listBottom() {
        return height - PAD - 30;
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
        String from = Component.translatable("screen.justenoughstructures.picker.from", source.template().toString()).getString();
        Gui.fineClipped(g, font, from, left + 2, TOP + 21, w, Gui.LABEL_SOFT);

        Gui.inset(g, left, listTop(), w, listBottom() - listTop(), Gui.PANEL);
        if (!FoundIn.ready() && shown.isEmpty()) {
            Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.indexing"), left + 4, listTop() + 4, w - 8, Gui.LABEL_SOFT);
        } else if (shown.isEmpty()) {
            Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.picker.none"), left + 4, listTop() + 4, w - 8, Gui.LABEL_SOFT);
        }
        Gui.scissor(g, left + 1, listTop() + 1, right - 1, listBottom() - 1);
        int y = listTop() + 2 - (int) scroll;
        // Narrower rows when the list scrolls, leaving room for its bar.
        int rowRight = shown.size() * ROW > listBottom() - listTop() - 4 ? right - 8 : right;
        for (ResourceLocation id : shown) {
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
                String name = id.equals(typed) ? Component.translatable("screen.justenoughstructures.picker.typed", id.toString()).getString()
                        : StructureNames.lootTable(id.toString());
                String now = id.toString().equals(current) ? Component.translatable("screen.justenoughstructures.picker.now").getString() : "";
                int nowWidth = now.isEmpty() ? 0 : Gui.fineWidth(font, now) + 4;
                Gui.fitted(g, font, name, left + 6, y + 3, rowRight - left - 14 - nowWidth, Gui.LABEL);
                Gui.fineClipped(g, font, id.toString(), left + 6, y + 13, rowRight - left - 14 - nowWidth, Gui.LABEL_SOFT);
                if (!now.isEmpty()) {
                    Gui.fine(g, font, now, rowRight - 6 - nowWidth + 4, y + 8, 0xFF2E7D1F);
                }
            }
            y += ROW;
        }
        Gui.endScissor(g);
        Gui.scrollbar(g, right - 4, listTop(), listBottom() - listTop(), scroll, Math.max(0, shown.size() * ROW - (listBottom() - listTop() - 4)));
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
        g.pose().pushPose();
        g.pose().translate(0, 0, 600);
        Gui.clippedTooltip(g, font, mouseX, mouseY);
        g.pose().popPose();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return navBar.keyPressed(key, modifiers) || super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Whether the list of tables is here. For the screenshot harness. */
    public boolean ready() {
        return indexed;
    }

    /** Picks a row of the list. For the screenshot harness. */
    public void pick(int row) {
        if (row >= 0 && row < shown.size()) {
            picked = shown.get(row);
            updateUse();
        }
    }

    /** Presses Use it. For the screenshot harness. */
    public void useIt() {
        apply(false);
    }
}
