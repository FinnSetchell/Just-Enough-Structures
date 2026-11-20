package com.finndog.justenoughstructures.gametest.scripted;

import com.mojang.blaze3d.platform.InputConstants;
import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.catalog.Availability;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.ConfigScreens;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Other mods' settings, from the browser. Needs those mods, so only with the dev mods; each part
 * skips itself where the mod it opens has no settings screen.
 * <ul>
 * <li>{@code config}: the settings button on the mod's row. None for buried treasure, as no mod's
 * settings govern it. On a Moog's Voyager house it opens Moog's Structure Lib's settings, which list
 * that mod's structures, and Esc comes back to the browser on the same house. None for the desert
 * pyramid either, whose row says Minecraft, even where another mod replaces it. Not where Moog's
 * Structure Lib has no settings screen (Forge 26.x).</li>
 * <li>{@code config_link}: the Settings link on the Info tab of the desert pyramid, after the line
 * saying which mod replaces it, opens that mod's settings (Moog's Structure Lib's where its Replace
 * Vanilla setting did it), and Esc comes back. Where no mod with a
 * settings screen replaces it, it's skipped once the browser shows that (logged as skipped in a suite).</li>
 * <li>{@code config_owner}: first, on any loader, the loader's lookup finds Moog's Voyager Structures
 * as the mod whose jar holds its desert house. Then Towns and Towers: its structures are under
 * towns_and_towers, but the mod is t_and_t, so its settings are found by the mod whose jar holds the
 * structure. That part only where t_and_t has a settings screen, which Cristel Lib 3 and Cloth Config
 * give it from 1.21.1.</li>
 * <li>{@code config_msl}: with Moog's Structure Lib's Replace Vanilla setting on for Moog's Temples
 * Reimagined's desert temple, after a /reload, the desert pyramid shows as replaced by that mod, and
 * its Settings link opens Moog's Structure Lib's settings, where that setting is. The setting is turned
 * off again at the end, and when the game closes, so no later run sees it.</li>
 * </ul>
 */
final class ConfigScenario {
    private static final ResourceLocation HOUSE = Ids.of("mvs", "desert_house");
    private static final ResourceLocation PYRAMID = Ids.parse("desert_pyramid");
    private static final String TOWNS_MOD = "t_and_t";
    private static final ResourceLocation OUTPOST = Ids.of("towns_and_towers", "pillager_outpost_badlands");
    private static final String MSL = "moogs_structures";
    private static final String TEMPLES_MOD = "mtr";
    private static final String TEMPLES_PRESET = "replace_desert_temple";
    private static final int[] reloadsBefore = new int[1];

    private ConfigScenario() {
    }

