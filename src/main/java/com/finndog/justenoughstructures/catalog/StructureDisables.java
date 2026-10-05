package com.finndog.justenoughstructures.catalog;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JesLog;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * What structure mods turn off or replace in their own settings, which doesn't show in the
 * registry: Moog's Structure Lib, and YUNG's mods that stand in for vanilla structures. Read by
 * reflection, as JES doesn't depend on any of them. Anything that doesn't read as expected counts
 * as not turned off.
 */
final class StructureDisables {
    /** A YUNG's mod's setting that turns off vanilla structures, or null for one that always does. */
    private record Yung(String modClass, String[] setting, List<ResourceLocation> vanilla, ResourceLocation replacement) {
        String modId() {
            return replacement.getNamespace();
        }
    }

    private static final String YUNG = "com.yungnickyoung.minecraft.";
    private static final List<Yung> YUNGS = List.of(
            new Yung(YUNG + "betterdeserttemples.BetterDesertTemplesCommon", new String[]{"general", "disableVanillaPyramids"},
                    List.of(Ids.parse("desert_pyramid")), Ids.of("betterdeserttemples", "desert_temple")),
            new Yung(YUNG + "betterjungletemples.BetterJungleTemplesCommon", new String[]{"general", "disableVanillaJungleTemples"},
                    List.of(Ids.parse("jungle_pyramid")), Ids.of("betterjungletemples", "jungle_temple")),
            new Yung(YUNG + "betteroceanmonuments.BetterOceanMonumentsCommon", new String[]{"general", "disableVanillaMonuments"},
                    List.of(Ids.parse("monument")), Ids.of("betteroceanmonuments", "ocean_monument")),
            new Yung(YUNG + "betterfortresses.BetterFortressesCommon", new String[]{"general", "disableVanillaFortresses"},
                    List.of(Ids.parse("fortress")), Ids.of("betterfortresses", "fortress")),
            new Yung(YUNG + "betterwitchhuts.BetterWitchHutsCommon", new String[]{"general", "disableVanillaWitchHuts"},
                    List.of(Ids.parse("swamp_hut")), Ids.of("betterwitchhuts", "witch_hut")),
            new Yung(YUNG + "bettermineshafts.BetterMineshaftsCommon", new String[]{"disableVanillaMineshafts"},
                    List.of(Ids.parse("mineshaft"), Ids.parse("mineshaft_mesa")), Ids.of("bettermineshafts", "mineshaft")),
            // Better Strongholds always replaces the vanilla one.
            new Yung(YUNG + "betterstrongholds.BetterStrongholdsCommon", null,
                    List.of(Ids.parse("stronghold")), Ids.of("betterstrongholds", "stronghold")));

    private StructureDisables() {
    }

    /** What a mod's settings do to this structure, or null if nothing. */
    static Availability check(ResourceLocation id) {
        Availability msl = moogs(id);
        if (msl != null) {
            return msl;
        }
        for (Yung yung : YUNGS) {
            if (yung.vanilla().contains(id) && on(yung)) {
                return new Availability(Availability.Reason.REPLACED, yung.modId(), yung.replacement());
            }
        }
        return null;
    }

    private static Availability moogs(ResourceLocation id) {
        try {
            Class<?> config = Class.forName("com.finndog.moogs_structures.config.MslConfig");
            Object settings = config.getMethod("get").invoke(null);
            if ((boolean) config.getMethod("isStructureDisabled", ResourceLocation.class).invoke(settings, id)) {
                return new Availability(Availability.Reason.TURNED_OFF, "moogs_structures", null);
            }
            Class<?> manager = Class.forName("com.finndog.moogs_structures.config.ReplaceVanillaManager");
            Optional<?> replacement = (Optional<?>) manager.getMethod("getActiveReplacement", ResourceLocation.class).invoke(null, id);
            if (replacement.isPresent()) {
                Object r = replacement.get();
                Method mod = r.getClass().getMethod("modid");
                Method by = r.getClass().getMethod("replacementStructure");
                return new Availability(Availability.Reason.REPLACED, (String) mod.invoke(r), (ResourceLocation) by.invoke(r));
            }
        } catch (ClassNotFoundException e) {
            return null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            JesLog.debug("Couldn't read what Moog's Structure Lib turns off", e);
        }
        return null;
    }

    private static boolean on(Yung yung) {
        if (yung.setting() == null) {
            return loaded(yung.modClass());
        }
        try {
            Object value = Class.forName(yung.modClass()).getField("CONFIG").get(null);
            for (String name : yung.setting()) {
                Field field = value.getClass().getField(name);
                value = field.get(value);
            }
            return value instanceof Boolean b && b;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            JesLog.debug("Couldn't read {}'s setting for turning off vanilla structures", yung.modId(), e);
            return false;
        }
    }

    private static boolean loaded(String className) {
        try {
            Class.forName(className, false, StructureDisables.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
