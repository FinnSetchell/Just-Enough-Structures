package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.render.SnapshotView;
import com.finndog.justenoughstructures.client.render.StructureViewport;
import com.finndog.justenoughstructures.loot.LootOdds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.lwjgl.glfw.GLFW;

/** The structure browser: a list on the left, the 3D preview in the middle and details on the right. */
public class JesScreen extends Screen {
    private static final int PAD = 6;
    private static ResourceLocation lastSelected;

    private final StructureList list = new StructureList();
    private final StructureViewport viewport = new StructureViewport();
    private final Map<String, LootOdds> odds = new HashMap<>();
    private InfoPanel info;

    private EditBox search;
    private Button rerollButton;
    private Button spinButton;
    private Button markersButton;
    private LayerSlider slider;
    private Button chestReroll;
    private Button chestPrev;
    private Button chestNext;
    private Button chestClose;

    private List<StructureCatalog.Entry> catalog;
    private String catalogError;
    private StructureCatalog.Entry selected;
    private long seed;
    private CaptureResult result;
    private SnapshotView view;
    private ChestPopup popup;

    private boolean spin = true;
    private MarkerMode markers = MarkerMode.ALL;
    private long lastFrame = System.nanoTime();

    private int listX, listY, listW, listH;
    private int centreX, centreW;
    private int viewX, viewY, viewW, viewH;
    private int infoX, infoW;

    private boolean pressedInViewport;
    private boolean dragged;
    private final List<Marker> markerRects = new ArrayList<>();

    private record Marker(int x, int y, int size, StructureSnapshot.Container container) {
    }

    /** Which loot markers float over the preview. Chests only leaves out suspicious sand and gravel. */
    private enum MarkerMode {
        ALL("all"), CHESTS("chests"), OFF("off");

        final String key;

        MarkerMode(String key) {
            this.key = key;
        }

        MarkerMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public JesScreen() {
        super(Component.translatable("screen.justenoughstructures.title"));
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void init() {
        if (info == null) {
            info = new InfoPanel(font, this::selectTable, this::openContainer);
        }
        int leftW = clamp(width / 4, 120, 175);
        int rightW = clamp(width / 4, 140, 200);
        listX = PAD;
        listW = leftW;
        centreX = listX + leftW + PAD;
        infoW = rightW;
        infoX = width - PAD - infoW;
        centreW = infoX - PAD - centreX;

        String query = search == null ? "" : search.getValue();
        search = new EditBox(font, listX + 5, PAD + 5, listW - 10, 16, Component.translatable("screen.justenoughstructures.search"));
        search.setHint(Component.translatable("screen.justenoughstructures.search_hint").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(value -> list.setQuery(value));
        addRenderableWidget(search);
        listY = PAD + 25;
        listH = height - PAD - listY - 5;
        list.layout(listX + 5, listY, listW - 10, listH);

        viewX = centreX + 5;
        viewY = PAD + 28;
        int toolbarY = height - PAD - 25;
        viewW = centreW - 10;
        viewH = toolbarY - 3 - viewY;

        int bx = viewX;
        rerollButton = addRenderableWidget(Button.builder(Component.translatable("screen.justenoughstructures.reroll"), b -> reroll())
                .bounds(bx, toolbarY, 56, 20).tooltip(Tooltip.create(
                        Component.translatable("screen.justenoughstructures.reroll_tooltip"))).build());
        bx += 58;
        addRenderableWidget(Button.builder(Component.translatable("screen.justenoughstructures.reset"), b -> viewport.resetCamera())
                .bounds(bx, toolbarY, 40, 20).build());
        bx += 42;
        spinButton = addRenderableWidget(Button.builder(spinLabel(), b -> {
            spin = !spin;
            b.setMessage(spinLabel());
        }).bounds(bx, toolbarY, 40, 20).build());
        bx += 42;
        markersButton = addRenderableWidget(Button.builder(markersLabel(), b -> {
            markers = markers.next();
            b.setMessage(markersLabel());
        }).bounds(bx, toolbarY, 62, 20).build());
        bx += 64;
        slider = addRenderableWidget(new LayerSlider(bx, toolbarY, Math.max(60, viewX + viewW - bx), 20, shown -> {
            if (view != null) {
                view.setSliceY(shown);
            }
        }));
        updateSlider();

        info.layout(infoX + 5, PAD + 5, infoW - 10, height - PAD * 2 - 10);

        chestReroll = addRenderableWidget(Button.builder(Component.translatable("screen.justenoughstructures.reroll_loot"), b -> rerollLoot())
                .bounds(0, 0, 70, 20).build());
        chestPrev = addRenderableWidget(Button.builder(Component.literal("<"), b -> stepContainer(-1)).bounds(0, 0, 20, 20).build());
        chestNext = addRenderableWidget(Button.builder(Component.literal(">"), b -> stepContainer(1)).bounds(0, 0, 20, 20).build());
        chestClose = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> closePopup())
                .bounds(0, 0, 44, 20).build());
        layoutPopupButtons();

        if (catalog == null && catalogError == null) {
            if (!ClientRequests.serverSupported()) {
                catalogError = "screen.justenoughstructures.no_server";
            } else {
                ClientRequests.catalog().thenAccept(this::onCatalog);
            }
        }
    }