    static Director build(Minecraft mc) {
        if (!ConfigScreens.canOpen("moogs_structures")) {
            throw new UnsupportedOperationException("needs Moog's Structure Lib with a settings screen");
        }
        JesScreen.startOn(Ids.parse("buried_treasure"));
        Director d = new Director(mc);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 400))
                .then(check(() -> browser(mc).configShown() == null, "buried treasure has a settings button"))
                .then(shoot("g01_treasure_no_settings"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> mc.screen == null, 40))
                .then(run(() -> JesScreen.startOn(HOUSE)))
                .then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 400))
                .then(check(() -> HOUSE.equals(browser(mc).selectedId()), "the browser didn't open on the desert house"))
                .then(check(() -> "moogs_structures".equals(browser(mc).configShown()),
                        "the desert house's settings button doesn't open Moog's Structure Lib"))
                .then(check(() -> onModRow(browser(mc)), "the settings button isn't on the mod's row, above the preview"))
                .then(moveTo(() -> browser(mc).button("config"), 10))
                .then(pause(10))
                .then(shoot("g02_settings_button"))
                .then(click())
                .then(until(() -> mc.screen != null && browser(mc) == null, 40))
                .then(check(() -> mc.screen != null && browser(mc) == null, "clicking the settings button opened nothing"))
                .then(pause(10))
                .then(shoot("g03_msl_settings"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 400))
                .then(check(() -> browser(mc) != null && HOUSE.equals(browser(mc).selectedId()),
                        "Esc didn't come back to the browser on the desert house"))
                .then(shoot("g04_back_in_browser"))
                // A vanilla structure, here one another mod replaces: its row says Minecraft, which has no settings.
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> mc.screen == null, 40))
                .then(run(() -> JesScreen.startOn(PYRAMID)))
                .then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 400))
                .then(check(() -> PYRAMID.equals(browser(mc).selectedId()), "the browser didn't open on the desert pyramid"))
                .then(check(() -> browser(mc).configShown() == null,
                        "the desert pyramid's mod row has a settings button, though Minecraft has no settings"))
                .then(shoot("g05_pyramid_no_settings"));
        return d;
    }

    static Director link(Minecraft mc) {
        if (ConfigScreens.get() == null) {
            throw new UnsupportedOperationException("needs a loader that opens other mods' settings (Mod Menu on Fabric)");
        }
        JesScreen.startOn(PYRAMID);
        String[] expected = new String[1];
        Director d = new Director(mc);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 400))
                .then(check(() -> PYRAMID.equals(browser(mc).selectedId()), "the browser didn't open on the desert pyramid"))
                .then(run(() -> {
                    expected[0] = linkTarget(browser(mc).selectedAvailability());
                    if (expected[0] == null || !ConfigScreens.canOpen(expected[0])) {
                        throw new UnsupportedOperationException("needs a mod with a settings screen that replaces the desert pyramid or turns it off");
                    }
                    browser(mc).showInfoTab();
                }))
                .then(until(() -> browser(mc).settingsLink() != null, 40))
                .then(check(() -> browser(mc).settingsLink() != null, "the desert pyramid's Info tab has no Settings link for the mod that replaces it"))
                .then(check(() -> expected[0].equals(browser(mc).settingsLinkMod()),
                        "the desert pyramid's Settings link doesn't open the settings that replace it"))
                .then(moveTo(() -> browser(mc).settingsLink(), 10))
                .then(pause(10))
                .then(shoot("l01_settings_link"))
                .then(click())
                .then(until(() -> mc.screen != null && browser(mc) == null, 40))
                .then(check(() -> mc.screen != null && browser(mc) == null, "clicking the Settings link opened nothing"))
                .then(pause(10))
                .then(shoot("l02_replacing_mod_settings"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 400))
                .then(check(() -> browser(mc) != null && PYRAMID.equals(browser(mc).selectedId()),
                        "Esc didn't come back to the browser on the desert pyramid"))
                .then(shoot("l03_back_in_browser"));
        return d;
    }

    static Director owner(Minecraft mc) {
        // The loader's own lookup, on every loader: the jar that holds a Moog's Voyager house is that mod's.
        ConfigScreens screens = ConfigScreens.get();
        if (screens == null) {
            throw new UnsupportedOperationException("needs a loader that opens other mods' settings (Mod Menu on Fabric)");
        }
        String houseOwner = screens.modWithFile("data/" + HOUSE.getNamespace() + "/worldgen/structure/" + HOUSE.getPath() + ".json");
        if (!HOUSE.getNamespace().equals(houseOwner)) {
            throw new IllegalStateException("the desert house's file was found in " + houseOwner + ", not in Moog's Voyager Structures");
        }
        if (!ConfigScreens.canOpen(TOWNS_MOD)) {
            throw new UnsupportedOperationException("needs Towns and Towers with a settings screen (Cristel Lib 3 and Cloth Config)");
        }
        JesScreen.startOn(OUTPOST);
        Director d = new Director(mc);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 400))
                .then(check(() -> OUTPOST.equals(browser(mc).selectedId()), "the browser didn't open on Towns and Towers' badlands outpost"))
                .then(check(() -> TOWNS_MOD.equals(browser(mc).configShown()),
                        "the badlands outpost's settings button doesn't open Towns and Towers' settings"))
                .then(moveTo(() -> browser(mc).button("config"), 10))
                .then(pause(10))
                .then(shoot("o01_towns_settings_button"))
                .then(click())
                .then(until(() -> mc.screen != null && browser(mc) == null, 40))
                .then(check(() -> mc.screen != null && browser(mc) == null, "clicking the settings button opened nothing"))
                .then(pause(10))
                .then(shoot("o02_towns_settings"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 400))
                .then(check(() -> browser(mc) != null && OUTPOST.equals(browser(mc).selectedId()),
                        "Esc didn't come back to the browser on the badlands outpost"));
        return d;
    }

    /**
     * The mod whose settings should turn the structure back on: Moog's Structure Lib's where its
     * Replace Vanilla setting put another structure in its place, otherwise the mod that turned it off
     * or replaced it. Null where no mod did.
     */
    private static String linkTarget(Availability availability) {
        if (availability == null || availability.by() == null) {
            return null;
        }
        return switch (availability.reason()) {
            case REPLACED -> StructureCatalog.replacedByMsl(PYRAMID) ? "moogs_structures" : availability.by();
            case TURNED_OFF -> availability.by();
            default -> null;
        };
    }

    static Director msl(Minecraft mc) {
        ConfigScreens screens = ConfigScreens.get();
        if (screens == null || !ConfigScreens.canOpen(MSL)) {
            throw new UnsupportedOperationException("needs Moog's Structure Lib with a settings screen");
        }
        if (!TEMPLES_MOD.equals(screens.modWithFile("data/" + TEMPLES_MOD + "/" + MSL + "/replace_vanilla.json"))) {
            throw new UnsupportedOperationException("needs Moog's Temples Reimagined, whose desert temple Moog's Structure Lib can put in the desert pyramid's place");
        }
        Method setPreset = mslSetter();
        // Left on, the setting would show in every later run of this dev game.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> setTemplesPreset(setPreset, false)));
        JesScreen.startOn(PYRAMID);
        Director d = new Director(mc);
        d.then(run(() -> setTemplesPreset(setPreset, true)))
                .then(run(() -> reload(mc)))
                .then(until(() -> ClientRequests.reloads() > reloadsBefore[0], 1200))
                .then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 400))
                .then(check(() -> PYRAMID.equals(browser(mc).selectedId()), "the browser didn't open on the desert pyramid"))
                .then(check(() -> {
                    Availability availability = browser(mc).selectedAvailability();
                    return availability != null && availability.reason() == Availability.Reason.REPLACED && TEMPLES_MOD.equals(availability.by());
                }, "with Moog's Structure Lib's Replace Vanilla setting on, the desert pyramid isn't shown as replaced by Moog's Temples Reimagined"))
                .then(run(() -> browser(mc).showInfoTab()))
                .then(until(() -> browser(mc).settingsLink() != null, 40))
                .then(check(() -> MSL.equals(browser(mc).settingsLinkMod()),
                        "the Settings link for a replacement Moog's Structure Lib makes doesn't open Moog's Structure Lib's settings"))
                .then(moveTo(() -> browser(mc).settingsLink(), 10))
                .then(pause(10))
                .then(shoot("m01_msl_replacement_link"))
                .then(click())
                .then(until(() -> mc.screen != null && browser(mc) == null, 40))
                .then(check(() -> mc.screen != null && browser(mc) == null, "clicking the Settings link opened nothing"))
                .then(pause(10))
                .then(shoot("m02_msl_settings"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> browser(mc) != null, 40))
                .then(check(() -> browser(mc) != null && PYRAMID.equals(browser(mc).selectedId()),
                        "Esc didn't come back to the browser on the desert pyramid"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> mc.screen == null, 40))
                .then(run(() -> setTemplesPreset(setPreset, false)))
                .then(run(() -> reload(mc)))
                .then(until(() -> ClientRequests.reloads() > reloadsBefore[0], 1200));
        return d;
    }

    /** Moog's Structure Lib's own setter for a Replace Vanilla preset, which saves its settings file. */
    private static Method mslSetter() {
        try {
            Class<?> config = Class.forName("com.finndog.moogs_structures.config.MslConfig");
            return config.getMethod("setAndSave", String.class, String.class, boolean.class);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Moog's Structure Lib has no setAndSave for its Replace Vanilla presets", e);
        }
    }

    private static void setTemplesPreset(Method setPreset, boolean on) {
        try {
            Object settings = setPreset.getDeclaringClass().getMethod("get").invoke(null);
            setPreset.invoke(settings, TEMPLES_MOD, TEMPLES_PRESET, on);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("couldn't turn " + (on ? "on" : "off") + " Moog's Temples Reimagined's desert temple in Moog's Structure Lib", e);
        }
    }

    private static void reload(Minecraft mc) {
        reloadsBefore[0] = ClientRequests.reloads();
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload"));
    }

    /** Whether the settings button is on the mod's row: well above the buttons in the preview's corner. */
    private static boolean onModRow(JesScreen browser) {
        return browser.button("config")[1] < browser.button("maximise")[1] - 20;
    }

    private static Director.Action check(BooleanSupplier holds, String failure) {
        return run(() -> {
            if (!holds.getAsBoolean()) {
                throw new IllegalStateException(failure);
            }
        });
    }
}
