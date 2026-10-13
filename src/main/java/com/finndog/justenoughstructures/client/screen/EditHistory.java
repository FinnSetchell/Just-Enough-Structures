package com.finndog.justenoughstructures.client.screen;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * What a table was before each change made to it in the editor, to go back to and forward again.
 * Each step also says what was picked when its change was made, so going back shows where the
 * change was. Plain, with nothing of the screen's in it, so the game tests can run it.
 */
public final class EditHistory {
    /** How many changes are kept to go back through. */
    static final int MOST = 200;

    /** A table to go to, and the pool and entry to show it with. */
    public record Step(String table, int pool, int entry) {
    }

    private final Deque<Step> back = new ArrayDeque<>();
    private final Deque<Step> forward = new ArrayDeque<>();
    private String now;

    /** Starts over from a table as it was opened, which there's nothing to go back to before. Null for none yet. */
    public void start(String table) {
        back.clear();
        forward.clear();
        now = table;
    }

    /** Notes the table after a change, made with this pool and entry picked. One that's the same as before isn't a change. */
    public void changed(String table, int pool, int entry) {
        if (now == null) {
            now = table;
            return;
        }
        if (table.equals(now)) {
            return;
        }
        back.push(new Step(now, pool, entry));
        while (back.size() > MOST) {
            back.removeLast();
        }
        forward.clear();
        now = table;
    }

    public boolean canUndo() {
        return !back.isEmpty();
    }

    public boolean canRedo() {
        return !forward.isEmpty();
    }

    /** The table before the last change, or null if there's none to take back. */
    public Step undo() {
        return move(back, forward);
    }

    /** The table after the change last taken back, or null if there's none to put back. */
    public Step redo() {
        return move(forward, back);
    }

    private Step move(Deque<Step> from, Deque<Step> to) {
        if (from.isEmpty()) {
            return null;
        }
        Step step = from.pop();
        to.push(new Step(now, step.pool(), step.entry()));
        now = step.table();
        return step;
    }
}
