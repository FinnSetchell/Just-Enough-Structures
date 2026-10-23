package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.loot.LootFormat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The mod's current table and an edit of it side by side, line by line, with what one has and the
 * other doesn't marked. For deciding what to do when a mod has changed a table since it was edited.
 */
final class DiffScreen extends BackdropScreen implements Nav.Page {
    private static final int PAD = 6;
    private static final int TOP = NavBar.TOP;
    private static final int LINE = 10;
    private static final int GONE = 0x40FF3030;
    private static final int ADDED = 0x4030C030;
    /** Past this many line pairs, comparing takes too long to be worth it and every line is shown as changed. */
    private static final long MAX_CELLS = 4_000_000L;
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** A line from each side, or a gap on one; {@code changed} when they're the same line, altered. */
    private record Row(String left, String right, boolean changed) {
        Row(String left, String right) {
            this(left, right, false);
        }
    }

    private final Screen parent;
    private final String tableName;
    private final String theirs;
    private final String yours;
    private final List<Row> rows;
    private final NavBar navBar = new NavBar(this);
    private final Scroll scroll = new Scroll(LINE * 3);
    private HeldBar heldBar;

    DiffScreen(Screen parent, String tableName, String theirs, String yours) {
        super(Component.translatable("screen.justenoughstructures.editor.diff.title", tableName));
        this.parent = parent;
        this.tableName = tableName;
        this.theirs = theirs;
        this.yours = yours;
        this.rows = diff(lines(theirs), lines(yours));
    }

    /** Where this is, for Back and Forward: the changes to one table, as they were. */
    private record DiffLayer(String tableName, String theirs, String yours) implements Nav.Layer {
        @Override
        public Object key() {
            return List.of("diff", tableName);
        }

        @Override
        public Component label() {
            return Component.translatable("screen.justenoughstructures.nav.diff", tableName);
        }

        @Override
        public Screen open(Screen below) {
            return new DiffScreen(below, tableName, theirs, yours);
        }
    }

    @Override
    public Nav.Layer layer() {
        return new DiffLayer(tableName, theirs == null ? "" : theirs, yours == null ? "" : yours);
    }

    @Override
    public Screen below() {
        return parent;
    }

