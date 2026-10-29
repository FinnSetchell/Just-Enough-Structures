package com.finndog.justenoughstructures.compat.ftbquests;

import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbquests.quest.task.Task;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//? if >=26.1 {
/*import dev.ftb.mods.ftblibrary.client.gui.widget.Button;
*///?} else {
import dev.ftb.mods.ftblibrary.ui.Button;
//?}

/**
 * Links FTB Quests to the browser: a left click on a Find Structure task opens the browser on its
 * structure, over the quest book, and the task's tooltip says it can. Only for structures the
 * server's JES lists, so a task for a hidden structure, or on a server without JES, does what it did
 * before. Called from FtbQuestsTaskButtonMixin. Client only.
 */
public final class FtbQuestsLink {
    private FtbQuestsLink() {
    }

    /** Handles a left click on {@code task}'s button. False leaves the click to FTB Quests. */
    public static boolean click(Task task, Button button) {
        TaskTarget target = TaskTarget.of(task);
        if (!opens(target)) {
            return false;
        }
        CompletableFuture<List<StructureCatalog.Entry>> catalog = ClientRequests.catalog();
        List<StructureCatalog.Entry> listed = arrived(catalog);
        if (listed != null && !lists(listed, target)) {
            return false;
        }
        button.playClickSound();
        Minecraft minecraft = Minecraft.getInstance();
        Screen quests = minecraft.screen;
        // The list is usually here by now, asked for when the tooltip showed. If not, the browser opens
        // when it comes, unless the player has moved on.
        catalog.thenAccept(entries -> {
            if (minecraft.screen == quests && lists(entries, target)) {
                JesScreen.startOn(target.id());
                minecraft.setScreen(new JesScreen(quests));
            }
        });
        return true;
    }

    /** Adds a line to {@code task}'s tooltip when a click on it would open the browser. */
    public static void hint(Task task, TooltipList tooltip) {
        TaskTarget target = TaskTarget.of(task);
        if (!opens(target)) {
            return;
        }
        // Asks for the structure list the first time, so the line shows once it has come.
        List<StructureCatalog.Entry> listed = arrived(ClientRequests.catalog());
        if (listed != null && lists(listed, target)) {
            tooltip.add(Component.translatable("screen.justenoughstructures.ftb_quests_open").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Whether the browser could show a target like this one at all. */
    private static boolean opens(TaskTarget target) {
        return target != null && !target.tag() && ClientRequests.serverSupported();
    }

    /** The structure list if it has come, or null. */
    private static List<StructureCatalog.Entry> arrived(CompletableFuture<List<StructureCatalog.Entry>> catalog) {
        return catalog.isDone() && !catalog.isCompletedExceptionally() ? catalog.join() : null;
    }

    private static boolean lists(List<StructureCatalog.Entry> entries, TaskTarget target) {
        return entries.stream().anyMatch(entry -> entry.id().equals(target.id()));
    }
}
