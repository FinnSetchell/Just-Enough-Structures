package com.finndog.justenoughstructures.forge;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.client.ClientPackets;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.ConfigScreens;
import com.finndog.justenoughstructures.client.JesClient;
//? if cloth_config {
import com.finndog.justenoughstructures.compat.cloth.JesConfigScreen;
//?}
import com.finndog.justenoughstructures.network.Blobs;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
//? if <26.1 {
import net.minecraftforge.eventbus.api.IEventBus;
//?}
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.forgespi.language.IModFileInfo;
//? if >=1.21 {
/*import net.minecraftforge.network.NetworkContext;
*///?} else {
import net.minecraftforge.network.ConnectionData;
import net.minecraftforge.network.NetworkHooks;
//?}

/** The client's side of the Forge setup. Only ever loaded on a client. */
final class ForgeClient {
    private ForgeClient() {
    }

    //? if >=26.1 {
    /*static void init() {
    *///?} else {
    static void init(IEventBus modBus) {
    //?}
        ForgeNetworking.clientHandler = (channel, data) -> {
            ClientPackets.Handler handler = ClientPackets.handlers().get(channel);
            if (handler != null) {
                ClientPacketListener listener = Minecraft.getInstance().getConnection();
                handler.handle(Minecraft.getInstance(), Blobs.fromBytes(listener == null ? RegistryAccess.EMPTY : listener.registryAccess(), data));
            }
        };
        ClientRequests.setSender(new ClientRequests.ClientSender() {
            @Override
            public boolean canSend(ResourceLocation channel) {
                Connection connection = connection();
                return connection != null && ForgeNetworking.CHANNEL.isRemotePresent(connection);
            }

            // The channels the server listed when this client joined, which show a server on another
            // JES version.
            @Override
            public Collection<ResourceLocation> sendable() {
                Connection connection = connection();
                //? if >=1.21 {
                /*return connection == null ? List.of() : NetworkContext.get(connection).getRemoteChannels();
                *///?} else {
                ConnectionData data = connection == null ? null : NetworkHooks.getConnectionData(connection);
                return data == null ? List.of() : data.getChannels().keySet();
                //?}
            }

            @Override
            public void send(ResourceLocation channel, FriendlyByteBuf buf) {
                ForgeNetworking.sendToServer(channel, buf);
            }
        });

        //? if >=26.1 {
        /*// From 26.1 Forge listeners go on each event's BUS field.
        RegisterKeyMappingsEvent.BUS.addListener(event -> event.register(JesClient.OPEN));
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> JesClient.tick(Minecraft.getInstance()));
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> ClientRequests.reset());
        // The loot index is asked for as the server is joined, before the recipes and tags that start EMI's load are handled.
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(event -> ClientRequests.askIfWanted());
        *///?} else {
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(JesClient.OPEN));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                JesClient.tick(Minecraft.getInstance());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientRequests.reset());
        // The loot index is asked for as the server is joined, before the recipes and tags that start EMI's load are handled.
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> ClientRequests.askIfWanted());
        //?}

        //? if explorers_compass {
        if (ModList.get().isLoaded("explorerscompass")) {
            ForgeExplorersCompass.register();
        }
        //?}
        // Other mods' settings screens, the ones the Mods list's Config button opens.
        ConfigScreens.set(new ConfigScreens() {
            @Override
            public boolean has(String modId) {
                return screenFactory(modId).isPresent();
            }

            @Override
            public Screen open(String modId, Screen parent) {
                return screenFactory(modId).map(factory -> factory.apply(Minecraft.getInstance(), parent)).orElse(null);
            }

            @Override
            public String modWithFile(String path) {
                return modWithFileIn(path);
            }
        });
        //? if cloth_config {
        // Forge's Mods list gets a Config button for the settings screen, which Cloth Config draws.
        if (ModList.get().isLoaded("cloth_config")) {
            ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> JesConfigScreen.create(parent)));
        }
        //?}
    }

    private static Optional<BiFunction<Minecraft, Screen, Screen>> screenFactory(String modId) {
        return ModList.get().getModContainerById(modId).flatMap(container -> ConfigScreenHandler.getScreenFactoryFor(container.getModInfo()));
    }

    /** The first mod of the mod file that holds a file, looking through every mod file in the loader's order. */
    private static String modWithFileIn(String path) {
        for (IModFileInfo file : ModList.get().getModFiles()) {
            try {
                if (!file.getMods().isEmpty() && Files.exists(file.getFile().findResource(path))) {
                    return file.getMods().get(0).getModId();
                }
            } catch (Throwable t) {
                JesLog.debug("Couldn't look for {} in one of the mod files", path, t);
            }
        }
        return null;
    }

    private static Connection connection() {
        ClientPacketListener listener = Minecraft.getInstance().getConnection();
        return listener == null ? null : listener.getConnection();
    }
}
