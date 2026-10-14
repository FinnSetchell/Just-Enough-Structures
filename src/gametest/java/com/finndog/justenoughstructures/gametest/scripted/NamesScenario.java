package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.type;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.orZero;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.tools;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.finndog.justenoughstructures.client.screen.StructureNames;
import com.finndog.justenoughstructures.overrides.StructureNameFiles;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Naming a structure in Pack tools: the name typed and saved, the structure list the server sends
 * then carrying it, and on this game the resource pack that takes it to mods that look a name up
 * themselves. The name is taken away again at the end.
 */
final class NamesScenario {
    private static final ResourceLocation IGLOO = Ids.parse("igloo");
    private static final String NAME = "Snow House";

    private NamesScenario() {
    }

    static Director build(Minecraft mc) {
        CompletableFuture<?>[] reload = {null};
        String key = StructureNameFiles.key(IGLOO);
        JesScreen.startOn(IGLOO);
        Director d = new Director(mc);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 600))
                .then(moveTo(() -> browser(mc).button("tools"), 10))
                .then(click())
                .then(until(() -> tools(mc) != null && tools(mc).loaded(), 200))
                .then(run(() -> tools(mc).pickFor("structures", "minecraft:igloo")))
                .then(pause(30))
                .then(moveTo(() -> orZero(tools(mc).nameBox()), 10))
                .then(click())
                .then(type(NAME, 1))
                .then(pause(10))
                .then(shoot("m01_name_typed"))
                .then(moveTo(() -> orZero(tools(mc).buttonAt(Component.translatable("screen.justenoughstructures.tools.save").getString())), 10))
                .then(click())
                .then(until(() -> NAME.equals(StructureNames.structure(IGLOO)), 400))
                .then(run(() -> check(NAME.equals(StructureNames.structure(IGLOO)), "the saved name isn't shown: " + StructureNames.structure(IGLOO))))
                .then(pause(20))
                .then(shoot("m02_name_saved"))
                // Forgotten here, the name has to come back with the structure list the server sends.
                .then(run(() -> {
                    StructureNames.useGiven(null);
                    ClientRequests.forgetCatalog();
                }))
                .then(until(() -> {
                    ClientRequests.catalog();
                    return StructureNames.namesFromServer() && NAME.equals(StructureNames.structure(IGLOO));
                }, 400))
                .then(run(() -> check(NAME.equals(StructureNames.structure(IGLOO)), "the server's list didn't carry the name")))
                // On this game it's in a resource pack too, for mods that look the name up themselves.
                .then(run(() -> reload[0] = mc.reloadResourcePacks()))
                .then(until(() -> reload[0].isDone(), 1200))
                .then(run(() -> check(NAME.equals(I18n.get(key)), "the resource pack gives " + I18n.get(key))))
                // Taken away again, the mod's own name is back.
                .then(run(() -> ClientRequests.saveName(IGLOO, StructureNames.language(), "")))
                .then(until(() -> {
                    ClientRequests.catalog();
                    return !NAME.equals(StructureNames.structure(IGLOO));
                }, 400))
                .then(run(() -> reload[0] = mc.reloadResourcePacks()))
                .then(until(() -> reload[0].isDone(), 1200))
                .then(run(() -> check(!NAME.equals(I18n.get(key)) && !NAME.equals(StructureNames.structure(IGLOO)), "the name stayed")));
        return d;
    }

    private static void check(boolean ok, String problem) {
        if (!ok) {
            throw new IllegalStateException(problem);
        }
    }
}
