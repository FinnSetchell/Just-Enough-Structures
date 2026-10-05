package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.server.PackToolsAccess;
import com.finndog.justenoughstructures.server.PackToolsState;
import com.finndog.justenoughstructures.server.ServerConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * The server's rules, as in server.json5: who can find and teleport to structures and who can use
 * Pack tools, what players see, and whole mods to hide. Each change is saved straight away.
 */
final class ToolsRules extends ToolsSection {
    private static final int LINE = 18;
    /** The space above each heading after the first. */
    private static final int HEADING_GAP = 8;
    private final Scroller scroller = new Scroller();
    private EditBox nameBox;
    private String typed = "";

    ToolsRules(PackToolsScreen screen) {
        super(screen);
    }

    @Override
    void init(int x, int y, int w, int h) {
        nameBox = screen.add(new EditBox(font, x, y, 100, 14, Component.translatable("screen.justenoughstructures.tools.player_name")));
        nameBox.setMaxLength(16);
        nameBox.setHint(Component.translatable("screen.justenoughstructures.tools.player_name").withStyle(ChatFormatting.DARK_GRAY));
        nameBox.setValue(typed);
        nameBox.setResponder(text -> typed = text);
        nameBox.visible = false;
    }

    @Override
    void tick() {
        if (nameBox != null) {
            nameBox.tick();
        }
    }

    @Override
    boolean scroll(double mouseX, double mouseY, double delta) {
        return scroller.scroll(mouseX, mouseY, delta);
    }

