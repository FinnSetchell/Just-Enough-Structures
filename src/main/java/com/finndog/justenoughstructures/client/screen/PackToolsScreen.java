package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.server.PackToolsState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Pack tools: everything a pack maker or server owner changes, in one place. Loot tables, the
 * containers pointed at other tables, which structures players see and what they're told about
 * them, and the server's rules. Opened from the browser, which it goes back to.
 */
public final class PackToolsScreen extends Screen implements Nav.Page {
    private static final int PAD = 6;
    private static final int TOP = NavBar.TOP;
    static final ResourceLocation WRENCH = JustEnoughStructures.id("textures/gui/wrench.png");
    /** How long a message from the server stays in the title row. */
    private static final long MESSAGE_MILLIS = 8000;

    /** The parts of Pack tools, down the left. */
    enum Section {
        OVERVIEW, LOOT, CHESTS, STRUCTURES, RULES;

        Component label() {
            return Component.translatable("screen.justenoughstructures.tools.section." + name().toLowerCase(Locale.ROOT));
        }
    }

    private final Screen parent;
    private final ToolsUi ui;
    private final NavBar navBar = new NavBar(this);
    private final Map<Section, ToolsSection> sections = new EnumMap<>(Section.class);
    private Section section;
    private PackToolsState state;
    private List<StructureCatalog.Entry> catalog = List.of();
    private Component message;
    private boolean messageGood;
    private long messageUntil;
    private int seenReloads = ClientRequests.reloads();
    private int seenStructures = ClientRequests.structureChanges();
    private int contentX, contentY, contentW, contentH;

    PackToolsScreen(Screen parent, Section section, Object selection) {
        super(Component.translatable("screen.justenoughstructures.tools.title"));
        this.parent = parent;
        this.ui = new ToolsUi(net.minecraft.client.Minecraft.getInstance().font);
        sections.put(Section.OVERVIEW, new ToolsOverview(this));
        sections.put(Section.LOOT, new ToolsLoot(this));
        sections.put(Section.CHESTS, new ToolsChests(this));
        sections.put(Section.STRUCTURES, new ToolsStructures(this));
        sections.put(Section.RULES, new ToolsRules(this));
        this.section = section;
        if (selection != null) {
            sections.get(section).select(selection);
        }
        refresh();
        ClientRequests.catalog().thenAccept(entries -> catalog = entries);
        ClientRequests.index();
    }

    // ------------------------------------------------------------------ what it shows

    /** What the server last said, or null until it has. */
    PackToolsState state() {
        return state;
    }

    /** The structures players can see, as the browser lists them. */
    List<StructureCatalog.Entry> catalog() {
        return catalog;
    }

    /** Asks the server again how everything stands, after a change or a /reload. */
    void refresh() {
        ClientRequests.tools().thenAccept(fresh -> {
            boolean first = state == null;
            state = fresh;
            sections.values().forEach(ToolsSection::stateChanged);
            // Some sections only add their text boxes once there's something to show.
            if (first && minecraft != null && minecraft.screen == this) {
                rebuildWidgets();
            }
        });
        ClientRequests.requestOverrides();
    }

    /** Says how something went, in the title row, for a while. */
    void say(Component text, boolean good) {
        message = text;
        messageGood = good;
        messageUntil = Util.getMillis() + MESSAGE_MILLIS;
    }

    /** Shows the server's answer, and asks again how everything stands. */
    void replied(ClientRequests.EditReply reply, String goodKeyEnd) {
        if (reply.message() != null) {
            say(reply.message(), JesScreen.replyIs(reply.message(), goodKeyEnd));
        }
        refresh();
    }

    boolean waiting(String key) {
        return state != null && state.pending().contains(key);
    }

    Section section() {
        return section;
    }

    // ------------------------------------------------------------------ going places

    /** Shows another section, or another thing in one, as a step Back can return from. */
    void go(Section to, Object selection) {
        Nav.remember();
        current().leaving();
        section = to;
        sections.get(to).select(selection);
        rebuildWidgets();
    }

    /** Picks something in the section on show, as a step. */
    void pick(Object selection) {
        if (java.util.Objects.equals(selection, current().selection())) {
            return;
        }
        Nav.remember();
        current().leaving();
        current().select(selection);
        rebuildWidgets();
    }

    void toBrowser() {
        Nav.remember();
        current().leaving();
        minecraft.setScreen(parent);
    }

    /** The browser this was opened from, if it was. */
    JesScreen browser() {
        return parent instanceof JesScreen browser ? browser : null;
    }

    /** Opens a structure in the browser, on a tab and with a table picked if given. */
    void showInBrowser(ResourceLocation structure, String table) {
        JesScreen browser = browser();
        if (browser == null) {
            return;
        }
        Nav.remember();
        current().leaving();
        browser.showFromTools(structure, table == null ? InfoPanel.Tab.OVERVIEW : InfoPanel.Tab.LOOT, table);
        minecraft.setScreen(browser);
    }

