package com.finndog.justenoughstructures.gametest.forge;

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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Dev-only screenshot run on Forge, the gallery mode of the Fabric one: makes a superflat world,
 * opens the browser on each structure in turn and saves a screenshot of each, and of a chest and the
 * Loot tab for a few. Writes a summary and quits. Enabled with {@code -Djes.autoshot=<folder>}.
 */
final class ForgeAutoshot {
    private static final List<String> DEFAULT_STRUCTURES = List.of(
            "minecraft:village_plains", "minecraft:desert_pyramid", "minecraft:jungle_pyramid", "minecraft:shipwreck",
            "minecraft:igloo", "minecraft:pillager_outpost", "minecraft:swamp_hut", "minecraft:ocean_ruin_warm",
            "minecraft:ruined_portal", "minecraft:bastion_remnant", "minecraft:fortress", "minecraft:end_city",
            "minecraft:trail_ruins", "minecraft:ancient_city", "minecraft:mansion", "minecraft:stronghold");
    private static final Set<String> OPEN_CHEST = Set.of("minecraft:desert_pyramid", "minecraft:shipwreck", "minecraft:pillager_outpost");
    private static final int TIMEOUT_TICKS = 20 * 90;

    private enum Step { START, WAIT_WORLD, WAIT_CATALOG, WAIT_IDLE, SETTLE, WAIT_CHEST, WAIT_LOOT_TAB, DONE }

    private Step step = Step.START;
    private int stepTicks;
    private int index;
    private JesScreen screen;
    private final List<ResourceLocation> structures;
    private final Path out;
    private final boolean hide;
    private final JsonArray summary = new JsonArray();

    private ForgeAutoshot(Path out) {
        String list = System.getProperty("jes.autoshot.structures", "");
        structures = (list.isBlank() ? DEFAULT_STRUCTURES : Arrays.asList(list.split(","))).stream()
                .map(String::trim).filter(s -> !s.isEmpty()).map(ResourceLocation::new).toList();
        hide = Boolean.parseBoolean(System.getProperty("jes.autoshot.hidden", "true"));
        this.out = out;
    }

    static void install() {
        String folder = System.getProperty("jes.autoshot");
        if (folder == null || folder.isBlank()) {
            return;
        }
        ForgeAutoshot autoshot = new ForgeAutoshot(Path.of(folder));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                autoshot.tick(Minecraft.getInstance());
            }
        });
    }

    private void tick(Minecraft mc) {
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
                    createWorld(mc);
                    go(Step.WAIT_WORLD);
                }
            }
            case WAIT_WORLD -> {
                if (mc.player != null && mc.level != null && mc.screen == null && stepTicks > 40) {
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

    private static void shoot(Minecraft mc, String file) {
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

    private static void createWorld(Minecraft mc) {
        // Stop the sun moving, so the world behind the screen stays still between shots.
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        LevelSettings settings = new LevelSettings("JES autoshot", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                rules, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("jes-autoshot", settings, new WorldOptions(0L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }
}
