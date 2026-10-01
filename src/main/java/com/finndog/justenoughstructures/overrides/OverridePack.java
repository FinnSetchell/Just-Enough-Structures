package com.finndog.justenoughstructures.overrides;

import com.finndog.justenoughstructures.JesLog;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * Offers the loot override folder to every world as a datapack that's always on, above all others.
 * It only appears once something has been saved in it. Each time it's opened, which happens on
 * every /reload, it checks every override and leaves out any the game couldn't load.
 */
public final class OverridePack implements RepositorySource {
    @Override
    public void loadPacks(Consumer<Pack> out) {
        Path root = LootOverrides.folder();
        if (!Files.isDirectory(root.resolve("data"))) {
            return;
        }
        try {
            LootOverrides.ensurePack(root);
        } catch (IOException e) {
            JesLog.warnOnce("override-pack", "Couldn't set up the loot override pack in {}: {}", root, e.toString());
            JesLog.debug("Couldn't set up the loot override pack in {}", root, e);
            return;
        }
        // The game opens the pack once to read its details and again to load it, so check once for both.
        Set<ResourceLocation> broken = CheckedResources.findBroken(root);
        Pack pack = Pack.readMetaAndCreate(LootOverrides.PACK_ID, Component.translatable("pack.justenoughstructures.loot_overrides"),
                true, id -> new CheckedResources(id, root, broken), PackType.SERVER_DATA, Pack.Position.TOP, PackSource.BUILT_IN);
        if (pack != null) {
            out.accept(pack);
        }
    }

    /** The folder as a datapack, less any loot table that doesn't load. */
    static final class CheckedResources extends PathPackResources {
        private final Set<ResourceLocation> broken;

        CheckedResources(String id, Path root, Set<ResourceLocation> broken) {
            super(id, root, true);
            this.broken = broken;
        }

        @Override
        public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
            return broken.contains(location) ? null : super.getResource(type, location);
        }

        @Override
        public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
            super.listResources(type, namespace, path, (location, supplier) -> {
                if (!broken.contains(location)) {
                    output.accept(location, supplier);
                }
            });
        }

        static Set<ResourceLocation> findBroken(Path root) {
            Set<ResourceLocation> out = new HashSet<>();
            Path data = root.resolve("data");
            if (!Files.isDirectory(data)) {
                return out;
            }
            try (Stream<Path> files = Files.walk(data)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    Path relative = data.relativize(file);
                    if (relative.getNameCount() < 3 || !relative.getName(1).toString().equals("loot_tables")) {
                        continue;
                    }
                    String namespace = relative.getName(0).toString();
                    String path = relative.subpath(1, relative.getNameCount()).toString().replace('\\', '/');
                    ResourceLocation location = ResourceLocation.tryBuild(namespace, path);
                    String tablePath = path.substring("loot_tables/".length(), path.length() - ".json".length());
                    ResourceLocation table = ResourceLocation.tryBuild(namespace, tablePath);
                    Component problem;
                    try {
                        problem = table == null ? Component.literal(path) : LootOverrides.check(table, Files.readString(file));
                    } catch (IOException e) {
                        problem = Component.literal(String.valueOf(e.getMessage()));
                    }
                    if (problem != null && location != null) {
                        out.add(location);
                        String reason = problem.getString();
                        JesLog.warnOnce("override:" + file + "|" + reason, "Leaving out the loot override {}, the game can't load it: {}", file, reason);
                    }
                }
            } catch (IOException e) {
                JesLog.warnOnce("override-check", "Couldn't check the loot overrides in {}: {}", data, e.toString());
                JesLog.debug("Couldn't check the loot overrides in {}", data, e);
            }
            return out;
        }
    }
}