    /** Opens the layout a container was picked in, with its popup open. */
    void showContainerInBrowser(ToolsChests.ChestRef ref) {
        JesScreen browser = browser();
        if (browser == null || ref.structure() == null) {
            return;
        }
        Nav.remember();
        current().leaving();
        browser.showContainerFromTools(ref);
        minecraft.setScreen(browser);
    }

    /** Back to the browser to click the chest to change. */
    void pickChest() {
        JesScreen browser = browser();
        if (browser == null) {
            return;
        }
        Nav.remember();
        current().leaving();
        browser.startPicking();
        minecraft.setScreen(browser);
    }

    /** The table picker for a container, which comes back here once one's picked. */
    void changeChest(ToolsChests.ChestRef ref, String table) {
        ResourceLocation block = ResourceLocation.tryParse(ref.block());
        if (ref.byCode() || block == null) {
            return;
        }
        Nav.remember();
        current().leaving();
        StructureSnapshot.Source source = new StructureSnapshot.Source(ref.template(), ref.templatePos(), block, null);
        minecraft.setScreen(new TablePickerScreen(this, source, table, ref.title(), TablePickerScreen.saysSo(this)));
    }

    void openEditor(ResourceLocation table, boolean merge) {
        Nav.remember();
        current().leaving();
        LootEditorScreen editor = new LootEditorScreen(this, table, StructureNames.lootTable(table.toString()));
        if (merge) {
            editor.mergeWhenLoaded();
        }
        minecraft.setScreen(editor);
    }

    /** The editor on a new table, which can be named before it's saved. */
    void newTable() {
        ResourceLocation id = new ResourceLocation(JustEnoughStructures.MOD_ID, "chests/new_table");
        openEditor(id, false);
    }

    /** The mod's table and the edit side by side. */
    void showChanges(ResourceLocation table) {
        ClientRequests.table(table).thenAccept(reply -> {
            if (reply.view() == null || minecraft.screen != this) {
                if (reply.problem() != null) {
                    say(reply.problem(), false);
                }
                return;
            }
            Nav.remember();
            current().leaving();
            minecraft.setScreen(new DiffScreen(this, StructureNames.lootTable(table.toString()), reply.view().original(), reply.view().current()));
        });
    }

    void keep(ResourceLocation table) {
        ClientRequests.tableAction(table, JesNetwork.ACTION_KEEP).thenAccept(reply -> replied(reply, "override.kept"));
    }

    void removeEdit(ResourceLocation table) {
        ClientRequests.tableAction(table, JesNetwork.ACTION_REMOVE).thenAccept(reply -> replied(reply, "override.removed"));
    }

    void undoChest(ResourceLocation template, net.minecraft.core.BlockPos pos) {
        ClientRequests.containerAction(template, pos, null).thenAccept(reply -> replied(reply, "container.removed"));
    }

    private void reloadNow() {
        ClientRequests.reloadServer().thenAccept(reply -> {
            if (reply.message() != null) {
                say(reply.message(), true);
            }
        });
    }

    private ToolsSection current() {
        return sections.get(section);
    }

    // ------------------------------------------------------------------ screen

    @Override
    protected void init() {
        int menuW = Math.max(84, Math.min(110, width / 6));
        int menuX = PAD + 5;
        contentX = menuX + menuW + 9;
        contentY = TOP + 24;
        contentW = width - PAD - 6 - contentX;
        contentH = height - PAD - 5 - contentY;
        current().init(contentX, contentY, contentW, contentH);
    }

    /** Lets a section add a text box or other widget. */
    <T extends GuiEventListener & net.minecraft.client.gui.components.Renderable & NarratableEntry> T add(T widget) {
        return addRenderableWidget(widget);
    }

