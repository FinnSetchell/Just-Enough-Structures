package com.finndog.justenoughstructures.gametest.scripted;

import com.mojang.blaze3d.platform.InputConstants;
import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.dragBy;
import static com.finndog.justenoughstructures.gametest.scripted.Director.dragTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.record;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.wheel;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.type;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Scripts for the review screenshots and the showcase recordings. */
public final class Scenarios {
    private static final String CHEST = "minecraft:chests/desert_pyramid";

    private Scenarios() {
    }

    /** Scenarios only some loaders can build, which their test mod adds with {@link #add}. */
    private static final Map<String, Function<Minecraft, Director>> EXTRA = new HashMap<>();

    public static void add(String mode, Function<Minecraft, Director> build) {
        EXTRA.put(mode, build);
    }

    public static Director build(String mode, Minecraft mc) {
        return switch (mode) {
            case "review" -> review(mc);
            case "open" -> open(mc);
            case "showcase" -> showcase(mc);
            case "spin" -> spin(mc);
            case "teleport" -> teleport(mc);
            case "compass" -> CompassScenario.build(mc);
            case "editor" -> EditorScenario.build(mc);
            case "containers" -> ContainerScenario.build(mc);
            case "details" -> DetailsScenario.build(mc);
            case "tabs" -> InfoTabsScenario.build(mc);
            case "lootfixes" -> LootFixesScenario.build(mc);
            case "markers" -> MarkersScenario.build(mc);
            case "favourites" -> FavouritesScenario.build(mc);
            case "spawners" -> SpawnersScenario.build(mc);
            case "back" -> BackScenario.build(mc);
            case "hover_spin" -> SpinScenario.build(mc);
            case "highlight" -> HighlightScenario.build(mc);
            case "loot_tab" -> LootTabScenario.build(mc);
            case "pack_tools" -> PackToolsScenario.build(mc);
            case "pack_tools_edits" -> PackToolsEditsScenario.build(mc);
            case "update_before" -> UpdateFlowScenario.before(mc);
            case "update_after" -> UpdateFlowScenario.after(mc);
            case "update_after_mods" -> UpdateFlowScenario.afterUseMods(mc);
            case "pack_tour" -> PackTourScenario.build(mc);
            case "suite" -> suite(mc, System.getProperty("jes.autoshot.suite", "pack_tour,pack_tools"));
            default -> {
                Function<Minecraft, Director> extra = EXTRA.get(mode);
                if (extra == null) {
                    throw new IllegalArgumentException("Unknown autoshot mode " + mode);
                }
                yield extra.apply(mc);
            }
        };
    }

    /**
     * Several scenarios one after another in one game, so a big modpack only has to start once. Each
     * starts with nothing open, and one that fails is logged and left for the next.
     */
    private static Director suite(Minecraft mc, String modes) {
        Director d = new Director(mc, null);
        d.showCursor = false;
        for (String mode : modes.split(",")) {
            String name = mode.trim();
            d.then(Director.run(() -> mc.setScreen(null)))
                    .then(Director.pause(40))
                    .then(Director.play(name, () -> build(name, mc)));
        }
        return d;
    }

    private static JesScreen screen(Minecraft mc) {
        return mc.screen instanceof JesScreen s ? s : null;
    }

    private static boolean idle(Minecraft mc) {
        JesScreen s = screen(mc);
        return s != null && s.idle();
    }

    private static Supplier<int[]> at(Minecraft mc, Function<JesScreen, int[]> where) {
        return () -> where.apply(screen(mc));
    }

    private static Supplier<int[]> offset(Supplier<int[]> base, int dx, int dy) {
        return () -> {
            int[] p = base.get();
            return new int[]{p[0] + dx, p[1] + dy};
        };
    }

    private static void erase(Director d, int count) {
        for (int i = 0; i < count; i++) {
            d.key(InputConstants.KEY_BACKSPACE);
        }
    }

