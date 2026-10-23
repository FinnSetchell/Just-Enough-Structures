package com.finndog.justenoughstructures.client.screen;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Undo and redo history for the loot editor. Each step records the selection at the time, so undo
 * shows where the change was. No screen dependencies, so game tests can use it.
 */
public final class EditHistory {
    /** How many changes are kept to go back through. */
    static final int MOST = 200;

    /** A table state and the selection to restore with it: pool, entry, and a nested entry's path. */
    public record Step(String table, int pool, int entry, String child) {
        public Step(String table, int pool, int entry) {
            this(table, pool, entry, "");
        }
    }

    private final Deque<Step> back = new ArrayDeque<>();
    private final Deque<Step> forward = new ArrayDeque<>();
    private String now;

    /** Clears the history and starts from the table as opened. Null if there's no table yet. */
    public void start(String table) {
        back.clear();
        forward.clear();
        now = table;
    }

    /** Records the table after a change made with this pool and entry selected. An unchanged table is ignored. */
    public void changed(String table, int pool, int entry) {
        changed(table, pool, entry, "");
    }

    /** The same, with a nested entry selected: {@code child} is its path from the pool's top-level entry. */
    public void changed(String table, int pool, int entry, String child) {
        if (now == null) {
            now = table;
            return;
        }
        if (table.equals(now)) {
            return;
        }
        back.push(new Step(now, pool, entry, child));
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

    /** Returns the step before the last change, or null if there's nothing to undo. */
    public Step undo() {
        return move(back, forward);
    }

    /** Returns the step after the last undone change, or null if there's nothing to redo. */
    public Step redo() {
        return move(forward, back);
    }

    private Step move(Deque<Step> from, Deque<Step> to) {
        if (from.isEmpty()) {
            return null;
        }
        Step step = from.pop();
        to.push(new Step(now, step.pool(), step.entry(), step.child()));
        now = step.table();
        return step;
    }
}
