package com.finndog.justenoughstructures.compat.ftbquests;

import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbquests.quest.task.Task;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
//? if >=26.1 {
/*import dev.ftb.mods.ftblibrary.client.gui.widget.Button;
*///?} else {
import dev.ftb.mods.ftblibrary.ui.Button;
//?}

/**
 * Links FTB Quests to the browser: a left click on a Find Structure task opens the browser over the
 * quest book, on its structure, or for a tag searching for the tag's structures. The task's tooltip
 * says it can. Only for what the server's JES lists, so a task for a hidden structure, a tag with none
 * listed, or one on a server without JES does what it did before. Called from
 * FtbQuestsTaskButtonMixin. Client only.
 */
public final class FtbQuestsLink {
    private FtbQuestsLink() {
    }

    /** Handles a left click on {@code task}'s button. False leaves the click to FTB Quests. */
    public static boolean click(Task task, Button button) {
        TaskTarget target = TaskTarget.of(task);
        if (target == null || !ClientRequests.serverSupported()) {
            return false;
        }
        CompletableFuture<List<StructureCatalog.Entry>> catalog = ClientRequests.catalog();
        List<StructureCatalog.Entry> listed = arrived(catalog);
        if (listed != null && !shows(listed, target)) {
            return false;
        }
        button.playClickSound();
        Minecraft minecraft = Minecraft.getInstance();
        Screen quests = minecraft.screen;
        // The list is usually here by now, asked for when the tooltip showed. If not, the browser opens
        // when it comes, unless the player has moved on.
        catalog.thenAccept(entries -> {
            if (minecraft.screen == quests && shows(entries, target)) {
                if (target.tag()) {
                    JesScreen.startSearching("#" + target.id());
                } else {
                    JesScreen.startOn(target.id());
                }
                minecraft.setScreen(new JesScreen(quests));
            }
        });
        return true;
    }

    /** Adds a line to {@code task}'s tooltip when a click on it would open the browser. */
    public static void hint(Task task, TooltipList tooltip) {
        TaskTarget target = TaskTarget.of(task);
        if (target == null || !ClientRequests.serverSupported()) {
            return;
        }
        // Asks for the structure list the first time, so the line shows once it has come.
        List<StructureCatalog.Entry> listed = arrived(ClientRequests.catalog());
        if (listed != null && shows(listed, target)) {
            String key = target.tag() ? "screen.justenoughstructures.ftb_quests_open_tag" : "screen.justenoughstructures.ftb_quests_open";
            tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
    }

    /** The structure list if it has come, or null. */
    private static List<StructureCatalog.Entry> arrived(CompletableFuture<List<StructureCatalog.Entry>> catalog) {
        return catalog.isDone() && !catalog.isCompletedExceptionally() ? catalog.join() : null;
    }

    /**
     * Whether the browser can show what {@code target} asks for: a listed structure, or a tag with
     * listed structures in it. Servers from before tag searches send no tags.
     */
    private static boolean shows(List<StructureCatalog.Entry> entries, TaskTarget target) {
        if (target.tag()) {
            Map<ResourceLocation, List<ResourceLocation>> tags = ClientRequests.structureTags();
            return tags != null && tags.containsKey(target.id());
        }
        return entries.stream().anyMatch(entry -> entry.id().equals(target.id()));
    }
}
