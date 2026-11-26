package com.finndog.justenoughstructures.loot;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.Regs;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * The loot of vanilla's structures without pools, which name their loot tables in their code: what
 * generating each one for the loot index finds, so the first index can list them before anything is
 * generated. Used only while the structure is still vanilla's type, as a datapack can make one of
 * these ids something else. Generating them later puts what they really give in its place, and a game
 * test checks this against that on every build.
 */
public final class KnownLoot {
    /** A structure's type, the loot tables it uses and the items it places itself. */
    public record Known(ResourceLocation type, Set<ResourceLocation> tables, Set<ResourceLocation> placed) {
    }

    private static final Map<ResourceLocation, Known> VANILLA = Map.ofEntries(
            known("buried_treasure", "buried_treasure", tables("chests/buried_treasure")),
            known("desert_pyramid", "desert_pyramid", tables("chests/desert_pyramid", "archaeology/desert_pyramid")),
            known("jungle_pyramid", "jungle_temple", tables("chests/jungle_temple", "chests/jungle_temple_dispenser")),
            known("igloo", "igloo", tables("chests/igloo_chest"), "splash_potion"),
            //? if >=26.1 {
            /*known("mansion", "woodland_mansion", tables("chests/woodland_mansion"), "allium", "dark_oak_sapling", "iron_axe"),
            *///?} else {
            known("mansion", "woodland_mansion", tables("chests/woodland_mansion"), "dark_oak_sapling", "iron_axe"),
            //?}
            known("mineshaft", "mineshaft", tables("chests/abandoned_mineshaft")),
            known("mineshaft_mesa", "mineshaft", tables("chests/abandoned_mineshaft")),
            known("ocean_ruin_cold", "ocean_ruin", tables("chests/underwater_ruin_big", "chests/underwater_ruin_small", "archaeology/ocean_ruin_cold")),
            known("ocean_ruin_warm", "ocean_ruin", tables("chests/underwater_ruin_big", "chests/underwater_ruin_small", "archaeology/ocean_ruin_warm")),
            known("ruined_portal", "ruined_portal", tables("chests/ruined_portal")),
            known("ruined_portal_desert", "ruined_portal", tables("chests/ruined_portal")),
            known("ruined_portal_jungle", "ruined_portal", tables("chests/ruined_portal")),
            known("ruined_portal_mountain", "ruined_portal", tables("chests/ruined_portal")),
            known("ruined_portal_nether", "ruined_portal", tables("chests/ruined_portal")),
            known("ruined_portal_ocean", "ruined_portal", tables("chests/ruined_portal")),
            known("ruined_portal_swamp", "ruined_portal", tables("chests/ruined_portal")),
            known("shipwreck", "shipwreck", tables("chests/shipwreck_map", "chests/shipwreck_supply", "chests/shipwreck_treasure")),
            known("shipwreck_beached", "shipwreck", tables("chests/shipwreck_map", "chests/shipwreck_supply", "chests/shipwreck_treasure")),
            known("stronghold", "stronghold", tables("chests/stronghold_corridor", "chests/stronghold_crossing", "chests/stronghold_library")),
            known("fortress", "fortress", tables("chests/nether_bridge")),
            known("end_city", "end_city", tables("chests/end_city_treasure"), "elytra", "potion"));

    private KnownLoot() {
    }

    /** What's known of this structure's loot without generating it, or null if it isn't one of vanilla's above as it is now. */
    public static Known of(Registry<Structure> registry, ResourceLocation id) {
        Known known = VANILLA.get(id);
        if (known == null) {
            return null;
        }
        Structure structure = Regs.value(registry, id);
        return structure != null && known.type().equals(BuiltInRegistries.STRUCTURE_TYPE.getKey(structure.type())) ? known : null;
    }

    /** Every structure listed here. For the game test that checks them. */
    public static Set<ResourceLocation> listed() {
        return VANILLA.keySet();
    }

    private static Map.Entry<ResourceLocation, Known> known(String id, String type, Set<ResourceLocation> tables, String... placed) {
        return Map.entry(Ids.parse(id), new Known(Ids.parse(type), tables, Stream.of(placed).map(Ids::parse).collect(Collectors.toUnmodifiableSet())));
    }

    private static Set<ResourceLocation> tables(String... ids) {
        return Stream.of(ids).map(Ids::parse).collect(Collectors.toUnmodifiableSet());
    }
}
