package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.client.Exports;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

    private static final int TAB_HEIGHT = 16;
    private static final int PAD = 5;
    private static final int TEXT = 0xFF202020;
    private static final int RARE = 0xFF8A5A00;
    private static final int GOOD = 0xFF2E5B1D;
    private static boolean showDetails;
    private static boolean rarestFirst;

    private final Font font;
    private final Consumer<String> onSelectTable;
    private final Consumer<StructureSnapshot.Container> onOpenContainer;
    private final Consumer<ItemStack> onItemClicked;

    private Tab tab = Tab.OVERVIEW;
    private int x, y, width, height;
    private double scroll;
    private int contentHeight;
    private int contentRight;

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

    private record Hotspot(int x, int y, int w, int h, Runnable action) {
    }

    InfoPanel(Font font, Consumer<String> onSelectTable, Consumer<StructureSnapshot.Container> onOpenContainer,
              Consumer<ItemStack> onItemClicked) {
        this.font = font;
        this.onSelectTable = onSelectTable;
        this.onOpenContainer = onOpenContainer;
        this.onItemClicked = onItemClicked;
    }

    void layout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
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

    void setTab(Tab tab) {
        this.tab = tab;
        this.scroll = 0;
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

    int[] tabCentre(Tab t) {
        int tabWidth = width / Tab.values().length;
        return new int[]{x + t.ordinal() * tabWidth + tabWidth / 2, y + TAB_HEIGHT / 2};
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

        int tabWidth = width / Tab.values().length;
        // Words on every tab if they all fit, otherwise icons on every tab, never a mix.
        boolean words = true;
        for (Tab t : Tab.values()) {
            words &= font.width(t.label()) <= tabWidth - 6;
        }
        for (Tab t : Tab.values()) {
            int tx = x + t.ordinal() * tabWidth;
            int tw = t.ordinal() == Tab.values().length - 1 ? width - tabWidth * (Tab.values().length - 1) : tabWidth;
            boolean active = t == tab;
            g.fill(tx, y, tx + tw, y + TAB_HEIGHT, Gui.EDGE);
            g.fill(tx + 1, y + 1, tx + tw - 1, y + TAB_HEIGHT - (active ? 0 : 1), active ? Gui.PANEL : 0xFF9C9C9C);
            String label = t.label().getString();
            if (words) {
                g.drawString(font, label, tx + (tw - font.width(label)) / 2, y + 4, active ? TEXT : 0xFF404040, false);
            } else {
                // Too narrow for the word: show an icon and put the word in a tooltip.
                g.pose().pushPose();
                g.pose().translate(tx + tw / 2f - 6, y + 2, 0);
                g.pose().scale(0.75f, 0.75f, 1f);
                g.renderItem(t.icon, 0, 0);
                g.pose().popPose();
                if (mouseX >= tx && mouseX < tx + tw && mouseY >= y && mouseY < y + TAB_HEIGHT) {
                    hoveredText = List.of(t.label());
                }
            }
        }

        int top = y + TAB_HEIGHT + 1;
        int bodyHeight = height - TAB_HEIGHT - 1;
        boolean scrolls = contentHeight > bodyHeight;
        contentRight = x + width - (scrolls ? 6 : 2);
        Gui.inset(g, x, top, width, bodyHeight, 0xFFBDBDBD);
        g.enableScissor(x + 1, top + 1, x + width - 1, top + bodyHeight - 1);
        int cursor = top + PAD - (int) scroll;
        int end = switch (tab) {
            case OVERVIEW -> overview(g, cursor, mouseX, mouseY, top, bodyHeight);
            case LOOT -> loot(g, cursor, mouseX, mouseY, top, bodyHeight);
            case BLOCKS -> blocks(g, cursor, mouseX, mouseY, top, bodyHeight);
            case ENTITIES -> mobs(g, cursor);
        };
        g.disableScissor();
        contentHeight = end - cursor + PAD;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - bodyHeight)));
        if (contentHeight > bodyHeight) {
            Gui.scrollbar(g, x + width - 5, top + 2, bodyHeight - 4, scroll, contentHeight - bodyHeight);
        }
    }

    boolean click(double mouseX, double mouseY) {
        if (mouseY >= y && mouseY < y + TAB_HEIGHT && mouseX >= x && mouseX < x + width) {
            int index = (int) ((mouseX - x) / (width / Tab.values().length));
            setTab(Tab.values()[Math.min(Tab.values().length - 1, Math.max(0, index))]);
            return true;
        }
        if (mouseX < x || mouseX >= x + width || mouseY < y + TAB_HEIGHT + 1 || mouseY >= y + height) {
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
        return true;
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
        String rarity = rarity();
        if (rarity != null) {
            cy = field(g, cy, "rarity", rarity);
        }
        if (result != null && result.succeeded()) {
            StructureSnapshot s = result.snapshot();
            cy = field(g, cy, "size", Component.translatable("screen.justenoughstructures.size_blocks",
                    s.size().getX(), s.size().getY(), s.size().getZ()).getString());
            cy = field(g, cy, "loot_here", lootSummary(s));
        }

        // Everything a datapack author wants and a player doesn't, folded away by default.
        cy += 2;
        Component toggle = Component.translatable(showDetails ? "screen.justenoughstructures.details_hide" : "screen.justenoughstructures.details_show");
        boolean over = inside(mouseX, mouseY, x + 2, cy - 1, width - 4, font.lineHeight + 3, clipTop, clipHeight);
        g.drawString(font, toggle, x + PAD, cy, over ? 0xFF1F3F8F : 0xFF3A55A0, false);
        hotspots.add(new Hotspot(x + 2, cy - 1, width - 4, font.lineHeight + 3, () -> showDetails = !showDetails));
        cy += font.lineHeight + 5;
        if (!showDetails) {
            return cy;
        }
        cy = field(g, cy, "id", entry.id().toString());
        cy = field(g, cy, "type", entry.type() == null ? "?" : entry.type().toString());
        if (def != null) {
            cy = field(g, cy, "step", string(def.get("step")));
            cy = field(g, cy, "biome_tag", string(def.get("biomes")));
            if (def.has("start_pool")) {
                cy = field(g, cy, "start_pool", string(def.get("start_pool")));
            }
            if (def.has("size")) {
                cy = field(g, cy, "jigsaw_size", string(def.get("size")));
            }
            cy = field(g, cy, "terrain", def.has("terrain_adaptation") ? string(def.get("terrain_adaptation")) : "none");
        }
        for (StructureCatalog.SetInfo set : entry.sets()) {
            JsonObject p = set.placement();
            StringBuilder placement = new StringBuilder(set.setId().toString());
            if (p != null) {
                placement.append("\n").append(string(p.get("type")));
                for (String key : List.of("spacing", "separation", "salt", "frequency", "distance", "count")) {
                    if (p.has(key)) {
                        placement.append("\n").append(key).append(": ").append(string(p.get(key)));
                    }
                }
            }
            cy = field(g, cy, "placement", placement.toString());
        }
        if (result != null && result.succeeded()) {
            StructureSnapshot s = result.snapshot();
            cy = field(g, cy, "pieces", String.valueOf(s.pieceCount()));
            cy = field(g, cy, "generated_on", Component.translatable("screen.justenoughstructures.terrain." + s.terrain().name().toLowerCase(Locale.ROOT)).getString());
            cy = field(g, cy, "seed", Long.toHexString(s.seed()).toUpperCase(Locale.ROOT));
            cy = field(g, cy, "time", result.millis() + " ms");
        }
        return cy;
    }

    /** "At most one in each 544 x 544 block area", from the structure set's placement. */
    private String rarity() {
        for (StructureCatalog.SetInfo set : entry.sets()) {
            JsonObject p = set.placement();
            if (p == null) {
                continue;
            }
            String type = string(p.get("type"));
            if (p.has("spacing") && p.get("spacing").isJsonPrimitive()) {
                String blocks = String.format("%,d", p.get("spacing").getAsInt() * 16);
                String out = Component.translatable("screen.justenoughstructures.rarity_spread", blocks, blocks).getString();
                if (p.has("frequency") && p.get("frequency").isJsonPrimitive() && p.get("frequency").getAsFloat() < 1f) {
                    out += " " + Component.translatable("screen.justenoughstructures.rarity_frequency",
                            Math.round(p.get("frequency").getAsFloat() * 100)).getString();
                }
                return out;
            }
            if (type.endsWith("concentric_rings")) {
                return Component.translatable("screen.justenoughstructures.rarity_rings", p.has("count") ? string(p.get("count")) : "?").getString();
            }
        }
        return null;
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
        kinds.forEach((name, count) -> parts.add(count + " x " + name));
        return String.join(", ", parts);
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
        Gui.small(g, font, Component.translatable("screen.justenoughstructures.field." + key).getString(), x + PAD, cy, Gui.LABEL_SOFT);
        cy += 8;
        for (String line : value.split("\n")) {
            cy = Gui.wrapped(g, font, Component.literal(breakable(line)), x + PAD, cy, textWidth(), TEXT);
        }
        return cy + 4;
    }

    /** Lets long ids wrap after underscores and slashes rather than mid-word. */
    private static String breakable(String text) {
        return text.contains(" ") ? text : text.replace("_", "_​").replace("/", "/​").replace(":", ":​");
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
        if (result == null || !result.succeeded()) {
            return Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.loot_waiting"), x + PAD, cy, textWidth(), Gui.LABEL_SOFT);
        }
        StructureSnapshot snapshot = result.snapshot();
        Map<String, List<StructureSnapshot.Container>> groups = new LinkedHashMap<>();
        for (StructureSnapshot.Container c : snapshot.containers()) {
            groups.computeIfAbsent(c.lootTable() == null ? "" : c.lootTable(), k -> new ArrayList<>()).add(c);
        }
        if (groups.isEmpty()) {
            return Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.no_loot"), x + PAD, cy, textWidth(), Gui.LABEL_SOFT);
        }
        List<Map.Entry<String, List<StructureSnapshot.Container>>> sorted = new ArrayList<>(groups.entrySet());
        sorted.sort(Comparator.comparing((Map.Entry<String, List<StructureSnapshot.Container>> e) -> e.getKey().isEmpty())
                .thenComparing(e -> -e.getValue().size()));

        int selectedCount = 1;
        String selectedName = null;
        for (Map.Entry<String, List<StructureSnapshot.Container>> group : sorted) {
            String table = group.getKey();
            List<StructureSnapshot.Container> containers = group.getValue();
            int rowHeight = 22;
            boolean selected = table.equals(selectedTable);
            boolean hovered = inside(mouseX, mouseY, x + 2, cy, contentRight - x - 2, rowHeight, clipTop, clipHeight);
            g.fill(x + 2, cy, contentRight, cy + rowHeight, selected ? Gui.ROW_SELECTED : hovered ? Gui.ROW_HOVER : 0xFFB3B3B3);
            ItemStack icon = containerIcon(snapshot, containers.get(0));
            Gui.slot(g, x + PAD - 1, cy + 2);
            g.renderItem(icon, x + PAD, cy + 3);
            String name = icon.getHoverName().getString() + " x" + containers.size();
            String detail = table.isEmpty() ? Component.translatable("screen.justenoughstructures.prefilled").getString() : StructureNames.lootTable(table);
            int textWidth = contentRight - x - PAD - 21 - 10;
            Gui.fitted(g, font, name, x + PAD + 21, cy + 3, textWidth, TEXT);
            Gui.small(g, font, Gui.clip(font, detail, (int) (textWidth / 0.75f)), x + PAD + 21, cy + 13, Gui.LABEL_SOFT);
            g.drawString(font, ">", contentRight - 8, cy + 7, hovered ? TEXT : Gui.LABEL_SOFT, false);
            if (hovered) {
                hoveredText = List.of(Component.literal(name), Component.translatable("screen.justenoughstructures.group_hint").withStyle(ChatFormatting.YELLOW));
            }
            if (selected) {
                selectedCount = containers.size();
                selectedName = icon.getHoverName().getString().toLowerCase(Locale.ROOT);
            }
            StructureSnapshot.Container first = containers.get(0);
            hotspots.add(new Hotspot(x + 2, cy, contentRight - x - 2, rowHeight, () -> {
                onSelectTable.accept(table.isEmpty() ? null : table);
                onOpenContainer.accept(first);
            }));
            cy += rowHeight + 1;
        }

        if (selectedTable == null) {
            return Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.pick_group"), x + PAD, cy + 4, textWidth(), Gui.LABEL_SOFT);
        }
        cy += 5;
        g.drawString(font, Component.translatable("screen.justenoughstructures.odds", selectedName == null ? "" : selectedName), x + PAD, cy, TEXT, false);
        cy += font.lineHeight + 2;
        Component sortLabel = Component.translatable(rarestFirst ? "screen.justenoughstructures.sort_rare" : "screen.justenoughstructures.sort_common");
        int sortWidth = (int) (font.width(sortLabel) * 0.75f) + 2;
        boolean overSort = inside(mouseX, mouseY, x + PAD, cy - 1, sortWidth, 9, clipTop, clipHeight);
        Gui.small(g, font, sortLabel.getString(), x + PAD, cy, overSort ? 0xFF1F3F8F : 0xFF3A55A0);
        if (overSort) {
            hoveredText = List.of(Component.translatable(rarestFirst ? "screen.justenoughstructures.sort_to_common" : "screen.justenoughstructures.sort_to_rare"));
        }
        hotspots.add(new Hotspot(x + PAD, cy - 1, sortWidth, 9, () -> rarestFirst = !rarestFirst));
        cy += 10;
        if (odds == null) {
            return Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.rolling"), x + PAD, cy, textWidth(), Gui.LABEL_SOFT);
        }
        if (odds.rows().isEmpty()) {
            return Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.always_empty"), x + PAD, cy, textWidth(), Gui.LABEL_SOFT);
        }
        List<LootOdds.Row> rows = new ArrayList<>(odds.rows());
        if (rarestFirst) {
            rows.sort(Comparator.comparingInt(LootOdds.Row::hits));
        }
        Map<String, Integer> nameCounts = new HashMap<>();
        rows.forEach(r -> nameCounts.merge(r.example().getHoverName().getString(), 1, Integer::sum));
        for (LootOdds.Row row : rows) {
            float chance = (float) row.hits() / odds.rolls();
            boolean rare = chance < 0.1f;
            int rowTop = cy;
            boolean hovered = inside(mouseX, mouseY, x + 2, cy, contentRight - x - 2, 19, clipTop, clipHeight);
            if (hovered) {
                g.fill(x + 2, cy, contentRight, cy + 19, Gui.ROW_HOVER);
            }
            Gui.slot(g, x + PAD - 1, cy);
            g.renderItem(row.example(), x + PAD, cy + 1);
            int textX = x + PAD + 21;
            String pct = chance >= 0.1f ? Math.round(chance * 100) + "%" : String.format("%.1f%%", chance * 100);
            int nameWidth = contentRight - textX - font.width(pct) - 6;
            String name = row.example().getHoverName().getString();
            if (nameCounts.getOrDefault(name, 0) > 1) {
                name = StructureNames.pretty(BuiltInRegistries.ITEM.getKey(row.example().getItem()).getPath());
            }
            Gui.fitted(g, font, name, textX, cy + 1, nameWidth, rare ? RARE : TEXT);
            g.drawString(font, pct, contentRight - 2 - font.width(pct), cy + 1, rare ? RARE : TEXT, false);
            float average = (float) row.total() / row.hits();
            String counts = row.min() == row.max()
                    ? Component.translatable("screen.justenoughstructures.count_exact", row.min()).getString()
                    : Component.translatable("screen.justenoughstructures.count_range", row.min(), row.max(), Math.round(average)).getString();
            Gui.small(g, font, counts, textX, cy + 11, Gui.LABEL_SOFT);
            int barLeft = textX + (int) (font.width(counts) * 0.75f) + 4;
            int barRight = contentRight - 2;
            if (barRight - barLeft > 10) {
                g.fill(barLeft, cy + 12, barRight, cy + 16, Gui.BAR_BACK);
                g.fill(barLeft, cy + 12, barLeft + Math.max(1, (int) ((barRight - barLeft) * chance)), cy + 16, rare ? 0xFFC08A20 : Gui.BAR);
            }
            if (hovered) {
                hoveredStack = row.example();
                hoveredExtra = oddsTooltip(row, chance, selectedCount, selectedName == null ? "" : selectedName);
            }
            ItemStack example = row.example();
            hotspots.add(new Hotspot(x + 2, cy, contentRight - x - 2, 19, () -> onItemClicked.accept(example)));
            if (rowTop >= clipTop && rowTop + 19 <= clipTop + clipHeight) {
                oddsRows.put(example.getItem(), new int[]{x + PAD + 60, rowTop + 9});
            }
            cy += 20;
        }
        if (odds.emptyRolls() > 0) {
            cy = Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.empty_rolls",
                    String.format("%.1f%%", 100f * odds.emptyRolls() / odds.rolls())), x + PAD, cy + 2, textWidth(), Gui.LABEL_SOFT);
        }
        cy = Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.odds_note", String.format("%,d", odds.rolls())),
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
            return Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.loot_waiting"), x + PAD, cy, textWidth(), Gui.LABEL_SOFT);
        }
        StructureSnapshot s = result.snapshot();
        List<Map.Entry<Block, Integer>> sorted = Exports.blockCounts(s);
        cy = Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.block_types", sorted.size(), String.format("%,d", s.blockCount())),
                x + PAD, cy, textWidth(), TEXT) + 3;

        int bx = x + PAD;
        for (String action : List.of("copy", "save")) {
            Component label = Component.translatable("screen.justenoughstructures.blocks_" + action);
            int w = font.width(label) + 8;
            if (bx > x + PAD && bx + w > contentRight - 2) {
                // No room beside the first button, so this one goes underneath.
                bx = x + PAD;
                cy += 16;
            }
            boolean over = inside(mouseX, mouseY, bx, cy, w, 13, clipTop, clipHeight);
            g.fill(bx, cy, bx + w, cy + 13, Gui.EDGE);
            g.fill(bx + 1, cy + 1, bx + w - 1, cy + 12, over ? 0xFF8D8D8D : 0xFF737373);
            g.drawString(font, label, bx + 4, cy + 3, over ? 0xFFFFFFA0 : 0xFFE0E0E0, true);
            hotspots.add(new Hotspot(bx, cy, w, 13, action.equals("copy")
                    ? () -> notify(Exports.copyMaterialList(entry.id(), s))
                    : () -> notify(Exports.saveStructure(entry.id(), s))));
            bx += w + 4;
        }
        cy += 16;
        if (notice != null && System.currentTimeMillis() < noticeUntil) {
            cy = Gui.wrapped(g, font, notice, x + PAD, cy, textWidth(), GOOD) + 2;
        }

        for (Map.Entry<Block, Integer> e : sorted) {
            Block block = e.getKey();
            int count = e.getValue();
            boolean hovered = inside(mouseX, mouseY, x + 2, cy, contentRight - x - 2, 18, clipTop, clipHeight);
            if (hovered) {
                g.fill(x + 2, cy, contentRight, cy + 18, Gui.ROW_HOVER);
            }
            ItemStack stack = new ItemStack(block.asItem());
            if (stack.isEmpty()) {
                stack = new ItemStack(Items.BARRIER);
            }
            g.renderItem(stack, x + PAD, cy + 1);
            String amount = String.format("%,d", count);
            Gui.fitted(g, font, block.getName().getString(), x + PAD + 20, cy + 5, contentRight - x - PAD - 26 - font.width(amount), TEXT);
            g.drawString(font, amount, contentRight - 2 - font.width(amount), cy + 5, Gui.LABEL_SOFT, false);
            if (hovered) {
                List<Component> lines = new ArrayList<>();
                lines.add(block.getName());
                lines.add(Component.translatable("screen.justenoughstructures.stacks", count / 64, count % 64).withStyle(ChatFormatting.GRAY));
                if (Minecraft.getInstance().options.advancedItemTooltips) {
                    lines.add(Component.literal(BuiltInRegistries.BLOCK.getKey(block).toString()).withStyle(ChatFormatting.DARK_GRAY));
                }
                hoveredText = lines;
            }
            cy += 18;
        }
        return cy;
    }

    // ------------------------------------------------------------------ mobs

    private int mobs(GuiGraphics g, int cy) {
        if (result == null || !result.succeeded()) {
            return Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.loot_waiting"), x + PAD, cy, textWidth(), Gui.LABEL_SOFT);
        }
        StructureSnapshot s = result.snapshot();
        Map<String, Integer> placed = new LinkedHashMap<>();
        for (CompoundTag tag : s.entities()) {
            placed.merge(tag.getString("id"), 1, Integer::sum);
        }
        Map<String, Integer> spawners = new LinkedHashMap<>();
        for (CompoundTag tag : s.blockEntities()) {
            if (tag.getString("id").equals("minecraft:spawner")) {
                String mob = tag.getCompound("SpawnData").getCompound("entity").getString("id");
                spawners.merge(mob.isEmpty() ? "?" : mob, 1, Integer::sum);
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

        if (placed.isEmpty() && spawners.isEmpty() && overTime.isEmpty()) {
            return Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.no_entities"), x + PAD, cy, textWidth(), Gui.LABEL_SOFT);
        }
        cy = mobSection(g, cy, "placed", placed, true);
        cy = mobSection(g, cy, "spawners", spawners, true);
        cy = mobSection(g, cy, "over_time", overTime, false);
        return cy;
    }

    private int mobSection(GuiGraphics g, int cy, String key, Map<String, Integer> mobs, boolean counts) {
        if (mobs.isEmpty()) {
            return cy;
        }
        g.drawString(font, Component.translatable("screen.justenoughstructures.mobs_" + key), x + PAD, cy, TEXT, false);
        cy += font.lineHeight + 2;
        for (Map.Entry<String, Integer> e : mobs.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(e.getKey());
            EntityType<?> type = id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? BuiltInRegistries.ENTITY_TYPE.get(id) : null;
            Component name = type != null ? type.getDescription() : Component.literal(e.getKey());
            g.fill(x + 2, cy, contentRight, cy + 18, 0xFFB3B3B3);
            SpawnEggItem egg = type == null ? null : SpawnEggItem.byId(type);
            if (egg != null) {
                g.renderItem(new ItemStack(egg), x + PAD, cy + 1);
            }
            String count = counts ? "x" + e.getValue() : "";
            Gui.fitted(g, font, name.getString(), x + PAD + 20, cy + 5, contentRight - x - PAD - 26 - font.width(count), TEXT);
            g.drawString(font, count, contentRight - 2 - font.width(count), cy + 5, Gui.LABEL_SOFT, false);
            cy += 19;
        }
        Component note = Component.translatable("screen.justenoughstructures.mobs_" + key + "_note");
        return Gui.wrapped(g, font, note, x + PAD, cy + 1, textWidth(), Gui.LABEL_SOFT) + 6;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h, int clipTop, int clipHeight) {
        return mx >= x && mx < x + w && my >= y && my < y + h && my >= clipTop && my < clipTop + clipHeight;
    }
}