    @Override
    boolean keyPressed(int key, int modifiers) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && nameBox != null && nameBox.isFocused()) {
            addPlayer();
            return true;
        }
        return false;
    }

    @Override
    void render(GuiGraphics g, ToolsUi ui, int x, int y, int w, int h, int mouseX, int mouseY) {
        ServerConfig.Settings s = screen.state().settings();
        String[] lines = ServerConfig.render(s).split("\n");
        int codeW = 0;
        for (String line : lines) {
            codeW = Math.max(codeW, Gui.fineWidth(font, line.replace("\t", "  ")) + 8);
        }
        // No wider than reads well, or than the file as saved needs for its longest line.
        int top = scroller.begin(g, ui, x, y, Math.min(w, Math.max(READABLE, codeW) + 8), h);
        int cw = scroller.width();
        int cy = ui.heading(g, Component.translatable("screen.justenoughstructures.tools.who_can"), x, top, cw);
        cy = levelRow(g, ui, x, cy, cw, "locate", s.locatePermission(), 0, level -> with(s, level, s.teleportPermission(), s.packTools()));
        cy = levelRow(g, ui, x, cy, cw, "teleport", s.teleportPermission(), 0, level -> with(s, s.locatePermission(), level, s.packTools()));

        cy = ui.heading(g, Component.translatable("screen.justenoughstructures.tools.who_tools"), x, cy + HEADING_GAP, cw);
        cy = label(g, ui, x, cy, cw, "singleplayer", Component.translatable("screen.justenoughstructures.tools.cheats_on"));
        Gui.drawClipped(g, font, Component.translatable("screen.justenoughstructures.tools.by_name").getString(), x + 2, cy + 4, cw - 4, ToolsUi.TEXT, false);
        cy += 15;
        int cx = x + 2;
        List<String> players = s.packTools().players();
        if (players.isEmpty()) {
            Gui.fine(g, font, Component.translatable("screen.justenoughstructures.tools.nobody_listed").getString(), cx, cy + 2, Gui.LABEL_SOFT);
        }
        for (String player : players) {
            String label = player + "  x";
            int chipW = Gui.fineWidth(font, label) + 6;
            if (cx > x + 2 && cx + chipW > x + cw) {
                cx = x + 2;
                cy += ui.chipHeight() + 2;
            }
            cx += ui.chip(g, label, cx, cy, false, () -> removePlayer(player),
                    Component.translatable("screen.justenoughstructures.tools.unlist", player)) + 3;
        }
        cy += ui.chipHeight() + 4;
        Component add = Component.translatable("screen.justenoughstructures.tools.add");
        int addW = ui.buttonWidth(add) + 6;
        placeNameBox(g, x + 3, cy + 1, cw - addW - 10, y, h);
        ui.button(g, add, x + cw - addW - 2, cy, addW, 16, ServerConfig.isPlayerName(typed.trim()), this::addPlayer);
        cy += LINE + 4;
        cy = label(g, ui, x, cy, cw, "node", Component.literal(PackToolsAccess.NODE));
        cy = levelRow(g, ui, x, cy, cw, "tools_level", s.packTools().permissionLevel(), -1,
                level -> with(s, s.locatePermission(), s.teleportPermission(), new ServerConfig.PackTools(players, level)));

        cy = ui.heading(g, Component.translatable("screen.justenoughstructures.tools.players_see"), x, cy + HEADING_GAP, cw);
        cy = switchRow(g, ui, x, cy, cw, "show_loot", s.showLootLocations(), () -> save(new ServerConfig.Settings(s.hiddenStructures(),
                s.hiddenMods(), s.locatePermission(), s.teleportPermission(), !s.showLootLocations(), s.packTools(), s.containerChanges())), null);
        cy = switchRow(g, ui, x, cy, cw, "use_changes", s.containerChanges(), () -> save(new ServerConfig.Settings(s.hiddenStructures(),
                        s.hiddenMods(), s.locatePermission(), s.teleportPermission(), s.showLootLocations(), s.packTools(), !s.containerChanges())),
                screen.waiting(PackToolsState.RULES) ? Component.translatable("screen.justenoughstructures.tools.from_next_reload") : null);

        cy = ui.heading(g, Component.translatable("screen.justenoughstructures.tools.hide_mods"), x, cy + HEADING_GAP, cw);
        cx = x;
        for (var mod : mods(s).entrySet()) {
            boolean hidden = s.hiddenMods().contains(mod.getValue());
            String label = hidden ? Component.translatable("screen.justenoughstructures.tools.hidden_mod", mod.getKey()).getString() : mod.getKey();
            int chipW = Gui.fineWidth(font, label) + 6;
            if (cx > x && cx + chipW > x + cw) {
                cx = x;
                cy += ui.chipHeight() + 2;
            }
            String namespace = mod.getValue();
            cx += ui.chip(g, label, cx, cy, hidden, () -> save(hideMod(s, namespace, !hidden)),
                    Component.translatable(hidden ? "screen.justenoughstructures.tools.show_mod" : "screen.justenoughstructures.tools.hide_mod")) + 2;
        }
        cy += ui.chipHeight();

        cy = ui.heading(g, Component.translatable("screen.justenoughstructures.tools.as_saved"), x, cy + HEADING_GAP, cw);
        int lineH = Gui.fineLine(font) + 1;
        g.fill(x, cy, x + cw, cy + lines.length * lineH + 6, 0xFF1E1E1E);
        int ly = cy + 3;
        for (String line : lines) {
            Gui.fineClipped(g, font, line.replace("\t", "  "), x + 4, ly, cw - 8, line.trim().startsWith("//") ? 0xFF6A9955 : 0xFFD4D4D4);
            ly += lineH;
        }
        cy = ly + 4;
        scroller.end(g, ui, cy - top);
    }

    /**
     * The text box for a name, kept where its row is as the page scrolls. It's a text box only while
     * all of it is in view; while only some is, a picture of it stands in, cut off at the edge like the rest.
     */
    private void placeNameBox(GuiGraphics g, int x, int y, int w, int viewTop, int viewHeight) {
        if (nameBox == null) {
            return;
        }
        w = Math.max(40, w);
        nameBox.setX(x);
        nameBox.setY(y);
        nameBox.setWidth(w);
        nameBox.visible = y >= viewTop && y + 14 <= viewTop + viewHeight;
        if (nameBox.visible || y + 14 <= viewTop || y >= viewTop + viewHeight) {
            return;
        }
        g.fill(x, y, x + w, y + 14, 0xFFA0A0A0);
        g.fill(x + 1, y + 1, x + w - 1, y + 13, 0xFF000000);
        boolean empty = typed.isEmpty();
        Gui.drawClipped(g, font, empty ? Component.translatable("screen.justenoughstructures.tools.player_name").getString() : typed,
                x + 4, y + 3, w - 8, empty ? 0xFF555555 : 0xFFE0E0E0, false);
    }

    // Each row has its label on the left and its value or control on the right, or under the label when both don't fit.

    private int label(GuiGraphics g, ToolsUi ui, int x, int y, int w, String key, Component value) {
        Component label = Component.translatable("screen.justenoughstructures.tools." + key);
        String text = value.getString();
        boolean under = font.width(label) + 8 + Gui.fineWidth(font, text) > w - 4;
        Gui.drawClipped(g, font, label.getString(), x + 2, y + 5, w - 4, ToolsUi.TEXT, false);
        int room = under ? w - 4 : w - 12 - font.width(label);
        String shown = Gui.fineClip(font, text, room);
        Gui.fineClipped(g, font, text, x + w - 2 - Gui.fineWidth(font, shown), under ? y + 15 : y + 6, room, Gui.LABEL_SOFT);
        int h = under ? LINE + Gui.fineLine(font) + 3 : LINE;
        hint(ui, x, y, w, h - 1, key);
        g.fill(x, y + h - 1, x + w, y + h, 0xFFB0B0B0);
        return y + h;
    }

    private int levelRow(GuiGraphics g, ToolsUi ui, int x, int y, int w, String key, int level, int lowest,
                         java.util.function.IntFunction<ServerConfig.Settings> change) {
        Component label = Component.translatable("screen.justenoughstructures.tools." + key);
        // As wide as the longest level's name, so the button doesn't change size as it's clicked through.
        int buttonW = 0;
        for (int l = lowest; l <= 4; l++) {
            buttonW = Math.max(buttonW, ui.buttonWidth(levelName(l)));
        }
        buttonW = Math.min(buttonW, w - 4);
        boolean under = font.width(label) + 8 + buttonW > w - 4;
        Gui.drawClipped(g, font, label.getString(), x + 2, y + 5, w - 4, ToolsUi.TEXT, false);
        int h = under ? LINE + ToolsUi.BUTTON + 1 : LINE;
        hint(ui, x, y, under ? w : w - buttonW - 4, under ? 14 : LINE - 1, key);
        int next = level >= 4 ? lowest : level + 1;
        ui.button(g, levelName(level), x + w - buttonW - 2, under ? y + 15 : y + 2, buttonW, ToolsUi.BUTTON, true, () -> save(change.apply(next)));
        g.fill(x, y + h - 1, x + w, y + h, 0xFFB0B0B0);
        return y + h;
    }

    private int switchRow(GuiGraphics g, ToolsUi ui, int x, int y, int w, String key, boolean on, Runnable flip, Component note) {
        Component label = Component.translatable("screen.justenoughstructures.tools." + key);
        String yes = Component.translatable(on ? "gui.yes" : "gui.no").getString();
        String noteText = note == null ? "" : note.getString();
        int controlW = 23 + font.width(yes) + (noteText.isEmpty() ? 0 : Gui.fineWidth(font, noteText) + 5);
        boolean under = font.width(label) + 8 + controlW > w - 4;
        Gui.drawClipped(g, font, label.getString(), x + 2, y + 5, w - 4, ToolsUi.TEXT, false);
        int h = under ? LINE + 14 : LINE;
        hint(ui, x, y, under ? w : w - controlW - 4, under ? 14 : LINE - 1, key);
        int cy = under ? y + 14 : y;
        int textX = x + w - 2 - font.width(yes);
        g.drawString(font, yes, textX, cy + 5, ToolsUi.TEXT, false);
        ui.toggle(g, textX - 21, cy + 4, on, flip);
        if (!noteText.isEmpty()) {
            int noteX = Math.max(x + 2, textX - 26 - Gui.fineWidth(font, noteText));
            Gui.fineClipped(g, font, noteText, noteX, cy + 6, textX - 26 - noteX, ToolsUi.CHANGED);
        }
        g.fill(x, y + h - 1, x + w, y + h, 0xFFB0B0B0);
        return y + h;
    }

    /** A row's tooltip, if its key has one. */
    private void hint(ToolsUi ui, int x, int y, int w, int h, String key) {
        String hintKey = "screen.justenoughstructures.tools." + key + "_hint";
        if (net.minecraft.client.resources.language.I18n.exists(hintKey)) {
            ui.tooltip(x, y, w, h, Component.translatable(hintKey));
        }
    }

    static Component levelName(int level) {
        return Component.translatable("screen.justenoughstructures.tools.level." + (level < 0 ? "none" : String.valueOf(level)));
    }

    /** Every mod with structures, by its name, to its namespace. */
    private TreeMap<String, String> mods(ServerConfig.Settings s) {
        Set<String> namespaces = new HashSet<>(s.hiddenMods());
        for (StructureCatalog.Entry entry : screen.catalog()) {
            namespaces.add(entry.id().getNamespace());
        }
        for (StructureCatalog.Entry entry : screen.state().hidden()) {
            namespaces.add(entry.id().getNamespace());
        }
        TreeMap<String, String> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String namespace : namespaces) {
            out.put(StructureNames.mod(namespace), namespace);
        }
        return out;
    }

    static ServerConfig.Settings hideMod(ServerConfig.Settings s, String namespace, boolean hide) {
        Set<String> mods = new HashSet<>(s.hiddenMods());
        if (hide) {
            mods.add(namespace);
        } else {
            mods.remove(namespace);
        }
        return new ServerConfig.Settings(s.hiddenStructures(), mods, s.locatePermission(), s.teleportPermission(), s.showLootLocations(),
                s.packTools(), s.containerChanges());
    }

    static ServerConfig.Settings hideStructure(ServerConfig.Settings s, ResourceLocation id, boolean hide) {
        Set<ResourceLocation> structures = new HashSet<>(s.hiddenStructures());
        Set<String> mods = new HashSet<>(s.hiddenMods());
        if (hide) {
            structures.add(id);
        } else {
            structures.remove(id);
        }
        return new ServerConfig.Settings(structures, mods, s.locatePermission(), s.teleportPermission(), s.showLootLocations(),
                s.packTools(), s.containerChanges());
    }

    private static ServerConfig.Settings with(ServerConfig.Settings s, int locate, int teleport, ServerConfig.PackTools tools) {
        return new ServerConfig.Settings(s.hiddenStructures(), s.hiddenMods(), locate, teleport, s.showLootLocations(), tools, s.containerChanges());
    }

    private void addPlayer() {
        String name = typed.trim();
        if (!ServerConfig.isPlayerName(name)) {
            screen.say(Component.translatable("config.justenoughstructures.pack_tools_players.invalid", name), false);
            return;
        }
        ServerConfig.Settings s = screen.state().settings();
        List<String> players = new ArrayList<>(s.packTools().players());
        if (players.stream().noneMatch(name::equalsIgnoreCase)) {
            players.add(name);
        }
        typed = "";
        if (nameBox != null) {
            nameBox.setValue("");
        }
        save(with(s, s.locatePermission(), s.teleportPermission(), new ServerConfig.PackTools(players, s.packTools().permissionLevel())));
    }

    private void removePlayer(String name) {
        ServerConfig.Settings s = screen.state().settings();
        List<String> players = new ArrayList<>(s.packTools().players());
        players.removeIf(name::equalsIgnoreCase);
        save(with(s, s.locatePermission(), s.teleportPermission(), new ServerConfig.PackTools(players, s.packTools().permissionLevel())));
    }

    private void save(ServerConfig.Settings settings) {
        ClientRequests.saveRules(settings).thenAccept(reply -> screen.replied(reply, "tools.rules_saved"));
    }
}
