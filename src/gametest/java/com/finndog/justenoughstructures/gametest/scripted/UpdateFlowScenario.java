package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.record;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.type;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.ClientState;
import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.finndog.justenoughstructures.client.screen.LootEditorScreen;
import com.finndog.justenoughstructures.client.screen.PackToolsScreen;
import com.finndog.justenoughstructures.client.screen.TablePickerScreen;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Two runs either side of a structure mod updating, to show what happens to a dev's changes.
 * "update_before" makes an item more common in one of a structure's loot tables, then points two of
 * its containers at other tables. "update_after", once the mod's jar has been swapped for a newer one
 * where that table and those containers changed, shows how the browser and Pack tools flag the
 * table, the changes side by side, a merge, and what became of the containers. Each part is recorded
 * into screenshots/u*, with stills in screenshots/review. The containers the first run used are kept
 * in update-flow.json for the second. -Pstructures picks the structure (mvs:desert_house by default).
 */
final class UpdateFlowScenario {
    private static final String FLOW_FILE = "update-flow.json";
    private static final String ITEM = "emerald";
    private static final int WEIGHT = 60;
    private static final int TREE_ROW = 18;

    private UpdateFlowScenario() {
    }

    /** The containers the runs use: c1 for the loot table, a and c2 pointed at other tables. */
    private static final class Picks {
        StructureSnapshot.Container c1, c2, a;
        BlockPos c1Pos, c2Pos, aPos;
        String table;
        int entry = -1;
        int reloads;

        void choose(Minecraft mc) {
            List<StructureSnapshot.Container> solo = new ArrayList<>();
            for (Object m : (List<?>) get(browser(mc), "markerRects")) {
                List<?> containers = (List<?>) call(m, "containers");
                if (containers.size() == 1 && containers.get(0) instanceof StructureSnapshot.Container c
                        && c.lootTable() != null && c.source() != null && !c.entity()) {
                    solo.add(c);
                }
            }
            solo.sort(Comparator.comparingLong(c -> c.pos().asLong()));
            List<StructureSnapshot.Container> chests = solo.stream().filter(c -> c.id().contains("chest")).toList();
            List<StructureSnapshot.Container> others = solo.stream().filter(c -> !c.id().contains("chest")).toList();
            c1 = chests.isEmpty() ? solo.get(0) : chests.get(0);
            c2 = chests.size() > 1 ? chests.get(1) : others.size() > 1 ? others.get(1) : solo.get(Math.min(1, solo.size() - 1));
            a = others.isEmpty() ? solo.get(solo.size() - 1) : others.get(0);
            c1Pos = c1.pos();
            c2Pos = c2.pos();
            aPos = a.pos();
            table = c1.lootTable();
            log("chose c1 {} {} in {} at {}, c2 {} {} at {}, a {} {} at {}; table {}; {} solo markers", c1.id(), c1.pos(),
                    c1.source().template(), c1.source().pos(), c2.id(), c2.pos(), c2.source().pos(), a.id(), a.pos(), a.source().pos(), table, solo.size());
        }