    /**
     * Returns the JSON laid out the same way on both sides, so only real differences show. A table is
     * converted to the format this game version writes, as a mod's file may use an older format with
     * the same meaning.
     */
    private static List<String> lines(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (parsed.isJsonObject()) {
                parsed = LootFormat.forGame(LootFormat.forEditing(parsed.getAsJsonObject()));
            }
            return Arrays.asList(PRETTY.toJson(parsed).split("\n"));
        } catch (JsonParseException e) {
            return Arrays.asList(json.split("\n"));
        }
    }

    /** Lines lined up by their longest run in common; a line only one side has sits against a gap. */
    private static List<Row> diff(List<String> a, List<String> b) {
        List<Row> out = new ArrayList<>();
        int n = a.size();
        int m = b.size();
        if ((long) n * m > MAX_CELLS) {
            a.forEach(line -> out.add(new Row(line, null)));
            b.forEach(line -> out.add(new Row(null, line)));
            return out;
        }
        int[][] common = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                common[i][j] = a.get(i).equals(b.get(j)) ? common[i + 1][j + 1] + 1 : Math.max(common[i + 1][j], common[i][j + 1]);
            }
        }
        int i = 0;
        int j = 0;
        while (i < n || j < m) {
            if (i < n && j < m && a.get(i).equals(b.get(j))) {
                out.add(new Row(a.get(i++), b.get(j++)));
            } else if (j < m && (i == n || common[i][j + 1] >= common[i + 1][j])) {
                out.add(new Row(null, b.get(j++)));
            } else {
                out.add(new Row(a.get(i++), null));
            }
        }
        return paired(out);
    }

    /** Lines removed right next to lines added are the same lines changed, so they're shown side by side. */
    private static List<Row> paired(List<Row> rows) {
        List<Row> out = new ArrayList<>();
        int k = 0;
        while (k < rows.size()) {
            List<String> gone = new ArrayList<>();
            List<String> added = new ArrayList<>();
            while (k < rows.size() && (rows.get(k).left() == null || rows.get(k).right() == null)) {
                Row row = rows.get(k++);
                if (row.left() != null) {
                    gone.add(row.left());
                } else {
                    added.add(row.right());
                }
            }
            for (int x = 0; x < Math.max(gone.size(), added.size()); x++) {
                String left = x < gone.size() ? gone.get(x) : null;
                String right = x < added.size() ? added.get(x) : null;
                out.add(new Row(left, right, left != null && right != null));
            }
            if (k < rows.size()) {
                out.add(rows.get(k++));
            }
        }
        return out;
    }

    @Override
    protected void init() {
        int w = font.width(Component.translatable("screen.justenoughstructures.editor.back")) + 12;
        addRenderableWidget(Button.builder(Component.translatable("screen.justenoughstructures.editor.back"), b -> onClose())
                .bounds(width - PAD - 6 - w, height - PAD - 26, w, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (navBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            fitScroll();
            heldBar = HeldBar.at(scroll, mouseX, mouseY, barRight(), top() + 1, bottom() - top() - 2);
            if (heldBar != null) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (heldBar != null) {
            heldBar.dragTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (heldBar != null) {
            heldBar.letGo();
            heldBar = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return navBar.keyPressed(key, modifiers) || super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        fitScroll();
        scroll.wheel(delta);
        return true;
    }

    private void fitScroll() {
        scroll.fit(bottom() - top(), rows.size() * LINE);
    }

    /** The right edge of the bar, just inside the right-hand column's frame. */
    private int barRight() {
        int column = (width - PAD * 2 - 18) / 2;
        return PAD + 6 + column + 6 + column - 1;
    }

    private int top() {
        return TOP + 32;
    }

    private int bottom() {
        return height - PAD - 30;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Gui.beginClipped();
        backdrop(g);
        Gui.panel(g, PAD, TOP, width - PAD * 2, height - TOP - PAD);
        int left = PAD + 6;
        int column = (width - PAD * 2 - 18) / 2;
        int right = left + column + 6;
        Gui.drawClipped(g, font, title.getString(), left, TOP + 8, width - PAD * 2 - 16, Gui.label(), false);
        Gui.band(g, font, Component.translatable("screen.justenoughstructures.editor.diff.theirs").getString(), left, TOP + 18, column, 12);
        Gui.band(g, font, Component.translatable("screen.justenoughstructures.editor.diff.yours").getString(), right, TOP + 18, column, 12);
        Gui.inset(g, left, top(), column, bottom() - top(), Gui.Tone.LIGHT);
        Gui.inset(g, right, top(), column, bottom() - top(), Gui.Tone.LIGHT);
        fitScroll();
        // When the list scrolls, the right column's lines leave room for the bar.
        int room = scroll.scrolls() ? 6 : 0;
        Gui.scissor(g, left, top() + 1, right + column, bottom() - 1);
        int y = top() + 2 - scroll.top();
        for (Row row : rows) {
            if (y > bottom()) {
                break;
            }
            if (y + LINE >= top()) {
                boolean different = row.changed() || row.left() == null || row.right() == null;
                if (different && row.left() != null) {
                    g.fill(left + 1, y, left + column - 1, y + LINE, GONE);
                }
                if (different && row.right() != null) {
                    g.fill(right + 1, y, right + column - 1, y + LINE, ADDED);
                }
                if (row.left() != null) {
                    Gui.drawClipped(g, font, row.left(), left + 3, y + 1, column - 6, Gui.label(), false);
                }
                if (row.right() != null) {
                    Gui.drawClipped(g, font, row.right(), right + 3, y + 1, column - 6 - room, Gui.label(), false);
                }
            }
            y += LINE;
        }
        Gui.endScissor(g);
        Gui.scrollbar(g, barRight() - 4, top() + 1, bottom() - top() - 2, scroll.offset(), scroll.most());
        super.render(g, mouseX, mouseY, partialTick);
        navBar.render(g, font, mouseX, mouseY, partialTick);
        Gui.push(g);
        Gui.lift(g, 600);
        Gui.clippedTooltip(g, font, mouseX, mouseY);
        Gui.pop(g);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