    private void onCatalog(List<StructureCatalog.Entry> entries) {
        catalog = entries;
        list.setEntries(entries);
        StructureCatalog.Entry start = entries.stream().filter(e -> e.id().equals(lastSelected)).findFirst()
                .orElse(entries.isEmpty() ? null : entries.get(0));
        if (start != null) {
            select(start, defaultSeed(start.id()));
        }
    }

    private static long defaultSeed(ResourceLocation id) {
        return id.toString().hashCode() * 0x9E3779B97F4A7C15L;
    }

    // ------------------------------------------------------------------ selection

    /** Selects a structure by id. Used by the screenshot harness as well as the list. */
    public boolean select(ResourceLocation id) {
        if (catalog == null) {
            return false;
        }
        for (StructureCatalog.Entry entry : catalog) {
            if (entry.id().equals(id)) {
                select(entry, defaultSeed(id));
                return true;
            }
        }
        return false;
    }

    private void select(StructureCatalog.Entry entry, long newSeed) {
        selected = entry;
        lastSelected = entry.id();
        seed = newSeed;
        list.setSelected(entry.id());
        list.revealSelected();
        info.setEntry(entry);
        closePopup();
        result = null;
        view = null;
        viewport.setView(null);
        updateSlider();
        ResourceLocation id = entry.id();
        long wanted = newSeed;
        ClientRequests.capture(id, newSeed).thenAccept(reply -> {
            if (selected != null && selected.id().equals(id) && seed == wanted && reply.id().equals(id)) {
                onCaptured(reply.result());
            }
        });
    }

    private void onCaptured(CaptureResult captured) {
        result = captured;
        info.setResult(captured);
        if (captured.succeeded() && minecraft != null && minecraft.level != null) {
            view = new SnapshotView(captured.snapshot());
            view.createRenderables(minecraft.level);
            viewport.setView(view);
        }
        updateSlider();
    }

    private void reroll() {
        if (selected != null) {
            select(selected, ThreadLocalRandom.current().nextLong());
        }
    }

    /** True once the current structure has been generated (or failed) and fully meshed. */
    public boolean idle() {
        return catalog != null && result != null && !viewport.meshing();
    }

    public CaptureResult result() {
        return result;
    }

    private void updateSlider() {
        if (slider == null) {
            return;
        }
        slider.active = view != null;
        if (view != null) {
            slider.setLayers(view.size().getY(), view.sliceY());
        } else {
            slider.setLayers(1, 1);
        }
        rerollButton.active = selected != null;
    }

    // ------------------------------------------------------------------ loot

