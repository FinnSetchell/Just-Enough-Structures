package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.Nbt;
import com.finndog.justenoughstructures.Regs;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.capture.TrialSpawners;
import com.finndog.justenoughstructures.client.ClientRequests;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A spawner opened from the preview or the Mobs tab: every mob it can make with each one's chance,
 * and how often it spawns them. A trial spawner's list goes on to what it makes once ominous, when
 * that's different. It's drawn like the chest popup and sits beside the preview the same way, with
 * arrows to the other spawners that make the same.
 */
final class SpawnerPopup extends SidePopup {
    /** The list of mobs scrolls once it's taller than this. */
    private static final int MOST_LIST = 108;
    private static final int ICON = 14;
    private static final ItemStack SPAWNER = new ItemStack(Items.SPAWNER);

    /** What the links on the popup do. */
    enum Action {
        /** Open the spawner in Pack tools. */
        TOOLS
    }

    /**
     * A row of the list: a mob it can make, with the share of spawns or spawners that get it, out of
     * {@code of} mobs, and whether it's given gear; or with no mob, a line about the mobs after it.
     */
    private record Row(String mob, float chance, int of, boolean gear, Component text) {
        static Row text(Component text) {
            return new Row(null, 0, 0, false, text);
        }
    }

    final StructureSnapshot.Spawner spawner;
    final SpawnerKind kind;
    private final List<Row> rows = new ArrayList<>();
    /** How many it spawns and how often, or null when its block entity doesn't say. */
    private final Component timing;
    /** Whether this player sees Pack tools: its shortcut, and what a changed spawner made before. */
    final boolean packTools = ClientRequests.showsPackTools();
    /**
     * Set while the player is picking a spawner for Pack tools to change: the button that opens it in
     * Pack tools is pointed out, so it's learnt as the way to change one.
     */
    boolean picking;
    private int scroll;
    private final PopupLinks<Action> links = new PopupLinks<>(Action.class);

    SpawnerPopup(StructureSnapshot.Spawner spawner, CompoundTag tag, Component title, int index, int count) {
        super(title, index, count);
        this.spawner = spawner;
        this.kind = tag == null ? new SpawnerKind(SpawnerKind.Type.MOB, spawner.mob().isEmpty() ? Map.of() : Map.of(spawner.mob(), 1)) : SpawnerKind.of(tag);
        boolean ominous = kind.trial() && !kind.sameOminous() && !kind.ominous().isEmpty();
        if (kind.mobs().isEmpty() && ominous) {
            rows.add(Row.text(Component.translatable("screen.justenoughstructures.hover_spawns_nothing")));
        }
        addRows(kind.mobs(), Set.of());
        if (ominous) {
            rows.add(Row.text(Component.translatable(kind.gear().isEmpty() ? "screen.justenoughstructures.spawner.ominous"
                    : "screen.justenoughstructures.spawner.ominous_gear")));
            addRows(kind.ominous(), kind.gear());
        }
        timing = tag == null ? null : timing(tag);
    }

    /** A row for each mob, likeliest first. */
    private void addRows(Map<String, Integer> weights, Set<String> gear) {
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        List<Row> added = new ArrayList<>();
        weights.forEach((mob, weight) -> added.add(new Row(mob, (float) weight / Math.max(1, total), weights.size(), gear.contains(mob), null)));
        added.sort((a, b) -> Float.compare(b.chance(), a.chance()));
        rows.addAll(added);
    }

    /** "Up to 4 at a time, every 10 to 40 seconds, while a player is within 16 blocks", from a spawner's settings. */
    private static Component timing(CompoundTag tag) {
        for (String key : new String[]{"MinSpawnDelay", "MaxSpawnDelay", "SpawnCount", "RequiredPlayerRange"}) {
            if (!Nbt.hasNumber(tag, key)) {
                return null;
            }
        }
        int min = Nbt.getShort(tag, "MinSpawnDelay");
        int max = Nbt.getShort(tag, "MaxSpawnDelay");
        int spawnCount = Nbt.getShort(tag, "SpawnCount");
        int range = Nbt.getShort(tag, "RequiredPlayerRange");
        return min == max
                ? Component.translatable("screen.justenoughstructures.spawner.timing_exact", spawnCount, seconds(min), range)
                : Component.translatable("screen.justenoughstructures.spawner.timing", spawnCount, seconds(min), seconds(max), range);
    }

    private static String seconds(int ticks) {
        return ticks % 20 == 0 ? String.valueOf(ticks / 20) : String.format("%.1f", ticks / 20f);
    }