    /** A tour of every part of the screen, one screenshot per state, for reviewing the UI. */
    private static Director review(Minecraft mc) {
        JesScreen.startOn(Ids.parse("village_plains"));
        Director d = new Director(mc, null);
        Supplier<int[]> viewport = at(mc, JesScreen::viewportCentre);
        d.then(pressBrowserKey())
                .then(until(() -> screen(mc) != null, 40))
                .then(run(() -> screen(mc).setSpin(false)))
                .then(until(() -> idle(mc), 400))
                .then(moveTo(offset(viewport, 150, 110), 10))
                .then(pause(10))
                .then(shoot("r01_open_village"))
                .then(moveTo(at(mc, s -> s.button("locate")), 10))
                .then(click())
                .then(pause(20))
                .then(shoot("r01b_locate"))
                .then(moveTo(viewport, 10))
                .then(pause(6))
                .then(shoot("r02_hover_block"))
                .then(moveTo(at(mc, s -> s.tab("entities")), 10))
                .then(click())
                .then(pause(4))
                .then(shoot("r03_mobs_tab"))
                .then(moveTo(at(mc, JesScreen::searchBox), 10))
                .then(click())
                .then(type("@mine ruin", 1))
                .then(pause(4))
                .then(shoot("r04_search_mod"))
                .then(run(() -> erase(d, 12)))
                .then(until(FoundIn::ready, 2400))
                .then(type("$diamond", 1))
                .then(pause(4))
                .then(shoot("r05_search_item"))
                .then(run(() -> erase(d, 12)))
                .then(pause(2))
                .then(moveTo(() -> screen(mc).structureRow(Ids.parse("desert_pyramid")).orElse(new int[]{0, 0}), 12))
                .then(click())
                .then(until(() -> idle(mc), 400))
                .then(moveTo(offset(viewport, 150, 110), 8))
                .then(pause(6))
                .then(shoot("r06_desert_pyramid"))
                .then(moveTo(() -> screen(mc).marker(CHEST).orElse(screen(mc).viewportCentre()), 10))
                .then(pause(6))
                .then(shoot("r07_marker_hover"))
                .then(click())
                .then(until(() -> screen(mc).containerOpen(), 100))
                .then(moveTo(() -> screen(mc).chestSlotWithItem().orElse(screen(mc).viewportCentre()), 10))
                .then(pause(4))
                .then(shoot("r08_chest_item"))
                .then(click())
                .then(pause(6))
                .then(shoot("r09_found_in"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(moveTo(at(mc, s -> s.tab("loot")), 10))
                .then(click())
                .then(until(() -> screen(mc).oddsRow(Items.DIAMOND).isPresent(), 100))
                .then(pause(4))
                .then(shoot("r10_loot_tab"))
                .then(moveTo(() -> screen(mc).oddsRow(Items.DIAMOND).orElse(new int[]{0, 0}), 10))
                .then(pause(4))
                .then(shoot("r11_odds_hover"))
                .then(moveTo(at(mc, s -> s.tab("blocks")), 10))
                .then(click())
                .then(moveTo(offset(at(mc, s -> s.tab("overview")), 0, 40), 8))
                .then(pause(4))
                .then(shoot("r12_blocks_tab"))
                .then(moveTo(at(mc, s -> s.tab("overview")), 8))
                .then(click())
                .then(pause(4))
                .then(shoot("r13_info_tab"))
                .then(moveTo(at(mc, s -> s.sliderAt(1f)), 10))
                .then(dragTo(at(mc, s -> s.sliderAt(0.3f)), 12, InputConstants.MOUSE_BUTTON_LEFT))
                .then(pause(8))
                .then(shoot("r14_layers"))
                .then(dragTo(at(mc, s -> s.sliderAt(1f)), 6, InputConstants.MOUSE_BUTTON_LEFT))
                .then(moveTo(viewport, 8))
                .then(wheel(3))
                .then(dragBy(-60, 20, 12))
                .then(pause(6))
                .then(shoot("r15_zoomed"))
                .then(moveTo(() -> screen(mc).structureRow(Ids.parse("ancient_city")).orElse(new int[]{0, 0}), 10))
                .then(click())
                .then(pause(3))
                .then(shoot("r16_generating"))
                .then(until(() -> idle(mc), 600))
                .then(moveTo(offset(viewport, 150, 110), 8))
                .then(pause(6))
                .then(shoot("r17_ancient_city"))
                .then(run(() -> screen(mc).showDetails(true)))
                .then(pause(4))
                .then(shoot("r17b_details"))
                .then(run(() -> screen(mc).showDetails(false)))
                .then(moveTo(at(mc, s -> s.button("maximise")), 10))
                .then(click())
                .then(pause(8))
                .then(moveTo(offset(viewport, 60, 60), 8))
                .then(pause(4))
                .then(shoot("r18_maximised"));
        return d;
    }

    /** Opening the browser and watching a mansion generate and build up. */
    private static Director open(Minecraft mc) {
        // Open and close it once first, so the recording isn't of a cold server.
        JesScreen.startOn(Ids.parse("igloo"));
        Director d = new Director(mc, null);
        Supplier<int[]> viewport = at(mc, JesScreen::viewportCentre);
        d.then(pressBrowserKey())
                .then(until(() -> screen(mc) != null, 40))
                .then(until(() -> idle(mc), 400))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> screen(mc) == null, 40))
                .then(run(() -> JesScreen.startOn(Ids.parse("mansion"))))
                .then(pause(20))
                .then(record("open"))
                .then(pause(12))
                .then(pressBrowserKey())
                .then(until(() -> screen(mc) != null, 40))
                .then(run(() -> screen(mc).setSpin(false)))
                .then(moveTo(offset(viewport, 120, -120), 24))
                .then(until(() -> idle(mc), 400))
                .then(pause(12))
                .then(dragBy(-150, 18, 60))
                .then(moveTo(offset(viewport, 170, 150), 16))
                .then(pause(10));
        return d;
    }

