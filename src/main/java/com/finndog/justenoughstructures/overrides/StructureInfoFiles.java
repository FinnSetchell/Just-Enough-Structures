package com.finndog.justenoughstructures.overrides;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.catalog.StructureInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * What Pack tools writes about structures: notes for players and keeping where loot is a secret.
 * Each is a structure info file in the JES pack beside the loot overrides, in the same format a mod
 * or datapack uses, so the pack's file is the one used. A structure put back the way its mod has it
 * gets no file at all, so the mod's own stays in charge.
 */
public final class StructureInfoFiles {
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private StructureInfoFiles() {
    }

    static Path file(Path root, ResourceLocation id) {
        return root.resolve("data").resolve(id.getNamespace()).resolve(StructureInfo.DIRECTORY).resolve(id.getPath() + ".json");
    }

    /** Whether the JES pack has a file for this structure. */
    public static boolean written(ResourceLocation id) {
        return Files.exists(file(LootOverrides.folder(), id));
    }

    /** What the mods and datapacks say about a structure, leaving out the JES pack. */
    public static StructureInfo fromMods(ResourceManager resources, ResourceLocation id) {
        ResourceLocation location = new ResourceLocation(id.getNamespace(), StructureInfo.DIRECTORY + "/" + id.getPath() + ".json");
        List<Resource> stack = resources.getResourceStack(location);
        for (int i = stack.size() - 1; i >= 0; i--) {
            Resource resource = stack.get(i);
            if (LootOverrides.PACK_ID.equals(resource.sourcePackId())) {
                continue;
            }
            try (InputStream in = resource.open()) {
                return StructureInfo.parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
            } catch (IOException | RuntimeException e) {
                JesLog.debug("Couldn't read what {} says about {}", resource.sourcePackId(), id, e);
                return StructureInfo.NONE;
            }
        }
        return StructureInfo.NONE;
    }

    /**
     * Saves what players are told about a structure and puts it in use straight away. Notes that
     * read the same as the mod's keep the mod's own text and author.
     *
     * @param notes  plain text, or blank for none
     * @param secret keep where its loot is out of the preview
     */
    public static Component save(ResourceManager resources, ResourceLocation id, String notes, boolean secret) {
        StructureInfo mods = fromMods(resources, id);
        StructureInfo now = StructureInfo.forStructure(id);
        String text = notes == null ? "" : notes.strip();
        StructureInfo info;
        if (text.isEmpty()) {
            info = new StructureInfo(null, null, secret);
        } else if (mods.notes() != null && mods.notes().getString().equals(text)) {
            info = new StructureInfo(mods.notes(), mods.author(), secret);
        } else if (now.notes() != null && now.notes().getString().equals(text)) {
            info = new StructureInfo(now.notes(), now.author(), secret);
        } else {
            info = new StructureInfo(Component.literal(text), null, secret);
        }
        Path root = LootOverrides.folder();
        Path file = file(root, id);
        try {
            if (same(info, mods)) {
                Files.deleteIfExists(file);
                info = mods;
            } else {
                LootOverrides.ensurePack(root);
                Files.createDirectories(file.getParent());
                Path temp = file.resolveSibling(file.getFileName() + ".tmp");
                Files.writeString(temp, PRETTY.toJson(toJson(info)));
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            JustEnoughStructures.LOGGER.warn("Couldn't save what players are told about {}: {}", id, e.toString());
            JesLog.debug("Couldn't save what players are told about {}", id, e);
            return Component.translatable("screen.justenoughstructures.override.save_failed", String.valueOf(e.getMessage()));
        }
        StructureInfo.put(id, info);
        return Component.translatable("screen.justenoughstructures.tools.structure_saved");
    }

    private static boolean same(StructureInfo a, StructureInfo b) {
        return a.hideLootLocations() == b.hideLootLocations() && Objects.equals(a.author(), b.author())
                && Objects.equals(a.notes() == null ? null : a.notes().getString(), b.notes() == null ? null : b.notes().getString());
    }

    /** The file as a mod would write it. Plain notes are a plain string, anything richer a text component. */
    static JsonObject toJson(StructureInfo info) {
        JsonObject json = new JsonObject();
        if (info.notes() != null) {
            boolean plain = info.notes().getContents() instanceof LiteralContents && info.notes().getSiblings().isEmpty()
                    && info.notes().getStyle().isEmpty();
            if (plain) {
                json.addProperty("notes", info.notes().getString());
            } else {
                json.add("notes", Component.Serializer.toJsonTree(info.notes()));
            }
        }
        if (info.author() != null) {
            json.addProperty("author", info.author());
        }
        if (info.hideLootLocations()) {
            json.addProperty("hide_loot_locations", true);
        }
        return json;
    }
}
