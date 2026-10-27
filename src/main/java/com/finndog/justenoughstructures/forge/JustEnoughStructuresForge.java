package com.finndog.justenoughstructures.forge;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.catalog.StructureInfo;
//? if explorers_compass {
import com.finndog.justenoughstructures.compat.explorerscompass.ExplorersCompassSearch;
//?}
import com.finndog.justenoughstructures.server.JesCommands;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.PackToolsAccess;
import com.finndog.justenoughstructures.server.SpareTime;
import java.util.stream.Collectors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
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
        //? if explorers_compass {
        if (ModList.get().isLoaded("explorerscompass")) {
            ExplorersCompassSearch.install();
        }
        //?}
        ForgePermissions.install();

        //? if >=26.1 {
        /*// From 26.1 Forge listeners go on each event's BUS field.
        RegisterCommandsEvent.BUS.addListener(event -> JesCommands.register(event.getDispatcher()));
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                PackToolsAccess.joined(player);
            }
        });
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                JesServer.left(player);
            }
        });
        AddReloadListenerEvent.BUS.addListener(event -> event.addListener(new StructureInfo.Loader()));
        ServerStartingEvent.BUS.addListener(event -> JesServer.starting(event.getServer()));
        ServerStartedEvent.BUS.addListener(event -> JesServer.reload(event.getServer()));
        ServerStoppingEvent.BUS.addListener(event -> JesServer.stop());
        TickEvent.ServerTickEvent.Pre.BUS.addListener(event -> SpareTime.tickStarted(event.server()));
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> SpareTime.tickEnded(event.server()));
        // Sent to all players at once only after /reload, partway through applying the new data, so
        // JES reloads on the next tick, once all of it is in place.
        OnDatapackSyncEvent.BUS.addListener(event -> {
            if (event.getPlayer() == null) {
                MinecraftServer server = event.getPlayerList().getServer();
                server.schedule(new TickTask(server.getTickCount(), () -> JesServer.reload(server)));
            }
        });

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClient.init();
        }
        *///?} else {
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> JesCommands.register(event.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                PackToolsAccess.joined(player);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                JesServer.left(player);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new StructureInfo.Loader()));
        MinecraftForge.EVENT_BUS.addListener((ServerStartingEvent event) -> JesServer.starting(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> JesServer.reload(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> JesServer.stop());
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.START) {
                SpareTime.tickStarted(event.getServer());
            } else {
                SpareTime.tickEnded(event.getServer());
            }
        });
        // Sent to all players at once only after /reload, partway through applying the new data, so
        // JES reloads on the next tick, once all of it is in place.
        MinecraftForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> {
            if (event.getPlayer() == null) {
                MinecraftServer server = event.getPlayerList().getServer();
                server.tell(new TickTask(server.getTickCount(), () -> JesServer.reload(server)));
            }
        });

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClient.init(FMLJavaModLoadingContext.get().getModEventBus());
        }
        //?}
    }
}
