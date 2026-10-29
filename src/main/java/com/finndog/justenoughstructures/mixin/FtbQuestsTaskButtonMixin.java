package com.finndog.justenoughstructures.mixin;

import com.finndog.justenoughstructures.compat.ftbquests.FtbQuestsLink;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbquests.client.gui.quests.TaskButton;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >=26.1 {
/*import dev.ftb.mods.ftblibrary.client.gui.widget.Button;
import dev.ftb.mods.ftbquests.quest.task.TaskClient;
*///?} else {
import dev.ftb.mods.ftblibrary.ui.Button;
//?}

/**
 * Lets a Find Structure task in FTB Quests' quest book open the browser (FtbQuestsLink). FTB Quests
 * has no event for a task being clicked, so this wraps the call its task button makes for a left
 * click, and the one that fills in the task's tooltip. Alt-click editing and the right-click menu
 * never reach the first.
 *
 * <p>Only these FTB Quests methods, whose descriptors name no game classes, so nothing is remapped.
 * Listed by JesMixinPlugin only where FTB Quests is installed, and both hooks use require = 0, so an
 * FTB Quests that changes these methods loses the link instead of failing to load.
 */
@Mixin(value = TaskButton.class, remap = false)
public abstract class FtbQuestsTaskButtonMixin {
    //? if >=26.1 {
    /*// From 26.1 a task's click goes through the TaskClient its class picks.
    @WrapOperation(method = "onClicked", require = 0, at = @At(value = "INVOKE",
            target = "Ldev/ftb/mods/ftbquests/quest/task/TaskClient;onButtonClicked(Ldev/ftb/mods/ftbquests/quest/task/Task;Ldev/ftb/mods/ftblibrary/client/gui/widget/Button;Z)V"))
    private void justenoughstructures$openStructure(TaskClient client, Task task, Button button, boolean canClick, Operation<Void> original) {
        if (!FtbQuestsLink.click(task, button)) {
            original.call(client, task, button, canClick);
        }
    }
    *///?} else {
    @WrapOperation(method = "onClicked", require = 0, at = @At(value = "INVOKE",
            target = "Ldev/ftb/mods/ftbquests/quest/task/Task;onButtonClicked(Ldev/ftb/mods/ftblibrary/ui/Button;Z)V"))
    private void justenoughstructures$openStructure(Task task, Button button, boolean canClick, Operation<Void> original) {
        if (!FtbQuestsLink.click(task, button)) {
            original.call(task, button, canClick);
        }
    }
    //?}

    @WrapOperation(method = "addMouseOverText", require = 0, at = @At(value = "INVOKE",
            target = "Ldev/ftb/mods/ftbquests/quest/task/Task;addMouseOverText(Ldev/ftb/mods/ftblibrary/util/TooltipList;Ldev/ftb/mods/ftbquests/quest/TeamData;)V"))
    private void justenoughstructures$addHint(Task task, TooltipList tooltip, TeamData team, Operation<Void> original) {
        original.call(task, tooltip, team);
        FtbQuestsLink.hint(task, tooltip);
    }
}