        void save(Minecraft mc) {
            JsonObject out = new JsonObject();
            out.addProperty("structure", structure().toString());
            out.addProperty("table", table);
            out.addProperty("entry", entry);
            out.addProperty("item", "minecraft:" + ITEM);
            out.addProperty("weight", WEIGHT);
            out.add("c1", describe(c1));
            out.add("c2", describe(c2));
            out.add("a", describe(a));
            try {
                Files.writeString(flowFile(mc), new GsonBuilder().setPrettyPrinting().create().toJson(out), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            log("saved {}", out);
        }

        static Picks load(Minecraft mc) {
            Picks p = new Picks();
            try {
                JsonObject in = JsonParser.parseString(Files.readString(flowFile(mc))).getAsJsonObject();
                p.table = in.get("table").getAsString();
                p.entry = in.get("entry").getAsInt();
                p.c1Pos = pos(in.getAsJsonObject("c1"));
                p.c2Pos = pos(in.getAsJsonObject("c2"));
                p.aPos = pos(in.getAsJsonObject("a"));
            } catch (IOException | RuntimeException e) {
                throw new IllegalStateException("Couldn't read " + FLOW_FILE + " from the first run", e);
            }
            return p;
        }

        private static JsonObject describe(StructureSnapshot.Container c) {
            JsonObject o = new JsonObject();
            o.addProperty("block", c.id());
            o.add("pos", array(c.pos()));
            o.addProperty("table", c.lootTable());
            o.addProperty("template", c.source().template().toString());
            o.add("template_pos", array(c.source().pos()));
            return o;
        }

        private static JsonArray array(BlockPos pos) {
            JsonArray a = new JsonArray();
            a.add(pos.getX());
            a.add(pos.getY());
            a.add(pos.getZ());
            return a;
        }

        private static BlockPos pos(JsonObject o) {
            JsonArray a = o.getAsJsonArray("pos");
            return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
        }

        private static Path flowFile(Minecraft mc) {
            return mc.gameDirectory.toPath().resolve(FLOW_FILE);
        }
    }

    // ------------------------------------------------------------------ before the update

    static Director before(Minecraft mc) {
        Director d = new Director(mc, null);
        Picks p = new Picks();
        open(mc, d);
        // Saved now as well as at the end, so the containers are known even if a later step goes wrong.
        d.then(run(() -> {
            p.choose(mc);
            p.save(mc);
        }));

        // 1: an item made more common in the structure's loot table, from one of its chests.
        d.then(record("u1_edit"))
                .then(pause(20))
                .then(openContainer(mc, () -> p.c1Pos))
                .then(moveTo(() -> orCentre(mc, browser(mc).popupLink("odds")), 18))
                .then(pause(6))
                .then(click())
                .then(pause(40))
                .then(shoot("u1_chances_before"))
                .then(moveTo(() -> orCentre(mc, browser(mc).popupLink("tools_table")), 20))
                .then(pause(14))
                .then(click())
                .then(until(() -> tools(mc) != null && tools(mc).loaded(), 200))
                .then(pause(30))
                .then(moveTo(() -> orCentre(mc, tools(mc).buttonAt(text("screen.justenoughstructures.tools.edit"))), 22))
                .then(pause(10))
                .then(click())
                .then(until(() -> editor(mc) != null && editor(mc).loaded(), 200))
                .then(pause(30))
                .then(run(() -> p.entry = entryFor(editor(mc), ITEM)))
                .then(moveTo(() -> treeRow(mc, p.entry), 22))
                .then(pause(6))
                .then(click())
                .then(pause(24))
                .then(moveTo(() -> orCentre(mc, editor(mc).fieldAt("pools.0.entries." + p.entry + ".weight")), 22))
                .then(pause(6))
                .then(click())
                .then(pause(4))
                .then(erase(4, 2))
                .then(type(String.valueOf(WEIGHT), 5))
                .then(pause(6))
                .then(pressKey(InputConstants.KEY_RETURN))
                .then(pause(36))
                .then(shoot("u1_edited"))
                .then(saveAndReload(mc, p))
                .then(shoot("u1_saved"))
                .then(backToBrowser(mc))
                .then(openContainer(mc, () -> p.c1Pos))
                .then(moveTo(() -> orCentre(mc, browser(mc).popupLink("odds")), 18))
                .then(pause(6))
                .then(click())
                .then(pause(50))
                .then(shoot("u1_chances_after"));

        // 2: two containers pointed at other tables, one waiting for a reload and one reloading.
        d.then(record("u2_switch"))
                .then(closePopup(mc))
                .then(pause(16))
                .then(changeContainer(mc, () -> p.aPos, "end city", false, p))
                .then(shoot("u2_first_waiting"))
                .then(clickBrowserButton(mc))
                .then(changeContainer(mc, () -> p.c2Pos, "buried", true, p))
                .then(moveTo(() -> orCentre(mc, section(mc, 2)), 22))
                .then(pause(6))
                .then(click())
                .then(pause(40))
                .then(shoot("u2_changed_chests"))
                .then(clickBrowserButton(mc))
                .then(openContainer(mc, () -> p.aPos))
                .then(pause(40))
                .then(shoot("u2_a_after"))
                // Its popup cuts the view down to it, which hides the other container's marker.
                .then(closePopup(mc))
                .then(openContainer(mc, () -> p.c2Pos))
                .then(pause(40))
                .then(shoot("u2_c2_after"))
                .then(run(() -> p.save(mc)))
                .then(record("u_end"))
                .then(pause(4));
        return d;
    }

    // ------------------------------------------------------------------ after the update

    static Director after(Minecraft mc) {
        Director d = new Director(mc, null);
        Picks p = Picks.load(mc);
        open(mc, d);

        // 3: how the edited table shows up once the mod's changed it, and the changes side by side.
        d.then(record("u3_flagged"))
                .then(pause(20))
                .then(moveTo(() -> orCentre(mc, browser(mc) == null ? null : browser(mc).tab("loot")), 22))
                .then(pause(4))
                .then(click())
                .then(pause(20))
                .then(moveTo(() -> orCentre(mc, browser(mc).highlightRow("loot:" + p.table).orElse(null)), 20))
                .then(pause(50))
                .then(shoot("u3_loot_tab"))
                .then(openContainer(mc, () -> p.c1Pos))
                .then(pause(20))
                .then(shoot("u3_popup"))
                .then(moveTo(() -> orCentre(mc, browser(mc).popupLink("tools_table")), 20))
                .then(pause(14))
                .then(click())
                .then(until(() -> tools(mc) != null && tools(mc).loaded(), 200))
                .then(pause(40))
                .then(shoot("u3_tools"))
                .then(moveTo(() -> orCentre(mc, tools(mc).buttonAt(text("screen.justenoughstructures.tools.edit"))), 22))
                .then(pause(10))
                .then(click())
                .then(until(() -> editor(mc) != null && editor(mc).loaded(), 200))
                .then(pause(50))
                .then(shoot("u3_editor"))
                .then(moveTo(() -> orCentre(mc, editor(mc).buttonAt(text("screen.justenoughstructures.editor.see_changes"))), 22))
                .then(pause(10))
                .then(click())
                .then(until(() -> mc.screen != null && mc.screen.getClass().getSimpleName().equals("DiffScreen"), 60))
                .then(pause(80))
                .then(shoot("u3_changes"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> editor(mc) != null, 60))
                .then(pause(20));

        // 4: merging the mod's changes in, and the result in the browser.
        d.then(record("u4_merge"))
                .then(pause(10))
                .then(moveTo(() -> orCentre(mc, editor(mc).buttonAt(text("screen.justenoughstructures.editor.merge"))), 22))
                .then(pause(10))
                .then(click())
                .then(pause(50))
                .then(shoot("u4_merged"))
                .then(saveAndReload(mc, p))
                .then(shoot("u4_saved"))
                .then(backToBrowser(mc))
                .then(openContainer(mc, () -> p.c1Pos))
                .then(moveTo(() -> orCentre(mc, browser(mc).popupLink("odds")), 18))
                .then(pause(6))
                .then(click())
                .then(pause(50))
                .then(shoot("u4_chances_after"));

        // 5: the two containers pointed at other tables, after the mod changed them.
        d.then(record("u5_containers"))
                // A click outside an open popup only closes it, so it's closed first.
                .then(closePopup(mc))
                .then(pause(10))
                .then(moveTo(() -> orCentre(mc, browser(mc) == null ? null : browser(mc).button("tools")), 24))
                .then(pause(6))
                .then(click())
                .then(until(() -> tools(mc) != null && tools(mc).loaded(), 200))
                .then(pause(20))
                .then(moveTo(() -> orCentre(mc, section(mc, 2)), 22))
                .then(pause(6))
                .then(click())
                .then(pause(40))
                .then(shoot("u5_changed_chests"))
                .then(clickBrowserButton(mc))
                .then(run(() -> log("after the update: c2 {} a {}", describeAt(mc, p.c2Pos), describeAt(mc, p.aPos))))
                // Where the container the mod took out was, before a popup covers the preview.
                .then(moveTo(() -> orCentre(mc, projected(mc, p.aPos)), 24))
                .then(pause(40))
                .then(shoot("u5_a_spot"))
                .then(openContainer(mc, () -> p.c2Pos))
                .then(pause(40))
                .then(shoot("u5_c2_after"))
                .then(record("u_end"))
                .then(pause(4));
        return d;
    }

    /** After the update again, taking the mod's table instead of merging: "update_after_mods". */
    static Director afterUseMods(Minecraft mc) {
        Director d = new Director(mc, null);
        Picks p = Picks.load(mc);
        open(mc, d);
        d.then(record("u6_use_mods"))
                .then(pause(16))
                .then(openContainer(mc, () -> p.c1Pos))
                .then(moveTo(() -> orCentre(mc, browser(mc).popupLink("tools_table")), 20))
                .then(pause(14))
                .then(click())
                .then(until(() -> tools(mc) != null && tools(mc).loaded(), 200))
                .then(pause(30))
                .then(moveTo(() -> orCentre(mc, tools(mc).buttonAt(text("screen.justenoughstructures.tools.edit"))), 22))
                .then(pause(10))
                .then(click())
                .then(until(() -> editor(mc) != null && editor(mc).loaded(), 200))
                .then(pause(40))
                .then(shoot("u6_editor_choices"))
                // Held over the button long enough for its hint to show.
                .then(moveTo(() -> orCentre(mc, editor(mc).buttonAt(text("screen.justenoughstructures.editor.use_mods"))), 22))
                .then(pause(40))
                .then(shoot("u6_use_mods_hint"))
                .then(click())
                .then(pause(50))
                .then(shoot("u6_used_mods"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> tools(mc) != null || browser(mc) != null, 60))
                .then(pause(30))
                .then(shoot("u6_back_in_tools"))
                .then(reloadNow(mc, p))
                .then(pause(40))
                .then(shoot("u6_reloaded"))
                .then(clickBrowserButton(mc))
                .then(openContainer(mc, () -> p.c1Pos))
                .then(moveTo(() -> orCentre(mc, browser(mc).popupLink("odds")), 18))
                .then(pause(6))
                .then(click())
                .then(pause(50))
                .then(shoot("u6_chances_mods"))
                .then(record("u_end"))
                .then(pause(4));
        return d;
    }

    // ------------------------------------------------------------------ steps

    /** Pack tools' Reload now, or a reload straight away if it isn't showing, waiting for it. */
    private static Director.Action reloadNow(Minecraft mc, Picks p) {
        java.util.function.Supplier<int[]> button = () -> tools(mc) == null ? null
                : tools(mc).buttonAt(text("screen.justenoughstructures.tools.reload"));
        return chain(
                run(() -> p.reloads = ClientRequests.reloads()),
                skipIf(() -> button.get() == null, chain(moveTo(button::get, 22), pause(10), click())),
                run(() -> {
                    if (button.get() == null && ClientRequests.reloads() == p.reloads) {
                        log("no Reload now button, reloading directly");
                        ClientRequests.reloadServer();
                    }
                }),
                until(() -> ClientRequests.reloads() > p.reloads, 2400),
                pause(40));
    }

    /** Opens the browser on the structure, with the camera still, once the loot index is ready. Not recorded. */
    private static void open(Minecraft mc, Director d) {
        ResourceLocation id = structure();
        d.then(run(() -> {
                    ClientState.spin = false;
                    ClientState.markers = true;
                    ClientState.ground = true;
                    JesScreen.startOn(id);
                }))
                .then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 100))
                .then(until(() -> ready(mc), 3600))
                .then(run(() -> {
                    CaptureResult r = browser(mc).result();
                    if (r == null || !r.succeeded() || !r.snapshot().structureId().equals(id)) {
                        browser(mc).select(id);
                    }
                }))
                .then(pause(2))
                .then(until(() -> ready(mc), 3600))
                .then(run(() -> browser(mc).setSpin(false)))
                .then(until(FoundIn::ready, 20 * 900))
                .then(pause(20))
                .then(moveTo(() -> offset(browser(mc).viewportCentre(), 160, 60), 1));
    }

