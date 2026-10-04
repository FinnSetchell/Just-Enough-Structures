package com.finndog.justenoughstructures.server;

import com.finndog.justenoughstructures.network.JesNetwork;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.buffer.Unpooled;
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
                                .executes(context -> open(context.getSource(), ResourceLocationArgument.getId(context, "structure"))))));
    }

    /** Every structure the browser shows: all of them less the ones the server hides. */
    private static Iterable<ResourceLocation> visible(MinecraftServer server) {
        return server.registryAccess().registryOrThrow(Registries.STRUCTURE).keySet().stream()
                .filter(id -> !ServerConfig.hides(id)).toList();
    }

    private static int open(CommandSourceStack source, ResourceLocation structure) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!JesNetwork.canSend(player, JesNetwork.OPEN_BROWSER)) {
            // Their game doesn't have the mod, so it can't show this text in their language either.
            source.sendFailure(Component.translatableWithFallback("commands.justenoughstructures.open.no_mod",
                    "Just Enough Structures isn't installed on your game, so there's no structure browser to open."));
            return 0;
        }
        if (structure != null && (ServerConfig.hides(structure)
                || !source.getServer().registryAccess().registryOrThrow(Registries.STRUCTURE).containsKey(structure))) {
            source.sendFailure(Component.translatable("commands.justenoughstructures.open.unknown", structure.toString()));
            return 0;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(structure != null);
        if (structure != null) {
            buf.writeResourceLocation(structure);
        }
        JesNetwork.send(player, JesNetwork.OPEN_BROWSER, buf);
        return 1;
    }
}
