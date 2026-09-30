package com.finndog.justenoughstructures.gametest.fabric;

import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;

/**
 * Dev-only screenshot run: makes a superflat world, then either opens the browser on each structure
 * in turn and saves a screenshot of each (mode "gallery"), or plays one of the {@link Scenarios}
 * ("review", "open", "showcase"). Writes a summary and quits. Enabled with
 * {@code -Djes.autoshot=<folder>}; see the runAutoshot task.
 */
public final class Autoshot implements ClientModInitializer {
    private static final List<String> DEFAULT_STRUCTURES = List.of(
            "minecraft:village_plains", "minecraft:desert_pyramid", "minecraft:jungle_pyramid", "minecraft:shipwreck",
            "minecraft:igloo", "minecraft:pillager_outpost", "minecraft:swamp_hut", "minecraft:ocean_ruin_warm",
            "minecraft:ruined_portal", "minecraft:bastion_remnant", "minecraft:fortress", "minecraft:end_city",
            "minecraft:trail_ruins", "minecraft:ancient_city", "minecraft:mansion", "minecraft:stronghold");
    private static final Set<String> OPEN_CHEST = Set.of("minecraft:desert_pyramid", "minecraft:shipwreck", "minecraft:pillager_outpost");
    private static final int TIMEOUT_TICKS = 20 * 90;

    private enum Step { START, WAIT_WORLD, WAIT_CATALOG, WAIT_IDLE, SETTLE, WAIT_CHEST, WAIT_LOOT_TAB, SCRIPT, DONE }

    private Step step = Step.START;
    private int ticks;
    private int stepTicks;
    private int index;
    private JesScreen screen;
    private List<ResourceLocation> structures;
    private Path out;
    private boolean hide;
    private String mode;
    private Director director;
    private final JsonArray summary = new JsonArray();