    /** Clicks a container's marker and waits for its popup. */
    private static Director.Action openContainer(Minecraft mc, java.util.function.Supplier<BlockPos> pos) {
        return chain(
                moveTo(() -> orCentre(mc, markerOf(mc, pos.get())), 26),
                pause(10),
                click(),
                until(() -> browser(mc) != null && browser(mc).containerOpen(), 60),
                pause(30));
    }

    /** Closes an open container's popup with its Done button, so the view's layers come back. */
    private static Director.Action closePopup(Minecraft mc) {
        return chain(
                skipIf(() -> browser(mc) == null || !browser(mc).containerOpen() || widget(mc, text("gui.done")) == null,
                        chain(moveTo(() -> widget(mc, text("gui.done")), 18), pause(6), click())),
                run(() -> {
                    if (browser(mc) != null && browser(mc).containerOpen()) {
                        browser(mc).closeContainer();
                    }
                }),
                pause(24));
    }

    /** From a container in the browser to its page in Pack tools, Change, and a table picked by search. */
    private static Director.Action changeContainer(Minecraft mc, java.util.function.Supplier<BlockPos> pos, String search, boolean reload, Picks p) {
        return chain(
                openContainer(mc, pos),
                moveTo(() -> orCentre(mc, browser(mc).popupLink("tools_container")), 20),
                pause(14),
                click(),
                until(() -> tools(mc) != null && tools(mc).loaded(), 200),
                pause(24),
                moveTo(() -> orCentre(mc, tools(mc).buttonAt(text("screen.justenoughstructures.container.change"))), 22),
                pause(10),
                click(),
                until(() -> tablePicker(mc) != null && tablePicker(mc).ready(), 1200),
                pause(16),
                type(search, 3),
                pause(16),
                moveTo(() -> pickerRow(mc, 0, 18), 22),
                pause(6),
                click(),
                pause(14),
                moveTo(() -> orCentre(mc, widget(mc, text(reload ? "screen.justenoughstructures.picker.use_reload" : "screen.justenoughstructures.picker.use"))), 22),
                pause(10),
                run(() -> p.reloads = ClientRequests.reloads()),
                click(),
                until(() -> tools(mc) != null, 100),
                reload ? until(() -> ClientRequests.reloads() > p.reloads, 2400) : pause(1),
                pause(40));
    }

