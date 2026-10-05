package com.finndog.justenoughstructures.forge;

import com.finndog.justenoughstructures.client.ClientPackets;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.JesClient;
//? if cloth_config {
import com.finndog.justenoughstructures.compat.cloth.JesConfigScreen;
//?}
import com.finndog.justenoughstructures.network.Blobs;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.Minecraft;
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
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
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

    static void init(IEventBus modBus) {
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

            // The server's channels as it listed them when this client joined, which is how a server on
            // another version of JES shows up.
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

        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(JesClient.OPEN));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                JesClient.tick(Minecraft.getInstance());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientRequests.reset());

        //? if explorers_compass {
        if (ModList.get().isLoaded("explorerscompass")) {
            ForgeExplorersCompass.register();
        }
        //?}
        //? if cloth_config {
        // Forge's Mods list gets a Config button for the settings screen, which Cloth Config draws.
        if (ModList.get().isLoaded("cloth_config")) {
            ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> JesConfigScreen.create(parent)));
        }
        //?}
    }

    private static Connection connection() {
        ClientPacketListener listener = Minecraft.getInstance().getConnection();
        return listener == null ? null : listener.getConnection();
    }
}
