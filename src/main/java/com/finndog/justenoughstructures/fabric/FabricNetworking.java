package com.finndog.justenoughstructures.fabric;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.catalog.StructureInfo;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.server.JesServer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;
import net.minecraft.core.BlockPos;
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

        ServerPlayNetworking.registerGlobalReceiver(JesNetwork.REQUEST_TABLE, (server, player, handler, buf, responder) -> {
            int requestId = buf.readVarInt();
            ResourceLocation table = buf.readResourceLocation();
            server.execute(() -> JesServer.onRequestTable(player, requestId, table));
        });

        ServerPlayNetworking.registerGlobalReceiver(JesNetwork.TABLE_ACTION, (server, player, handler, buf, responder) -> {
            int requestId = buf.readVarInt();
            ResourceLocation table = buf.readResourceLocation();
            int action = buf.readVarInt();
            server.execute(() -> JesServer.onTableAction(player, requestId, table, action));
        });

        ServerPlayNetworking.registerGlobalReceiver(JesNetwork.CONTAINER_ACTION, (server, player, handler, buf, responder) -> {
            int requestId = buf.readVarInt();
            ResourceLocation template = buf.readResourceLocation();
            BlockPos pos = buf.readBlockPos();
            ResourceLocation table = buf.readBoolean() ? buf.readResourceLocation() : null;
            server.execute(() -> JesServer.onContainerAction(player, requestId, template, pos, table));
        });

        ServerPlayNetworking.registerGlobalReceiver(JesNetwork.UPLOAD, (server, player, handler, buf, responder) ->
                JesServer.onUploadPart(server, player, Blobs.Part.read(buf)));

        ServerPlayNetworking.registerGlobalReceiver(JesNetwork.REQUEST_COMPASS, (server, player, handler, buf, responder) -> {
            int requestId = buf.readVarInt();
            ResourceLocation structure = buf.readResourceLocation();
            JesServer.queueCompass(server, player, requestId, structure);
        });

        ServerPlayNetworking.registerGlobalReceiver(JesNetwork.REQUEST_LOCATE, (server, player, handler, buf, responder) -> {
            int requestId = buf.readVarInt();
            ResourceLocation structure = buf.readResourceLocation();
            boolean teleport = buf.readBoolean();
            JesServer.queueLocate(server, player, requestId, structure, teleport);
        });

        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new StructureInfoLoader());
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