    /** The editor's Save & reload, waiting for the reload. */
    private static Director.Action saveAndReload(Minecraft mc, Picks p) {
        return chain(
                moveTo(() -> orCentre(mc, widget(mc, text("screen.justenoughstructures.editor.save_reload"))), 22),
                pause(10),
                run(() -> p.reloads = ClientRequests.reloads()),
                click(),
                until(() -> ClientRequests.reloads() > p.reloads, 2400),
                pause(40));
    }

    /** From the editor back to Pack tools, then its Browser button. */
    private static Director.Action backToBrowser(Minecraft mc) {
        return chain(
                pressKey(InputConstants.KEY_ESCAPE),
                until(() -> tools(mc) != null || browser(mc) != null, 60),
                pause(20),
                clickBrowserButton(mc));
    }

    /** Pack tools' Browser button, unless the browser's showing already, then waits for the structure. */
    private static Director.Action clickBrowserButton(Minecraft mc) {
        return chain(
                skipIf(() -> browser(mc) != null, chain(moveTo(() -> browserButton(mc), 24), pause(8), click())),
                until(() -> browser(mc) != null, 60),
                until(() -> ready(mc), 1200),
                pause(24));
    }

    /** Skips {@code action} if {@code when} holds as it starts. */
    private static Director.Action skipIf(java.util.function.BooleanSupplier when, Director.Action action) {
        boolean[] skip = new boolean[1];
        return (d, frame) -> {
            if (frame == 0) {
                skip[0] = when.getAsBoolean();
            }
            return skip[0] || action.step(d, frame);
        };
    }

