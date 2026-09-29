package com.finndog.justenoughstructures.fabric;

import com.finndog.justenoughstructures.client.JesClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

public final class JustEnoughStructuresFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricClientNetworking.registerClient();
        KeyBindingHelper.registerKeyBinding(JesClient.OPEN);
        ClientTickEvents.END_CLIENT_TICK.register(JesClient::tick);
    }
}
