package com.finndog.justenoughstructures.server;

import com.finndog.justenoughstructures.FileFormat;
import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.Players;
import com.finndog.justenoughstructures.SafeFiles;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who can use Pack tools: editing loot tables and chests, hiding structures and the server's
 * settings. In singleplayer, having cheats on. On a server, anyone a permissions mod gives
 * {@link #NODE}, anyone listed by name in server.json5, and anyone with the permission level it
 * sets, if it sets one.
 *
 * <p>A listed name is tied to the first player to join with it, by UUID, the way whitelist.json
 * works: that player keeps access after a rename, and whoever takes the old name later doesn't get
 * it. Who's been tied to which name is kept in {@code pack_tools_players.json} beside server.json5.
 */
public final class PackToolsAccess {
    public static final String NODE = JustEnoughStructures.MOD_ID + ".pack_tools";
    private static final String KNOWN_FILE = "pack_tools_players.json";
    private static final Gson GSON = new Gson();

    /** Asks a permissions mod about a player, or null when there isn't one. Set by the loader. */
    private static volatile PermissionCheck permissions;
    /** Lowercase listed name to the player who joined with it first. */
    private static Map<String, UUID> known;

    private PackToolsAccess() {
    }

    /** A permissions mod's answer for a player and a permission node. */
    public interface PermissionCheck {
        boolean has(ServerPlayer player, String node);
    }

    public static void setPermissions(PermissionCheck check) {
        permissions = check;
    }

    /** The permissions mod's check, or null, so tests can put it back after swapping it. */
    public static PermissionCheck permissions() {
        return permissions;
    }

    public static boolean allowed(ServerPlayer player) {
        MinecraftServer server = Players.server(player);
        if (server != null && Players.isSingleplayerOwner(server, player)) {
            // Singleplayer, or the host of a world opened to LAN: cheats on is enough.
            return Players.hasPermission(player, 2);
        }
        PermissionCheck check = permissions;
        if (check != null) {
            try {
                if (check.has(player, NODE)) {
                    return true;
                }
            } catch (RuntimeException | LinkageError e) {
                JesLog.warnOnce("permissions", "Couldn't ask the permissions mod about {}: {}", NODE, e.toString());
            }
        }
        ServerConfig.PackTools rules = ServerConfig.get().packTools();
        if (rules.permissionLevel() >= 0 && Players.hasPermission(player, rules.permissionLevel())) {
            return true;
        }
        return listed(player, rules.players());
    }

    /** When a player joins: ties a listed name to them if nobody has it yet, so a later rename keeps their access. */
    public static void joined(ServerPlayer player) {
        listed(player, ServerConfig.get().packTools().players());
    }

    /** Whether a player is one of these names, by UUID once the name is tied to someone. */
    static synchronized boolean listed(ServerPlayer player, List<String> names) {
        if (names.isEmpty()) {
            return false;
        }
        Map<String, UUID> tied = known();
        UUID id = player.getUUID();
        String name = player.getScoreboardName();
        for (String listed : names) {
            String key = listed.toLowerCase(Locale.ROOT);
            UUID owner = tied.get(key);
            if (owner != null) {
                if (owner.equals(id)) {
                    return true;
                }
            } else if (listed.equalsIgnoreCase(name)) {
                tied.put(key, id);
                save(tied);
                return true;
            }
        }
        return false;
    }

    /** Set when a newer version saved the file, which is then left as it is. */
    private static boolean newer;

    private static Map<String, UUID> known() {
        if (known == null) {
            known = new HashMap<>();
            Path file = file();
            try {
                if (Files.exists(file)) {
                    JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    if (FileFormat.newer(json)) {
                        // Saved by a newer version: not read, and not written over.
                        newer = true;
                        FileFormat.check(json, file);
                    }
                    json.remove(FileFormat.KEY);
                    json.entrySet().forEach(e -> {
                        try {
                            known.put(e.getKey().toLowerCase(Locale.ROOT), UUID.fromString(e.getValue().getAsString()));
                        } catch (RuntimeException ignored) {
                            // An entry that doesn't read is just not tied to anyone.
                        }
                    });
                }
            } catch (IOException | RuntimeException e) {
                JesLog.warnOnce("read:" + file, "Couldn't read {}, so names in pack_tools are matched by name again: {}", file, e.toString());
            }
        }
        return known;
    }

    private static void save(Map<String, UUID> tied) {
        if (newer) {
            return;
        }
        JsonObject json = new JsonObject();
        tied.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> json.addProperty(e.getKey(), e.getValue().toString()));
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            SafeFiles.write(file, GSON.toJson(FileFormat.stamped(json)));
        } catch (IOException e) {
            JesLog.warnOnce("write:" + file, "Couldn't save {}: {}", file, e.toString());
        }
    }

    private static Path file() {
        return JustEnoughStructures.configDir().resolve(KNOWN_FILE);
    }

    /** Forgets who's tied to which name, for tests. */
    public static synchronized void forget() {
        known = new HashMap<>();
        save(known);
    }
}