    @Override
    public void tick() {
        super.tick();
        if (ClientRequests.reloads() != seenReloads) {
            seenReloads = ClientRequests.reloads();
            // Whatever was waiting for it is in use now, so "from the next /reload" no longer holds.
            message = null;
            refresh();
        }
        if (ClientRequests.structureChanges() != seenStructures) {
            seenStructures = ClientRequests.structureChanges();
            ClientRequests.catalog().thenAccept(entries -> catalog = entries);
        }
        current().tick();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Gui.panel(g, PAD, TOP, width - PAD * 2, height - TOP - PAD);
        ui.begin(mouseX, mouseY);

        // The title row: what this is and any message on the left, what's waiting and the way back on the right.
        int rowY = TOP + 5;
        g.blit(WRENCH, PAD + 6, rowY - 1, 0, 0, 12, 12, 12, 12);
        g.drawString(font, title, PAD + 22, rowY + 2, ToolsUi.TEXT, false);
        int right = width - PAD - 6;
        Component back = Component.translatable("screen.justenoughstructures.tools.browser");
        right -= ui.backButtonWidth(back);
        ui.backButton(g, back, right, rowY - 1, parent != null, this::toBrowser,
                Component.translatable("screen.justenoughstructures.tools.browser_hint"));
        right -= 6;
        int waiting = state == null ? 0 : state.waiting();
        if (waiting > 0) {
            Component reload = Component.translatable("screen.justenoughstructures.tools.reload");
            right -= ui.buttonWidth(reload);
            ui.button(g, reload, right, rowY - 1, true, this::reloadNow, Component.translatable("screen.justenoughstructures.tools.reload_hint"));
            right -= 4;
        }
        String status = Component.translatable(waiting > 0 ? "screen.justenoughstructures.tools.waiting" : "screen.justenoughstructures.tools.applied",
                waiting).getString();
        right -= Gui.fineWidth(font, status);
        Gui.fine(g, font, status, right, rowY + 3, waiting > 0 ? ToolsUi.CHANGED : Gui.LABEL_SOFT);
        if (message != null && Util.getMillis() > messageUntil) {
            message = null;
        }
        if (message != null) {
            int x = PAD + 26 + font.width(title);
            String text = Gui.fineClip(font, message.getString(), right - 8 - x);
            Gui.fine(g, font, text, x, rowY + 3, messageGood ? ToolsUi.GOOD : ToolsUi.BAD);
            ui.tooltip(x, rowY, Gui.fineWidth(font, text), 10, message);
        }

        // The sections down the left, each with a count of what's in it.
        int menuX = PAD + 5;
        int menuW = contentX - 9 - menuX;
        int y = TOP + 22;
        for (Section s : Section.values()) {
            String count = state == null ? "" : sections.get(s).count();
            ui.choice(g, s.label(), count, menuX, y, menuW, 16, s == section, () -> {
                if (s != section) {
                    go(s, null);
                }
            });
            y += 18;
        }
        g.fill(contentX - 5, TOP + 20, contentX - 4, height - PAD - 4, 0xFF8B8B8B);

        if (state == null) {
            Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.tools.loading"), contentX, contentY + 2, contentW, Gui.LABEL_SOFT);
        } else {
            current().render(g, ui, contentX, contentY, contentW, contentH, mouseX, mouseY);
        }
        super.render(g, mouseX, mouseY, partialTick);
        current().renderOver(g, ui, mouseX, mouseY);
        navBar.render(g, font, mouseX, mouseY, partialTick);
        List<Component> tip = ui.tooltip();
        if (tip != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 600);
            List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
            for (Component line : tip) {
                lines.addAll(font.split(line, 260));
            }
            g.renderTooltip(font, lines, mouseX, mouseY);
            g.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (navBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && current().clickFirst(mouseX, mouseY)) {
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && ui.click(mouseX, mouseY)) {
            setFocused(null);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return current().scroll(mouseX, mouseY, delta) || super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (navBar.keyPressed(key, modifiers)) {
            return true;
        }
        if (current().keyPressed(key, modifiers)) {
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        current().leaving();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ back and forward

    /** Where Pack tools is: a section, and what's picked in it. */
    private record ToolsLayer(Section section, Object selection) implements Nav.Layer {
        @Override
        public Object key() {
            return Arrays.asList("tools", section, selection);
        }

        @Override
        public Component label() {
            return Component.translatable("screen.justenoughstructures.nav.tools", section.label());
        }

        @Override
        public boolean sameScreen(Nav.Layer other) {
            return other instanceof ToolsLayer;
        }

        @Override
        public Screen open(Screen below) {
            return new PackToolsScreen(below, section, selection);
        }
    }

    @Override
    public Nav.Layer layer() {
        return new ToolsLayer(section, current().selection());
    }

    @Override
    public Screen below() {
        return parent;
    }

    @Override
    public void restore(Nav.Layer layer) {
        if (layer instanceof ToolsLayer place) {
            current().leaving();
            section = place.section();
            sections.get(section).select(place.selection());
            if (minecraft != null && minecraft.screen == this) {
                rebuildWidgets();
            }
        }
    }

    // ------------------------------------------------------------------ for the screenshot harness

    public void showSection(String name) {
        Section to = Section.valueOf(name.toUpperCase(Locale.ROOT));
        if (to != section) {
            go(to, null);
        }
    }

    public boolean loaded() {
        return state != null;
    }

    public void startPicking() {
        pickChest();
    }

    /** Where a button or link with this label was last drawn, or null. */
    public int[] buttonAt(String label) {
        return ui.centre(label);
    }

    public void pickFor(String name, String selection) {
        Section to = Section.valueOf(name.toUpperCase(Locale.ROOT));
        go(to, sections.get(to).parse(selection));
    }
}