    private void selectTable(String table) {
        info.setSelectedTable(table);
        if (table == null) {
            return;
        }
        LootOdds cached = odds.get(table);
        if (cached != null) {
            info.setOdds(cached);
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(table);
        if (id != null) {
            ClientRequests.odds(id).thenAccept(o -> {
                odds.put(table, o);
                info.setOdds(o);
            });
        }
    }

    /** Opens a container from the preview. Public so the screenshot harness can open one. */
    public void openContainer(StructureSnapshot.Container container) {
        if (result == null || !result.succeeded()) {
            return;
        }
        List<StructureSnapshot.Container> same = sameTable(container);
        popup = new ChestPopup(container, containerTitle(container), containerSize(container), same.indexOf(container), same.size());
        popup.place(width, height);
        layoutPopupButtons();
        selectTable(container.lootTable());
        if (container.lootTable() == null) {
            popup.items = prefilledItems(container);
            return;
        }
        popup.seed = container.lootSeed() != 0 ? container.lootSeed()
                : seed ^ (container.pos().asLong() * 0x9E3779B97F4A7C15L);
        rollPopup();
    }

    private void rollPopup() {
        ChestPopup current = popup;
        ResourceLocation table = ResourceLocation.tryParse(current.container.lootTable());
        if (table == null) {
            current.items = List.of();
            return;
        }
        current.items = null;
        ClientRequests.loot(table, current.seed, current.size).thenAccept(items -> current.items = items);
    }

    private void rerollLoot() {
        if (popup != null && popup.container.lootTable() != null) {
            popup.seed = ThreadLocalRandom.current().nextLong();
            rollPopup();
        }
    }

    private void stepContainer(int direction) {
        if (popup == null) {
            return;
        }
        List<StructureSnapshot.Container> same = sameTable(popup.container);
        if (same.size() > 1) {
            int next = Math.floorMod(same.indexOf(popup.container) + direction, same.size());
            openContainer(same.get(next));
        }
    }

    private List<StructureSnapshot.Container> sameTable(StructureSnapshot.Container container) {
        List<StructureSnapshot.Container> out = new ArrayList<>();
        for (StructureSnapshot.Container c : result.snapshot().containers()) {
            if (Objects.equals(c.lootTable(), container.lootTable())) {
                out.add(c);
            }
        }
        return out;
    }

    public void closeContainer() {
        closePopup();
    }

    public void showLootTab() {
        info.setTab(InfoPanel.Tab.LOOT);
    }

    public void showInfoTab() {
        info.setTab(InfoPanel.Tab.OVERVIEW);
    }

    private void closePopup() {
        popup = null;
        layoutPopupButtons();
    }

    private void layoutPopupButtons() {
        boolean show = popup != null;
        for (Button b : new Button[]{chestReroll, chestPrev, chestNext, chestClose}) {
            if (b != null) {
                b.visible = show;
            }
        }
        if (!show || chestReroll == null) {
            return;
        }
        int rowY = popup.buttonRowY();
        chestReroll.setX(popup.x + 6);
        chestReroll.setY(rowY);
        chestReroll.active = popup.container.lootTable() != null;
        chestPrev.setX(popup.x + 80);
        chestPrev.setY(rowY);
        chestNext.setX(popup.x + 102);
        chestNext.setY(rowY);
        chestPrev.active = chestNext.active = popup.count > 1;
        chestClose.setX(popup.x + ChestPopup.WIDTH - 50);
        chestClose.setY(rowY);
    }

    private Component containerTitle(StructureSnapshot.Container container) {
        if (container.entity()) {
            ResourceLocation id = ResourceLocation.tryParse(container.id());
            return id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)
                    ? BuiltInRegistries.ENTITY_TYPE.get(id).getDescription()
                    : Component.literal(container.id());
        }
        return view != null ? view.rawState(container.pos().getX(), container.pos().getY(), container.pos().getZ()).getBlock().getName()
                : Component.literal(container.id());
    }

    private int containerSize(StructureSnapshot.Container container) {
        if (view != null) {
            if (container.entity()) {
                for (Entity entity : view.entities()) {
                    if (entity instanceof Container c && entity.blockPosition().equals(container.pos())) {
                        return c.getContainerSize();
                    }
                }
            } else {
                BlockEntity be = view.blockEntities().get(container.pos());
                if (be instanceof Container c) {
                    return c.getContainerSize();
                }
                // Suspicious sand and gravel hold a single item.
                return 1;
            }
        }
        return 27;
    }