    /** What it makes, in a line: the mob, the one this spawner got from its list, or a mix. */
    private Component summary() {
        return switch (kind.type()) {
            case MOB -> StructureNames.mob(kind.mob());
            case POOL -> overview() ? Component.translatable("screen.justenoughstructures.spawner.each_got")
                    : Component.translatable("screen.justenoughstructures.spawner.got", StructureNames.mob(spawner.mob()));
            case MIX -> Component.translatable("screen.justenoughstructures.spawner.mix");
        };
    }

    /**
     * The lines under the summary: what a changed spawner made before, what a trial spawner makes
     * once it's ominous when that's the same mobs, and how often it spawns.
     */
    private List<Line> notes(Font font) {
        List<Line> lines = new ArrayList<>();
        int width = (int) ((WIDTH - 14) / Gui.fineScale());
        String changedFrom = spawner.source() == null ? null : spawner.source().patchedFrom();
        if (packTools && changedFrom != null && !overview()) {
            // Made the other kind of spawner: which kind it was says what changed.
            boolean wasTrial = spawner.source().block().equals(TrialSpawners.BLOCK);
            String key = wasTrial == kind.trial() ? "container.changed_from" : wasTrial ? "spawner.was_trial" : "spawner.was_spawner";
            Component note = Component.translatable("screen.justenoughstructures." + key, StructureNames.mob(changedFrom));
            font.split(note, width).forEach(line -> lines.add(new Line(line, ToolsUi.CHANGED)));
        }
        if (kind.trial() && kind.sameOminous() && !kind.mobs().isEmpty()) {
            Component note = Component.translatable(kind.gear().isEmpty() ? "screen.justenoughstructures.spawner.ominous_same"
                    : "screen.justenoughstructures.spawner.ominous_same_gear");
            font.split(note, width).forEach(line -> lines.add(new Line(line, Gui.LABEL_SOFT)));
        }
        if (timing != null) {
            font.split(timing, width).forEach(line -> lines.add(new Line(line, Gui.LABEL_SOFT)));
        }
        return lines;
    }

    private record Line(FormattedCharSequence text, int colour) {
    }

    private int listHeight() {
        return rows.isEmpty() ? OddsList.ROW + 4 : Math.min(MOST_LIST, rows.size() * (OddsList.ROW + 1) + 3);
    }

    private int infoHeight(Font font) {
        int fine = Gui.fineLine(font);
        int height = 5 + ICON + 2 + font.lineHeight + 1 + notes(font).size() * (fine + 1) + 3 + 20 + 6;
        if (Gui.advanced() && kind.type() == SpawnerKind.Type.MOB && !kind.mob().isEmpty()) {
            height += fine + 1;
        }
        return height;
    }

    @Override
    int height(Font font) {
        return 17 + listHeight() + 7 + infoHeight(font);
    }

    /** Scrolls the list of mobs, when the mouse is over the popup. */
    @Override
    boolean scroll(double mouseX, double mouseY, double delta) {
        if (mouseX < x || mouseX >= x + WIDTH) {
            return false;
        }
        scroll = Math.max(0, Math.min(mostScroll(), scroll - (int) (delta * (OddsList.ROW + 1))));
        return true;
    }

    /** How far the list scrolls, or 0 when it all fits. */
    private int mostScroll() {
        return Math.max(0, rows.size() * (OddsList.ROW + 1) - (listHeight() - 4));
    }

    @Override
    ItemStack render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        links.clear();
        hoveredTip = null;
        int body = listHeight();

        // The chest's frame: its title bar, a plain strip for the list, then its bottom edge.
        Gui.blit(g, TEXTURE, x, y, 0, 0, WIDTH, 17);
        for (int filled = 0; filled < body; filled += 12) {
            Gui.blit(g, TEXTURE, x, y + 17 + filled, 0, 127, WIDTH, Math.min(12, body - filled));
        }
        Gui.blit(g, TEXTURE, x, y + 17 + body, 0, 215, WIDTH, 7);
        renderTitle(g, font);

        renderList(g, font, mouseX, mouseY, y + 17, body);

