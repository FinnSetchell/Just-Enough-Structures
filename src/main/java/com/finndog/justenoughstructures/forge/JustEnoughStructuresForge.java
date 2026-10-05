package com.finndog.justenoughstructures.forge;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.catalog.StructureInfo;
import com.finndog.justenoughstructures.compat.explorerscompass.ExplorersCompassSearch;
import com.finndog.justenoughstructures.server.JesCommands;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.PackToolsAccess;
import java.util.stream.Collectors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;

@Mod(JustEnoughStructures.MOD_ID)
public final class JustEnoughStructuresForge {
    public JustEnoughStructuresForge() {
        JustEnoughStructures.setModNames(namespace -> ModList.get().getModContainerById(namespace)
                .map(mod -> mod.getModInfo().getDisplayName())
                .orElse(namespace));
        JustEnoughStructures.setConfigDir(FMLPaths.CONFIGDIR.get());
        JustEnoughStructures.setGameDir(FMLPaths.GAMEDIR.get());
        JustEnoughStructures.setModVersions(() -> ModList.get().getMods().stream()
                .collect(Collectors.toMap(IModInfo::getModId, mod -> mod.getVersion().toString(), (a, b) -> a)));
        JustEnoughStructures.init();
        ForgeNetworking.register();
        if (ModList.get().isLoaded("explorerscompass")) {
            ExplorersCompassSearch.install();
        }
        ForgePermissions.install();

        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> JesCommands.register(event.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                PackToolsAccess.joined(player);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new StructureInfo.Loader()));
        MinecraftForge.EVENT_BUS.addListener((ServerStartingEvent event) -> JesServer.starting());
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> JesServer.reload(event.getServer()));
        // Stops the loot index and drops what belonged to that world when it closes.
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> JesServer.stop());
        // Sent to everyone at once only after /reload. It comes partway through putting the new data
        // in place, so JES starts over on the next tick, once the rest of it is in.
        MinecraftForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> {
            if (event.getPlayer() == null) {
                MinecraftServer server = event.getPlayerList().getServer();
                server.tell(new TickTask(server.getTickCount(), () -> JesServer.reload(server)));
            }
        });

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClient.init(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }
}
