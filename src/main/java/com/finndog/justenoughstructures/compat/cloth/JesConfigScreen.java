package com.finndog.justenoughstructures.compat.cloth;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.ClientState;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.ServerConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * The settings screen Mod Menu opens, drawn by Cloth Config. The browser's own settings are the
 * ones it remembers as you use it. The server's are the server.json5 of worlds played from this
 * game, and apply straight away to one that's open.
 */
public final class JesConfigScreen {
    private JesConfigScreen() {
    }

    public static Screen create(Screen parent) {
        ClientState.load();
        ServerConfig.Settings server = ServerConfig.read(ServerConfig.file());
        List<String> hidden = new ArrayList<>();
        server.hiddenMods().stream().sorted().forEach(mod -> hidden.add(mod + ":*"));
        server.hiddenStructures().stream().map(ResourceLocation::toString).sorted().forEach(hidden::add);
        // Filled in by the entries as they save, then written out together.
        ServerEdit edit = new ServerEdit(new ArrayList<>(hidden), server.locatePermission(), server.teleportPermission(), server.showLootLocations(),
                server.editPermission(), server.containerChanges());

        ConfigBuilder builder = ConfigBuilder.create().setParentScreen(parent).setTitle(text("title"));
        ConfigEntryBuilder entries = builder.entryBuilder();

        ConfigCategory browser = builder.getOrCreateCategory(text("browser"));
        browser.addEntry(entries.startTextDescription(text("browser.about")).build());
        browser.addEntry(entries.startBooleanToggle(text("spin"), ClientState.spin).setDefaultValue(true)
                .setSaveConsumer(value -> ClientState.spin = value).build());
        browser.addEntry(entries.startBooleanToggle(text("markers"), ClientState.markers).setDefaultValue(true)
                .setSaveConsumer(value -> ClientState.markers = value).build());
        browser.addEntry(entries.startBooleanToggle(text("ground"), ClientState.ground).setDefaultValue(true)
                .setSaveConsumer(value -> ClientState.ground = value).build());
        browser.addEntry(entries.startBooleanToggle(text("maximised"), ClientState.maximised).setDefaultValue(false)
                .setSaveConsumer(value -> ClientState.maximised = value).build());
        browser.addEntry(entries.startBooleanToggle(text("details"), ClientState.details).setDefaultValue(false)
                .setSaveConsumer(value -> ClientState.details = value).build());
        browser.addEntry(entries.startBooleanToggle(text("rarest_first"), ClientState.rarestFirst).setDefaultValue(false)
                .setSaveConsumer(value -> ClientState.rarestFirst = value).build());

        ConfigCategory serverSettings = builder.getOrCreateCategory(text("server"));
        serverSettings.addEntry(entries.startTextDescription(text("server.about")).build());
        serverSettings.addEntry(entries.startStrList(text("hidden"), hidden).setDefaultValue(List.of())
                .setTooltip(text("hidden.tooltip"))
                .setCellErrorSupplier(JesConfigScreen::hiddenEntryError)
                .setSaveConsumer(value -> edit.hidden = value).build());
        serverSettings.addEntry(entries.startIntSlider(text("locate"), server.locatePermission(), 0, 4).setDefaultValue(2)
                .setTextGetter(JesConfigScreen::permission).setTooltip(text("locate.tooltip"))
                .setSaveConsumer(value -> edit.locate = value).build());
        serverSettings.addEntry(entries.startIntSlider(text("teleport"), server.teleportPermission(), 0, 4).setDefaultValue(2)
                .setTextGetter(JesConfigScreen::permission).setTooltip(text("teleport.tooltip"))
                .setSaveConsumer(value -> edit.teleport = value).build());
        serverSettings.addEntry(entries.startBooleanToggle(text("show_loot"), server.showLootLocations()).setDefaultValue(true)
                .setTooltip(text("show_loot.tooltip"))
                .setSaveConsumer(value -> edit.showLoot = value).build());
        serverSettings.addEntry(entries.startIntSlider(text("edit"), server.editPermission(), 0, 4).setDefaultValue(4)
                .setTextGetter(JesConfigScreen::permission).setTooltip(text("edit.tooltip"))
                .setSaveConsumer(value -> edit.edit = value).build());
        serverSettings.addEntry(entries.startBooleanToggle(text("container_changes"), server.containerChanges()).setDefaultValue(true)
                .setTooltip(text("container_changes.tooltip"))
                .setSaveConsumer(value -> edit.containers = value).build());

        builder.setSavingRunnable(() -> {
            ClientState.save();
            saveServer(edit);
        });
        return builder.build();
    }

    /** What the server entries were set to, gathered as each one saves. */
    private static final class ServerEdit {
        List<String> hidden;
        int locate;
        int teleport;
        boolean showLoot;
        int edit;
        boolean containers;

        ServerEdit(List<String> hidden, int locate, int teleport, boolean showLoot, int edit, boolean containers) {
            this.hidden = hidden;
            this.locate = locate;
            this.teleport = teleport;
            this.showLoot = showLoot;
            this.edit = edit;
            this.containers = containers;
        }
    }

    /** Writes the server settings out, and applies them to a world open in this game. */
    private static void saveServer(ServerEdit edit) {
        // Built as the file would be, so they're checked exactly as the file is when it's read.
        JsonObject json = new JsonObject();
        JsonArray hidden = new JsonArray();
        edit.hidden.stream().map(String::trim).filter(entry -> !entry.isEmpty()).forEach(hidden::add);
        json.add("hidden", hidden);
        json.addProperty("locate_permission", edit.locate);
        json.addProperty("teleport_permission", edit.teleport);
        json.addProperty("show_loot_locations", edit.showLoot);
        json.addProperty("edit_permission", edit.edit);
        json.addProperty("container_changes", edit.containers);
        ServerConfig.Settings settings = ServerConfig.parse(json.toString(), "the settings screen");
        try {
            ServerConfig.save(ServerConfig.file(), settings);
        } catch (IOException e) {
            JustEnoughStructures.LOGGER.warn("Couldn't save {}: {}", ServerConfig.file(), e.toString());
            JesLog.debug("Couldn't save {}", ServerConfig.file(), e);
            return;
        }
        MinecraftServer running = Minecraft.getInstance().getSingleplayerServer();
        if (running != null) {
            running.execute(() -> JesServer.reload(running));
        }
    }

    private static Optional<Component> hiddenEntryError(String entry) {
        String value = entry.trim();
        String mod = value.endsWith(":*") ? value.substring(0, value.length() - 2) : null;
        boolean valid = mod != null
                ? !mod.isEmpty() && ResourceLocation.isValidResourceLocation(mod + ":any")
                : !value.isEmpty() && ResourceLocation.tryParse(value) != null;
        return valid ? Optional.empty() : Optional.of(text("hidden.invalid"));
    }

    private static Component permission(int level) {
        return Component.translatable("config.justenoughstructures.permission." + level);
    }

    private static Component text(String key) {
        return Component.translatable("config.justenoughstructures." + key);
    }
}
