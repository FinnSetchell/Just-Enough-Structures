package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.catalog.Availability;
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
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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
import net.minecraft.world.entity.Entity;
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
    /** Opens a container, and whether it was picked from a row standing for several. */
    private final BiConsumer<StructureSnapshot.Container, Boolean> onOpenContainer;
    private final Consumer<ItemStack> onItemClicked;
    private Consumer<String> onOpenTable = table -> {
    };
    /** Opens a spawner, and whether it was picked from a row standing for several. */
    private BiConsumer<StructureSnapshot.Spawner, Boolean> onOpenSpawner = (spawner, group) -> {
    };
    private Runnable beforeMove = () -> {
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

    InfoPanel(Font font, Consumer<String> onSelectTable, BiConsumer<StructureSnapshot.Container, Boolean> onOpenContainer,
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

    /** The same structure, as the server describes it now, keeping everything else on show. */
    void updateEntry(StructureCatalog.Entry entry) {
        this.entry = entry;
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

    /** What clicking a loot table this layout doesn't have does: opens it in a popup on its own. */
    void onOpenTable(Consumer<String> action) {
        onOpenTable = action;
    }

    /** What clicking a row of spawners on the Mobs tab does: opens them in a popup, one at a time. */
    void onOpenSpawner(BiConsumer<StructureSnapshot.Spawner, Boolean> action) {
        onOpenSpawner = action;
    }

    /** Told just before the panel moves somewhere else, like another tab, so Back can return. */
    void onMove(Runnable action) {
        beforeMove = action;
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
        Gui.scissor(g, x, top, x + width, top + bodyHeight);
        clampScroll();
        int cursor = top + 2 - (int) scroll;
        int end = switch (tab) {
            case OVERVIEW -> overview(g, cursor, mouseX, mouseY, top, bodyHeight);
            case LOOT -> loot(g, cursor, mouseX, mouseY, top, bodyHeight);
            case BLOCKS -> blocks(g, cursor, mouseX, mouseY, top, bodyHeight);
            case ENTITIES -> mobs(g, cursor, mouseX, mouseY, top, bodyHeight);
        };
        Gui.endScissor(g);
        contentHeight = end - cursor + 2;
        clampScroll();
        if (contentHeight > bodyHeight) {
            Gui.scrollbar(g, x + width - 4, top, bodyHeight, scroll, contentHeight - bodyHeight);
        }
    }

    boolean click(double mouseX, double mouseY) {
        if (mouseY >= tabsY && mouseY < tabsY + TAB_SIZE - 3 && mouseX >= tabsX && mouseX < tabsX + TAB_SIZE * Tab.values().length) {
            Tab clicked = Tab.values()[(int) ((mouseX - tabsX) / TAB_SIZE)];
            if (clicked != tab) {
                beforeMove.run();
                setTab(clicked);
            }
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
        if (!entry.availability().generates()) {
            cy = field(g, cy, "unavailable", unavailable(entry.availability()).getString());
        }

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
            Gui.band(g, font, title, x, cy, contentRight - x, 13);
            cy += 16;
            cy = Gui.wrapped(g, font, info.notes(), x + PAD, cy, textWidth(), TEXT) + 3;
        }

        // Everything a datapack author wants and a player doesn't: only with advanced tooltips (F3+H),
        // as vanilla does with ids, and folded away even then.
        if (!Gui.advanced()) {
            return cy;
        }
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

    /** Secondary text cut short to fit {@code width}, shown in full when hovered. */
    private void fineClipped(GuiGraphics g, String text, int left, int top, int width, int color) {
        String shown = fineClip(text, width);
        fine(g, shown, left, top, color);
        if (!shown.equals(text)) {
            Gui.noteClipped(left, top - 1, Math.max(6, secondaryWidth(shown)), (int) Math.ceil(font.lineHeight * secondaryScale()) + 1, text);
        }
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

    /** Why a structure won't turn up in new worlds, in a sentence. */
    static Component unavailable(Availability availability) {
        String by = availability.by() == null ? "" : StructureNames.mod(availability.by());
        return switch (availability.reason()) {
            case NO_SET -> Component.translatable("screen.justenoughstructures.unavailable.no_set");
            case NEVER -> Component.translatable("screen.justenoughstructures.unavailable.never");
            case TAGGED_OFF -> Component.translatable("screen.justenoughstructures.unavailable.tagged");
            case TURNED_OFF -> Component.translatable("screen.justenoughstructures.unavailable.turned_off", by);
            case REPLACED -> availability.replacedBy() != null
                    ? Component.translatable("screen.justenoughstructures.unavailable.replaced", StructureNames.structure(availability.replacedBy()), by)
                    : Component.translatable("screen.justenoughstructures.unavailable.replaced_by_mod", by);
            case GENERATES -> Component.empty();
        };
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
        // One row per kind of container and loot table, so a chest and a barrel on the same table are told apart.
        Map<String, List<StructureSnapshot.Container>> groups = new LinkedHashMap<>();
        for (StructureSnapshot.Container c : snapshot.containers()) {
            groups.computeIfAbsent((c.lootTable() == null ? "" : c.lootTable()) + "|" + c.id(), k -> new ArrayList<>()).add(c);
        }
        // Tables this layout happens not to have, from every layout the structure can generate, so
        // what they hold can be seen without rolling new layouts until one turns up.
        Set<ResourceLocation> everyTable = FoundIn.tablesIn(entry.id());
        if (everyTable == null) {
            ClientRequests.index();
        }
        List<String> others = everyTable == null ? List.of() : everyTable.stream().map(ResourceLocation::toString)
                .filter(t -> snapshot.containers().stream().noneMatch(c -> t.equals(c.lootTable())))
                .sorted(Comparator.comparing(StructureNames::lootTable)).toList();
        if (groups.isEmpty() && others.isEmpty()) {
            return fineWrapped(g, Component.translatable("screen.justenoughstructures.no_loot"), x + PAD, cy, textWidth(), Gui.LABEL_SOFT);
        }

        int rowHeight = 15 + secondaryLine();
        if (!groups.isEmpty()) {
            fine(g, Component.translatable("screen.justenoughstructures.containers_here").getString(), x + PAD, cy, Gui.LABEL_SOFT);
            cy += secondaryLine() + 2;
            List<List<StructureSnapshot.Container>> sorted = new ArrayList<>(groups.values());
            sorted.sort(Comparator.comparing((List<StructureSnapshot.Container> list) -> list.get(0).lootTable() == null)
                    .thenComparing(list -> -list.size()));
            for (List<StructureSnapshot.Container> containers : sorted) {
                StructureSnapshot.Container first = containers.get(0);
                String table = first.lootTable() == null ? "" : first.lootTable();
                boolean hovered = inside(mouseX, mouseY, x, cy, contentRight - x, rowHeight, clipTop, clipHeight);
                Gui.card(g, x, cy, contentRight - x, rowHeight);
                if (hovered) {
                    g.fill(x + 1, cy + 1, contentRight - 1, cy + rowHeight - 1, Gui.ROW_HOVER);
                }
                ItemStack icon = containerIcon(snapshot, first);
                Gui.slot(g, x + PAD + 1, cy + 2);
                g.renderItem(icon, x + PAD + 2, cy + 3);
                String name = Component.translatable("screen.justenoughstructures.name_times", icon.getHoverName(), containers.size()).getString();
                String detail = table.isEmpty() ? Component.translatable("screen.justenoughstructures.prefilled").getString() : StructureNames.lootTable(table);
                int textWidth = contentRight - x - PAD - 23 - 12;
                Gui.fitted(g, font, name, x + PAD + 23, cy + 3, textWidth, TEXT);
                int mark = editedMark(g, table, x + PAD + 23 + textWidth, cy + 13);
                fineClipped(g, detail, x + PAD + 23, cy + 13, textWidth - mark, Gui.LABEL_SOFT);
                g.drawString(font, ">", contentRight - 9, cy + (rowHeight - 8) / 2, hovered ? TEXT : Gui.LABEL_SOFT, false);
                String key = "loot:" + table + "|" + first.id();
                if (hovered) {
                    List<Component> lines = new ArrayList<>(List.of(Component.literal(name)));
                    if (Gui.advanced() && !table.isEmpty()) {
                        lines.add(Component.literal(table).withStyle(ChatFormatting.DARK_GRAY));
                    }
                    hoveredText = withEditedNote(lines, table);
                    hoveredBlocks = new Hovered(key, s -> positionsOf(containers));
                }
                highlightRows.put("loot:" + table, new int[]{x + (contentRight - x) / 2, cy + rowHeight / 2});
                hotspots.add(new Hotspot(x, cy, contentRight - x, rowHeight, () -> onOpenContainer.accept(first, containers.size() > 1)));
                cy += rowHeight + 1;
            }
        }

        if (!others.isEmpty()) {
            cy += 2;
            fine(g, Component.translatable("screen.justenoughstructures.other_layouts").getString(), x + PAD, cy, Gui.LABEL_SOFT);
            cy += secondaryLine() + 2;
            // One line each, as the heading already says they aren't in this layout.
            int otherHeight = 20;
            for (String table : others) {
                boolean hovered = inside(mouseX, mouseY, x, cy, contentRight - x, otherHeight, clipTop, clipHeight);
                Gui.card(g, x, cy, contentRight - x, otherHeight);
                if (hovered) {
                    g.fill(x + 1, cy + 1, contentRight - 1, cy + otherHeight - 1, Gui.ROW_HOVER);
                }
                Gui.slot(g, x + PAD + 1, cy + 1);
                g.renderItem(SECRET_ICON, x + PAD + 2, cy + 2);
                int mark = editedMark(g, table, contentRight - 12, cy + 7);
                Gui.fitted(g, font, StructureNames.lootTable(table), x + PAD + 23, cy + 6, contentRight - x - PAD - 23 - 12 - mark, TEXT);
                g.drawString(font, ">", contentRight - 9, cy + 6, hovered ? TEXT : Gui.LABEL_SOFT, false);
                if (hovered) {
                    List<Component> lines = new ArrayList<>(List.of(Component.literal(StructureNames.lootTable(table))));
                    if (Gui.advanced()) {
                        lines.add(Component.literal(table).withStyle(ChatFormatting.DARK_GRAY));
                    }
                    hoveredText = withEditedNote(lines, table);
                }
                highlightRows.put("other:" + table, new int[]{x + (contentRight - x) / 2, cy + otherHeight / 2});
                hotspots.add(new Hotspot(x, cy, contentRight - x, otherHeight, () -> onOpenTable.accept(table)));
                cy += otherHeight + 1;
            }
        }
        return lootTotals(g, cy, snapshot, mouseX, mouseY, clipTop, clipHeight);
    }

    /** One item's chance across every container in the layout, built up a table at a time. */
    private static final class Total {
        final ItemStack example;
        double none = 1;
        double amount;
        final List<Component> from = new ArrayList<>();
        final Map<String, Integer> variants = new TreeMap<>();

        Total(ItemStack example) {
            this.example = example;
        }

        float chance() {
            return (float) (1 - none);
        }
    }

    /**
     * What the whole layout can give: for each item, the chance of at least one across every
     * container, and how many come in all, from each table's chance in one roll and how many
     * containers use it.
     */
    private int lootTotals(GuiGraphics g, int cy, StructureSnapshot snapshot, int mouseX, int mouseY, int clipTop, int clipHeight) {
        // How many containers use each table, by kind.
        Map<String, Map<String, Integer>> perTable = new LinkedHashMap<>();
        for (StructureSnapshot.Container c : snapshot.containers()) {
            if (c.lootTable() == null) {
                continue;
            }
            String kind = containerIcon(snapshot, c).getHoverName().getString();
            perTable.computeIfAbsent(c.lootTable(), k -> new LinkedHashMap<>()).merge(kind, 1, Integer::sum);
        }
        if (perTable.isEmpty()) {
            return cy;
        }
        cy += 4;
        String heading = Component.translatable("screen.justenoughstructures.whole_structure").getString();
        if (font.width(heading) > contentRight - x - 4) {
            heading = Component.translatable("screen.justenoughstructures.whole_structure_short").getString();
        }
        Gui.band(g, font, heading, x, cy, contentRight - x, 13);
        cy += 16;
        cy = sortLink(g, cy, mouseX, mouseY, clipTop, clipHeight);

        // Every table's chances are needed before the totals mean anything.
        Map<String, LootOdds> odds = new LinkedHashMap<>();
        for (String table : perTable.keySet()) {
            ResourceLocation id = ResourceLocation.tryParse(table);
            LootOdds found = id == null ? null : ClientRequests.odds(id).getNow(null);
            if (found == null) {
                return fineWrapped(g, Component.translatable("screen.justenoughstructures.rolling"), x + PAD, cy, textWidth(), Gui.LABEL_SOFT);
            }
            odds.put(table, found);
        }
        Map<Item, Total> totals = new LinkedHashMap<>();
        for (Map.Entry<String, LootOdds> e : odds.entrySet()) {
            LootOdds table = e.getValue();
            Map<String, Integer> kinds = perTable.get(e.getKey());
            int n = kinds.values().stream().mapToInt(Integer::intValue).sum();
            for (LootOdds.Row row : table.rows()) {
                float p = (float) row.hits() / table.rolls();
                Total total = totals.computeIfAbsent(row.example().getItem(), item -> new Total(row.example()));
                total.none *= Math.pow(1 - p, n);
                total.amount += n * (double) row.total() / table.rolls();
                total.from.add(Component.literal("  ").append(Component.translatable("screen.justenoughstructures.tip_from_table",
                        StructureNames.lootTable(e.getKey()), String.format("%.1f%%", p * 100), counted(kinds))).withStyle(ChatFormatting.DARK_AQUA));
                row.variants().forEach((variant, level) -> total.variants.merge(variant, level, Math::max));
            }
        }
        List<Total> rows = new ArrayList<>(totals.values());
        rows.sort(ClientState.rarestFirst ? Comparator.comparingDouble(Total::chance) : Comparator.comparingDouble((Total t) -> -t.chance()));
        Map<Total, String> amounts = new HashMap<>();
        for (Total total : rows) {
            amounts.put(total, total.amount >= 0.95
                    ? Component.translatable("screen.justenoughstructures.in_all", Math.max(1, Math.round(total.amount))).getString()
                    : Component.translatable("screen.justenoughstructures.on_average", String.format("%.1f", total.amount)).getString());
        }
        int detailRoom = OddsList.detailRoom(font, amounts.values());
        for (Total total : rows) {
            float chance = total.chance();
            String amount = amounts.get(total);
            boolean hovered = inside(mouseX, mouseY, x + 2, cy, contentRight - x - 2, OddsList.ROW, clipTop, clipHeight);
            if (cy + OddsList.ROW >= clipTop && cy <= clipTop + clipHeight) {
                OddsList.drawRow(g, font, total.example, total.example.getHoverName().getString(), amount, chance, x, cy, contentRight, hovered, detailRoom);
            }
            if (hovered) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("screen.justenoughstructures.tip_at_least_one", String.format("%.1f%%", chance * 100)).withStyle(ChatFormatting.GRAY));
                lines.add((total.amount >= 0.95
                        ? Component.translatable("screen.justenoughstructures.tip_in_all", Math.max(1, Math.round(total.amount)))
                        : Component.translatable("screen.justenoughstructures.tip_on_average", String.format("%.1f", total.amount))).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("screen.justenoughstructures.tip_from").withStyle(ChatFormatting.GRAY));
                lines.addAll(total.from);
                lines.addAll(OddsList.variantLines(total.variants));
                hoveredStack = total.example;
                hoveredExtra = lines;
            }
            ItemStack example = total.example;
            hotspots.add(new Hotspot(x + 2, cy, contentRight - x - 2, OddsList.ROW, () -> onItemClicked.accept(example)));
            if (cy >= clipTop && cy + OddsList.ROW <= clipTop + clipHeight) {
                oddsRows.put(example.getItem(), new int[]{x + PAD + 60, cy + OddsList.ROW / 2});
            }
            cy += OddsList.ROW + 1;
        }
        return cy;
    }

    /** "7 x Suspicious Sand and 4 x Chest", most first. */
    private static String counted(Map<String, Integer> kinds) {
        List<String> parts = kinds.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> Component.translatable("screen.justenoughstructures.count_of", e.getValue(), e.getKey()).getString()).toList();
        if (parts.size() < 2) {
            return String.join("", parts);
        }
        return Component.translatable("screen.justenoughstructures.and", String.join(", ", parts.subList(0, parts.size() - 1)),
                parts.get(parts.size() - 1)).getString();
    }

    /** "Most likely first" or "Rarest first", which switches the order. Returns the y below it. */
    private int sortLink(GuiGraphics g, int cy, int mouseX, int mouseY, int clipTop, int clipHeight) {
        Component sortLabel = Component.translatable(ClientState.rarestFirst ? "screen.justenoughstructures.sort_rare" : "screen.justenoughstructures.sort_common");
        int sortWidth = secondaryWidth(sortLabel.getString()) + 2;
        int linkHeight = secondaryLine() + 2;
        boolean overSort = inside(mouseX, mouseY, x + PAD, cy - 1, sortWidth, linkHeight, clipTop, clipHeight);
        fine(g, sortLabel.getString(), x + PAD, cy, overSort ? 0xFF1F3F8F : 0xFF3A55A0);
        hotspots.add(new Hotspot(x + PAD, cy - 1, sortWidth, linkHeight, () -> {
            ClientState.rarestFirst = !ClientState.rarestFirst;
            ClientState.save();
        }));
        return cy + linkHeight + 2;
    }

    /**
     * For a structure that keeps where its loot is a secret: the previews come without loot, so this
     * lists its loot tables from the loot index instead of from the containers in the layout.
     */
    private int secretLoot(GuiGraphics g, int cy, int mouseX, int mouseY, int clipTop, int clipHeight) {
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
        if (selectedTable == null || !sorted.contains(selectedTable)) {
            onSelectTable.accept(sorted.get(0));
        }
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
            if (hovered && Gui.advanced()) {
                hoveredText = List.of(Component.literal(StructureNames.lootTable(table)), Component.literal(table).withStyle(ChatFormatting.DARK_GRAY));
            }
            hotspots.add(new Hotspot(x, cy, contentRight - x, rowHeight, () -> onSelectTable.accept(table)));
            cy += rowHeight + 1;
        }
        return lootOdds(g, cy, mouseX, mouseY, clipTop, clipHeight, 1,
                Component.translatable("screen.justenoughstructures.container").getString());
    }

    /**
     * A short note at the right of a table's row when it's been edited: edited, edited with the
     * mod's table changed since, or an edit that isn't used. Returns the room it took, 0 if none.
     */
    private int editedMark(GuiGraphics g, String table, int right, int top) {
        LootOverrides.Status status = ClientRequests.showsPackTools() ? ClientRequests.overrideStatus(table) : null;
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
        LootOverrides.Status status = ClientRequests.showsPackTools() ? ClientRequests.overrideStatus(table) : null;
        if (status == null || status == LootOverrides.Status.NONE) {
            return lines;
        }
        List<Component> out = new ArrayList<>(lines);
        out.add(1, Component.translatable("screen.justenoughstructures.editor.status." + status.name().toLowerCase(Locale.ROOT))
                .withStyle(ChatFormatting.GRAY));
        return out;
    }

    private int lootOdds(GuiGraphics g, int cy, int mouseX, int mouseY, int clipTop, int clipHeight, int selectedCount, String selectedName) {
        if (selectedTable == null) {
            return cy;
        }
        cy += 4;
        Gui.band(g, font, Component.translatable("screen.justenoughstructures.odds").getString(), x, cy, contentRight - x, 13);
        cy += 16;
        cy = sortLink(g, cy, mouseX, mouseY, clipTop, clipHeight);
        if (odds == null) {
            return fineWrapped(g, Component.translatable("screen.justenoughstructures.rolling"), x + PAD, cy, textWidth(), Gui.LABEL_SOFT);
        }
        if (odds.rows().isEmpty()) {
            return fineWrapped(g, Component.translatable("screen.justenoughstructures.always_empty"), x + PAD, cy, textWidth(), Gui.LABEL_SOFT);
        }
        List<LootOdds.Row> rows = OddsList.sorted(odds, ClientState.rarestFirst);
        Map<LootOdds.Row, String> names = OddsList.names(rows);
        int detailRoom = OddsList.detailRoom(font, rows.stream().map(OddsList::counts).toList());
        for (LootOdds.Row row : rows) {
            float chance = (float) row.hits() / odds.rolls();
            boolean hovered = inside(mouseX, mouseY, x + 2, cy, contentRight - x - 2, OddsList.ROW, clipTop, clipHeight);
            OddsList.drawRow(g, font, row.example(), names.get(row), OddsList.counts(row), chance, x, cy, contentRight, hovered, detailRoom);
            if (hovered) {
                hoveredStack = row.example();
                hoveredExtra = OddsList.tooltip(row, chance, selectedCount, selectedName == null ? "" : selectedName);
            }
            ItemStack example = row.example();
            hotspots.add(new Hotspot(x + 2, cy, contentRight - x - 2, OddsList.ROW, () -> onItemClicked.accept(example)));
            if (cy >= clipTop && cy + OddsList.ROW <= clipTop + clipHeight) {
                oddsRows.put(example.getItem(), new int[]{x + PAD + 60, cy + OddsList.ROW / 2});
            }
            cy += OddsList.ROW + 1;
        }
        if (odds.emptyRolls() > 0) {
            cy = fineWrapped(g, Component.translatable("screen.justenoughstructures.empty_rolls",
                    String.format("%.1f%%", 100f * odds.emptyRolls() / odds.rolls())), x + PAD, cy + 2, textWidth(), Gui.LABEL_SOFT);
        }
        if (Gui.advanced()) {
            cy = fineWrapped(g, Component.translatable("screen.justenoughstructures.odds_note", String.format("%,d", odds.rolls())),
                    x + PAD, cy + 2, textWidth(), Gui.LABEL_SOFT);
        }
        return cy;
    }

    /** "3 stacks and 12", "1 stack and 5", or "2 stacks" for a number of blocks. */
    private static Component stacks(int count) {
        int full = count / 64;
        int rest = count % 64;
        if (rest == 0) {
            return full == 1 ? Component.translatable("screen.justenoughstructures.stack_one")
                    : Component.translatable("screen.justenoughstructures.stacks_only", full);
        }
        return full == 1 ? Component.translatable("screen.justenoughstructures.stack_one_and", rest)
                : Component.translatable("screen.justenoughstructures.stacks", full, rest);
    }

    /** A block's item, or a bucket of it for water and lava, which have none. */
    private static ItemStack blockIcon(Block block) {
        if (block.asItem() != Items.AIR) {
            return new ItemStack(block.asItem());
        }
        Item bucket = block.defaultBlockState().getFluidState().getType().getBucket();
        return new ItemStack(bucket != Items.AIR ? bucket : Items.BARRIER);
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
        int w = font.width(label) + 10;
        boolean over = inside(mouseX, mouseY, bx, cy, w, 14, clipTop, clipHeight);
        g.fill(bx, cy, bx + w, cy + 14, Gui.EDGE);
        g.fill(bx + 1, cy + 1, bx + w - 1, cy + 13, over ? 0xFF8D8D8D : 0xFF737373);
        g.drawString(font, label, bx + 5, cy + 3, over ? 0xFFFFFFA0 : 0xFFE0E0E0, true);
        hotspots.add(new Hotspot(bx, cy, w, 14, () -> notify(Exports.copyMaterialList(entry.id(), s))));
        cy += 17;
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
            ItemStack stack = blockIcon(block);
            Gui.slot(g, x + PAD - 1, cy);
            g.renderItem(stack, x + PAD, cy + 1);
            int textX = x + PAD + 21;
            Gui.fitted(g, font, block.getName().getString(), textX, cy + 1, contentRight - 2 - textX, TEXT);
            String number = String.format("%,d", count);
            String amount = count >= 64 ? Component.translatable("screen.justenoughstructures.block_amount", number, stacks(count)).getString() : number;
            // Just the number when the stacks don't fit beside it: the tooltip has them.
            if (secondaryWidth(amount) > contentRight - 2 - textX) {
                amount = number;
            }
            fineClipped(g, amount, textX, cy + 11, contentRight - 2 - textX, Gui.LABEL_SOFT);
            if (hovered) {
                List<Component> lines = new ArrayList<>();
                lines.add(block.getName());
                lines.add(stacks(count).copy().withStyle(ChatFormatting.GRAY));
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
        // Spawners by what they make: a mob, a list each got one of, or a mix they keep making. A
        // row shows its spawners in the preview while it's hovered, and opens them when clicked.
        Map<SpawnerKind, List<StructureSnapshot.Spawner>> byKind = new LinkedHashMap<>();
        Map<BlockPos, CompoundTag> spawnerTags = SpawnerKind.tags(s);
        for (StructureSnapshot.Spawner spawner : s.spawners()) {
            CompoundTag tag = spawnerTags.get(spawner.pos());
            if (tag != null && tag.getString("id").equals(SPAWNER)) {
                byKind.computeIfAbsent(SpawnerKind.of(tag), k -> new ArrayList<>()).add(spawner);
            }
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

        if (placed.isEmpty() && byKind.isEmpty() && overTime.isEmpty()) {
            return fineWrapped(g, Component.translatable("screen.justenoughstructures.no_entities"), x + PAD, cy, textWidth(), Gui.LABEL_SOFT);
        }
        cy = mobSection(g, cy, "placed", placed, true);
        // For Pack tools: what the spawners of a row made before a dev gave them this mob.
        Map<String, String> changedFrom = new HashMap<>();
        if (ClientRequests.showsPackTools()) {
            for (StructureSnapshot.Spawner spawner : s.spawners()) {
                if (spawner.source() != null && spawner.source().patchedFrom() != null) {
                    changedFrom.put(spawner.mob().isEmpty() ? "?" : spawner.mob(), spawner.source().patchedFrom());
                }
            }
        }
        if (!byKind.isEmpty()) {
            Gui.band(g, font, Component.translatable("screen.justenoughstructures.mobs_spawners").getString(), x, cy, contentRight - x, 13);
            cy += 16;
            for (Map.Entry<SpawnerKind, List<StructureSnapshot.Spawner>> e : byKind.entrySet()) {
                if (e.getKey().type() != SpawnerKind.Type.MOB) {
                    continue;
                }
                String mob = e.getKey().mob().isEmpty() ? "?" : e.getKey().mob();
                int top = cy;
                boolean over = inside(mouseX, mouseY, x, cy, contentRight - x, 22, clipTop, clipHeight);
                cy = mobRow(g, cy, mob, Component.translatable("screen.justenoughstructures.times", e.getValue().size()).getString(), over, true);
                String was = changedFrom.get(mob);
                if (was != null) {
                    cy = fineWrapped(g, Component.translatable("screen.justenoughstructures.container.changed_from", StructureNames.mob(was)),
                            x + PAD, cy + 1, textWidth(), ToolsUi.CHANGED) + 2;
                }
                spawnerRow(mouseX, mouseY, top, cy, clipTop, clipHeight, "spawners:" + mob, e.getValue());
            }
            cy = pools(g, cy, byKind, SpawnerKind.Type.POOL, "spawner_pool", mouseX, mouseY, clipTop, clipHeight);
            cy = pools(g, cy, byKind, SpawnerKind.Type.MIX, "spawner_mix", mouseX, mouseY, clipTop, clipHeight);
            cy += 4;
        }
        cy = mobSection(g, cy, "over_time", overTime, false);
        return cy;
    }

    /**
     * Each list of mobs spawners pick from, under a line saying how many spawners here use it, with
     * every mob's chance. The whole list is one row: it lights up and opens its spawners as one.
     */
    private int pools(GuiGraphics g, int cy, Map<SpawnerKind, List<StructureSnapshot.Spawner>> byKind, SpawnerKind.Type type, String key,
                      int mouseX, int mouseY, int clipTop, int clipHeight) {
        for (Map.Entry<SpawnerKind, List<StructureSnapshot.Spawner>> pool : byKind.entrySet()) {
            if (pool.getKey().type() != type) {
                continue;
            }
            int top = cy;
            int count = pool.getValue().size();
            Component line = count == 1 ? Component.translatable("screen.justenoughstructures." + key + "_one")
                    : Component.translatable("screen.justenoughstructures." + key + "_many", count);
            cy = fineWrapped(g, line, x + PAD, cy + 2, textWidth(), Gui.LABEL_SOFT) + 3;
            Map<String, Integer> weights = pool.getKey().mobs();
            int total = weights.values().stream().mapToInt(Integer::intValue).sum();
            List<Map.Entry<String, Integer>> mobs = new ArrayList<>(weights.entrySet());
            mobs.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
            boolean over = inside(mouseX, mouseY, x, top, contentRight - x, cy - top + mobs.size() * 23, clipTop, clipHeight);
            for (Map.Entry<String, Integer> mob : mobs) {
                cy = mobRow(g, cy, mob.getKey(), OddsList.percent((float) mob.getValue() / total), over, true);
            }
            spawnerRow(mouseX, mouseY, top, cy, clipTop, clipHeight, key + ":" + weights, pool.getValue());
        }
        return cy;
    }

    /**
     * A row of spawners from {@code top} to {@code bottom}: shows them in the preview while it's
     * hovered, and opens them when clicked.
     */
    private void spawnerRow(int mouseX, int mouseY, int top, int bottom, int clipTop, int clipHeight, String key, List<StructureSnapshot.Spawner> spawners) {
        highlightRows.put(key, new int[]{x + (contentRight - x) / 2, bottom - 11});
        if (inside(mouseX, mouseY, x, top, contentRight - x, bottom - top, clipTop, clipHeight)) {
            LongSet positions = new LongOpenHashSet();
            spawners.forEach(spawner -> positions.add(spawner.pos().asLong()));
            hoveredBlocks = new Hovered(key, s -> positions);
        }
        StructureSnapshot.Spawner first = spawners.get(0);
        hotspots.add(new Hotspot(x, top, contentRight - x, bottom - top, () -> onOpenSpawner.accept(first, spawners.size() > 1)));
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
        return cy + 4;
    }

    /** A mob's card: its icon, its name and, on the right, {@code right}. */
    private int mobRow(GuiGraphics g, int cy, String mob, String right) {
        return mobRow(g, cy, mob, right, false, false);
    }

    /**
     * {@code hovered} rows are lit, for the ones that show something in the preview, and rows that
     * open something when clicked, {@code opens}, end in an arrow, as the Loot tab's do.
     */
    private int mobRow(GuiGraphics g, int cy, String mob, String right, boolean hovered, boolean opens) {
        ResourceLocation id = ResourceLocation.tryParse(mob);
        EntityType<?> type = id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? BuiltInRegistries.ENTITY_TYPE.get(id) : null;
        // "?" is a spawner with no mob at all.
        boolean empty = mob.equals("?");
        Component name = type != null ? type.getDescription() : empty ? StructureNames.mob("") : Component.literal(mob);
        Gui.card(g, x, cy, contentRight - x, 22);
        if (hovered) {
            g.fill(x + 1, cy + 1, contentRight - 1, cy + 21, Gui.ROW_HOVER);
        }
        Gui.slot(g, x + PAD + 1, cy + 2);
        ItemStack icon = type != null ? entityIcon(type) : empty ? new ItemStack(Items.SPAWNER) : ItemStack.EMPTY;
        if (!icon.isEmpty()) {
            g.renderItem(icon, x + PAD + 2, cy + 3);
        }
        int rightEdge = contentRight - 4 - (opens ? 10 : 0);
        Gui.fitted(g, font, name.getString(), x + PAD + 23, cy + 7, rightEdge - x - PAD - 26 - font.width(right), TEXT);
        g.drawString(font, right, rightEdge - font.width(right), cy + 7, Gui.LABEL_SOFT, false);
        if (opens) {
            g.drawString(font, ">", contentRight - 9, cy + 7, hovered ? TEXT : Gui.LABEL_SOFT, false);
        }
        return cy + 23;
    }

    /** Items for entities that have no spawn egg, worked out once for each kind. */
    private static final Map<EntityType<?>, ItemStack> ENTITY_ITEMS = new HashMap<>();

    /**
     * What stands for an entity: its spawn egg, or for one without, like an armor stand, a minecart or
     * an item frame, the item picking it in creative gives, which mods set for their own entities too.
     * Failing that, an item with the entity's own id, or nothing.
     */
    static ItemStack entityIcon(EntityType<?> type) {
        SpawnEggItem egg = SpawnEggItem.byId(type);
        if (egg != null) {
            return new ItemStack(egg);
        }
        ItemStack known = ENTITY_ITEMS.get(type);
        if (known != null) {
            return known;
        }
        ClientLevel level = Minecraft.getInstance().level;
        ItemStack found = ItemStack.EMPTY;
        if (level != null) {
            try {
                // A bare one, never added to the world: an item frame made from the structure's own
                // data would give the item in it instead of the frame.
                Entity entity = type.create(level);
                ItemStack picked = entity == null ? null : entity.getPickResult();
                if (picked != null && !picked.isEmpty()) {
                    found = picked.copyWithCount(1);
                }
            } catch (RuntimeException | LinkageError e) {
                JesLog.debug("Couldn't make a {} to find its item", BuiltInRegistries.ENTITY_TYPE.getKey(type), e);
            }
        }
        if (found.isEmpty()) {
            Item named = BuiltInRegistries.ITEM.get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
            found = named == Items.AIR ? ItemStack.EMPTY : new ItemStack(named);
        }
        if (level != null) {
            ENTITY_ITEMS.put(type, found);
        }
        return found;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h, int clipTop, int clipHeight) {
        return mx >= x && mx < x + w && my >= y && my < y + h && my >= clipTop && my < clipTop + clipHeight;
    }
}
