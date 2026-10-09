package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.client.ClientRequests;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * What the loot table and mob pickers share: a list to search and pick from, Use it, and Use it &
 * reload for players who can run /reload. The pick is saved on the server as a patch to a template,
 * and Pack tools, which a picker goes back to, says so. If the server turns it down, why shows over
 * the list until something else is picked.
 */
abstract class PickerScreen<T> extends BackdropScreen implements Nav.Page {
    static final int PAD = 6;
    static final int TOP = NavBar.TOP;
    static final int ROW = 24;
    /** The widest the list gets. */
    private static final int MOST_WIDTH = 640;
    /** The note on the choice in use now. */
    private static final int NOW = 0xFF2E7D1F;

    protected final Screen parent;
    /** Where the picker's own text is in the lang file: "picker" or "mob_picker". */
    private final String keys;
    private final NavBar navBar = new NavBar(this);

    protected EditBox search;
    private Button use;
    private Button useReload;
    protected List<T> shown = List.of();
    protected T picked;
    /** Why the server turned the last pick down, shown until something else is picked. */
    private Component problem;
    private double scroll;

    PickerScreen(Component title, Screen parent, String keys) {
        super(title);
        this.parent = parent;
        this.keys = keys;
    }

    /** The choices that match what's typed, trimmed and in lower case, in the order they're listed. */
    protected abstract List<T> matching(String query);

    /** Whether this is the choice in use now. */
    protected abstract boolean inUseNow(T choice);

    /** The template the choice is for, named under the title. */
    protected abstract ResourceLocation template();

    /** Saves the choice on the server. */
    protected abstract CompletableFuture<ClientRequests.EditReply> save(T choice);

    /** The end of the key the server's reply has when it saved the choice. */
    protected abstract String savedReply();

    /** What the choice is called, to say it was saved. */
    protected abstract String name(T choice);

    /** Draws a row's text, from {@code left} up to {@code right}, with the row's top at {@code y}. */
    protected abstract void drawRow(GuiGraphics g, T choice, int left, int y, int right);

    /** Buttons at the bottom left, beside Use it and Back. */
    protected void addButtons(int left, int y) {
    }

    /** What the list says when nothing matches. */
    protected Component nothingShown() {
        return Component.translatable("screen.justenoughstructures." + keys + ".none");
    }

    /** A line under the list about the choice picked, or null for none. */
    protected Component note() {
        return null;
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
        Component searchName = Component.translatable("screen.justenoughstructures." + keys + ".search");
        search = addRenderableWidget(new EditBox(font, left + 1, TOP + 34, right - left - 2, 16, searchName));
        search.setMaxLength(256);
        search.setHint(searchName);
        search.setValue(text);
        search.setResponder(value -> refilter());
        setInitialFocus(search);

        int y = height - PAD - 26;
        addButtons(left, y);
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
                .tooltip(Tooltip.create(Component.translatable("screen.justenoughstructures." + keys + ".use_hint"))).build());
        refilter();
    }

    protected void refilter() {
        shown = matching(search.getValue().trim().toLowerCase(Locale.ROOT));
        scroll = 0;
        if (picked != null && !shown.contains(picked)) {
            picked = null;
        }
        problem = null;
        updateUse();
    }

    protected void updateUse() {
        boolean active = picked != null && !inUseNow(picked);
        if (use != null) {
            use.active = active;
        }
        if (useReload != null) {
            useReload.active = active;
        }
    }

    /** Asks the server to use the choice picked. If it can't, why stays here to be fixed; if it can, Pack tools says so. */
    protected void apply(boolean reload) {
        if (picked == null) {
            return;
        }
        T choice = picked;
        save(choice).thenAccept(reply -> {
            if (!JesScreen.replyIs(reply.message(), savedReply())) {
                problem = reply.message();
                return;
            }
            if (reload) {
                ClientRequests.reloadServer();
            }
            if (parent instanceof PackToolsScreen tools) {
                tools.say(Component.translatable(reload ? "screen.justenoughstructures.container.saved_reloading"
                        : "screen.justenoughstructures.container.saved_named", name(choice)), true);
                tools.refresh();
            }
            minecraft.setScreen(parent);
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

    protected int listBottom() {
        return height - PAD - 30;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (navBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (mouseX >= left() && mouseX < right() && mouseY >= listTop() && mouseY < listBottom()) {
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
        String from = Component.translatable("screen.justenoughstructures.picker.from", template().toString()).getString();
        Gui.fineClipped(g, font, from, left + 2, TOP + 21, w, Gui.LABEL_SOFT);

        Gui.inset(g, left, listTop(), w, listBottom() - listTop(), Gui.PANEL);
        if (shown.isEmpty()) {
            Gui.wrapped(g, font, nothingShown(), left + 4, listTop() + 4, w - 8, Gui.LABEL_SOFT);
        }
        Gui.scissor(g, left + 1, listTop() + 1, right - 1, listBottom() - 1);
        int y = listTop() + 2 - (int) scroll;
        // Narrower rows when the list scrolls, leaving room for its bar.
        int rowRight = shown.size() * ROW > listBottom() - listTop() - 4 ? right - 8 : right;
        for (T choice : shown) {
            if (y > listBottom()) {
                break;
            }
            if (y + ROW >= listTop()) {
                boolean selected = choice.equals(picked);
                boolean over = mouseX >= left + 2 && mouseX < rowRight - 2 && mouseY >= y && mouseY < y + ROW - 1 && mouseY < listBottom();
                Gui.card(g, left + 2, y, rowRight - left - 4, ROW - 1);
                if (selected || over) {
                    g.fill(left + 3, y + 1, rowRight - 3, y + ROW - 2, selected ? Gui.ROW_SELECTED : Gui.ROW_HOVER);
                }
                String now = inUseNow(choice) ? Component.translatable("screen.justenoughstructures.picker.now").getString() : "";
                int nowWidth = now.isEmpty() ? 0 : Gui.fineWidth(font, now) + 4;
                drawRow(g, choice, left, y, rowRight - nowWidth);
                if (!now.isEmpty()) {
                    Gui.fine(g, font, now, rowRight - 6 - nowWidth + 4, y + 8, NOW);
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

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return navBar.keyPressed(key, modifiers) || super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
