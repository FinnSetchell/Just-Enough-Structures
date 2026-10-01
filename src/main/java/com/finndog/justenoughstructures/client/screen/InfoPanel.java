package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.SpawnerPools;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.catalog.StructureInfo;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.ClientState;
import com.finndog.justenoughstructures.client.CompassLink;
import com.finndog.justenoughstructures.client.Exports;
import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.client.render.Highlight;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** The tabs on the right: what the structure is, its loot, its blocks and its mobs. */
final class InfoPanel {
    enum Tab {
        OVERVIEW("overview", Items.BOOK), LOOT("loot", Items.CHEST), BLOCKS("blocks", Items.BRICKS), ENTITIES("entities", Items.ZOMBIE_HEAD);

        final String key;
        final ItemStack icon;

        Tab(String key, Item icon) {
            this.key = key;
            this.icon = new ItemStack(icon);
        }

        Component label() {
            return Component.translatable("screen.justenoughstructures.tab." + key);
        }
    }

    private static final int TAB_SIZE = 24;
    private static final int PAD = 2;
    private static final int TEXT = Gui.LABEL;
    private static final int RARE = 0xFF8A5A00;
    private static final int GOOD = 0xFF2E5B1D;
    private static final ItemStack SECRET_ICON = new ItemStack(Items.CHEST);

    private final Font font;
    private final Consumer<String> onSelectTable;
    private final Consumer<StructureSnapshot.Container> onOpenContainer;
    private final Consumer<ItemStack> onItemClicked;
    private Runnable onNewTable = () -> {
    };
    private Consumer<String> onEditTable = table -> {
    };

    private Tab tab = Tab.OVERVIEW;
    private int x, y, width, height;
    private double scroll;
    private int contentHeight;
    private int contentRight;
    private int tabsX;
    private int tabsY;

    private StructureCatalog.Entry entry;
    private CaptureResult result;
    private String selectedTable;
    private LootOdds odds;
    private Component notice;
    private long noticeUntil;

    private final List<Hotspot> hotspots = new ArrayList<>();
    private final Map<Item, int[]> oddsRows = new HashMap<>();
    private ItemStack hoveredStack = ItemStack.EMPTY;
    private List<Component> hoveredExtra = List.of();
    private List<Component> hoveredText = List.of();
    private Hovered hoveredBlocks;
    /** Where each row that tints the preview was last drawn, by its key, for the screenshot harness. */
    private final Map<String, int[]> highlightRows = new HashMap<>();

    private record Hotspot(int x, int y, int w, int h, Runnable action) {
    }

    InfoPanel(Font font, Consumer<String> onSelectTable, Consumer<StructureSnapshot.Container> onOpenContainer,
              Consumer<ItemStack> onItemClicked) {
        this.font = font;
        this.onSelectTable = onSelectTable;
        this.onOpenContainer = onOpenContainer;
        this.onItemClicked = onItemClicked;
    }