    /** Plays actions one after another as one. */
    private static Director.Action chain(Director.Action... actions) {
        int[] at = {0};
        int[] start = {0};
        return (d, frame) -> {
            if (frame == 0) {
                at[0] = 0;
                start[0] = 0;
            }
            while (at[0] < actions.length) {
                if (actions[at[0]].step(d, frame - start[0])) {
                    at[0]++;
                    start[0] = frame + 1;
                    return at[0] >= actions.length;
                }
                return false;
            }
            return true;
        };
    }

    private static Director.Action erase(int count, int framesPer) {
        return (d, frame) -> {
            if (frame % framesPer == 0 && frame / framesPer < count) {
                d.key(InputConstants.KEY_BACKSPACE);
            }
            return frame / framesPer >= count;
        };
    }

    // ------------------------------------------------------------------ where things are

    private static ResourceLocation structure() {
        String asked = System.getProperty("jes.autoshot.structures", "").trim();
        return Ids.parse(asked.isEmpty() ? "mvs:desert_house" : asked);
    }

    private static void log(String message, Object... args) {
        JustEnoughStructures.LOGGER.info("UPDFLOW " + message, args);
    }

    private static String describeAt(Minecraft mc, BlockPos pos) {
        CaptureResult r = browser(mc) == null ? null : browser(mc).result();
        if (r == null || !r.succeeded()) {
            return "no structure";
        }
        for (StructureSnapshot.Container c : r.snapshot().containers()) {
            if (c.pos().equals(pos)) {
                return c.id() + " " + c.lootTable() + (c.source() == null ? "" : " from " + c.source().patchedFrom());
            }
        }
        return "no container there";
    }

