package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.screen.StructureNames;
import com.finndog.justenoughstructures.overrides.StructureNameFiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;

/**
 * A name saved in Pack tools beats another mod's name for the same structure. The game merges lang
 * files one namespace at a time, in hash map order, so pack order only counts within a namespace,
 * and Explorer's Compass names the igloo under its own namespace. The test mod names a made-up
 * structure under a namespace that comes after JES's at any map size, and the saved name must win.
 */
final class NamesContestedScenario {
    private static final ResourceLocation CONTESTED = Ids.of("jes_test", "contested");
    /** The test mod's lang file names the structure under this namespace too. */
    private static final String OTHER_NAMESPACE = "jes_names_after_10";
    private static final String OTHER_NAME = "Named by another mod";
    private static final String NAME = "Named in Pack tools";

    private NamesContestedScenario() {
    }

    static Director build(Minecraft mc) {
        CompletableFuture<?>[] reload = {null};
        String key = StructureNameFiles.key(CONTESTED);
        String language = StructureNames.language();
        Director d = new Director(mc);
        // A name left by a run that stopped part way would hide the other mod's.
        d.then(run(() -> StructureNameFiles.save(CONTESTED, language, "")))
                .then(run(() -> reload[0] = mc.reloadResourcePacks()))
                .then(until(() -> reload[0].isDone(), 1200))
                .then(run(() -> {
                    List<String> order = new ArrayList<>(mc.getResourceManager().getNamespaces());
                    int jes = order.indexOf(JustEnoughStructures.MOD_ID);
                    int other = order.indexOf(OTHER_NAMESPACE);
                    check(jes >= 0 && other > jes, "the game merges " + OTHER_NAMESPACE + "'s lang files before JES's here, so this can't show anything: " + order);
                    check(OTHER_NAME.equals(I18n.get(key)), "the other mod's name isn't there, the game gives " + I18n.get(key));
                }))
                .then(run(() -> {
                    StructureNameFiles.save(CONTESTED, language, NAME);
                    Map<String, String> saved = StructureNameFiles.all().getOrDefault(language, Map.of());
                    check(NAME.equals(saved.get(key)), "the name wasn't saved: " + saved.get(key));
                }))
                .then(run(() -> reload[0] = mc.reloadResourcePacks()))
                .then(until(() -> reload[0].isDone(), 1200))
                .then(run(() -> check(NAME.equals(I18n.get(key)), "the other mod's name won: the game gives " + I18n.get(key))))
                // Taken away again, the other mod's name is back.
                .then(run(() -> StructureNameFiles.save(CONTESTED, language, "")))
                .then(run(() -> reload[0] = mc.reloadResourcePacks()))
                .then(until(() -> reload[0].isDone(), 1200))
                .then(run(() -> check(OTHER_NAME.equals(I18n.get(key)), "the saved name stayed: the game gives " + I18n.get(key))));
        return d;
    }

    private static void check(boolean ok, String problem) {
        if (!ok) {
            throw new IllegalStateException(problem);
        }
    }
}
