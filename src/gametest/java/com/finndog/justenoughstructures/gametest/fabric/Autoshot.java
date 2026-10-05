package com.finndog.justenoughstructures.gametest.fabric;

import com.finndog.justenoughstructures.gametest.Gallery;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;

/**
 * Dev-only screenshot run: makes a superflat world, then either opens the browser on each structure
 * in turn and saves a screenshot of each (mode "gallery", which {@link Gallery} runs the same on
 * every loader), or plays one of the {@link Scenarios} ("review", "open", "showcase"). Writes a
 * summary and quits. Enabled with {@code -Djes.autoshot=<folder>}; see the runAutoshot task.
 */
public final class Autoshot implements ClientModInitializer {
    private enum Step { START, WAIT_WORLD, SCRIPT, DONE }

    private Step step = Step.START;
    private int stepTicks;
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
        hide = Boolean.parseBoolean(System.getProperty("jes.autoshot.hidden", "true"));
        mode = System.getProperty("jes.autoshot.mode", "gallery");
        out = Path.of(folder);
        if (mode.equals("gallery")) {
            ClientTickEvents.END_CLIENT_TICK.register(Gallery.fromProperties()::tick);
            return;
        }
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
        stepTicks++;
        switch (step) {
            case START -> {
                if (Gallery.ready(mc)) {
                    Gallery.startWorld(mc, hide, "teleport".equals(mode));
                    go(Step.WAIT_WORLD);
                }
            }
            case WAIT_WORLD -> {
                if (mc.player != null && mc.level != null && mc.screen == null && stepTicks > 40) {
                    director = Scenarios.build(mode, mc);
                    go(Step.SCRIPT);
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
}
