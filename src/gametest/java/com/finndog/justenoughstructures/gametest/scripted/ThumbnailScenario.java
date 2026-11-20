package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.Thumbnails;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * Pictures in the structure list after a GUI scale or resource pack change ({@code thumbnails}).
 * The igloo's picture is drawn again at the new size when the GUI scale goes from 2 to 3, the one for
 * scale 2 stays saved beside it and comes back from disk, without being drawn again, when the scale
 * goes back. Until a structure's picture is drawn again, the old one stays in the list rather than
 * none. Turning on a resource pack draws it again and saves that one beside the others too. The pack
 * is Programmer Art, which every version has. Both are set back at the end.
 */
final class ThumbnailScenario {
    private static final ResourceLocation IGLOO = Ids.parse("igloo");
    private static final String PACK = "programmer_art";
    private static final int FIRST_SCALE = 2;
    private static final int SECOND_SCALE = 3;

    private ThumbnailScenario() {
    }

    static Director build(Minecraft mc) {
        if (!mc.getResourcePackRepository().getAvailableIds().contains(PACK)) {
            throw new UnsupportedOperationException("needs the game's Programmer Art pack");
        }
        Path folder = Thumbnails.savedIn(IGLOO);
        // Pictures saved by an earlier run would be found instead of drawn.
        delete(folder);
        Map<Path, Long> firstSaved = new HashMap<>();
        Object[] before = {null};
        Set<ResourceLocation> pictured = new HashSet<>();
        CompletableFuture<?>[] reload = {null};
        JesScreen.startOn(IGLOO);
        Director d = new Director(mc);
        d.then(run(() -> setScale(mc, FIRST_SCALE)))
                .then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 600))
                .then(until(() -> Thumbnails.widthInMemory(IGLOO) > 0 && saved(folder).size() == 1, 600))
                .then(check(() -> Thumbnails.widthInMemory(IGLOO) == Thumbnails.widthNow(),
                        () -> "the igloo's picture is " + Thumbnails.widthInMemory(IGLOO) + " pixels wide, not " + Thumbnails.widthNow()))
                .then(check(() -> saved(folder).size() == 1, () -> "the igloo's picture wasn't saved: " + saved(folder)))
                .then(run(() -> {
                    firstSaved.putAll(saved(folder));
                    pictured.addAll(Thumbnails.inMemory());
                }))
                .then(shoot("t01_scale_2"))
                .then(run(() -> setScale(mc, SECOND_SCALE)))
                .then(pause(2))
                .then(check(() -> Thumbnails.inMemory().containsAll(pictured),
                        () -> "after the GUI scale changed, the list lost the pictures of " + missing(pictured)))
                .then(until(() -> browser(mc).idle() && Thumbnails.widthInMemory(IGLOO) == Thumbnails.widthNow(), 600))
                .then(pause(10))
                .then(shoot("t02_scale_3"))
                .then(check(() -> Thumbnails.widthInMemory(IGLOO) == Thumbnails.widthNow(),
                        () -> "after the GUI scale went from " + FIRST_SCALE + " to " + SECOND_SCALE + ", the igloo's picture is "
                                + Thumbnails.widthInMemory(IGLOO) + " pixels wide, not " + Thumbnails.widthNow()))
                .then(until(() -> saved(folder).size() == 2, 200))
                .then(check(() -> saved(folder).size() == 2,
                        () -> "the picture for GUI scale " + FIRST_SCALE + " wasn't kept beside the one for " + SECOND_SCALE + ": " + saved(folder)))
                .then(run(() -> setScale(mc, FIRST_SCALE)))
                .then(until(() -> Thumbnails.widthInMemory(IGLOO) == Thumbnails.widthNow(), 200))
                // A picture drawn again would be saved again, over the first one.
                .then(pause(40))
                .then(check(() -> Thumbnails.widthInMemory(IGLOO) == Thumbnails.widthNow(),
                        () -> "back at GUI scale " + FIRST_SCALE + ", the igloo's picture is " + Thumbnails.widthInMemory(IGLOO)
                                + " pixels wide, not " + Thumbnails.widthNow()))
                .then(check(() -> saved(folder).entrySet().containsAll(firstSaved.entrySet()),
                        () -> "back at GUI scale " + FIRST_SCALE + ", the saved picture was drawn again, not read: " + firstSaved + " then " + saved(folder)))
                .then(run(() -> {
                    before[0] = Thumbnails.inMemory(IGLOO);
                    reload[0] = setPack(mc, true);
                }))
                .then(until(() -> reload[0].isDone() && mc.getOverlay() == null, 1200))
                .then(until(() -> browser(mc) != null && browser(mc).idle() && saved(folder).size() == 3, 600))
                .then(pause(10))
                .then(shoot("t03_programmer_art"))
                .then(check(() -> Thumbnails.inMemory(IGLOO) != null && Thumbnails.inMemory(IGLOO) != before[0],
                        () -> "turning on Programmer Art didn't draw the igloo's picture again"))
                .then(check(() -> saved(folder).size() == 3,
                        () -> "the picture with Programmer Art wasn't saved beside the other two: " + saved(folder)))
                .then(run(() -> reload[0] = setPack(mc, false)))
                .then(until(() -> reload[0].isDone() && mc.getOverlay() == null, 1200))
                .then(until(() -> browser(mc) != null && browser(mc).idle() && Thumbnails.inMemory(IGLOO) != null, 600))
                .then(pause(10))
                .then(shoot("t04_packs_back"))
                .then(check(() -> saved(folder).size() == 3,
                        () -> "with Programmer Art off again, the saved pictures are " + saved(folder)));
        return d;
    }

    private static Set<ResourceLocation> missing(Set<ResourceLocation> pictured) {
        Set<ResourceLocation> out = new HashSet<>(pictured);
        out.removeAll(Thumbnails.inMemory());
        return out;
    }

    private static void setScale(Minecraft mc, int scale) {
        mc.options.guiScale().set(scale);
        //? if >=26.1 {
        /*mc.resizeGui();
        *///?} else {
        mc.resizeDisplay();
        //?}
    }

    private static CompletableFuture<?> setPack(Minecraft mc, boolean on) {
        PackRepository packs = mc.getResourcePackRepository();
        List<String> ids = new ArrayList<>(packs.getSelectedIds());
        ids.remove(PACK);
        if (on) {
            ids.add(PACK);
        }
        packs.setSelected(ids);
        return mc.reloadResourcePacks();
    }

    /** The pictures saved for the structure, each with when it was written. */
    private static Map<Path, Long> saved(Path folder) {
        Map<Path, Long> out = new HashMap<>();
        if (!Files.isDirectory(folder)) {
            return out;
        }
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".png")).toList()) {
                out.put(file.getFileName(), Files.getLastModifiedTime(file).toMillis());
            }
        } catch (IOException e) {
            throw new IllegalStateException("couldn't list " + folder, e);
        }
        return out;
    }

    private static void delete(Path folder) {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        } catch (IOException e) {
            throw new IllegalStateException("couldn't clear " + folder, e);
        }
    }

    private static Director.Action check(BooleanSupplier holds, Supplier<String> failure) {
        return run(() -> {
            if (!holds.getAsBoolean()) {
                throw new IllegalStateException(failure.get());
            }
        });
    }
}