    /** The body goes in x, y, width, height; the tabs sit in a row above it from tabsX, tabsY. */
    void layout(int x, int y, int width, int height, int tabsX, int tabsY) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.tabsX = tabsX;
        this.tabsY = tabsY;
    }

    void setEntry(StructureCatalog.Entry entry) {
        this.entry = entry;
        this.result = null;
        this.selectedTable = null;
        this.odds = null;
        this.scroll = 0;
    }

    void setResult(CaptureResult result) {
        this.result = result;
        this.selectedTable = null;
        this.odds = null;
    }

    String selectedTable() {
        return selectedTable;
    }

    void setSelectedTable(String table) {
        this.selectedTable = table;
        this.odds = null;
    }

    void setOdds(LootOdds odds) {
        if (odds != null && odds.tableId().toString().equals(selectedTable)) {
            this.odds = odds;
        }
    }

    Tab tab() {
        return tab;
    }

    /** What the Loot tab's Edit link does with the picked table. */
    /** What the Loot tab's New loot table link does. */
    void onNewTable(Runnable action) {
        onNewTable = action;
    }

    void onEditTable(Consumer<String> action) {
        onEditTable = action;
    }

    void setTab(Tab tab) {
        this.tab = tab;
        this.scroll = 0;
    }

    static void showDetails(boolean shown) {
        ClientState.details = shown;
    }

    ItemStack hoveredStack() {
        return hoveredStack;
    }

    /** Lines to add to the tooltip of {@link #hoveredStack()}, such as chances and enchantments. */
    List<Component> hoveredExtra() {
        return hoveredExtra;
    }

    List<Component> hoveredText() {
        return hoveredText;
    }

    /**
     * The blocks a row is about, for the preview to tint while it's hovered: {@code key} says which,
     * and {@code positions} finds them in a snapshot, as {@link BlockPos#asLong}.
     */
    record Hovered(String key, Function<StructureSnapshot, LongSet> positions) {
    }

    /** What the row under the mouse is about in the preview, or null. */
    Hovered hoveredBlocks() {
        return hoveredBlocks;
    }

    int[] highlightRow(String key) {
        return highlightRows.get(key);
    }

    int[] tabCentre(Tab t) {
        return new int[]{tabsX + t.ordinal() * TAB_SIZE + TAB_SIZE / 2, tabsY + TAB_SIZE / 2};
    }

    int[] oddsRow(Item item) {
        return oddsRows.get(item);
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        hotspots.clear();
        oddsRows.clear();
        hoveredStack = ItemStack.EMPTY;
        hoveredExtra = List.of();
        hoveredText = List.of();
        hoveredBlocks = null;
        highlightRows.clear();

        // JEI's category tabs: icons only, the selected one joined to the panel below.
        for (Tab t : Tab.values()) {
            int tx = tabsX + t.ordinal() * TAB_SIZE;
            Gui.tab(g, tx, tabsY, t == tab);
            g.renderItem(t.icon, tx + 4, tabsY + 4);
            if (mouseX >= tx && mouseX < tx + TAB_SIZE && mouseY >= tabsY && mouseY < tabsY + TAB_SIZE - 3) {
                hoveredText = List.of(t.label());
            }
        }

        int top = y;
        int bodyHeight = height;
        boolean scrolls = contentHeight > bodyHeight;
        contentRight = x + width - (scrolls ? 8 : 0);
        g.enableScissor(x, top, x + width, top + bodyHeight);
        clampScroll();
        int cursor = top + 2 - (int) scroll;
        int end = switch (tab) {
            case OVERVIEW -> overview(g, cursor, mouseX, mouseY, top, bodyHeight);
            case LOOT -> loot(g, cursor, mouseX, mouseY, top, bodyHeight);
            case BLOCKS -> blocks(g, cursor, mouseX, mouseY, top, bodyHeight);
            case ENTITIES -> mobs(g, cursor, mouseX, mouseY, top, bodyHeight);
        };
        g.disableScissor();
        contentHeight = end - cursor + 2;
        clampScroll();
        if (contentHeight > bodyHeight) {
            Gui.scrollbar(g, x + width - 4, top, bodyHeight, scroll, contentHeight - bodyHeight);
        }
    }

    boolean click(double mouseX, double mouseY) {
        if (mouseY >= tabsY && mouseY < tabsY + TAB_SIZE - 3 && mouseX >= tabsX && mouseX < tabsX + TAB_SIZE * Tab.values().length) {
            setTab(Tab.values()[(int) ((mouseX - tabsX) / TAB_SIZE)]);
            return true;
        }
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }
        for (Hotspot h : hotspots) {
            if (mouseX >= h.x() && mouseX < h.x() + h.w() && mouseY >= h.y() && mouseY < h.y() + h.h()) {
                h.action().run();
                return true;
            }
        }
        return false;
    }

    boolean scroll(double mouseX, double mouseY, double delta) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }
        scroll -= delta * 20;
        clampScroll();
        return true;
    }

    /** Keeps the scroll inside the content, so it never shows past the top or bottom, even for a frame. */
    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - height)));
    }

    private int textWidth() {
        return contentRight - x - PAD;
    }

    private void notify(Component text) {
        notice = text;
        noticeUntil = System.currentTimeMillis() + 4000;
    }

    // ------------------------------------------------------------------ overview

    private int overview(GuiGraphics g, int cy, int mouseX, int mouseY, int clipTop, int clipHeight) {
        if (entry == null) {
            return cy;
        }
        JsonObject def = entry.definition();
        List<Holder<Biome>> biomes = biomes(def);

        Set<String> dimensions = new LinkedHashSet<>();
        for (Holder<Biome> biome : biomes) {
            dimensions.add(biome.is(BiomeTags.IS_NETHER) ? "nether" : biome.is(BiomeTags.IS_END) ? "end" : "overworld");
        }
        if (!dimensions.isEmpty()) {
            cy = field(g, cy, "dimension", String.join(", ", dimensions.stream()
                    .map(d -> Component.translatable("screen.justenoughstructures.dimension." + d).getString()).toList()));
        }
        if (!biomes.isEmpty()) {
            List<String> names = biomes.stream().map(InfoPanel::biomeName).distinct().sorted().toList();
            String shown = String.join(", ", names.subList(0, Math.min(6, names.size())));
            if (names.size() > 6) {
                shown += " " + Component.translatable("screen.justenoughstructures.and_more", names.size() - 6).getString();
            }
            cy = field(g, cy, "biomes", shown);
        }
        if (result != null && result.succeeded()) {
            StructureSnapshot s = result.snapshot();
            cy = field(g, cy, "size", Component.translatable("screen.justenoughstructures.size_blocks",
                    s.size().getX(), s.size().getY(), s.size().getZ()).getString());
            cy = field(g, cy, "loot_here", entry.info().hideLootLocations()
                    ? Component.translatable("screen.justenoughstructures.loot_secret").getString() : lootSummary(s));
        }

        // What a compass in the player's hand says about it.
        CompassLink compass = CompassLink.get();
        Component compassStatus = compass == null ? null : compass.status(Minecraft.getInstance().player, entry.id());
        if (compassStatus != null) {
            cy = field(g, cy, "compass", compassStatus.getString());
        }

        // Whatever the structure's mod or the modpack wrote about it.
        StructureInfo info = entry.info();
        if (info.notes() != null) {
            cy += 3;
            String title = info.author() == null ? Component.translatable("screen.justenoughstructures.notes").getString()
                    : Component.translatable("screen.justenoughstructures.notes_by", info.author()).getString();
            Gui.band(g, font, Gui.clip(font, title, contentRight - x - 8), x, cy, contentRight - x, 13);
            cy += 16;
            cy = Gui.wrapped(g, font, info.notes(), x + PAD, cy, textWidth(), TEXT) + 3;
        }

        // Everything a datapack author wants and a player doesn't, folded away by default.
        cy += 2;
        Component toggle = Component.translatable(ClientState.details ? "screen.justenoughstructures.details_hide" : "screen.justenoughstructures.details_show");
        boolean over = inside(mouseX, mouseY, x + 2, cy - 1, width - 4, font.lineHeight + 3, clipTop, clipHeight);
        g.drawString(font, toggle, x + PAD, cy, over ? 0xFF1F3F8F : 0xFF3A55A0, false);
        hotspots.add(new Hotspot(x + 2, cy - 1, width - 4, font.lineHeight + 3, () -> {
            ClientState.details = !ClientState.details;
            ClientState.save();
        }));
        cy += font.lineHeight + 5;
        if (!ClientState.details) {
            return cy;
        }
        // The structure's definition, how its sets place it, and how this layout came out, as small
        // label and value rows, so a lot fits without the long ids crowding the panel.
        List<List<String[]>> sections = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        List<String[]> definition = new ArrayList<>();
        definition.add(row("id", entry.id().toString()));
        definition.add(row("type", entry.type() == null ? "?" : entry.type().toString()));
        if (def != null) {
            definition.add(row("step", string(def.get("step"))));
            definition.add(row("biomes", string(def.get("biomes"))));
            if (def.has("start_pool")) {
                definition.add(row("start_pool", string(def.get("start_pool"))));
            }
            if (def.has("size")) {
                definition.add(row("depth", string(def.get("size"))));
            }
            definition.add(row("terrain", def.has("terrain_adaptation") ? string(def.get("terrain_adaptation")) : "none"));
        }
        titles.add("structure");
        sections.add(definition);
        for (StructureCatalog.SetInfo set : entry.sets()) {
            List<String[]> placement = new ArrayList<>();
            placement.add(row("set", set.setId().toString()));
            JsonObject p = set.placement();
            if (p != null) {
                placement.add(row("type", string(p.get("type"))));
                for (String key : List.of("spacing", "separation", "salt", "frequency", "distance", "count")) {
                    if (p.has(key)) {
                        placement.add(row(key, string(p.get(key))));
                    }
                }
            }
            titles.add("placement");
            sections.add(placement);
        }
        if (result != null && result.succeeded()) {
            StructureSnapshot s = result.snapshot();
            List<String[]> layout = new ArrayList<>();
            layout.add(row("pieces", String.valueOf(s.pieceCount())));
            layout.add(row("ground", Component.translatable("screen.justenoughstructures.terrain." + s.terrain().name().toLowerCase(Locale.ROOT)).getString()));
            layout.add(row("seed", Long.toHexString(s.seed()).toUpperCase(Locale.ROOT)));
            layout.add(row("time", Component.translatable("screen.justenoughstructures.millis", result.millis()).getString()));
            titles.add("layout");
            sections.add(layout);
        }
        // Labels sit in a column of their own when that leaves the values enough room, and above
        // their values when it doesn't.
        int labelWidth = 0;
        for (List<String[]> section : sections) {
            for (String[] r : section) {
                labelWidth = Math.max(labelWidth, secondaryWidth(r[0]));
            }
        }
        int valueX = textWidth() - labelWidth - 6 >= 90 ? labelWidth + 6 : 0;
        for (int i = 0; i < sections.size(); i++) {
            cy = detailTitle(g, cy, Component.translatable("screen.justenoughstructures.detail." + titles.get(i)).getString());
            for (String[] r : sections.get(i)) {
                cy = detailRow(g, cy, r[0], r[1], valueX);
            }
            cy += 4;
        }
        return cy;
    }

    private static String[] row(String key, String value) {
        return new String[]{Component.translatable("screen.justenoughstructures.detail." + key).getString(), value};
    }

    /** A small heading over a group of detail rows, with a faint rule under it. */
    private int detailTitle(GuiGraphics g, int cy, String title) {
        Gui.scaled(g, font, title, x + PAD, cy, TEXT, secondaryScale());
        int line = secondaryLine();
        g.fill(x + PAD, cy + line, contentRight - PAD, cy + line + 1, 0x30000000);
        return cy + line + 3;
    }

    /**
     * One detail: the label, and the value in small text beside it, or under it when {@code valueX}
     * is 0. Ids break at their separators, and their namespace is dimmed so the name stands out.
     */
    private int detailRow(GuiGraphics g, int cy, String label, String value, int valueX) {
        float scale = secondaryScale();
        int line = secondaryLine();
        Gui.scaled(g, font, label, x + PAD, cy, Gui.LABEL_SOFT, scale);
        if (valueX == 0) {
            cy += line + 1;
        }
        int left = x + PAD + (valueX == 0 ? 4 : valueX);
        int width = (int) ((contentRight - PAD - left) / scale);
        int namespace = value.contains(" ") ? 0 : value.indexOf(':') + 1;
        List<String> lines = value.contains(" ")
                ? font.getSplitter().splitLines(value, width, Style.EMPTY).stream().map(FormattedText::getString).toList()
                : idLines(value, width);
        int shown = 0;
        for (String text : lines) {
            // The part of this line that's still the namespace, if any.
            int soft = Math.max(0, Math.min(text.length(), namespace - shown));
            if (soft > 0) {
                Gui.scaled(g, font, text.substring(0, soft), left, cy, Gui.LABEL_SOFT, scale);
            }
            Gui.scaled(g, font, text.substring(soft), left + secondaryWidth(text.substring(0, soft)), cy, TEXT, scale);
            shown += text.length();
            cy += line + 1;
        }
        return cy + 1;
    }

    /**
     * The size of labels and details: small, unless small text can't be sharp at this GUI scale,
     * when there's room for them at full size anyway.
     */
    private static float secondaryScale() {
        return Gui.smallIsSharp() ? Gui.smallScale() : 1f;
    }

    private int secondaryWidth(String text) {
        return (int) Math.ceil(font.width(text) * secondaryScale());
    }

    /** Secondary text, at the size of labels and details. */
    private void fine(GuiGraphics g, String text, int left, int top, int color) {
        Gui.scaled(g, font, text, left, top, color, secondaryScale());
    }

    /** Cuts text short to fit {@code width} at the secondary size. */
    private String fineClip(String text, int width) {
        return Gui.clip(font, text, (int) (width / secondaryScale()));
    }

    /** Wrapped text at the secondary size, such as the notes under a list. Returns the y below it. */
    private int fineWrapped(GuiGraphics g, Component text, int left, int top, int width, int color) {
        float scale = secondaryScale();
        for (FormattedCharSequence line : font.split(text, (int) (width / scale))) {
            Gui.scaled(g, font, line, left, top, color, scale);
            top += secondaryLine() + 1;
        }
        return top;
    }

    /** How tall a line of labels or details is. */
    private int secondaryLine() {
        return (int) Math.ceil(font.lineHeight * secondaryScale());
    }

    private String lootSummary(StructureSnapshot s) {
        Map<String, Integer> kinds = new LinkedHashMap<>();
        for (StructureSnapshot.Container c : s.containers()) {
            if (c.lootTable() != null) {
                kinds.merge(containerIcon(s, c).getHoverName().getString(), 1, Integer::sum);
            }
        }
        if (kinds.isEmpty()) {
            return Component.translatable("screen.justenoughstructures.loot_none").getString();
        }
        List<String> parts = new ArrayList<>();
        kinds.forEach((name, count) -> parts.add(Component.translatable("screen.justenoughstructures.times_name", count, name).getString()));
        return String.join("\n", parts);
    }

    /** The structure's biomes, from a tag, a list or a single id in its definition. */
    private static List<Holder<Biome>> biomes(JsonObject def) {
        Minecraft mc = Minecraft.getInstance();
        if (def == null || mc.level == null || !def.has("biomes")) {
            return List.of();
        }
        Registry<Biome> registry = mc.level.registryAccess().registryOrThrow(Registries.BIOME);
        List<Holder<Biome>> out = new ArrayList<>();
        JsonElement biomes = def.get("biomes");
        List<String> ids = new ArrayList<>();
        if (biomes.isJsonArray()) {
            biomes.getAsJsonArray().forEach(e -> ids.add(e.getAsString()));
        } else if (biomes.isJsonPrimitive()) {
            ids.add(biomes.getAsString());
        }
        for (String id : ids) {
            if (id.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(id.substring(1));
                if (tag != null) {
                    registry.getTag(TagKey.create(Registries.BIOME, tag)).ifPresent(set -> set.forEach(out::add));
                }
            } else {
                ResourceLocation biome = ResourceLocation.tryParse(id);
                if (biome != null) {
                    registry.getHolder(ResourceKey.create(Registries.BIOME, biome)).ifPresent(out::add);
                }
            }
        }
        return out;
    }

    private static String biomeName(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> Component.translatable("biome." + key.location().getNamespace() + "." + key.location().getPath()).getString())
                .orElse("?");
    }

    private int field(GuiGraphics g, int cy, String key, String value) {
        Gui.scaled(g, font, Component.translatable("screen.justenoughstructures.field." + key).getString(), x + PAD, cy, Gui.LABEL_SOFT, secondaryScale());
        cy += secondaryLine() + 1;
        for (String line : value.split("\n")) {
            if (line.contains(" ")) {
                cy = Gui.wrapped(g, font, Component.literal(line), x + PAD, cy, textWidth(), TEXT);
                continue;
            }
            for (String part : idLines(line, textWidth())) {
                g.drawString(font, part, x + PAD, cy, TEXT, false);
                cy += font.lineHeight + 1;
            }
        }
        return cy + 4;
    }

    /**
     * Splits an id into lines that break after underscores, slashes and colons rather than
     * mid-word. Done by hand because Minecraft's font draws a zero-width space as a missing glyph.
     */
    private List<String> idLines(String text, int width) {
        // An id too long for one line goes over two at its colon first, so its name stays whole.
        int colon = text.indexOf(':');
        if (font.width(text) > width && colon > 0 && colon < text.length() - 1) {
            List<String> lines = new ArrayList<>(idLines(text.substring(0, colon + 1), width));
            lines.addAll(idLines(text.substring(colon + 1), width));
            return lines;
        }
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            boolean last = i == text.length() - 1;
            if (!last && "_/:".indexOf(text.charAt(i)) < 0) {
                continue;
            }
            String piece = text.substring(start, i + 1);
            start = i + 1;
            if (!line.isEmpty() && font.width(line + piece) > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(piece);
            // A single piece wider than the panel still has to be cut somewhere.
            while (font.width(line.toString()) > width) {
                String head = font.plainSubstrByWidth(line.toString(), width);
                if (head.isEmpty()) {
                    break;
                }
                lines.add(head);
                line.delete(0, head.length());
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static String string(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return "?";
        }
        if (e.isJsonPrimitive()) {
            return e.getAsString();
        }
        if (e.isJsonArray()) {
            List<String> parts = new ArrayList<>();
            e.getAsJsonArray().forEach(el -> parts.add(string(el)));
            return String.join(", ", parts);
        }
        return e.toString();
    }

    // ------------------------------------------------------------------ loot

    private int loot(GuiGraphics g, int cy, int mouseX, int mouseY, int clipTop, int clipHeight) {
        if (entry != null && entry.info().hideLootLocations()) {
            return secretLoot(g, cy, mouseX, mouseY, clipTop, clipHeight);
        }
        if (result == null || !result.succeeded()) {
            return fineWrapped(g, Component.translatable("screen.justenoughstructures.loot_waiting"), x + PAD, cy, textWidth(), Gui.LABEL_SOFT);
        }
        StructureSnapshot snapshot = result.snapshot();
        Map<String, List<StructureSnapshot.Container>> groups = new LinkedHashMap<>();
        for (StructureSnapshot.Container c : snapshot.containers()) {
            groups.computeIfAbsent(c.lootTable() == null ? "" : c.lootTable(), k -> new ArrayList<>()).add(c);
        }
        if (groups.isEmpty()) {
            return fineWrapped(g, Component.translatable("screen.justenoughstructures.no_loot"), x + PAD, cy, textWidth(), Gui.LABEL_SOFT);
        }
        List<Map.Entry<String, List<StructureSnapshot.Container>>> sorted = new ArrayList<>(groups.entrySet());
        sorted.sort(Comparator.comparing((Map.Entry<String, List<StructureSnapshot.Container>> e) -> e.getKey().isEmpty())
                .thenComparing(e -> -e.getValue().size()));

        int selectedCount = 1;
        String selectedName = null;
        for (Map.Entry<String, List<StructureSnapshot.Container>> group : sorted) {
            String table = group.getKey();
            List<StructureSnapshot.Container> containers = group.getValue();
            int rowHeight = 15 + secondaryLine();
            boolean selected = table.equals(selectedTable);
            boolean hovered = inside(mouseX, mouseY, x, cy, contentRight - x, rowHeight, clipTop, clipHeight);
            Gui.card(g, x, cy, contentRight - x, rowHeight);
            if (selected || hovered) {
                g.fill(x + 1, cy + 1, contentRight - 1, cy + rowHeight - 1, selected ? Gui.ROW_SELECTED : Gui.ROW_HOVER);
            }
            ItemStack icon = containerIcon(snapshot, containers.get(0));
            Gui.slot(g, x + PAD + 1, cy + 2);
            g.renderItem(icon, x + PAD + 2, cy + 3);
            String name = Component.translatable("screen.justenoughstructures.name_times", icon.getHoverName(), containers.size()).getString();
            String detail = table.isEmpty() ? Component.translatable("screen.justenoughstructures.prefilled").getString() : StructureNames.lootTable(table);
            int textWidth = contentRight - x - PAD - 23 - 12;
            Gui.fitted(g, font, name, x + PAD + 23, cy + 3, textWidth, TEXT);
            int mark = editedMark(g, table, x + PAD + 23 + textWidth, cy + 13);
            fine(g, fineClip(detail, textWidth - mark), x + PAD + 23, cy + 13, Gui.LABEL_SOFT);
            g.drawString(font, ">", contentRight - 9, cy + (rowHeight - 8) / 2, hovered ? TEXT : Gui.LABEL_SOFT, false);
            if (hovered) {
                hoveredText = withEditedNote(List.of(Component.literal(name),
                        Component.translatable("screen.justenoughstructures.group_hint").withStyle(ChatFormatting.YELLOW)), table);
                hoveredBlocks = new Hovered("loot:" + table, s -> positionsOf(containers));
            }
            highlightRows.put("loot:" + table, new int[]{x + (contentRight - x) / 2, cy + rowHeight / 2});
            if (selected) {
                selectedCount = containers.size();
                selectedName = icon.getHoverName().getString().toLowerCase(Locale.ROOT);
            }
            StructureSnapshot.Container first = containers.get(0);
            hotspots.add(new Hotspot(x, cy, contentRight - x, rowHeight, () -> {
                onSelectTable.accept(table.isEmpty() ? null : table);
                onOpenContainer.accept(first);
            }));
            cy += rowHeight + 1;
        }

        // Tables this layout happens not to have, from every layout the structure can generate, so
        // their odds can be seen and edited without rolling new layouts until one turns up.
        Set<ResourceLocation> everyTable = FoundIn.tablesIn(entry.id());
        if (everyTable == null) {
            ClientRequests.index();
        } else {
            List<String> others = everyTable.stream().map(ResourceLocation::toString).filter(t -> !groups.containsKey(t))
                    .sorted(Comparator.comparing(StructureNames::lootTable)).toList();
            if (!others.isEmpty()) {
                cy += 2;
                fine(g, Component.translatable("screen.justenoughstructures.other_layouts").getString(), x + PAD, cy, Gui.LABEL_SOFT);
                cy += secondaryLine() + 2;
            }
            for (String table : others) {
                int rowHeight = 15 + secondaryLine();
                boolean selected = table.equals(selectedTable);
                boolean hovered = inside(mouseX, mouseY, x, cy, contentRight - x, rowHeight, clipTop, clipHeight);
                Gui.card(g, x, cy, contentRight - x, rowHeight);
                if (selected || hovered) {
                    g.fill(x + 1, cy + 1, contentRight - 1, cy + rowHeight - 1, selected ? Gui.ROW_SELECTED : Gui.ROW_HOVER);
                }
                Gui.slot(g, x + PAD + 1, cy + 2);
                g.renderItem(SECRET_ICON, x + PAD + 2, cy + 3);
                int textWidth = contentRight - x - PAD - 23 - 12;
                Gui.fitted(g, font, StructureNames.lootTable(table), x + PAD + 23, cy + 3, textWidth, TEXT);
                int mark = editedMark(g, table, x + PAD + 23 + textWidth, cy + 13);
                fine(g, fineClip(Component.translatable("screen.justenoughstructures.not_in_layout").getString(), textWidth - mark),
                        x + PAD + 23, cy + 13, Gui.LABEL_SOFT);
                g.drawString(font, ">", contentRight - 9, cy + (rowHeight - 8) / 2, hovered ? TEXT : Gui.LABEL_SOFT, false);
                if (hovered) {
                    hoveredText = withEditedNote(List.of(Component.literal(StructureNames.lootTable(table)),
                            Component.translatable("screen.justenoughstructures.not_in_layout_hint").withStyle(ChatFormatting.GRAY)), table);
                }
                if (selected) {
                    selectedCount = 1;
                    selectedName = Component.translatable("screen.justenoughstructures.container").getString();
                }
                hotspots.add(new Hotspot(x, cy, contentRight - x, rowHeight, () -> onSelectTable.accept(table)));
                cy += rowHeight + 1;
            }
        }
        cy = newTableLink(g, cy, mouseX, mouseY, clipTop, clipHeight);

        return lootOdds(g, cy, mouseX, mouseY, clipTop, clipHeight, selectedCount, selectedName);
    }

    /**
     * For a structure that keeps where its loot is a secret: the previews come without loot, so this
     * lists its loot tables from the loot index instead of from the containers in the layout.
     */
    private int secretLoot(GuiGraphics g, int cy, int mouseX, int mouseY, int clipTop, int clipHeight) {
        cy = fineWrapped(g, Component.translatable("screen.justenoughstructures.loot_secret_note"), x + PAD, cy, textWidth(), Gui.LABEL_SOFT) + 3;
        Set<ResourceLocation> tables = FoundIn.tablesIn(entry.id());
        if (tables == null) {
            ClientRequests.index();
            float progress = ClientRequests.indexProgress();
            Component text = progress < 0
                    ? Component.translatable("screen.justenoughstructures.indexing")
                    : Component.translatable("screen.justenoughstructures.indexing_progress", Math.round(progress * 100));
            return fineWrapped(g, text, x + PAD, cy, textWidth(), Gui.LABEL_SOFT);
        }
        if (tables.isEmpty()) {
            return fineWrapped(g, Component.translatable("screen.justenoughstructures.loot_secret_none"), x + PAD, cy, textWidth(), Gui.LABEL_SOFT);
        }
        List<String> sorted = tables.stream().map(ResourceLocation::toString).sorted(Comparator.comparing(StructureNames::lootTable)).toList();
        for (String table : sorted) {
            int rowHeight = 20;
            boolean selected = table.equals(selectedTable);
            boolean hovered = inside(mouseX, mouseY, x, cy, contentRight - x, rowHeight, clipTop, clipHeight);
            Gui.card(g, x, cy, contentRight - x, rowHeight);
            if (selected || hovered) {
                g.fill(x + 1, cy + 1, contentRight - 1, cy + rowHeight - 1, selected ? Gui.ROW_SELECTED : Gui.ROW_HOVER);
            }
            Gui.slot(g, x + PAD + 1, cy + 1);
            g.renderItem(SECRET_ICON, x + PAD + 2, cy + 2);
            int mark = editedMark(g, table, contentRight - 12, cy + 7);
            Gui.fitted(g, font, StructureNames.lootTable(table), x + PAD + 23, cy + 6, contentRight - x - PAD - 23 - 12 - mark, TEXT);
            g.drawString(font, ">", contentRight - 9, cy + 6, hovered ? TEXT : Gui.LABEL_SOFT, false);
            hotspots.add(new Hotspot(x, cy, contentRight - x, rowHeight, () -> onSelectTable.accept(table)));
            cy += rowHeight + 1;
        }
        cy = newTableLink(g, cy, mouseX, mouseY, clipTop, clipHeight);
        return lootOdds(g, cy, mouseX, mouseY, clipTop, clipHeight, 1,
                Component.translatable("screen.justenoughstructures.container").getString());
    }

    /**
     * A short note at the right of a table's row when it's been edited: edited, edited with the
     * mod's table changed since, or an edit that isn't used. Returns the room it took, 0 if none.
     */
    private int editedMark(GuiGraphics g, String table, int right, int top) {
        LootOverrides.Status status = ClientRequests.overrideStatus(table);
        if (status == null || status == LootOverrides.Status.NONE) {
            return 0;
        }
        String key = switch (status) {
            case ORIGINAL_CHANGED, ORIGINAL_MISSING -> "edited_changed";
            case BROKEN -> "edited_broken";
            default -> "edited";
        };
        int colour = switch (status) {
            case ORIGINAL_CHANGED, ORIGINAL_MISSING -> 0xFF9A6200;
            case BROKEN -> 0xFFB02020;
            default -> GOOD;
        };
        String text = Component.translatable("screen.justenoughstructures.loot." + key).getString();
        int width = secondaryWidth(text);
        fine(g, text, right - width, top, colour);
        return width + 4;
    }

    /** A row's tooltip, with what the editor would say about the table's edit added. */
    private static List<Component> withEditedNote(List<Component> lines, String table) {
        LootOverrides.Status status = ClientRequests.overrideStatus(table);
        if (status == null || status == LootOverrides.Status.NONE) {
            return lines;
        }
        List<Component> out = new ArrayList<>(lines);
        out.add(1, Component.translatable("screen.justenoughstructures.editor.status." + status.name().toLowerCase(Locale.ROOT))
                .withStyle(ChatFormatting.GRAY));
        return out;
    }

    /**
     * For players who can edit loot: a link to make a loot table of their own, one that no chest
     * has yet, to point chests at later or give with /loot.
     */
    private int newTableLink(GuiGraphics g, int cy, int mouseX, int mouseY, int clipTop, int clipHeight) {
        if (!ClientRequests.canEditLoot()) {
            return cy;
        }
        String text = Component.translatable("screen.justenoughstructures.new_table").getString();
        int linkWidth = secondaryWidth(text) + 2;
        int linkHeight = secondaryLine() + 2;
        cy += 2;
        boolean over = inside(mouseX, mouseY, x + PAD, cy - 1, linkWidth, linkHeight, clipTop, clipHeight);
        fine(g, text, x + PAD, cy, over ? 0xFF1F3F8F : 0xFF3A55A0);
        if (over) {
            hoveredText = List.of(Component.translatable("screen.justenoughstructures.new_table_hint"));
        }
        hotspots.add(new Hotspot(x + PAD, cy - 1, linkWidth, linkHeight, onNewTable));
        return cy + linkHeight;
    }

    private int lootOdds(GuiGraphics g, int cy, int mouseX, int mouseY, int clipTop, int clipHeight, int selectedCount, String selectedName) {
        if (selectedTable == null) {
            return fineWrapped(g, Component.translatable("screen.justenoughstructures.pick_group"), x + PAD, cy + 4, textWidth(), Gui.LABEL_SOFT);
        }
        cy += 4;
        Gui.band(g, font, Component.translatable("screen.justenoughstructures.odds", selectedName == null ? "" : selectedName).getString(),
                x, cy, contentRight - x, 13);
        cy += 16;
        Component sortLabel = Component.translatable(ClientState.rarestFirst ? "screen.justenoughstructures.sort_rare" : "screen.justenoughstructures.sort_common");
        int sortWidth = secondaryWidth(sortLabel.getString()) + 2;
        int linkHeight = secondaryLine() + 2;
        boolean overSort = inside(mouseX, mouseY, x + PAD, cy - 1, sortWidth, linkHeight, clipTop, clipHeight);
        fine(g, sortLabel.getString(), x + PAD, cy, overSort ? 0xFF1F3F8F : 0xFF3A55A0);
        if (overSort) {
            hoveredText = List.of(Component.translatable(ClientState.rarestFirst ? "screen.justenoughstructures.sort_to_common" : "screen.justenoughstructures.sort_to_rare"));
        }
        hotspots.add(new Hotspot(x + PAD, cy - 1, sortWidth, linkHeight, () -> {
            ClientState.rarestFirst = !ClientState.rarestFirst;
            ClientState.save();
        }));
        // Right-aligned on the same line, for players the server lets edit loot tables.
        if (ClientRequests.canEditLoot() && selectedTable != null) {
            String edit = Component.translatable("screen.justenoughstructures.editor.edit_link").getString();
            int editWidth = secondaryWidth(edit) + 2;
            int editX = contentRight - 2 - editWidth;
            if (editX > x + PAD + sortWidth + 4) {
                boolean overEdit = inside(mouseX, mouseY, editX, cy - 1, editWidth, linkHeight, clipTop, clipHeight);
                fine(g, edit, editX, cy, overEdit ? 0xFF1F3F8F : 0xFF3A55A0);
                if (overEdit) {
                    hoveredText = List.of(Component.translatable("screen.justenoughstructures.editor.edit_hint"));
                }
                String table = selectedTable;
                hotspots.add(new Hotspot(editX, cy - 1, editWidth, linkHeight, () -> onEditTable.accept(table)));
            }
        }
        cy += linkHeight + 2;
        if (odds == null) {
            return fineWrapped(g, Component.translatable("screen.justenoughstructures.rolling"), x + PAD, cy, textWidth(), Gui.LABEL_SOFT);
        }
        if (odds.rows().isEmpty()) {
            return fineWrapped(g, Component.translatable("screen.justenoughstructures.always_empty"), x + PAD, cy, textWidth(), Gui.LABEL_SOFT);
        }
        List<LootOdds.Row> rows = new ArrayList<>(odds.rows());
        if (ClientState.rarestFirst) {
            rows.sort(Comparator.comparingInt(LootOdds.Row::hits));
        }
        Map<String, Integer> nameCounts = new HashMap<>();
        rows.forEach(r -> nameCounts.merge(r.example().getHoverName().getString(), 1, Integer::sum));
        int rowHeight = 20;
        for (LootOdds.Row row : rows) {
            float chance = (float) row.hits() / odds.rolls();
            boolean rare = chance < 0.1f;
            int rowTop = cy;
            boolean hovered = inside(mouseX, mouseY, x + 2, cy, contentRight - x - 2, rowHeight, clipTop, clipHeight);
            if (hovered) {
                g.fill(x + 2, cy, contentRight, cy + rowHeight, Gui.ROW_HOVER);
            }
            Gui.slot(g, x + PAD - 1, cy);
            g.renderItem(row.example(), x + PAD, cy + 1);
            int textX = x + PAD + 21;
            String name = row.example().getHoverName().getString();
            if (nameCounts.getOrDefault(name, 0) > 1) {
                name = StructureNames.pretty(BuiltInRegistries.ITEM.getKey(row.example().getItem()).getPath());
            }
            Gui.fitted(g, font, name, textX, cy + 1, contentRight - 2 - textX, rare ? RARE : TEXT);
            // The second line: how many come at a time, then the bar, then the chance it shows.
            int lineY = cy + 11;
            String pct = chance >= 0.1f ? Math.round(chance * 100) + "%" : String.format("%.1f%%", chance * 100);
            int pctX = contentRight - 2 - font.width(pct);
            g.drawString(font, pct, pctX, lineY, rare ? RARE : TEXT, false);
            float average = (float) row.total() / row.hits();
            String counts = row.min() == row.max()
                    ? Component.translatable("screen.justenoughstructures.count_exact", row.min()).getString()
                    : Component.translatable("screen.justenoughstructures.count_range", row.min(), row.max(), Math.round(average)).getString();
            int countsY = lineY + (font.lineHeight - 1 - secondaryLine()) / 2;
            fine(g, fineClip(counts, pctX - 4 - textX), textX, countsY, Gui.LABEL_SOFT);
            int barLeft = textX + secondaryWidth(counts) + 4;
            int barRight = pctX - 4;
            if (barRight - barLeft > 10) {
                g.fill(barLeft, lineY + 2, barRight, lineY + 6, Gui.BAR_BACK);
                g.fill(barLeft, lineY + 2, barLeft + Math.max(1, (int) ((barRight - barLeft) * chance)), lineY + 6, rare ? 0xFFC08A20 : Gui.BAR);
            }
            if (hovered) {
                hoveredStack = row.example();
                hoveredExtra = oddsTooltip(row, chance, selectedCount, selectedName == null ? "" : selectedName);
            }
            ItemStack example = row.example();
            hotspots.add(new Hotspot(x + 2, cy, contentRight - x - 2, rowHeight, () -> onItemClicked.accept(example)));
            if (rowTop >= clipTop && rowTop + rowHeight <= clipTop + clipHeight) {
                oddsRows.put(example.getItem(), new int[]{x + PAD + 60, rowTop + rowHeight / 2});
            }
            cy += rowHeight + 1;
        }
        if (odds.emptyRolls() > 0) {
            cy = fineWrapped(g, Component.translatable("screen.justenoughstructures.empty_rolls",
                    String.format("%.1f%%", 100f * odds.emptyRolls() / odds.rolls())), x + PAD, cy + 2, textWidth(), Gui.LABEL_SOFT);
        }
        cy = fineWrapped(g, Component.translatable("screen.justenoughstructures.odds_note", String.format("%,d", odds.rolls())),
                x + PAD, cy + 2, textWidth(), Gui.LABEL_SOFT);
        return cy;
    }

    private static List<Component> oddsTooltip(LootOdds.Row row, float chance, int containers, String containerName) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.justenoughstructures.tip_chance", containerName, String.format("%.1f%%", chance * 100)).withStyle(ChatFormatting.GRAY));
        if (containers > 1) {
            double atLeastOne = 1 - Math.pow(1 - chance, containers);
            lines.add(Component.translatable("screen.justenoughstructures.tip_total", containers, containerName,
                    String.format("%.0f%%", atLeastOne * 100)).withStyle(ChatFormatting.GRAY));
        }
        List<Component> variants = new ArrayList<>();
        for (Map.Entry<String, Integer> e : row.variants().entrySet()) {
            String key = e.getKey();
            if (key.startsWith("enchantment:")) {
                Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(ResourceLocation.tryParse(key.substring(12)));
                if (enchantment != null) {
                    variants.add(enchantment.getFullname(e.getValue()));
                }
            } else if (key.startsWith("potion:")) {
                Potion potion = BuiltInRegistries.POTION.get(ResourceLocation.tryParse(key.substring(7)));
                variants.add(Component.translatable(potion.getName("item.minecraft.potion.effect.")));
            }
        }
        if (!variants.isEmpty()) {
            lines.add(Component.translatable("screen.justenoughstructures.tip_variants").withStyle(ChatFormatting.GRAY));
            int shown = Math.min(8, variants.size());
            for (int i = 0; i < shown; i++) {
                lines.add(Component.literal("  ").append(variants.get(i).copy().withStyle(ChatFormatting.DARK_AQUA)));
            }
            if (variants.size() > shown) {
                lines.add(Component.translatable("screen.justenoughstructures.and_more", variants.size() - shown).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return lines;
    }

    /** The block's own item for block containers (so suspicious sand looks like sand), otherwise by id. */
    static ItemStack containerIcon(StructureSnapshot snapshot, StructureSnapshot.Container container) {
        if (!container.entity()) {
            BlockState state = snapshot.stateAt(container.pos());
            if (state != null && state.getBlock().asItem() != Items.AIR) {
                return new ItemStack(state.getBlock().asItem());
            }
        }
        ResourceLocation id = ResourceLocation.tryParse(container.id());
        Item item = id == null ? Items.CHEST : BuiltInRegistries.ITEM.get(id);
        return new ItemStack(item == Items.AIR ? Items.CHEST : item);
    }

    // ------------------------------------------------------------------ blocks

    private int blocks(GuiGraphics g, int cy, int mouseX, int mouseY, int clipTop, int clipHeight) {
        if (result == null || !result.succeeded()) {
            return fineWrapped(g, Component.translatable("screen.justenoughstructures.loot_waiting"), x + PAD, cy, textWidth(), Gui.LABEL_SOFT);
        }
        StructureSnapshot s = result.snapshot();
        List<Map.Entry<Block, Integer>> sorted = Exports.blockCounts(s);
        cy = fineWrapped(g, Component.translatable("screen.justenoughstructures.block_types", sorted.size(), String.format("%,d", s.blockCount())),
                x + PAD, cy, textWidth(), TEXT) + 3;

        Component label = Component.translatable("screen.justenoughstructures.blocks_copy");
        int bx = x + PAD;
        int w = font.width(label) + 8;
        boolean over = inside(mouseX, mouseY, bx, cy, w, 13, clipTop, clipHeight);
        g.fill(bx, cy, bx + w, cy + 13, Gui.EDGE);
        g.fill(bx + 1, cy + 1, bx + w - 1, cy + 12, over ? 0xFF8D8D8D : 0xFF737373);
        g.drawString(font, label, bx + 4, cy + 3, over ? 0xFFFFFFA0 : 0xFFE0E0E0, true);
        hotspots.add(new Hotspot(bx, cy, w, 13, () -> notify(Exports.copyMaterialList(entry.id(), s))));
        cy += 16;
        if (notice != null && System.currentTimeMillis() < noticeUntil) {
            cy = fineWrapped(g, notice, x + PAD, cy, textWidth(), GOOD) + 2;
        }

        int rowHeight = Math.max(19, 12 + secondaryLine());
        for (Map.Entry<Block, Integer> e : sorted) {
            if (cy + rowHeight < clipTop || cy > clipTop + clipHeight) {
                // Scrolled out of view: a big structure can use hundreds of kinds of block.
                cy += rowHeight;
                continue;
            }
            Block block = e.getKey();
            int count = e.getValue();
            boolean hovered = inside(mouseX, mouseY, x, cy, contentRight - x, rowHeight, clipTop, clipHeight);
            if (hovered) {
                g.fill(x, cy, contentRight, cy + rowHeight, Gui.ROW_HOVER);
            }
            ItemStack stack = new ItemStack(block.asItem());
            if (stack.isEmpty()) {
                stack = new ItemStack(Items.BARRIER);
            }
            Gui.slot(g, x + PAD - 1, cy);
            g.renderItem(stack, x + PAD, cy + 1);
            int textX = x + PAD + 21;
            Gui.fitted(g, font, block.getName().getString(), textX, cy + 1, contentRight - 2 - textX, TEXT);
            String amount = String.format("%,d", count);
            if (count >= 64) {
                amount = Component.translatable("screen.justenoughstructures.block_amount", amount,
                        Component.translatable("screen.justenoughstructures.stacks", count / 64, count % 64)).getString();
            }
            fine(g, fineClip(amount, contentRight - 2 - textX), textX, cy + 11, Gui.LABEL_SOFT);
            if (hovered) {
                List<Component> lines = new ArrayList<>();
                lines.add(block.getName());
                lines.add(Component.translatable("screen.justenoughstructures.stacks", count / 64, count % 64).withStyle(ChatFormatting.GRAY));
                if (Minecraft.getInstance().options.advancedItemTooltips) {
                    lines.add(Component.literal(BuiltInRegistries.BLOCK.getKey(block).toString()).withStyle(ChatFormatting.DARK_GRAY));
                }
                hoveredText = lines;
                if (count <= Highlight.MAX_BLOCKS) {
                    hoveredBlocks = new Hovered("block:" + BuiltInRegistries.BLOCK.getKey(block), snapshot -> positionsOf(snapshot, block));
                }
            }
            highlightRows.put("block:" + BuiltInRegistries.BLOCK.getKey(block), new int[]{x + (contentRight - x) / 2, cy + rowHeight / 2});
            cy += rowHeight;
        }
        return cy;
    }

    /** Where every block of a kind is in a snapshot. */
    private static LongSet positionsOf(StructureSnapshot snapshot, Block block) {
        boolean[] matches = new boolean[snapshot.palette().size()];
        for (int i = 0; i < matches.length; i++) {
            matches[i] = snapshot.palette().get(i).is(block);
        }
        LongSet out = new LongOpenHashSet();
        for (int i = 0; i < snapshot.blockCount(); i++) {
            if (matches[snapshot.paletteIndex(i)]) {
                int packed = snapshot.packedPosition(i);
                out.add(BlockPos.asLong(StructureSnapshot.unpackX(packed), StructureSnapshot.unpackY(packed), StructureSnapshot.unpackZ(packed)));
            }
        }
        return out;
    }

    private static LongSet positionsOf(List<StructureSnapshot.Container> containers) {
        LongSet out = new LongOpenHashSet();
        containers.forEach(c -> out.add(c.pos().asLong()));
        return out;
    }

    // ------------------------------------------------------------------ mobs

    private static final String SPAWNER = String.valueOf(BlockEntityType.getKey(BlockEntityType.MOB_SPAWNER));

    private int mobs(GuiGraphics g, int cy, int mouseX, int mouseY, int clipTop, int clipHeight) {
        if (result == null || !result.succeeded()) {
            return fineWrapped(g, Component.translatable("screen.justenoughstructures.loot_waiting"), x + PAD, cy, textWidth(), Gui.LABEL_SOFT);
        }
        StructureSnapshot s = result.snapshot();
        Map<String, Integer> placed = new LinkedHashMap<>();
        for (CompoundTag tag : s.entities()) {
            placed.merge(tag.getString("id"), 1, Integer::sum);
        }
        Map<String, Integer> spawners = new LinkedHashMap<>();
        // Spawners whose mob was picked from a list, or that cycle through several, by that list.
        Map<Map<String, Integer>, Integer> picked = new LinkedHashMap<>();
        Map<Map<String, Integer>, Integer> mixed = new LinkedHashMap<>();
        // Where the spawners of each row are, to show them in the preview while it's hovered.
        Map<Object, LongSet> where = new HashMap<>();
        for (CompoundTag tag : s.blockEntities()) {
            if (!tag.getString("id").equals(SPAWNER)) {
                continue;
            }
            Map<String, Integer> pool = weights(tag.getList(SpawnerPools.TAG, Tag.TAG_COMPOUND), "entity", null);
            Map<String, Integer> potentials = weights(tag.getList("SpawnPotentials", Tag.TAG_COMPOUND), "data", "weight");
            Object row;
            if (pool.size() > 1) {
                picked.merge(pool, 1, Integer::sum);
                row = pool;
            } else if (potentials.size() > 1) {
                mixed.merge(potentials, 1, Integer::sum);
                row = potentials;
            } else {
                String mob = tag.getCompound("SpawnData").getCompound("entity").getString("id");
                spawners.merge(mob.isEmpty() ? "?" : mob, 1, Integer::sum);
                row = mob.isEmpty() ? "?" : mob;
            }
            where.computeIfAbsent(row, k -> new LongOpenHashSet()).add(BlockPos.asLong(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")));
        }
        Map<String, Integer> overTime = new LinkedHashMap<>();
        JsonObject def = entry == null ? null : entry.definition();
        if (def != null && def.has("spawn_overrides") && def.get("spawn_overrides").isJsonObject()) {
            for (Map.Entry<String, JsonElement> category : def.getAsJsonObject("spawn_overrides").entrySet()) {
                JsonElement spawns = category.getValue().isJsonObject() ? category.getValue().getAsJsonObject().get("spawns") : null;
                if (spawns != null && spawns.isJsonArray()) {
                    spawns.getAsJsonArray().forEach(e -> {
                        if (e.isJsonObject() && e.getAsJsonObject().has("type")) {
                            overTime.merge(e.getAsJsonObject().get("type").getAsString(), 1, Integer::sum);
                        }
                    });
                }
            }
        }

        if (placed.isEmpty() && spawners.isEmpty() && picked.isEmpty() && mixed.isEmpty() && overTime.isEmpty()) {
            return fineWrapped(g, Component.translatable("screen.justenoughstructures.no_entities"), x + PAD, cy, textWidth(), Gui.LABEL_SOFT);
        }
        cy = mobSection(g, cy, "placed", placed, true);
        if (!spawners.isEmpty() || !picked.isEmpty() || !mixed.isEmpty()) {
            Gui.band(g, font, Component.translatable("screen.justenoughstructures.mobs_spawners").getString(), x, cy, contentRight - x, 13);
            cy += 16;
            for (Map.Entry<String, Integer> e : spawners.entrySet()) {
                int top = cy;
                boolean over = where.containsKey(e.getKey()) && inside(mouseX, mouseY, x, cy, contentRight - x, 22, clipTop, clipHeight);
                cy = mobRow(g, cy, e.getKey(), Component.translatable("screen.justenoughstructures.times", e.getValue()).getString(), over);
                hoverSpawners(mouseX, mouseY, top, cy, clipTop, clipHeight, "spawners:" + e.getKey(), where.get(e.getKey()));
            }
            cy = pools(g, cy, picked, "spawner_pool", mouseX, mouseY, clipTop, clipHeight, where);
            cy = pools(g, cy, mixed, "spawner_mix", mouseX, mouseY, clipTop, clipHeight, where);
            cy = fineWrapped(g, Component.translatable("screen.justenoughstructures.mobs_spawners_note"), x + PAD, cy + 1, textWidth(), Gui.LABEL_SOFT) + 6;
        }
        cy = mobSection(g, cy, "over_time", overTime, false);
        return cy;
    }

    /**
     * Each list of mobs spawners pick from, under a line saying how many spawners here use it, with
     * every mob's chance.
     */
    private int pools(GuiGraphics g, int cy, Map<Map<String, Integer>, Integer> pools, String key,
                      int mouseX, int mouseY, int clipTop, int clipHeight, Map<Object, LongSet> where) {
        for (Map.Entry<Map<String, Integer>, Integer> pool : pools.entrySet()) {
            int top = cy;
            int count = pool.getValue();
            Component line = count == 1 ? Component.translatable("screen.justenoughstructures." + key + "_one")
                    : Component.translatable("screen.justenoughstructures." + key + "_many", count);
            cy = fineWrapped(g, line, x + PAD, cy + 2, textWidth(), Gui.LABEL_SOFT) + 3;
            int total = pool.getKey().values().stream().mapToInt(Integer::intValue).sum();
            List<Map.Entry<String, Integer>> mobs = new ArrayList<>(pool.getKey().entrySet());
            mobs.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
            for (Map.Entry<String, Integer> mob : mobs) {
                float chance = (float) mob.getValue() / total;
                String pct = chance >= 0.1f ? Math.round(chance * 100) + "%" : String.format("%.1f%%", chance * 100);
                boolean over = where.containsKey(pool.getKey()) && inside(mouseX, mouseY, x, cy, contentRight - x, 22, clipTop, clipHeight);
                cy = mobRow(g, cy, mob.getKey(), pct, over);
            }
            hoverSpawners(mouseX, mouseY, top, cy, clipTop, clipHeight, key + ":" + pool.getKey(), where.get(pool.getKey()));
        }
        return cy;
    }

    /** Shows a row's spawners in the preview while anywhere from {@code top} to {@code bottom} is hovered. */
    private void hoverSpawners(int mouseX, int mouseY, int top, int bottom, int clipTop, int clipHeight, String key, LongSet positions) {
        highlightRows.put(key, new int[]{x + (contentRight - x) / 2, bottom - 11});
        if (positions != null && inside(mouseX, mouseY, x, top, contentRight - x, bottom - top, clipTop, clipHeight)) {
            hoveredBlocks = new Hovered(key, s -> positions);
        }
    }

    /**
     * Mob ids to weights from a list of entries, merging repeats. With no {@code weightKey} the id is
     * under {@code key}; otherwise {@code key} holds SpawnPotentials' entity data.
     */
    private static Map<String, Integer> weights(ListTag list, String key, String weightKey) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Tag t : list) {
            CompoundTag entry = (CompoundTag) t;
            String mob = weightKey == null ? entry.getString(key) : entry.getCompound(key).getCompound("entity").getString("id");
            int weight = entry.getInt(weightKey == null ? "weight" : weightKey);
            if (!mob.isEmpty() && weight > 0) {
                out.merge(mob, weight, Integer::sum);
            }
        }
        return out;
    }

    private int mobSection(GuiGraphics g, int cy, String key, Map<String, Integer> mobs, boolean counts) {
        if (mobs.isEmpty()) {
            return cy;
        }
        Gui.band(g, font, Component.translatable("screen.justenoughstructures.mobs_" + key).getString(), x, cy, contentRight - x, 13);
        cy += 16;
        for (Map.Entry<String, Integer> e : mobs.entrySet()) {
            cy = mobRow(g, cy, e.getKey(), counts ? Component.translatable("screen.justenoughstructures.times", e.getValue()).getString() : "");
        }
        Component note = Component.translatable("screen.justenoughstructures.mobs_" + key + "_note");
        return fineWrapped(g, note, x + PAD, cy + 1, textWidth(), Gui.LABEL_SOFT) + 6;
    }

    /** A mob's card: its spawn egg, its name and, on the right, {@code right}. */
    private int mobRow(GuiGraphics g, int cy, String mob, String right) {
        return mobRow(g, cy, mob, right, false);
    }

    /** {@code hovered} rows are lit, for the ones that show something in the preview. */
    private int mobRow(GuiGraphics g, int cy, String mob, String right, boolean hovered) {
        ResourceLocation id = ResourceLocation.tryParse(mob);
        EntityType<?> type = id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? BuiltInRegistries.ENTITY_TYPE.get(id) : null;
        Component name = type != null ? type.getDescription() : Component.literal(mob);
        Gui.card(g, x, cy, contentRight - x, 22);
        if (hovered) {
            g.fill(x + 1, cy + 1, contentRight - 1, cy + 21, Gui.ROW_HOVER);
        }
        Gui.slot(g, x + PAD + 1, cy + 2);
        SpawnEggItem egg = type == null ? null : SpawnEggItem.byId(type);
        if (egg != null) {
            g.renderItem(new ItemStack(egg), x + PAD + 2, cy + 3);
        }
        Gui.fitted(g, font, name.getString(), x + PAD + 23, cy + 7, contentRight - x - PAD - 30 - font.width(right), TEXT);
        g.drawString(font, right, contentRight - 4 - font.width(right), cy + 7, Gui.LABEL_SOFT, false);
        return cy + 23;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h, int clipTop, int clipHeight) {
        return mx >= x && mx < x + w && my >= y && my < y + h && my >= clipTop && my < clipTop + clipHeight;
    }
}