    private List<ItemStack> prefilledItems(StructureSnapshot.Container container) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < containerSize(container); i++) {
            items.add(ItemStack.EMPTY);
        }
        for (CompoundTag tag : result.snapshot().blockEntities()) {
            if (tag.getInt("x") == container.pos().getX() && tag.getInt("y") == container.pos().getY() && tag.getInt("z") == container.pos().getZ()) {
                ListTag list = tag.getList("Items", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag item = list.getCompound(i);
                    int slot = item.getByte("Slot") & 255;
                    if (slot < items.size()) {
                        items.set(slot, ItemStack.of(item));
                    }
                }
            }
        }
        return items;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float seconds = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        if (spin && view != null && !(pressedInViewport && dragged)) {
            viewport.spin(seconds * 12f);
        }

        renderBackground(g);
        Gui.panel(g, listX, PAD, listW, height - PAD * 2);
        Gui.panel(g, centreX, PAD, centreW, height - PAD * 2);
        Gui.panel(g, infoX, PAD, infoW, height - PAD * 2);

        boolean popupOpen = popup != null;
        int mx = popupOpen ? -1 : mouseX;
        int my = popupOpen ? -1 : mouseY;
        list.render(g, font, mx, my);
        renderHeader(g);
        StructureViewport.Hit hover = renderViewport(g, mx, my, partialTick);
        info.render(g, mx, my);

        for (Button b : new Button[]{chestReroll, chestPrev, chestNext, chestClose}) {
            b.visible = false;
        }
        super.render(g, mx, my, partialTick);

