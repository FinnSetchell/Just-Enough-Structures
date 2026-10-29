package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.mojang.blaze3d.platform.InputConstants;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.client.gui.quests.TaskButton;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.task.Task;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
//? if >=26.1 {
/*import dev.ftb.mods.ftblibrary.client.gui.widget.Panel;
import dev.ftb.mods.ftblibrary.client.gui.widget.ScreenWrapper;
import dev.ftb.mods.ftblibrary.client.gui.widget.Widget;
*///?} else {
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import dev.ftb.mods.ftblibrary.ui.Widget;
//?}

/**
 * FTB Quests' Find Structure tasks and the browser. Adds a chapter to the world's quests with one quest
 * of three tasks: a structure the browser lists, a tag, and a structure no mod adds. Then, in the quest
 * book, points at each task and clicks it with the mouse as a player would, and checks what its tooltip
 * says and what opened. The listed structure opens the browser on it, over the quest book, and Esc goes
 * back. The tag opens it searching for the tag, on one of its structures. The structure no mod adds
 * leaves the quest book as it is. Removes the chapter again at the end. Needs FTB Quests, which the dev
 * runtime has on the builds it exists for (on 26.1 NeoForge with -Pftbquests).
 */
final class FtbQuestsScenario {
    private static final String CHAPTER = "jes_scenario";
    // Ids FTB Quests would write as hex, made up for this chapter.
    private static final long CHAPTER_ID = 0x4A45530000000001L;
    private static final long QUEST_ID = 0x4A45530000000002L;
    private static final long LISTED_TASK = 0x4A45530000000003L;
    private static final long TAG_TASK = 0x4A45530000000004L;
    private static final long UNKNOWN_TASK = 0x4A45530000000005L;
    private static final ResourceLocation PYRAMID = Ids.parse("desert_pyramid");
    private static final String HINT = "screen.justenoughstructures.ftb_quests_open";
    private static final String TAG_HINT = "screen.justenoughstructures.ftb_quests_open_tag";

    private FtbQuestsScenario() {
    }

    static Director build(Minecraft mc) {
        if (!JustEnoughStructures.modVersions().containsKey("ftbquests")) {
            throw new UnsupportedOperationException("needs FTB Quests");
        }
        Director d = new Director(mc);
        d.then(run(() -> chapter(mc, true)))
                .then(until(() -> quest() != null, 200))
                .then(check(() -> quest() != null, "FTB Quests didn't load the test chapter"))
                // A listed structure: the tooltip says a click opens it, and it does.
                .then(openQuest(mc))
                .then(moveTo(() -> centre(button(mc, LISTED_TASK)), 10))
                .then(pause(10))
                .then(check(() -> hinted(mc, LISTED_TASK, HINT), "the desert pyramid's task has no hint in its tooltip"))
                .then(shoot("q01_listed_task_tooltip"))
                .then(click())
                .then(until(() -> browser(mc) != null, 100))
                .then(check(() -> browser(mc) != null, "clicking the desert pyramid's task didn't open the browser"))
                .then(until(() -> browser(mc).idle(), 400))
                .then(check(() -> PYRAMID.equals(browser(mc).selectedId()), "the browser opened on something other than the desert pyramid"))
                .then(shoot("q02_browser_on_the_pyramid"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> questScreen(mc) != null, 40))
                .then(check(() -> questScreen(mc) != null, "Esc in the browser didn't go back to the quest book"))
                // A structure no mod adds: no hint, and a click leaves the quest book open.
                .then(openQuest(mc))
                .then(moveTo(() -> centre(button(mc, UNKNOWN_TASK)), 10))
                .then(pause(10))
                .then(check(() -> !hinted(mc, UNKNOWN_TASK, HINT), "a task for a structure no mod adds says it opens the browser"))
                .then(click())
                .then(pause(30))
                .then(check(() -> questScreen(mc) != null, "clicking a task for a structure no mod adds left the quest book"))
                .then(shoot("q03_still_in_the_quest_book"))
                // A tag: the browser opens searching for it, on one of its structures.
                .then(moveTo(() -> centre(button(mc, TAG_TASK)), 10))
                .then(pause(10))
                .then(check(() -> hinted(mc, TAG_TASK, TAG_HINT), "the village tag's task has no hint in its tooltip"))
                .then(click())
                .then(until(() -> browser(mc) != null, 100))
                .then(check(() -> browser(mc) != null, "clicking the village tag's task didn't open the browser"))
                .then(until(() -> browser(mc).idle(), 400))
                .then(check(() -> "#minecraft:village".equals(browser(mc).searchText()), "the browser opened without searching for the village tag"))
                .then(check(() -> browser(mc).selectedId() != null && browser(mc).selectedId().getPath().startsWith("village_"),
                        "the browser opened on something other than a village"))
                .then(shoot("q04_browser_on_the_village_tag"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> questScreen(mc) != null, 40))
                .then(check(() -> questScreen(mc) != null, "Esc in the browser didn't go back to the quest book"))
                .then(run(() -> chapter(mc, false)));
        return d;
    }

    /** Opens the quest book on the test quest, unless it's showing already. */
    private static Director.Action openQuest(Minecraft mc) {
        return Director.chain(
                run(() -> {
                    if (button(mc, LISTED_TASK) == null) {
                        ClientQuestFile.openGui(quest(), true);
                    }
                }),
                until(() -> button(mc, LISTED_TASK) != null, 100),
                shoot("q00_quest_book"),
                run(() -> {
                    if (button(mc, LISTED_TASK) == null) {
                        throw new IllegalStateException("the quest book didn't show the test quest's tasks: " + shown(mc));
                    }
                }),
                pause(10));
    }

