package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.client.ClientState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Failures a player would notice reach the game's log, which players send with their reports, and not
 * only JES's debug log, which is off unless they turn it on. Here the browser's settings and notes:
 * a settings file that can't be read (the browser falls back to its defaults), one that can't be
 * saved, and a notes file that can't be read.
 */
public final class GameLogTests {
    private GameLogTests() {
    }

    public static void failuresPlayersNoticeReachTheGameLog(GameTestHelper helper) {
        ClientState.load();
        try {
            Path dir = Files.createTempDirectory("jes-game-log");

            Path settings = dir.resolve("state.json");
            Files.writeString(settings, "{ \"spin\": tru");
            ClientState.read(settings);
            helper.assertTrue(JesLog.reportedToGameLog("settings-unreadable"),
                    "a settings file that can't be read went only to the debug log");

            // A file where the folder should be, so the settings can't be saved there.
            Path blocker = Files.writeString(dir.resolve("blocker"), "");
            ClientState.write(blocker.resolve("state.json"));
            helper.assertTrue(JesLog.reportedToGameLog("settings-unsaved"),
                    "settings that couldn't be saved went only to the debug log");

            Path notes = dir.resolve("notes.json");
            Files.writeString(notes, "{ \"notes\": [");
            ClientState.readNotes(notes);
            helper.assertTrue(JesLog.reportedToGameLog("notes-unreadable"),
                    "a notes file that can't be read went only to the debug log");
        } catch (IOException e) {
            throw new AssertionError("couldn't use a temporary file", e);
        }
        helper.succeed();
    }
}