    @Override
    public void onInitializeClient() {
        String folder = System.getProperty("jes.autoshot");
        if (folder == null || folder.isBlank()) {
            return;
        }
        String list = System.getProperty("jes.autoshot.structures", "");
        structures = (list.isBlank() ? DEFAULT_STRUCTURES : Arrays.asList(list.split(","))).stream()
                .map(String::trim).filter(s -> !s.isEmpty()).map(ResourceLocation::new).toList();
        hide = Boolean.parseBoolean(System.getProperty("jes.autoshot.hidden", "true"));
        mode = System.getProperty("jes.autoshot.mode", "gallery");
        out = Path.of(folder);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        // The scripted modes draw a cursor and count frames after every screen and the HUD.
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) ->
                ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, partial) -> {
                    if (director != null) {
                        director.onRender(graphics);
                    }
                }));
        HudRenderCallback.EVENT.register((graphics, partial) -> {
            if (director != null && Minecraft.getInstance().screen == null) {
                director.onRender(graphics);
            }
        });
    }

    private void tick(Minecraft mc) {
        ticks++;
        stepTicks++;
        switch (step) {
            case START -> {
                // Wait for the loading overlay too: creating the world mid-reload renders chunks before shaders exist.
                if (mc.getOverlay() == null && (mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen)) {
                    if (hide) {
                        GLFW.glfwHideWindow(mc.getWindow().getWindow());
                    }
                    mc.options.guiScale().set(Integer.getInteger("jes.autoshot.gui", 2));
                    mc.resizeDisplay();
                    createWorld(mc, "teleport".equals(mode));
                    go(Step.WAIT_WORLD);
                }
            }
            case WAIT_WORLD -> {
                if (mc.player != null && mc.level != null && mc.screen == null && stepTicks > 40 && !mode.equals("gallery")) {
                    director = Scenarios.build(mode, mc);
                    go(Step.SCRIPT);
                } else if (mc.player != null && mc.level != null && mc.screen == null && stepTicks > 40) {
                    screen = new JesScreen();
                    mc.setScreen(screen);
                    go(Step.WAIT_CATALOG);
                }
            }
            case WAIT_CATALOG -> {
                keepScreen(mc);
                if (index >= structures.size()) {
                    finish(mc);
                } else if (screen.select(structures.get(index))) {
                    go(Step.WAIT_IDLE);
                } else if (stepTicks > TIMEOUT_TICKS) {
                    record(structures.get(index), null, "never listed");
                    index++;
                    go(Step.WAIT_CATALOG);
                }
            }
            case WAIT_IDLE -> {
                keepScreen(mc);
                if (screen.idle()) {
                    go(Step.SETTLE);
                } else if (stepTicks > TIMEOUT_TICKS) {
                    record(structures.get(index), null, "timed out");
                    index++;
                    go(Step.WAIT_CATALOG);
                }
            }
            case SETTLE -> {
                keepScreen(mc);
                if (stepTicks >= 10) {
                    ResourceLocation id = structures.get(index);
                    shoot(mc, name(id));
                    CaptureResult result = screen.result();
                    record(id, result, null);
                    if (OPEN_CHEST.contains(id.toString()) && result.succeeded()) {
                        StructureSnapshot.Container first = result.snapshot().containers().stream()
                                .filter(c -> c.lootTable() != null).findFirst().orElse(null);
                        if (first != null) {
                            screen.openContainer(first);
                            go(Step.WAIT_CHEST);
                            return;
                        }
                    }
                    index++;
                    go(Step.WAIT_CATALOG);
                }
            }
            case WAIT_CHEST -> {
                keepScreen(mc);
                if (stepTicks >= 30) {
                    shoot(mc, name(structures.get(index)) + "_chest");
                    screen.closeContainer();
                    screen.showLootTab();
                    go(Step.WAIT_LOOT_TAB);
                }
            }
            case WAIT_LOOT_TAB -> {
                keepScreen(mc);
                if (stepTicks >= 30) {
                    shoot(mc, name(structures.get(index)) + "_loot");
                    screen.showInfoTab();
                    index++;
                    go(Step.WAIT_CATALOG);
                }
            }
            case SCRIPT -> {
                director.tick();
                if (director.done() || stepTicks > 20 * 600) {
                    JsonObject row = new JsonObject();
                    row.addProperty("mode", mode);
                    row.addProperty("finished", director.done());
                    row.addProperty("frames", director.recordedFrames());
                    summary.add(row);
                    finish(mc);
                }
            }
            case DONE -> {
            }
        }
    }

    private void go(Step next) {
        step = next;
        stepTicks = 0;
    }

    private void keepScreen(Minecraft mc) {
        if (mc.screen != screen) {
            mc.setScreen(screen);
        }
    }

    private static String name(ResourceLocation id) {
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }

    private void shoot(Minecraft mc, String file) {
        Screenshot.grab(mc.gameDirectory, file + ".png", mc.getMainRenderTarget(), message -> {
        });
    }

    private void record(ResourceLocation id, CaptureResult result, String problem) {
        JsonObject row = new JsonObject();
        row.addProperty("structure", id.toString());
        if (result == null) {
            row.addProperty("ok", false);
            row.addProperty("error", problem);
        } else {
            row.addProperty("ok", result.succeeded());
            row.addProperty("millis", result.millis());
            if (result.succeeded()) {
                StructureSnapshot s = result.snapshot();
                row.addProperty("blocks", s.blockCount());
                row.addProperty("size", s.size().getX() + "x" + s.size().getY() + "x" + s.size().getZ());
                row.addProperty("containers", s.containers().size());
                row.addProperty("entities", s.entities().size());
                row.addProperty("terrain", s.terrain().name());
            } else {
                row.addProperty("error", result.error());
            }
        }
        summary.add(row);
    }

    private void finish(Minecraft mc) {
        go(Step.DONE);
        try {
            Files.createDirectories(out);
            Files.writeString(out.resolve("summary.json"), new GsonBuilder().setPrettyPrinting().create().toJson(summary), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't write the autoshot summary", e);
        }
        mc.stop();
    }

    /** A superflat world; with {@code structures} it gets villages, for trying locate and teleport. */
    private static void createWorld(Minecraft mc, boolean structures) {
        // Stop the sun moving, so the world behind the screen stays still in recordings.
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        LevelSettings settings = new LevelSettings("JES autoshot", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                rules, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("jes-autoshot", settings, new WorldOptions(0L, structures, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }
}