        int infoTop = y + 17 + body + 7;
        Gui.panel(g, x, infoTop, WIDTH, infoHeight(font));
        int cy = infoTop + 5;
        Gui.fine(g, font, Component.translatable("screen.justenoughstructures.spawner.spawns").getString(), x + 7,
                cy + (ICON - Gui.fineLine(font)) / 2, Gui.LABEL_SOFT);
        int iconRight = x + WIDTH - 7;
        if (packTools || picking) {
            toolsIcon(g, iconRight, cy, mouseX, mouseY);
        }
        cy += ICON + 2;
        Gui.drawClipped(g, font, summary().getString(), x + 7, cy, WIDTH - 14, 0xFF202020, false);
        cy += font.lineHeight + 1;
        if (Gui.advanced() && kind.type() == SpawnerKind.Type.MOB && !kind.mob().isEmpty()) {
            Gui.fineClipped(g, font, kind.mob(), x + 7, cy, WIDTH - 14, 0xFF555555);
            cy += Gui.fineLine(font) + 1;
        }
        for (Line line : notes(font)) {
            Gui.scaled(g, font, line.text(), x + 7, cy, line.colour(), Gui.fineScale());
            cy += Gui.fineLine(font) + 1;
        }
        return ItemStack.EMPTY;
    }

    private void renderList(GuiGraphics g, Font font, int mouseX, int mouseY, int bodyTop, int body) {
        int top = bodyTop + 1;
        int left = x + 7;
        int right = x + WIDTH - 7;
        int bottom = top + body - 2;
        Gui.inset(g, left, top - 1, right - left, body, 0xFFB9B9B9);
        if (rows.isEmpty()) {
            Gui.fineWrapped(g, font, Component.translatable("screen.justenoughstructures.hover_spawns_nothing"), left + 4, top + 4, right - left - 8, Gui.LABEL_SOFT);
            return;
        }
        // A list that scrolls says so with a bar down its side, and its rows make room for it.
        int most = mostScroll();
        int rowRight = most > 0 ? right - 7 : right;
        Gui.scissor(g, left + 1, top, right - 1, bottom);
        int cy = top + 1 - scroll;
        for (Row row : rows) {
            if (cy + OddsList.ROW > top && cy < bottom) {
                if (row.mob() == null) {
                    Gui.fineClipped(g, font, row.text().getString(), left + 4, cy + (OddsList.ROW - Gui.fineLine(font)) / 2 + 1, rowRight - left - 8, Gui.LABEL_SOFT);
                } else {
                    renderRow(g, font, row, left, rowRight, cy, top, bottom, mouseX, mouseY);
                }
            }
            cy += OddsList.ROW + 1;
        }
        Gui.endScissor(g);
        Gui.scrollbar(g, right - 5, top, bottom - top, scroll, most);
    }

    private void renderRow(GuiGraphics g, Font font, Row row, int left, int right, int cy, int top, int bottom, int mouseX, int mouseY) {
        boolean over = mouseX >= left && mouseX < right && mouseY >= Math.max(cy, top) && mouseY < Math.min(cy + OddsList.ROW, bottom);
        ResourceLocation id = ResourceLocation.tryParse(row.mob());
        EntityType<?> type = id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? Regs.value(BuiltInRegistries.ENTITY_TYPE, id) : null;
        ItemStack icon = type == null ? SPAWNER : InfoPanel.entityIcon(type);
        Component name = StructureNames.mob(row.mob());
        OddsList.drawRow(g, font, icon, name.getString(), "", row.chance(), left, cy, right - 1, over);
        if (!over) {
            return;
        }
        List<Component> tip = new ArrayList<>();
        tip.add(name);
        if (kind.type() == SpawnerKind.Type.POOL) {
            tip.add(Component.translatable("screen.justenoughstructures.spawner.pool_chance", OddsList.percent(row.chance())).withStyle(ChatFormatting.GRAY));
        } else if (row.of() > 1) {
            tip.add(Component.translatable("screen.justenoughstructures.spawner.mix_chance", OddsList.percent(row.chance())).withStyle(ChatFormatting.GRAY));
        }
        if (row.gear()) {
            tip.add(Component.translatable("screen.justenoughstructures.spawner.with_gear").withStyle(ChatFormatting.GRAY));
        }
        if (Gui.advanced()) {
            tip.add(Component.literal(row.mob()).withStyle(ChatFormatting.DARK_GRAY));
        }
        hoveredTip = tip;
    }

    /**
     * The spawner with a small wrench on it, that opens it in Pack tools, or greyed out until one is
     * picked. While picking a spawner to change, it's pointed out as the one to use.
     */
    private void toolsIcon(GuiGraphics g, int right, int top, int mouseX, int mouseY) {
        int left = right - ICON;
        boolean enabled = !overview();
        if (ChestPopup.drawToolsIcon(g, SPAWNER, left, top, mouseX, mouseY, enabled, picking)) {
            hoveredTip = enabled ? List.of(Component.translatable("screen.justenoughstructures.tools.open_spawner")) : pickOne();
        }
        if (enabled) {
            links.put(Action.TOOLS, left, top, ICON, ICON);
        }
    }

    private static List<Component> pickOne() {
        return List.of(Component.translatable("screen.justenoughstructures.tools.pick_one_spawner"));
    }

    /** The link at a point, or null. */
    Action actionAt(double mouseX, double mouseY) {
        return links.at(mouseX, mouseY);
    }

    /** The middle of a link, or null if it isn't shown. For the screenshot harness. */
    int[] linkCentre(Action action) {
        return links.centre(action);
    }
}
