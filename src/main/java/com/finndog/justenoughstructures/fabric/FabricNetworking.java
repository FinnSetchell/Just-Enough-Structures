package com.finndog.justenoughstructures.fabric;

import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.server.JesServer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;

final class FabricNetworking {
    private FabricNetworking() {
    }

    static void registerServer() {
        JesNetwork.setServerSender(ServerPlayNetworking::send);

        ServerPlayNetworking.registerGlobalReceiver(JesNetwork.REQUEST_CATALOG, (server, player, handler, buf, responder) ->
                server.execute(() -> JesServer.onRequestCatalog(player)));

        ServerPlayNetworking.registerGlobalReceiver(JesNetwork.REQUEST_CAPTURE, (server, player, handler, buf, responder) -> {
            int requestId = buf.readVarInt();
            ResourceLocation structure = buf.readResourceLocation();
            long seed = buf.readLong();
            server.execute(() -> JesServer.onRequestCapture(player, requestId, structure, seed));
        });

        ServerPlayNetworking.registerGlobalReceiver(JesNetwork.REQUEST_LOOT, (server, player, handler, buf, responder) -> {
            int requestId = buf.readVarInt();
            ResourceLocation table = buf.readResourceLocation();
            long seed = buf.readLong();
            int size = buf.readVarInt();
            server.execute(() -> JesServer.onRequestLoot(player, requestId, table, seed, size));
        });

        ServerPlayNetworking.registerGlobalReceiver(JesNetwork.REQUEST_ODDS, (server, player, handler, buf, responder) -> {
            int requestId = buf.readVarInt();
            ResourceLocation table = buf.readResourceLocation();
            server.execute(() -> JesServer.onRequestOdds(player, requestId, table));
        });

        ServerPlayNetworking.registerGlobalReceiver(JesNetwork.REQUEST_INDEX, (server, player, handler, buf, responder) ->
                server.execute(() -> JesServer.onRequestIndex(player)));

        ServerPlayNetworking.registerGlobalReceiver(JesNetwork.REQUEST_LOCATE, (server, player, handler, buf, responder) -> {
            int requestId = buf.readVarInt();
            ResourceLocation structure = buf.readResourceLocation();
            boolean teleport = buf.readBoolean();
            JesServer.queueLocate(server, player, requestId, structure, teleport);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> JesServer.invalidate());
        // Stops the loot index and drops what belonged to that world when it closes.
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> JesServer.invalidate());
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> JesServer.invalidate());
    }
}
