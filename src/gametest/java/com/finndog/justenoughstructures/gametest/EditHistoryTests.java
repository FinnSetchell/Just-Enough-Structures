package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.client.screen.EditHistory;
import net.minecraft.gametest.framework.GameTestHelper;

/** Going back and forward through the changes made to a table in the loot editor. */
public final class EditHistoryTests {
    private EditHistoryTests() {
    }

    /** Changes come back newest first, each with what was picked when it was made, and go forward again the same way. */
    public static void changesAreTakenBackAndPutBack(GameTestHelper helper) {
        EditHistory history = new EditHistory();
        history.start("a");
        helper.assertTrue(!history.canUndo() && !history.canRedo() && history.undo() == null && history.redo() == null, "a table just opened had something to go back or forward to");
        history.changed("b", 0, 1);
        history.changed("b", 0, 2);
        history.changed("c", 1, -1);
        EditHistory.Step first = history.undo();
        helper.assertTrue(new EditHistory.Step("b", 1, -1).equals(first), "taking back the change to c gave " + first);
        EditHistory.Step second = history.undo();
        helper.assertTrue(new EditHistory.Step("a", 0, 1).equals(second) && !history.canUndo(), "taking back the change to b gave " + second
                + ", a step for a table that hadn't changed among them");
        EditHistory.Step again = history.redo();
        helper.assertTrue(new EditHistory.Step("b", 0, 1).equals(again), "putting b back gave " + again);
        EditHistory.Step last = history.redo();
        helper.assertTrue(new EditHistory.Step("c", 1, -1).equals(last) && !history.canRedo(), "putting c back gave " + last);
        helper.succeed();
    }

    /** A change made after going back is where the table goes on from: what was taken back before it is gone. */
    public static void aNewChangeEndsWhatCouldBePutBack(GameTestHelper helper) {
        EditHistory history = new EditHistory();
        history.start("a");
        history.changed("b", -1, -1);
        history.changed("c", -1, -1);
        history.undo();
        history.changed("d", 2, 3);
        helper.assertTrue(!history.canRedo(), "c could still be put back after a new change");
        EditHistory.Step back = history.undo();
        helper.assertTrue(new EditHistory.Step("b", 2, 3).equals(back), "taking back the change to d gave " + back);
        helper.succeed();
    }

    /** Only so many changes are kept, the oldest going first, and a history with no table yet takes its first as where it starts. */
    public static void oldChangesAreLetGo(GameTestHelper helper) {
        EditHistory history = new EditHistory();
        history.start(null);
        history.changed("0", -1, -1);
        helper.assertTrue(!history.canUndo(), "the first table of a history with none was a change to take back");
        for (int i = 1; i <= 250; i++) {
            history.changed(String.valueOf(i), -1, -1);
        }
        int steps = 0;
        EditHistory.Step oldest = null;
        for (EditHistory.Step step = history.undo(); step != null; step = history.undo()) {
            oldest = step;
            steps++;
        }
        helper.assertTrue(steps == 200 && oldest.table().equals("50"), steps + " changes were kept, back to " + (oldest == null ? null : oldest.table()));
        helper.succeed();
    }
}