        ItemStack popupHover = ItemStack.EMPTY;
        if (popupOpen) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            g.fill(0, 0, width, height, 0x88000000);
            popupHover = popup.render(g, font, mouseX, mouseY);
            layoutPopupButtons();
            for (Button b : new Button[]{chestReroll, chestPrev, chestNext, chestClose}) {
                b.render(g, mouseX, mouseY, partialTick);
            }
            g.pose().popPose();
        }

        if (popupOpen) {
            if (!popupHover.isEmpty()) {
                g.renderTooltip(font, popupHover, mouseX, mouseY);
            }
        } else if (!info.hoveredStack().isEmpty()) {
            g.renderTooltip(font, info.hoveredStack(), mouseX, mouseY);
        } else if (!info.hoveredText().isEmpty()) {
            g.renderComponentTooltip(font, info.hoveredText(), mouseX, mouseY);
        } else if (hover != null) {
            g.renderComponentTooltip(font, hoverLines(hover), mouseX, mouseY);
        }
    }

    private void renderHeader(GuiGraphics g) {
        int x = centreX + 6;
        if (selected == null) {
            g.drawString(font, title, x, PAD + 6, 0xFF202020, false);
            return;
        }
        g.drawString(font, Gui.clip(font, StructureNames.structure(selected.id()), centreW - 12), x, PAD + 6, 0xFF202020, false);
        Gui.small(g, font, Gui.clip(font, selected.id() + "  " + StructureNames.mod(selected.id().getNamespace()), (int) ((centreW - 12) / 0.75f)),
                x, PAD + 17, Gui.LABEL_SOFT);
    }

    private StructureViewport.Hit renderViewport(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(viewX - 1, viewY - 1, viewX + viewW + 1, viewY + viewH + 1, Gui.SLOT_DARK);
        g.fillGradient(viewX, viewY, viewX + viewW, viewY + viewH, Gui.VIEW_TOP, Gui.VIEW_BOTTOM);
        markerRects.clear();

        if (catalogError != null) {
            centred(g, Component.translatable(catalogError));
            return null;
        }
        if (catalog == null) {
            centred(g, Component.translatable("screen.justenoughstructures.loading_list"));
            return null;
        }
        if (result == null) {
            centred(g, Component.translatable("screen.justenoughstructures.generating"));
            return null;
        }
        if (!result.succeeded()) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("screen.justenoughstructures.failed"));
            lines.add(Component.literal(result.error()).withStyle(ChatFormatting.GRAY));
            for (String attempt : result.attempts()) {
                lines.add(Component.literal(attempt).withStyle(ChatFormatting.DARK_GRAY));
            }
            int cy = viewY + 10;
            for (Component line : lines) {
                cy = Gui.wrapped(g, font, line, viewX + 10, cy, viewW - 20, 0xFFE0E0E0);
            }
            return null;
        }

        StructureViewport.Hit hover = viewport.pick(mouseX, mouseY).orElse(null);
        List<BlockPos> outlines = new ArrayList<>();
        if (hover != null && hover.entity() == null) {
            outlines.add(hover.pos());
        }
        if (popup != null && !popup.container.entity()) {
            outlines.add(popup.container.pos());
        }
        viewport.render(g, viewX, viewY, viewW, viewH, partialTick, outlines);

        StructureSnapshot s = result.snapshot();
        g.enableScissor(viewX, viewY, viewX + viewW, viewY + viewH);
        if (markers != MarkerMode.OFF) {
            for (StructureSnapshot.Container c : s.containers()) {
                if (c.lootTable() == null || c.pos().getY() >= view.sliceY()) {
                    continue;
                }
                if (markers == MarkerMode.CHESTS && !c.entity() && !(view.blockEntities().get(c.pos()) instanceof Container)) {
                    continue;
                }
                Optional<float[]> at = viewport.project(c.pos().getX() + 0.5, c.pos().getY() + 1.1, c.pos().getZ() + 0.5);
                if (at.isEmpty()) {
                    continue;
                }
                int size = 12;
                int mx = (int) at.get()[0] - size / 2;
                int my = (int) at.get()[1] - size;
                boolean over = mouseX >= mx && mouseX < mx + size && mouseY >= my && mouseY < my + size;
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(mx - 1, my - 1, mx + size + 1, my + size + 1, over ? 0xFFFFFF55 : 0xFF000000);
                g.fill(mx, my, mx + size, my + size, 0xFF2B2B2B);
                g.pose().translate(mx + 0.5f, my + 0.5f, 0);
                g.pose().scale(0.6875f, 0.6875f, 1f);
                g.renderItem(InfoPanel.containerIcon(s, c), 0, 0);
                g.pose().popPose();
                markerRects.add(new Marker(mx, my, size, c));
            }
        }

        String stats = Component.translatable("screen.justenoughstructures.stats", String.format("%,d", s.blockCount()),
                s.containers().stream().filter(c -> c.lootTable() != null).count()).getString();
        g.drawString(font, Gui.clip(font, stats, viewW - 12), viewX + 6, viewY + 6, 0xFFE8E8E8, true);
        Gui.small(g, font, Component.translatable("screen.justenoughstructures.seed", Long.toHexString(seed).toUpperCase(Locale.ROOT)).getString(),
                viewX + 6, viewY + 17, 0xFFB8B8B8);
        if (viewport.meshing()) {
            int barW = Math.min(120, viewW - 20);
            int bx = viewX + (viewW - barW) / 2;
            int by = viewY + viewH - 12;
            g.fill(bx - 1, by - 1, bx + barW + 1, by + 5, 0xFF000000);
            g.fill(bx, by, bx + (int) (barW * viewport.meshProgress()), by + 4, 0xFF7FD06A);
        } else {
            Gui.small(g, font, Gui.clip(font, Component.translatable("screen.justenoughstructures.controls").getString(), (int) ((viewW - 12) / 0.75f)),
                    viewX + 6, viewY + viewH - 10, 0xFFB0B0B0);
        }
        g.disableScissor();

        for (Marker m : markerRects) {
            if (mouseX >= m.x() && mouseX < m.x() + m.size() && mouseY >= m.y() && mouseY < m.y() + m.size()) {
                return new StructureViewport.Hit(m.container().pos(), view.rawState(m.container().pos().getX(), m.container().pos().getY(), m.container().pos().getZ()), null);
            }
        }
        return hover;
    }

    private void centred(GuiGraphics g, Component text) {
        int textH = Gui.wrappedHeight(font, text, viewW - 30);
        Gui.wrapped(g, font, text, viewX + 15, viewY + (viewH - textH) / 2, viewW - 30, 0xFFE0E0E0);
    }

    private List<Component> hoverLines(StructureViewport.Hit hit) {
        List<Component> lines = new ArrayList<>();
        StructureSnapshot.Container container = containerAt(hit);
        if (hit.entity() != null) {
            lines.add(hit.entity().getName());
        } else {
            BlockState state = hit.state();
            lines.add(state.getBlock().getName());
            for (Property<?> property : state.getProperties()) {
                lines.add(Component.literal(property.getName() + ": " + state.getValue(property)).withStyle(ChatFormatting.GRAY));
            }
        }
        if (container != null) {
            if (container.lootTable() != null) {
                lines.add(Component.translatable("screen.justenoughstructures.hover_loot", container.lootTable()).withStyle(ChatFormatting.AQUA));
            }
            lines.add(Component.translatable("screen.justenoughstructures.hover_open").withStyle(ChatFormatting.YELLOW));
        }
        ResourceLocation id = hit.entity() != null ? BuiltInRegistries.ENTITY_TYPE.getKey(hit.entity().getType())
                : BuiltInRegistries.BLOCK.getKey(hit.state().getBlock());
        lines.add(Component.literal(id + "  " + hit.pos().toShortString()).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.literal(StructureNames.mod(id.getNamespace())).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
        return lines;
    }

    private StructureSnapshot.Container containerAt(StructureViewport.Hit hit) {
        if (result == null || !result.succeeded()) {
            return null;
        }
        for (StructureSnapshot.Container c : result.snapshot().containers()) {
            if (c.pos().equals(hit.pos()) && c.entity() == (hit.entity() != null)) {
                return c;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (popup != null) {
            for (Button b : new Button[]{chestReroll, chestPrev, chestNext, chestClose}) {
                if (b.visible && b.isMouseOver(mouseX, mouseY)) {
                    return b.mouseClicked(mouseX, mouseY, button);
                }
            }
            if (!popup.contains(mouseX, mouseY)) {
                closePopup();
            }
            return true;
        }
        if (button == 0) {
            for (Marker m : markerRects) {
                if (mouseX >= m.x() && mouseX < m.x() + m.size() && mouseY >= m.y() && mouseY < m.y() + m.size()) {
                    openContainer(m.container());
                    return true;
                }
            }
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0) {
            Optional<StructureCatalog.Entry> clicked = list.click(mouseX, mouseY);
            if (clicked.isPresent()) {
                if (clicked.get() != selected) {
                    select(clicked.get(), defaultSeed(clicked.get().id()));
                }
                return true;
            }
            if (info.click(mouseX, mouseY)) {
                return true;
            }
        }
        if (view != null && mouseX >= viewX && mouseX < viewX + viewW && mouseY >= viewY && mouseY < viewY + viewH) {
            pressedInViewport = true;
            dragged = false;
            setFocused(null);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (pressedInViewport && popup == null) {
            dragged |= Math.abs(dx) + Math.abs(dy) > 0.5;
            if (button == 1 || button == 2 || hasShiftDown()) {
                viewport.pan(dx, dy);
            } else {
                viewport.rotate(dx, dy);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (pressedInViewport) {
            pressedInViewport = false;
            if (!dragged && button == 0) {
                viewport.pick(mouseX, mouseY).map(this::containerAt).ifPresent(this::openContainer);
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (popup != null) {
            return true;
        }
        if (view != null && viewport.contains(mouseX, mouseY)) {
            viewport.zoom(delta);
            return true;
        }
        return list.scroll(mouseX, mouseY, delta) || info.scroll(mouseX, mouseY, delta) || super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && popup != null) {
            closePopup();
            return true;
        }
        if (search.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER) {
                list.firstShown().ifPresent(e -> select(e, defaultSeed(e.id())));
                return true;
            }
            return super.keyPressed(key, scanCode, modifiers);
        }
        if (key == GLFW.GLFW_KEY_R && popup == null) {
            reroll();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        // The integrated server has to keep running to send previews back.
        return false;
    }

    @Override
    public void removed() {
        viewport.close();
        super.removed();
    }

    private Component spinLabel() {
        return Component.translatable(spin ? "screen.justenoughstructures.spin_on" : "screen.justenoughstructures.spin_off");
    }

    private Component markersLabel() {
        return Component.translatable("screen.justenoughstructures.markers_" + markers.key);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
