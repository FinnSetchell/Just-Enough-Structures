package com.finndog.justenoughstructures.gametest.fabric;

import static com.finndog.justenoughstructures.gametest.fabric.Director.click;
import static com.finndog.justenoughstructures.gametest.fabric.Director.dragBy;
import static com.finndog.justenoughstructures.gametest.fabric.Director.dragTo;
import static com.finndog.justenoughstructures.gametest.fabric.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.fabric.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.fabric.Director.pause;
import static com.finndog.justenoughstructures.gametest.fabric.Director.record;
import static com.finndog.justenoughstructures.gametest.fabric.Director.run;
import static com.finndog.justenoughstructures.gametest.fabric.Director.wheel;
import static com.finndog.justenoughstructures.gametest.fabric.Director.shoot;
import static com.finndog.justenoughstructures.gametest.fabric.Director.type;
import static com.finndog.justenoughstructures.gametest.fabric.Director.until;

import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/** Scripts for the review screenshots and the showcase recordings. */
final class Scenarios {
    private static final String CHEST = "minecraft:chests/desert_pyramid";

    private Scenarios() {
    }

    static Director build(String mode, Minecraft mc) {
        return switch (mode) {
            case "review" -> review(mc);
            case "open" -> open(mc);
            case "showcase" -> showcase(mc);
            default -> throw new IllegalArgumentException("Unknown autoshot mode " + mode);
        };
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
            d.key(GLFW.GLFW_KEY_BACKSPACE);
        }
    }

    /** A tour of every part of the screen, one screenshot per state, for reviewing the UI. */
    private static Director review(Minecraft mc) {
        JesScreen.startOn(new ResourceLocation("village_plains"));
        Director d = new Director(mc, null);
        Supplier<int[]> viewport = at(mc, JesScreen::viewportCentre);
        d.then(pressKey(GLFW.GLFW_KEY_K))
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
                .then(moveTo(() -> screen(mc).structureRow(new ResourceLocation("desert_pyramid")).orElse(new int[]{0, 0}), 12))
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
                .then(pressKey(GLFW.GLFW_KEY_ESCAPE))
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
                .then(dragTo(at(mc, s -> s.sliderAt(0.3f)), 12, 0))
                .then(pause(8))
                .then(shoot("r14_layers"))
                .then(dragTo(at(mc, s -> s.sliderAt(1f)), 6, 0))
                .then(moveTo(viewport, 8))
                .then(wheel(3))
                .then(dragBy(-60, 20, 12))
                .then(pause(6))
                .then(shoot("r15_zoomed"))
                .then(moveTo(() -> screen(mc).structureRow(new ResourceLocation("ancient_city")).orElse(new int[]{0, 0}), 10))
                .then(click())
                .then(pause(3))
                .then(shoot("r16_generating"))
                .then(until(() -> idle(mc), 600))
                .then(moveTo(offset(viewport, 150, 110), 8))
                .then(pause(6))
                .then(shoot("r17_ancient_city"))
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
        JesScreen.startOn(new ResourceLocation("igloo"));
        Director d = new Director(mc, null);
        Supplier<int[]> viewport = at(mc, JesScreen::viewportCentre);
        d.then(pressKey(GLFW.GLFW_KEY_K))
                .then(until(() -> screen(mc) != null, 40))
                .then(until(() -> idle(mc), 400))
                .then(pressKey(GLFW.GLFW_KEY_ESCAPE))
                .then(until(() -> screen(mc) == null, 40))
                .then(run(() -> JesScreen.startOn(new ResourceLocation("mansion"))))
                .then(pause(20))
                .then(record("open"))
                .then(pause(12))
                .then(pressKey(GLFW.GLFW_KEY_K))
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

    /** About ten seconds showing off the main features on a desert pyramid. */
    private static Director showcase(Minecraft mc) {
        JesScreen.startOn(new ResourceLocation("desert_pyramid"));
        Director d = new Director(mc, null);
        Supplier<int[]> viewport = at(mc, JesScreen::viewportCentre);
        // Get everything loaded before recording, so it opens on a finished preview.
        d.then(pressKey(GLFW.GLFW_KEY_K))
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
                .then(dragTo(at(mc, s -> s.sliderAt(0.5f)), 18, 0))
                .then(pause(6))
                .then(moveTo(() -> screen(mc).marker(CHEST).orElse(screen(mc).viewportCentre()), 14))
                .then(pause(4))
                .then(click())
                .then(pause(12))
                .then(moveTo(at(mc, s -> s.button("reroll_loot")), 12))
                .then(click())
                .then(pause(12))
                .then(pressKey(GLFW.GLFW_KEY_ESCAPE))
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
