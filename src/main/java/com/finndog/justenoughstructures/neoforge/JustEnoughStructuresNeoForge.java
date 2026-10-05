package com.finndog.justenoughstructures.neoforge;

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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforgespi.language.IModInfo;

@Mod(JustEnoughStructures.MOD_ID)
public final class JustEnoughStructuresNeoForge {
    public JustEnoughStructuresNeoForge(IEventBus modBus, ModContainer container) {
        JustEnoughStructures.setModNames(namespace -> ModList.get().getModContainerById(namespace)
                .map(mod -> mod.getModInfo().getDisplayName())
                .orElse(namespace));
        JustEnoughStructures.setConfigDir(FMLPaths.CONFIGDIR.get());
        JustEnoughStructures.setGameDir(FMLPaths.GAMEDIR.get());
        JustEnoughStructures.setModVersions(() -> ModList.get().getMods().stream()
                .collect(Collectors.toMap(IModInfo::getModId, mod -> mod.getVersion().toString(), (a, b) -> a)));
        JustEnoughStructures.init();
        NeoForgeNetworking.register(modBus);
        if (ModList.get().isLoaded("explorerscompass")) {
            ExplorersCompassSearch.install();
        }
        NeoForgePermissions.install();

        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> JesCommands.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                PackToolsAccess.joined(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new StructureInfo.Loader()));
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> JesServer.starting(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> JesServer.reload(event.getServer()));
        // Stops the loot index and drops what belonged to that world when it closes.
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> JesServer.stop());
        // Sent to everyone at once only after /reload. It comes partway through putting the new data
        // in place, so JES starts over on the next tick, once the rest of it is in.
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> {
            if (event.getPlayer() == null) {
                MinecraftServer server = event.getPlayerList().getServer();
                server.tell(new TickTask(server.getTickCount(), () -> JesServer.reload(server)));
            }
        });

        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForgeClient.init(modBus, container);
        }
    }
}