    /**
     * Ctrl-click locate on a plains village, in a world where structures generate (superflat makes
     * villages), then a screenshot and a log line of where the player ended up.
     */
    private static Director teleport(Minecraft mc) {
        JesScreen.startOn(Ids.parse("village_plains"));
        Director d = new Director(mc, null);
        d.then(pressBrowserKey())
                .then(until(() -> screen(mc) != null, 40))
                .then(until(() -> idle(mc), 400))
                .then(run(() -> JustEnoughStructures.LOGGER.info("Autoshot teleport from {}", mc.player.blockPosition())))
                .then(run(() -> screen(mc).locateAndTeleport()))
                .then(until(() -> screen(mc) == null, 2400))
                .then(pause(60))
                .then(run(() -> {
                    BlockPos at = mc.player.blockPosition();
                    JustEnoughStructures.LOGGER.info("Autoshot teleport to {} standing on {}, feet in {}, head in {}", at,
                            mc.level.getBlockState(at.below()), mc.level.getBlockState(at), mc.level.getBlockState(at.above()));
                }))
                .then(shoot("t01_arrived"));
        return d;
    }

    /**
     * JEI with the plugin: the inventory with JEI's item list, then the structures diamonds are found
     * in, once the loot index has arrived. With every dev mod installed the index takes a few minutes.
     */
    /** A few seconds of a desert pyramid turning on its own, to check the loot markers keep up with it. */
    private static Director spin(Minecraft mc) {
        JesScreen.startOn(Ids.parse("desert_pyramid"));
        Director d = new Director(mc, null);
        Supplier<int[]> viewport = at(mc, JesScreen::viewportCentre);
        d.then(pressBrowserKey())
                .then(until(() -> screen(mc) != null, 40))
                .then(until(() -> idle(mc), 400))
                .then(moveTo(offset(viewport, 150, 130), 2))
                .then(record("spin"))
                .then(pause(100));
        return d;
    }

    /** About ten seconds showing off the main features on a desert pyramid. */
    private static Director showcase(Minecraft mc) {
        JesScreen.startOn(Ids.parse("desert_pyramid"));
        Director d = new Director(mc, null);
        Supplier<int[]> viewport = at(mc, JesScreen::viewportCentre);
        // Get everything loaded before recording, so it opens on a finished preview.
        d.then(pressBrowserKey())
                .then(until(() -> screen(mc) != null, 40))
                .then(run(() -> screen(mc).setSpin(false)))
                .then(until(() -> idle(mc), 400))
                .then(until(FoundIn::ready, 2400))
                .then(moveTo(offset(viewport, 90, 70), 2))
                .then(pause(10))
                .then(record("showcase"))
                .then(pause(4))
                .then(moveTo(viewport, 14))
                .then(dragBy(110, -10, 24))
                .then(moveTo(at(mc, s -> s.sliderAt(1f)), 14))
                .then(dragTo(at(mc, s -> s.sliderAt(0.5f)), 18, InputConstants.MOUSE_BUTTON_LEFT))
                .then(pause(6))
                .then(moveTo(() -> screen(mc).marker(CHEST).orElse(screen(mc).viewportCentre()), 14))
                .then(pause(4))
                .then(click())
                .then(pause(12))
                .then(moveTo(at(mc, s -> s.button("reroll_loot")), 12))
                .then(click())
                .then(pause(12))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(moveTo(at(mc, s -> s.tab("loot")), 14))
                .then(click())
                .then(pause(6))
                .then(moveTo(() -> screen(mc).oddsRow(Items.DIAMOND).orElse(new int[]{0, 0}), 16))
                .then(pause(4))
                .then(click())
                .then(pause(14));
        return d;
    }
}