    /** The first pool's entry for an item, or its second entry. */
    private static int entryFor(LootEditorScreen editor, String item) {
        JsonObject draft = editor.draft();
        JsonArray entries = draft.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries");
        for (int i = 0; i < entries.size(); i++) {
            JsonObject e = entries.get(i).getAsJsonObject();
            if (e.has("name") && e.get("name").getAsString().contains(item)) {
                log("editing entry {}: {}", i, e);
                return i;
            }
        }
        return Math.min(1, entries.size() - 1);
    }

    private static String text(String key) {
        return Component.translatable(key).getString();
    }

    private static int[] offset(int[] p, int dx, int dy) {
        return p == null ? null : new int[]{p[0] + dx, p[1] + dy};
    }

    /** A point, or the middle of the screen if there isn't one, logged so a missing target shows up. */
    private static int[] orCentre(Minecraft mc, int[] p) {
        if (p != null) {
            return p;
        }
        log("missing target on {}", mc.screen == null ? "no screen" : mc.screen.getClass().getSimpleName(), new Throwable());
        return new int[]{mc.getWindow().getGuiScaledWidth() / 2, mc.getWindow().getGuiScaledHeight() / 2};
    }

    private static JesScreen browser(Minecraft mc) {
        return mc.screen instanceof JesScreen s ? s : null;
    }

    private static PackToolsScreen tools(Minecraft mc) {
        return mc.screen instanceof PackToolsScreen s ? s : null;
    }

    private static LootEditorScreen editor(Minecraft mc) {
        return mc.screen instanceof LootEditorScreen s ? s : null;
    }