    /** What's on screen instead of the test quest's tasks, for a failure to say. */
    private static String shown(Minecraft mc) {
        QuestScreen quests = questScreen(mc);
        if (quests == null) {
            return "the screen open is " + (mc.screen == null ? "none" : mc.screen.getClass().getName());
        }
        Quest viewed = quests.viewQuestPanel.getViewedQuest();
        StringBuilder widgets = new StringBuilder();
        describe(quests.viewQuestPanel, widgets);
        return "the quest book is open, viewing " + (viewed == null ? "no quest" : hex(viewed.id)) + ", with " + widgets;
    }

    private static void describe(Panel panel, StringBuilder out) {
        out.append(panel.getClass().getSimpleName()).append('[');
        for (Widget widget : panel.getWidgets()) {
            if (widget instanceof Panel inner) {
                describe(inner, out);
            } else {
                out.append(widget.getClass().getSimpleName());
            }
            out.append(' ');
        }
        out.append(']');
    }

    /** A step that fails the scenario unless {@code holds}. */
    private static Director.Action check(BooleanSupplier holds, String failure) {
        return run(() -> {
            if (!holds.getAsBoolean()) {
                throw new IllegalStateException(failure);
            }
        });
    }

    /**
     * Writes the test chapter into the quests FTB Quests loaded, or deletes it, and has FTB Quests
     * reload them, as a pack maker would with /ftbquests reload.
     */
    private static void chapter(Minecraft mc, boolean add) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            //? if >=26.1 {
            /*Path folder = ServerQuestFile.getInstance().getFolder().resolve("chapters");
            Path file = folder.resolve(CHAPTER + ".json5");
            String text = """
                    {
                      id: "%s",
                      filename: "%s",
                      group: "",
                      order_index: 1000,
                      title: "Just Enough Structures",
                      quests: [{
                        id: "%s",
                        x: 0.0,
                        y: 0.0,
                        title: "Find these",
                        tasks: [
                          { id: "%s", type: "structure", structure: "minecraft:desert_pyramid" },
                          { id: "%s", type: "structure", structure: "#minecraft:village" },
                          { id: "%s", type: "structure", structure: "jes_scenario:not_a_structure" },
                        ],
                      }],
                    }
                    """;
            *///?} else {
            Path folder = ServerQuestFile.INSTANCE.getFolder().resolve("chapters");
            Path file = folder.resolve(CHAPTER + ".snbt");
            String text = """
                    {
                    	id: "%s"
                    	filename: "%s"
                    	group: ""
                    	order_index: 1000
                    	title: "Just Enough Structures"
                    	quests: [{
                    		id: "%s"
                    		x: 0.0d
                    		y: 0.0d
                    		title: "Find these"
                    		tasks: [
                    			{ id: "%s", type: "structure", structure: "minecraft:desert_pyramid" }
                    			{ id: "%s", type: "structure", structure: "#minecraft:village" }
                    			{ id: "%s", type: "structure", structure: "jes_scenario:not_a_structure" }
                    		]
                    	}]
                    }
                    """;
            //?}
            try {
                if (add) {
                    Files.createDirectories(folder);
                    Files.writeString(file, text.formatted(hex(CHAPTER_ID), CHAPTER, hex(QUEST_ID), hex(LISTED_TASK), hex(TAG_TASK), hex(UNKNOWN_TASK)),
                            StandardCharsets.UTF_8);
                } else {
                    Files.deleteIfExists(file);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "ftbquests reload");
        });
    }

    private static String hex(long id) {
        return String.format("%016X", id);
    }

    /** The test quest as the client has it, or null before it has come. */
    private static Quest quest() {
        //? if >=26.1 {
        /*ClientQuestFile file = ClientQuestFile.exists() ? ClientQuestFile.getInstance() : null;
        *///?} else {
        ClientQuestFile file = ClientQuestFile.exists() ? ClientQuestFile.INSTANCE : null;
        //?}
        return file == null ? null : file.getQuest(QUEST_ID);
    }

    /** The quest book, or null when it isn't the screen open. */
    private static QuestScreen questScreen(Minecraft mc) {
        return mc.screen instanceof ScreenWrapper wrapper && wrapper.getGui() instanceof QuestScreen quests ? quests : null;
    }

    /** The open quest book's button for task {@code id}, or null. */
    private static TaskButton button(Minecraft mc, long id) {
        QuestScreen quests = questScreen(mc);
        return quests == null ? null : find(quests.viewQuestPanel, id);
    }

    private static TaskButton find(Panel panel, long id) {
        for (Widget widget : panel.getWidgets()) {
            if (widget instanceof TaskButton button && ((Task) Reflect.get(button, "task")).id == id) {
                return button;
            }
            if (widget instanceof Panel inner) {
                TaskButton found = find(inner, id);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static int[] centre(Widget widget) {
        return widget == null ? new int[]{0, 0} : new int[]{widget.getX() + widget.width / 2, widget.getY() + widget.height / 2};
    }

    /** Whether the tooltip of task {@code id}'s button has the line {@code key}, saying a click opens the browser. */
    private static boolean hinted(Minecraft mc, long id, String key) {
        TaskButton button = button(mc, id);
        if (button == null) {
            throw new IllegalStateException("the quest book isn't showing task " + hex(id));
        }
        TooltipList tooltip = new TooltipList();
        button.addMouseOverText(tooltip);
        String hint = Component.translatable(key).getString();
        return tooltip.getLines().stream().anyMatch(line -> line.getString().equals(hint));
    }
}
