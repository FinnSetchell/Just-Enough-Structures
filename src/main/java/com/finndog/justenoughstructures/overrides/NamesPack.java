package com.finndog.justenoughstructures.overrides;

import com.finndog.justenoughstructures.JesLog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
//? if >=1.21 {
/*import java.util.Optional;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
*///?}
//? if >=26.3 {
/*import java.util.stream.Stream;
import net.minecraft.server.packs.PackMetadataResources;
*///?}

/**
 * The names Pack tools gave structures on this game, offered to the game's resource packs as one
 * that's always on, above the mods' own, so mods that look a structure's name up themselves, like
 * Explorer's Compass, show it too. Like any resource pack it's read at start-up and on F3+T, and
 * it's only there once a name has been saved.
 */
public final class NamesPack implements RepositorySource {
    public static final String PACK_ID = "justenoughstructures/names";

    @Override
    public void loadPacks(Consumer<Pack> out) {
        Path root = LootOverrides.folder();
        if (!Files.isDirectory(StructureNameFiles.folder(root))) {
            return;
        }
        try {
            LootOverrides.ensurePack(root);
        } catch (IOException e) {
            JesLog.warnOnce("names-pack", "Couldn't set up the structure names pack in {}: {}", root, e.toString());
            return;
        }
        //? if >=26.3 {
        /*PackLocationInfo info = new PackLocationInfo(PACK_ID, Component.translatable("pack.justenoughstructures.names"),
                PackSource.BUILT_IN, Optional.empty());
        Pack pack = Pack.readMetaAndCreate(info, new Pack.ResourcesSupplier() {
            @Override
            public PackMetadataResources openMetadata(PackLocationInfo location) {
                return new PathPackResources(location, root);
            }

            @Override
            public Stream<PackResources> openResources(PackLocationInfo location, Pack.Metadata metadata) {
                return Stream.of(new PathPackResources(location, root));
            }
        }, PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.TOP, false));
        *///?} else if >=1.21 {
        /*PackLocationInfo info = new PackLocationInfo(PACK_ID, Component.translatable("pack.justenoughstructures.names"),
                PackSource.BUILT_IN, Optional.empty());
        Pack pack = Pack.readMetaAndCreate(info, new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo location) {
                return new PathPackResources(location, root);
            }

            @Override
            public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                return openPrimary(location);
            }
        }, PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.TOP, false));
        *///?} else {
        Pack pack = Pack.readMetaAndCreate(PACK_ID, Component.translatable("pack.justenoughstructures.names"),
                true, id -> new PathPackResources(id, root, true), PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.BUILT_IN);
        //?}
        if (pack != null) {
            out.accept(pack);
        }
    }
}
