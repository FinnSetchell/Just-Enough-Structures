package com.finndog.justenoughstructures.server;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.Players;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /jes open [structure]}: opens the browser, on a structure if one is named. Anyone can use
 * it, so guidebooks and chat links can point players at a structure.
 *
 * <p>{@code /jes reload [loot]}: builds again what JES keeps on the server, or with {@code loot} only
 * the loot index and loot chances, kept on disk too, for when they don't show a change. For those who
 * can use Pack tools, and the server console. Not while Pack tools has changes waiting for a /reload,
 * as changed containers and spawners only take effect with one, and the index would be built from
 * changes the structures don't have yet.
 */
public final class JesCommands {
    private JesCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("jes")
                .then(Commands.literal("open")
                        .executes(context -> open(context.getSource(), null))
                        .then(Commands.argument("structure", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                        visible(context.getSource().getServer()), builder))
                                .executes(context -> open(context.getSource(), ResourceLocationArgument.getId(context, "structure")))))
                .then(Commands.literal("reload")
                        .requires(JesCommands::mayReload)
                        .executes(context -> reload(context.getSource(), true))
                        .then(Commands.literal("loot")
                                .executes(context -> reload(context.getSource(), false)))));
    }

    /** Those who can use Pack tools, and sources that aren't players at operator level, as /reload needs: the console, command blocks, functions. */
    private static boolean mayReload(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player != null ? PackToolsAccess.allowed(player) : Players.hasPermission(source, 2);
    }

    // The text has an English fallback as well, as a player without JES has no lang file for it.
    private static int reload(CommandSourceStack source, boolean everything) {
        if (everything) {
            // First, so a change in it that waits for a /reload is refused below like Pack tools' own.
            JesServer.readSettings(source.getServer());
        }
        if (PackToolsServer.anyWaiting()) {
            source.sendFailure(Component.translatableWithFallback("commands.justenoughstructures.reload.waiting",
                    "Pack tools has changes waiting for /reload. Run /reload first, which brings the loot index up to date too"));
            return 0;
        }
        JustEnoughStructures.LOGGER.info("{} asked for {} to be built again from nothing", source.getTextName(),
                everything ? "everything Just Enough Structures keeps" : "the loot index and loot chances");
        source.sendSuccess(() -> everything
                ? Component.translatableWithFallback("commands.justenoughstructures.reload.all",
                        "Building everything Just Enough Structures keeps again, starting with the loot index")
                : Component.translatableWithFallback("commands.justenoughstructures.reload.loot",
                        "Building the loot index and loot chances again"), true);
        JesServer.rebuild(source.getServer(), everything, built -> {
            // In the log too, as over RCON the command has returned before this comes.
            if (built) {
                JustEnoughStructures.LOGGER.info("The loot index is built again");
            } else {
                JustEnoughStructures.LOGGER.warn("The loot index couldn't be built again; the error is above");
            }
            if (built) {
                source.sendSuccess(() -> Component.translatableWithFallback("commands.justenoughstructures.reload.done",
                        "The loot index is built again"), true);
            } else {
                source.sendFailure(Component.translatableWithFallback("commands.justenoughstructures.reload.failed",
                        "The loot index couldn't be built again. The log says why"));
            }
        });
        return 1;
    }

    /** Every structure the browser shows: all of them less the ones the server hides. */
    private static Iterable<ResourceLocation> visible(MinecraftServer server) {
        return server.registryAccess().registryOrThrow(Registries.STRUCTURE).keySet().stream()
                .filter(id -> !ServerConfig.hides(id)).toList();
    }

    private static int open(CommandSourceStack source, ResourceLocation structure) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!JesNetwork.canSend(player, JesNetwork.OPEN_BROWSER)) {
            // Their game has another version of JES, which shows its own text for this key, or no JES and
            // so no lang file, which shows the fallback. The fallback says the same as en_us.json.
            source.sendFailure(Component.translatableWithFallback("commands.justenoughstructures.open.no_mod",
                    "To open the structure browser, install the same version of Just Enough Structures as the server"));
            return 0;
        }
        if (structure != null && (ServerConfig.hides(structure)
                || !source.getServer().registryAccess().registryOrThrow(Registries.STRUCTURE).containsKey(structure))) {
            source.sendFailure(Component.translatable("commands.justenoughstructures.open.unknown", structure.toString()));
            return 0;
        }
        FriendlyByteBuf buf = Blobs.buffer(source.registryAccess());
        Codecs.writeOpenBrowser(buf, structure);
        JesNetwork.send(player, JesNetwork.OPEN_BROWSER, buf);
        return 1;
    }
}
