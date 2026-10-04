package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.client.ClientRequests;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A spawner opened from the preview or the Mobs tab: every mob it can make with each one's chance,
 * and how often it spawns them. It's drawn like the chest popup and sits beside the preview the same
 * way, with arrows to the other spawners that make the same.
 */
final class SpawnerPopup {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/container/generic_54.png");
    static final int WIDTH = ChestPopup.WIDTH;
    /** The list of mobs scrolls once it's taller than this. */
    private static final int MOST_LIST = 108;
    private static final int ICON = 14;
    private static final ItemStack SPAWNER = new ItemStack(Items.SPAWNER);

    /** What the links on the popup do. */
    enum Action {
        /** Give the spawner another mob, while picking a spawner for Pack tools. */
        CHANGE,
        /** Open the spawner in Pack tools. */
        TOOLS
    }

    /** A mob it can make, and the share of spawns or spawners that get it. */
    private record Mob(String id, float chance) {
    }

    final StructureSnapshot.Spawner spawner;
    final SpawnerKind kind;
    final Component title;
    /** Which of the {@code count} spawners like it this is, or -1 while it stands for all of them. */
    final int index;
    final int count;
    private final List<Mob> mobs = new ArrayList<>();
    /** How many it spawns and how often, or null when its block entity doesn't say. */
    private final Component timing;
    /** Whether this player sees Pack tools: its shortcut, and what a changed spawner made before. */
    final boolean packTools = ClientRequests.showsPackTools();
    /** Set while the player is picking a spawner for Pack tools to change: the popup offers Change. */
    boolean picking;
    int x;
    int y;
    private int scroll;
    /** The tooltip of whatever's under the mouse, set as it's drawn, or null. */
    List<Component> hoveredTip;
    private final Map<Action, int[]> links = new EnumMap<>(Action.class);

    SpawnerPopup(StructureSnapshot.Spawner spawner, CompoundTag tag, Component title, int index, int count) {
        this.spawner = spawner;
        this.kind = tag == null ? new SpawnerKind(SpawnerKind.Type.MOB, spawner.mob().isEmpty() ? Map.of() : Map.of(spawner.mob(), 1)) : SpawnerKind.of(tag);
        this.title = title;
        this.index = index;
        this.count = count;
        int total = kind.mobs().values().stream().mapToInt(Integer::intValue).sum();
        kind.mobs().forEach((mob, weight) -> mobs.add(new Mob(mob, (float) weight / Math.max(1, total))));
        mobs.sort((a, b) -> Float.compare(b.chance(), a.chance()));
        timing = tag == null ? null : timing(tag);
    }

    /** "Up to 4 at a time, every 10 to 40 seconds, while a player is within 16 blocks", from a spawner's settings. */
    private static Component timing(CompoundTag tag) {
        for (String key : new String[]{"MinSpawnDelay", "MaxSpawnDelay", "SpawnCount", "RequiredPlayerRange"}) {
            if (!tag.contains(key, Tag.TAG_ANY_NUMERIC)) {
                return null;
            }
        }
        int min = tag.getShort("MinSpawnDelay");
        int max = tag.getShort("MaxSpawnDelay");
        int spawnCount = tag.getShort("SpawnCount");
        int range = tag.getShort("RequiredPlayerRange");
        return min == max
                ? Component.translatable("screen.justenoughstructures.spawner.timing_exact", spawnCount, seconds(min), range)
                : Component.translatable("screen.justenoughstructures.spawner.timing", spawnCount, seconds(min), seconds(max), range);
    }

    private static String seconds(int ticks) {
        return ticks % 20 == 0 ? String.valueOf(ticks / 20) : String.format("%.1f", ticks / 20f);
    }

    /** Showing all the spawners like it rather than a particular one of them. */
    boolean overview() {
        return index < 0;
    }

