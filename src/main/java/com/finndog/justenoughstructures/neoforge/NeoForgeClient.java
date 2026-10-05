package com.finndog.justenoughstructures.neoforge;

import com.finndog.justenoughstructures.client.ClientPackets;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.JesClient;
//? if cloth_config {
import com.finndog.justenoughstructures.compat.cloth.JesConfigScreen;
//?}
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.network.JesPayload;
import com.finndog.justenoughstructures.network.ServerPackets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
//? if >=26.1 {
/*import net.neoforged.neoforge.client.network.ClientPacketDistributor;
*///?} else {
import net.neoforged.neoforge.network.PacketDistributor;
//?}

/** The client's side of the NeoForge setup. Only ever loaded on a client. */
final class NeoForgeClient {
    private NeoForgeClient() {
    }

    static void init(IEventBus modBus, ModContainer container) {
        NeoForgeNetworking.clientHandler = (channel, buf) -> {
            ClientPackets.Handler handler = ClientPackets.handlers().get(channel);
            if (handler != null) {
                handler.handle(Minecraft.getInstance(), buf);
            }
        };
        ClientRequests.setSender(new ClientRequests.ClientSender() {
            @Override
            public boolean canSend(ResourceLocation channel) {
                ClientPacketListener listener = Minecraft.getInstance().getConnection();
                return listener != null && listener.hasChannel(JesPayload.type(channel));
            }

            // NeoForge only tells a client the channels both sides have, so a server on another version
            // of JES shows by the one channel every version has.
            @Override
            public Collection<ResourceLocation> sendable() {
                ClientPacketListener listener = Minecraft.getInstance().getConnection();
                List<ResourceLocation> out = new ArrayList<>();
                if (listener != null) {
                    if (listener.hasChannel(JesPayload.type(JesNetwork.PRESENT))) {
                        out.add(JesNetwork.PRESENT);
                    }
                    for (ResourceLocation channel : ServerPackets.handlers().keySet()) {
                        if (listener.hasChannel(JesPayload.type(channel))) {
                            out.add(channel);
                        }
                    }
                }
                return out;
            }

            @Override
            public void send(ResourceLocation channel, FriendlyByteBuf buf) {
                //? if >=26.1 {
                /*ClientPacketDistributor.sendToServer(new JesPayload(JesPayload.type(channel), Blobs.bytes(buf)));
                *///?} else {
                PacketDistributor.sendToServer(new JesPayload(JesPayload.type(channel), Blobs.bytes(buf)));
                //?}
            }
        });

        //? if >=26.1 {
        /*modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.registerCategory(JesClient.CATEGORY);
            event.register(JesClient.OPEN);
        });
        *///?} else {
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(JesClient.OPEN));
        //?}
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> JesClient.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientRequests.reset());

        //? if explorers_compass {
        if (ModList.get().isLoaded("explorerscompass")) {
            NeoForgeExplorersCompass.register();
        }
        //?}
        //? if cloth_config {
        // NeoForge's Mods list gets a Config button for the settings screen, which Cloth Config draws.
        if (ModList.get().isLoaded("cloth_config")) {
            container.registerExtensionPoint(IConfigScreenFactory.class, (IConfigScreenFactory) (mod, parent) -> JesConfigScreen.create(parent));
        }
        //?}
    }
}
