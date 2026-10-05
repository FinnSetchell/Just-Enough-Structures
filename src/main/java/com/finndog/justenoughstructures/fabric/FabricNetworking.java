package com.finndog.justenoughstructures.fabric;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.catalog.StructureInfo;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.network.ServerPackets;
import com.finndog.justenoughstructures.server.JesServer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;
import net.minecraft.resources.ResourceLocation;

final class FabricNetworking {
    private FabricNetworking() {
    }

    static void registerServer() {
        JesNetwork.setServerSender(ServerPlayNetworking::send);
        JesNetwork.setServerCanSend(ServerPlayNetworking::canSend);

        ServerPackets.handlers().forEach((channel, handler) -> ServerPlayNetworking.registerGlobalReceiver(channel,
                (server, player, listener, buf, responder) -> handler.handle(server, player, buf)));

        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new StructureInfoLoader());
        ServerLifecycleEvents.SERVER_STARTING.register(server -> JesServer.starting());
        ServerLifecycleEvents.SERVER_STARTED.register(JesServer::reload);
        // Stops the loot index and drops what belonged to that world when it closes.
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> JesServer.stop());
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> JesServer.reload(server));
    }

    private static final class StructureInfoLoader extends StructureInfo.Loader implements IdentifiableResourceReloadListener {
        @Override
        public ResourceLocation getFabricId() {
            return JustEnoughStructures.id("structure_info");
        }
    }
}