    /** "3 / 17", or "all 17" before one is picked, or null for a spawner that's the only one like it. */
    private String indexText() {
        if (count <= 1) {
            return null;
        }
        return overview() ? Component.translatable("screen.justenoughstructures.container_all", count).getString()
                : Component.translatable("screen.justenoughstructures.container_index", index + 1, count).getString();
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

    /** The lines under the summary: what a changed spawner made before, and how often it spawns. */
    private List<Line> notes(Font font) {
        List<Line> lines = new ArrayList<>();
        int width = (int) ((WIDTH - 14) / Gui.fineScale());
        String changedFrom = spawner.source() == null ? null : spawner.source().patchedFrom();
        if (packTools && changedFrom != null && !overview()) {
            Component note = Component.translatable("screen.justenoughstructures.container.changed_from", StructureNames.mob(changedFrom));
            font.split(note, width).forEach(line -> lines.add(new Line(line, ToolsUi.CHANGED)));
        }
        if (timing != null) {
            font.split(timing, width).forEach(line -> lines.add(new Line(line, Gui.LABEL_SOFT)));
        }
        return lines;
    }

    private record Line(FormattedCharSequence text, int colour) {
    }

    private int listHeight() {
        return mobs.isEmpty() ? OddsList.ROW + 4 : Math.min(MOST_LIST, mobs.size() * (OddsList.ROW + 1) + 3);
    }

    private int infoHeight(Font font) {
        int fine = Gui.fineLine(font);
        int height = 5 + ICON + 2 + font.lineHeight + 1 + notes(font).size() * (fine + 1) + 3 + 20 + 6;
        if (Gui.advanced() && kind.type() == SpawnerKind.Type.MOB && !kind.mob().isEmpty()) {
            height += fine + 1;
        }
        return height;
    }

    int height(Font font) {
        return 17 + listHeight() + 7 + infoHeight(font);
    }

    /** Sits beside the preview at {@code x}, so the spawners it's about can be seen. */
    void placeAt(int x, int screenHeight, Font font) {
        this.x = x;
        y = Math.max(4, (screenHeight - height(font)) / 2);
    }

    boolean contains(double mouseX, double mouseY, Font font) {
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + height(font);
    }

    /** Where the buttons go, one row along the bottom. */
    int buttonRowY(Font font) {
        return y + height(font) - 26;
    }

    /** Scrolls the list of mobs, when the mouse is over the popup. */
    boolean scroll(double mouseX, double mouseY, double delta) {
        if (mouseX < x || mouseX >= x + WIDTH) {
            return false;
        }
        int most = Math.max(0, mobs.size() * (OddsList.ROW + 1) - (listHeight() - 4));
        scroll = Math.max(0, Math.min(most, scroll - (int) (delta * (OddsList.ROW + 1))));
        return true;
    }

    void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        links.clear();
        hoveredTip = null;
        int body = listHeight();

        // The chest's frame: its title bar, a plain strip for the list, then its bottom edge.
        g.blit(TEXTURE, x, y, 0, 0, WIDTH, 17);
        for (int filled = 0; filled < body; filled += 12) {
            g.blit(TEXTURE, x, y + 17 + filled, 0, 127, WIDTH, Math.min(12, body - filled));
        }
        g.blit(TEXTURE, x, y + 17 + body, 0, 215, WIDTH, 7);

        int right = x + WIDTH - 8;
        String of = indexText();
        if (of != null) {
            // Room for the widest it can say, so the title doesn't move as the arrows step.
            int all = font.width(Component.translatable("screen.justenoughstructures.container_all", count).getString());
            int last = font.width(Component.translatable("screen.justenoughstructures.container_index", count, count).getString());
            g.drawString(font, of, right - font.width(of), y + 6, Gui.LABEL_SOFT, false);
            right -= Math.max(all, last) + 4;
        }
        Gui.drawClipped(g, font, title.getString(), x + 8, y + 6, right - x - 8, Gui.LABEL, false);

        renderList(g, font, mouseX, mouseY, y + 17, body);

        int infoTop = y + 17 + body + 7;
        Gui.panel(g, x, infoTop, WIDTH, infoHeight(font));
        int cy = infoTop + 5;
        Gui.fine(g, font, Component.translatable("screen.justenoughstructures.spawner.spawns").getString(), x + 7,
                cy + (ICON - Gui.fineLine(font)) / 2, Gui.LABEL_SOFT);
        int iconRight = x + WIDTH - 7;
        if (picking) {
            changeLink(g, font, iconRight, cy + (ICON - Gui.fineLine(font)) / 2, mouseX, mouseY);
        } else if (packTools) {
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
    }

    private void renderList(GuiGraphics g, Font font, int mouseX, int mouseY, int bodyTop, int body) {
        int top = bodyTop + 1;
        int left = x + 7;
        int right = x + WIDTH - 7;
        int bottom = top + body - 2;
        Gui.inset(g, left, top - 1, right - left, body, 0xFFB9B9B9);
        if (mobs.isEmpty()) {
            Gui.fineWrapped(g, font, Component.translatable("screen.justenoughstructures.hover_spawns_nothing"), left + 4, top + 4, right - left - 8, Gui.LABEL_SOFT);
            return;
        }
        Gui.scissor(g, left + 1, top, right - 1, bottom);
        int cy = top + 1 - scroll;
        for (Mob mob : mobs) {
            if (cy + OddsList.ROW > top && cy < bottom) {
                boolean over = mouseX >= left && mouseX < right && mouseY >= Math.max(cy, top) && mouseY < Math.min(cy + OddsList.ROW, bottom);
                ResourceLocation id = ResourceLocation.tryParse(mob.id());
                EntityType<?> type = id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? BuiltInRegistries.ENTITY_TYPE.get(id) : null;
                ItemStack icon = type == null ? SPAWNER : InfoPanel.entityIcon(type);
                Component name = StructureNames.mob(mob.id());
                OddsList.drawRow(g, font, icon, name.getString(), "", mob.chance(), left, cy, right - 1, over);
                if (over) {
                    List<Component> tip = new ArrayList<>();
                    tip.add(name);
                    if (kind.type() != SpawnerKind.Type.MOB) {
                        tip.add(Component.translatable("screen.justenoughstructures.spawner." + (kind.type() == SpawnerKind.Type.POOL ? "pool_chance" : "mix_chance"),
                                OddsList.percent(mob.chance())).withStyle(ChatFormatting.GRAY));
                    }
                    if (Gui.advanced()) {
                        tip.add(Component.literal(mob.id()).withStyle(ChatFormatting.DARK_GRAY));
                    }
                    hoveredTip = tip;
                }
            }
            cy += OddsList.ROW + 1;
        }
        Gui.endScissor(g);
    }

    /** Change, while picking a spawner for Pack tools: on to the mob picker, or why it can't be. */
    private void changeLink(GuiGraphics g, Font font, int right, int top, int mouseX, int mouseY) {
        String text = Component.translatable("screen.justenoughstructures.container.change").getString();
        int w = Gui.fineWidth(font, text);
        int left = right - w;
        boolean over = mouseX >= left && mouseX < right && mouseY >= top - 1 && mouseY < top + Gui.fineLine(font) + 1;
        if (overview() || spawner.source() == null) {
            Gui.fine(g, font, text, left, top, Gui.LABEL_SOFT);
            if (over) {
                hoveredTip = overview() ? pickOne() : List.of(Component.translatable("screen.justenoughstructures.tools.spawner_code_note"));
            }
            return;
        }
        Gui.fine(g, font, text, left, top, over ? 0xFF2040C0 : 0xFF3A55A0);
        if (over) {
            g.fill(left, top + Gui.fineLine(font), right, top + Gui.fineLine(font) + 1, 0xFF2040C0);
            hoveredTip = List.of(Component.translatable("screen.justenoughstructures.spawner.change_hint"));
        }
        links.put(Action.CHANGE, new int[]{left, top - 1, w, Gui.fineLine(font) + 2});
    }

    /** The spawner with a small wrench on it, that opens it in Pack tools, or greyed out until one is picked. */
    private void toolsIcon(GuiGraphics g, int right, int top, int mouseX, int mouseY) {
        int left = right - ICON;
        boolean enabled = !overview();
        boolean over = mouseX >= left && mouseX < right && mouseY >= top && mouseY < top + ICON;
        if (over) {
            if (enabled) {
                g.fill(left, top, right, top + ICON, 0xFF555555);
                g.fill(left + 1, top + 1, right - 1, top + ICON - 1, 0x90FFFFFF);
            }
            hoveredTip = enabled ? List.of(Component.translatable("screen.justenoughstructures.tools.open_spawner"),
                    Component.translatable("screen.justenoughstructures.tools.open_spawner_hint").withStyle(ChatFormatting.GRAY)) : pickOne();
        }
        g.pose().pushPose();
        g.pose().translate(left + 1, top + 1, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.renderItem(SPAWNER, 0, 0);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.blit(PackToolsScreen.WRENCH, right - 8, top + ICON - 8, 0, 0, 8, 8, 8, 8);
        if (!enabled) {
            // The panel's own grey over the spawner and wrench, so they show through faintly.
            g.fill(left, top, right, top + ICON, 0xA0C6C6C6);
        }
        g.pose().popPose();
        if (enabled) {
            links.put(Action.TOOLS, new int[]{left, top, ICON, ICON});
        }
    }

    private static List<Component> pickOne() {
        return List.of(Component.translatable("screen.justenoughstructures.tools.pick_one_spawner"),
                Component.translatable("screen.justenoughstructures.tools.pick_one_hint").withStyle(ChatFormatting.GRAY));
    }

    /** The link at a point, or null. */
    Action actionAt(double mouseX, double mouseY) {
        for (Map.Entry<Action, int[]> e : links.entrySet()) {
            int[] r = e.getValue();
            if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                return e.getKey();
            }
        }
        return null;
    }

    /** The middle of a link, or null if it isn't shown. For the screenshot harness. */
    int[] linkCentre(Action action) {
        int[] r = links.get(action);
        return r == null ? null : new int[]{r[0] + r[2] / 2, r[1] + r[3] / 2};
    }
}