    private static TablePickerScreen tablePicker(Minecraft mc) {
        return mc.screen instanceof TablePickerScreen s ? s : null;
    }

    private static boolean ready(Minecraft mc) {
        JesScreen b = browser(mc);
        return b != null && b.idle() && b.result() != null;
    }

    /** Where a block in the preview is drawn, or null. */
    private static int[] projected(Minecraft mc, BlockPos pos) {
        if (pos == null || browser(mc) == null) {
            return null;
        }
        Object viewport = get(browser(mc), "viewport");
        try {
            Method project = viewport.getClass().getMethod("project", double.class, double.class, double.class);
            @SuppressWarnings("unchecked")
            java.util.Optional<float[]> p = (java.util.Optional<float[]>) project.invoke(viewport, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            return p.map(f -> new int[]{Math.round(f[0]), Math.round(f[1])}).orElse(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The marker standing for the container at {@code pos}, wherever it's drawn now. */
    private static int[] markerOf(Minecraft mc, BlockPos pos) {
        if (pos == null || browser(mc) == null) {
            return null;
        }
        for (Object m : (List<?>) get(browser(mc), "markerRects")) {
            for (Object c : (List<?>) call(m, "containers")) {
                if (((StructureSnapshot.Container) c).pos().equals(pos)) {
                    return new int[]{Math.round((float) call(m, "x") + (int) call(m, "size") / 2f),
                            Math.round((float) call(m, "y") + (int) call(m, "size") / 2f)};
                }
            }
        }
        return null;
    }

    /** Pack tools' section buttons down the left, by their place in the list. */
    private static int[] section(Minecraft mc, int index) {
        PackToolsScreen tools = tools(mc);
        if (tools == null) {
            return null;
        }
        int contentX = getInt(tools, "contentX");
        int menuX = 11;
        int menuW = contentX - 9 - menuX;
        return new int[]{menuX + menuW / 2, 20 + 22 + index * 18 + 8};
    }

    /** Pack tools' Browser button, at the top right. */
    private static int[] browserButton(Minecraft mc) {
        int w = mc.font.width(Component.translatable("screen.justenoughstructures.tools.browser")) + 19;
        return new int[]{mc.getWindow().getGuiScaledWidth() - 6 - 6 - w / 2, 20 + 5 - 1 + 7};
    }

    /** A row of the table picker's list. */
    private static int[] pickerRow(Minecraft mc, int row, int rowHeight) {
        return new int[]{mc.getWindow().getGuiScaledWidth() / 3, 20 + 56 + 2 + row * rowHeight + rowHeight / 2};
    }

    /** The middle of a vanilla button or other widget on the screen with this label. */
    private static int[] widget(Minecraft mc, String label) {
        if (mc.screen == null) {
            return null;
        }
        for (GuiEventListener child : mc.screen.children()) {
            if (child instanceof AbstractWidget w && w.visible && w.getMessage().getString().equals(label)) {
                return new int[]{w.getX() + w.getWidth() / 2, w.getY() + w.getHeight() / 2};
            }
        }
        return null;
    }

    /** The editor's tree row for an entry in the first pool: under the table's row and the pool's. */
    private static int[] treeRow(Minecraft mc, int entry) {
        LootEditorScreen editor = editor(mc);
        int treeX = getInt(editor, "treeX");
        int treeW = getInt(editor, "treeW");
        int contentTop = getInt(editor, "contentTop");
        return new int[]{treeX + treeW / 2, contentTop + 1 + TREE_ROW * (2 + entry) + TREE_ROW / 2};
    }

    // ------------------------------------------------------------------ reflection, for what the harness doesn't expose

    private static Object get(Object target, String name) {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                // Look further up.
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("No field " + name + " on " + target.getClass());
    }

    private static int getInt(Object target, String name) {
        return ((Number) get(target, name)).intValue();
    }

    private static Object call(Object target, String name) {
        try {
            Method m = target.getClass().getDeclaredMethod(name);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
