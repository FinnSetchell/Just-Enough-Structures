package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.ClientState;
import com.finndog.justenoughstructures.client.CompassLink;
import com.finndog.justenoughstructures.client.Thumbnails;
import com.finndog.justenoughstructures.client.render.SnapshotView;
import com.finndog.justenoughstructures.client.render.StructureViewport;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.mojang.blaze3d.pipeline.TextureTarget;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
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
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.lwjgl.glfw.GLFW;

/** The structure browser: a list on the left, the 3D preview in the middle and details on the right. */
public class JesScreen extends Screen {
    private static final int PAD = 6;
    /** How far the details panel sits down to make room for its tabs, as JEI's recipe panel does. */
    private static final int TABS = 21;
    private static final ResourceLocation RESET_ICON = JustEnoughStructures.id("textures/gui/reset_view.png");
    private static final ItemStack SPIN_ICON = new ItemStack(Items.CLOCK);
    private static final ItemStack MARKERS_ICON = new ItemStack(Items.CHEST);
    private static final ItemStack GROUND_ICON = new ItemStack(Items.GRASS_BLOCK);
    private static final ResourceLocation MAXIMISE_ICON = JustEnoughStructures.id("textures/gui/maximise.png");
    private static final ResourceLocation RESTORE_ICON = JustEnoughStructures.id("textures/gui/restore.png");
    /** How long a locate that found nothing stays in the header. */
    private static final long LOCATE_FAILURE_MILLIS = 8000;
    // One frame of the recovery compass. The item itself spins forever when there's no death point.
    private static final ResourceLocation LOCATE_ICON = new ResourceLocation("textures/item/recovery_compass_20.png");
    private static ResourceLocation lastSelected;

    private final Screen parent;
    private final StructureList list = new StructureList();
    private final StructureViewport viewport = new StructureViewport();
    private final ThumbnailQueue thumbnails = new ThumbnailQueue();
    private InfoPanel info;

    private EditBox search;
    private Button rerollButton;
    private IconButton spinButton;
    private IconButton markersButton;
    private IconButton groundButton;
    private IconButton maximiseButton;
    private IconButton locateButton;
    private IconButton compassButton;
    private Boolean compassPointing;
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
    private FoundInPopup foundIn;
    private String pendingTable;

    private Component locateText;
    private boolean locateFound;
    private boolean locating;
    private int locateAccess;
    private boolean markersSecret;
    private long locateUntil = Long.MAX_VALUE;
    private int searchY;
    private int lastViewW;
    private int lastViewH;
    private boolean sides;
    private long lastFrame = System.nanoTime();

    private int listX, listY, listW, listH;
    private int centreX, centreW;
    private int viewX, viewY, viewW, viewH;
    private int infoX, infoW;

    private boolean pressedInViewport;
    private boolean dragged;
    private final List<Marker> markerRects = new ArrayList<>();

    /**
     * A marker on screen, at an exact (not pixel-rounded) position so it keeps up with the preview
     * as it turns. Containers close together share one marker, so {@code containers} can hold several.
     */
    private record Marker(float x, float y, int size, List<StructureSnapshot.Container> containers) {
        StructureSnapshot.Container container() {
            return containers.get(0);
        }
    }

    public JesScreen() {
        this(null);
    }

    /** @param parent where closing the browser goes back to, or null for the game */
    public JesScreen(Screen parent) {
        super(Component.translatable("screen.justenoughstructures.title"));
        this.parent = parent;
        ClientState.load();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void init() {
        if (info == null) {
            info = new InfoPanel(font, this::selectTable, this::openContainer, this::openFoundIn);
        }
        // Side panels need room; below that, or when maximised, the preview takes the whole width.
        sides = !ClientState.maximised && width >= 330;
        int leftW = sides ? clamp(width / 4, 118, 175) : 0;
        int rightW = sides ? clamp(width / 4, 138, 200) : 0;
        listX = PAD;
        listW = leftW;
        centreX = sides ? listX + leftW + PAD : PAD;
        infoW = rightW;
        infoX = width - PAD - infoW;
        centreW = sides ? infoX - PAD - centreX : width - PAD * 2;

        String query = search == null ? "" : search.getValue();
        // At the bottom of the list, as JEI has it: a black box, the text drawn without a border.
        searchY = height - PAD - 6 - 20;
        search = new EditBox(font, listX + 10, searchY + 6, Math.max(10, listW - 24), 12, Component.translatable("screen.justenoughstructures.search"));
        search.setBordered(false);
        search.setHint(Component.translatable(listW >= 150 ? "screen.justenoughstructures.search_hint" : "screen.justenoughstructures.search_hint_short")
                .withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(value -> {
            list.setQuery(value);
            updateSearchHelp();
        });
        updateSearchHelp();
        search.visible = sides;
        addRenderableWidget(search);
        listY = PAD + 6;
        listH = searchY - 4 - listY;
        list.layout(listX + 6, listY, listW - 12, listH);

        // JEI's two title rows: previous/next structure around its name, previous/next mod around the mod's.
        addRenderableWidget(new ArrowButton(centreX + 6, PAD + 4, true,
                Component.translatable("screen.justenoughstructures.previous_structure"), b -> step(-1, false)));
        addRenderableWidget(new ArrowButton(centreX + centreW - 19, PAD + 4, false,
                Component.translatable("screen.justenoughstructures.next_structure"), b -> step(1, false)));
        addRenderableWidget(new ArrowButton(centreX + 6, PAD + 19, true,
                Component.translatable("screen.justenoughstructures.previous_mod"), b -> step(-1, true)));
        addRenderableWidget(new ArrowButton(centreX + centreW - 19, PAD + 19, false,
                Component.translatable("screen.justenoughstructures.next_mod"), b -> step(1, true)));

        viewX = centreX + 6;
        viewY = PAD + 35;
        int toolbarY = height - PAD - 26;
        viewW = centreW - 12;
        viewH = toolbarY - 4 - viewY;
        if (viewW != lastViewW || viewH != lastViewH) {
            // A bigger or smaller preview (maximised, or the window resized) gets zoomed to fit again.
            viewport.refit();
            lastViewW = viewW;
            lastViewH = viewH;
        }

        // Over the preview's top right corner, like a 3D viewer's controls.
        int overlayRight = viewX + viewW - 2;
        maximiseButton = addRenderableWidget(new IconButton(overlayRight - 20, viewY + 2, ClientState.maximised ? RESTORE_ICON : MAXIMISE_ICON,
                Component.translatable(ClientState.maximised ? "screen.justenoughstructures.restore" : "screen.justenoughstructures.maximise"), b -> {
            ClientState.maximised = !ClientState.maximised;
            ClientState.save();
            rebuildWidgets();
        }));
        locateButton = addRenderableWidget(new IconButton(overlayRight - 42, viewY + 2, LOCATE_ICON, Component.empty(),
                b -> locate(hasControlDown() && ClientRequests.canTeleport())));
        locateAccess = -1;
        updateLocateButton();
        // With a structure compass mod installed, hands the structure over to the compass in your hand.
        CompassLink compass = CompassLink.get();
        compassButton = compass == null ? null : addRenderableWidget(new IconButton(overlayRight - 64, viewY + 2, compass.icon(),
                Component.empty(), b -> {
            if (hasControlDown() && ClientRequests.canPointCompass()) {
                pointCompass();
            } else {
                openInCompass();
            }
        }));
        compassPointing = null;
        updateCompassButton();

        int bx = viewX;
        boolean roomy = viewW >= 250;
        rerollButton = addRenderableWidget(Button.builder(Component.translatable(roomy ? "screen.justenoughstructures.reroll" : "screen.justenoughstructures.reroll_short"), b -> reroll())
                .bounds(bx, toolbarY, roomy ? 66 : 34, 20).tooltip(Tooltip.create(
                        Component.translatable("screen.justenoughstructures.reroll_tooltip"))).build());
        bx += roomy ? 68 : 36;
        addRenderableWidget(new IconButton(bx, toolbarY, RESET_ICON,
                Component.translatable("screen.justenoughstructures.reset"), b -> viewport.resetCamera()));
        bx += 22;
        spinButton = addRenderableWidget(new IconButton(bx, toolbarY, () -> SPIN_ICON, () -> ClientState.spin, spinLabel(), b -> {
            ClientState.spin = !ClientState.spin;
            ClientState.save();
            spinButton.setLabel(spinLabel());
        }));
        bx += 22;
        markersButton = addRenderableWidget(new IconButton(bx, toolbarY, () -> MARKERS_ICON, () -> ClientState.markers && !lootSecret(), markersLabel(), b -> {
            ClientState.markers = !ClientState.markers;
            ClientState.save();
            markersButton.setLabel(markersLabel());
        }));
        markersSecret = lootSecret();
        markersButton.active = !markersSecret;
        bx += 22;
        groundButton = addRenderableWidget(new IconButton(bx, toolbarY, () -> GROUND_ICON, () -> ClientState.ground, groundLabel(), b -> {
            ClientState.ground = !ClientState.ground;
            ClientState.save();
            groundButton.setLabel(groundLabel());
            updateGround();
        }));
        bx += 24;
        slider = addRenderableWidget(new LayerSlider(bx, toolbarY, Math.max(30, viewX + viewW - bx), 20, shown -> {
            if (view != null) {
                view.setSliceY(shown);
            }
        }));
        slider.setTooltip(Tooltip.create(Component.translatable("screen.justenoughstructures.layers_tooltip")));
        updateSlider();

        // The details panel starts lower, so its tabs can sit on top of it the way JEI's do.
        info.layout(infoX + 6, PAD + TABS + 6, infoW - 12, height - PAD * 2 - TABS - 12, infoX + 2, PAD);

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
                // Start the loot index early so item search is usually ready by the time it's wanted.
                // The index can take minutes, so the callback finds whichever browser is open then
                // instead of holding on to this one after it's closed.
                ClientRequests.index().thenAccept(built -> {
                    if (Minecraft.getInstance().screen instanceof JesScreen open) {
                        open.list.refresh();
                    }
                });
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        updateLocateButton();
        updateMarkersButton();
        updateCompassButton();
    }

    /**
     * Greys out locate for players the server doesn't let use it, and only mentions Ctrl-click to
     * those who can teleport. Checked every tick, as the server's settings arrive after the screen
     * opens and a player can be opped while it's open.
     */
    private void updateLocateButton() {
        int access = !ClientRequests.canLocate() ? 0 : ClientRequests.canTeleport() ? 2 : 1;
        if (access != locateAccess) {
            locateAccess = access;
            Component label = Component.translatable("screen.justenoughstructures.locate");
            String extra = access == 0 ? "locate_no_permission" : access == 2 ? "locate_teleport_hint" : null;
            if (extra != null) {
                label = label.copy().append("\n").append(Component.translatable("screen.justenoughstructures." + extra)
                        .withStyle(ChatFormatting.GRAY));
            }
            locateButton.setLabel(label);
        }
        locateButton.active = access > 0 && !locating;
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
        return StructureCapture.defaultSeed(id);
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
        if (selected != entry) {
            locateText = null;
            locateUntil = Long.MAX_VALUE;
        }
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
        if (pendingTable != null) {
            selectTable(pendingTable);
            pendingTable = null;
        }
        if (captured.succeeded() && minecraft != null && minecraft.level != null) {
            view = new SnapshotView(captured.snapshot());
            view.createRenderables(minecraft.level);
            viewport.setView(view);
            updateGround();
        }
        updateSlider();
    }

    /** Where the flat ground the structure was generated on sits, as a local height, if it cuts through it. */
    private void updateGround() {
        if (view == null) {
            return;
        }
        StructureSnapshot s = view.snapshot();
        int surface = switch (s.terrain()) {
            case LAND -> 64;
            case OCEAN -> 43;
            case NETHER -> 41;
            case END -> 65;
            case VOID -> Integer.MIN_VALUE;
        };
        int local = surface - s.origin().getY();
        viewport.setGround(ClientState.ground && surface != Integer.MIN_VALUE && local >= 0 && local <= s.size().getY() ? local : -1);
    }

    /** The search help only while the box is empty, so it never covers what a search found. */
    private void updateSearchHelp() {
        search.setTooltip(search.getValue().isEmpty() ? Tooltip.create(Component.translatable("screen.justenoughstructures.search_help")) : null);
    }

    /** Finds the nearest one, and with {@code teleport} (Ctrl-click) goes there too. */
    private void locate(boolean teleport) {
        if (selected == null || locating) {
            return;
        }
        ResourceLocation id = selected.id();
        locateText = Component.translatable("screen.justenoughstructures.locating");
        locateFound = false;
        locateUntil = Long.MAX_VALUE;
        // One search at a time: they can take a while, and each click would otherwise start another.
        locating = true;
        locateButton.active = false;
        ClientRequests.locate(id, teleport).thenAccept(reply -> {
            locating = false;
            updateLocateButton();
            if (reply.getContents() instanceof TranslatableContents t && t.getKey().endsWith("locate_teleported")) {
                // They've gone there, so get out of the way and say where they are.
                minecraft.gui.setOverlayMessage(reply, false);
                minecraft.setScreen(null);
                return;
            }
            if (selected != null && selected.id().equals(id)) {
                locateText = reply;
                // Where it is stays up; why it couldn't be found goes away after a while.
                locateFound = reply.getContents() instanceof TranslatableContents t && t.getKey().endsWith("locate_found");
                locateUntil = locateFound ? Long.MAX_VALUE : Util.getMillis() + LOCATE_FAILURE_MILLIS;
            }
        });
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
        ResourceLocation id = ResourceLocation.tryParse(table);
        if (id != null) {
            ClientRequests.odds(id).thenAccept(info::setOdds);
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

    /** Shows the structures whose loot can give this item. */
    public void openFoundIn(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        closePopup();
        foundIn = new FoundInPopup(stack);
        foundIn.place(width, height);
        ClientRequests.index();
    }

    private void pickFromFoundIn(FoundInPopup.Row row) {
        foundIn = null;
        if (catalog == null) {
            return;
        }
        for (StructureCatalog.Entry entry : catalog) {
            if (entry.id().equals(row.structure())) {
                select(entry, defaultSeed(entry.id()));
                info.setTab(InfoPanel.Tab.LOOT);
                pendingTable = row.tables().iterator().next().toString();
                return;
            }
        }
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

    /** Ctrl-clicking the locate button, which a scripted click can't do: it reads the real keyboard. */
    public void locateAndTeleport() {
        locate(true);
    }

    /** Ctrl-clicking the compass button, which a scripted click can't do either. */
    public void pointCompassNow() {
        pointCompass();
    }

    /** The Loot tab with {@code table} picked, as clicking its row would. */
    public void showLoot(String table) {
        info.setTab(InfoPanel.Tab.LOOT);
        selectTable(table);
    }

    public void showDetails(boolean shown) {
        info.setTab(InfoPanel.Tab.OVERVIEW);
        InfoPanel.showDetails(shown);
    }

    // ------------------------------------------------------------------ positions, for the dev harness

    /** Makes the next opened browser start on this structure. */
    public static void startOn(ResourceLocation id) {
        lastSelected = id;
    }

    public void setSpin(boolean on) {
        ClientState.spin = on;
        if (spinButton != null) {
            spinButton.setLabel(spinLabel());
        }
    }

    public Optional<int[]> structureRow(ResourceLocation id) {
        return list.rowCentre(id);
    }

    public int[] viewportCentre() {
        return new int[]{viewX + viewW / 2, viewY + viewH / 2};
    }

    public int[] searchBox() {
        return new int[]{search.getX() + search.getWidth() / 2, search.getY() + search.getHeight() / 2};
    }

    /** A point on the layer slider, {@code fraction} of the way along it. */
    public int[] sliderAt(float fraction) {
        return new int[]{slider.getX() + 4 + Math.round((slider.getWidth() - 8) * fraction), slider.getY() + slider.getHeight() / 2};
    }

    public int[] button(String name) {
        Button b = switch (name) {
            case "reroll" -> rerollButton;
            case "spin" -> spinButton;
            case "markers" -> markersButton;
            case "ground" -> groundButton;
            case "maximise" -> maximiseButton;
            case "locate" -> locateButton;
            case "compass" -> compassButton;
            case "reroll_loot" -> chestReroll;
            case "next" -> chestNext;
            case "done" -> chestClose;
            default -> throw new IllegalArgumentException(name);
        };
        return new int[]{b.getX() + b.getWidth() / 2, b.getY() + b.getHeight() / 2};
    }

    public int[] tab(String name) {
        return info.tabCentre(InfoPanel.Tab.valueOf(name.toUpperCase(Locale.ROOT)));
    }

    /** Where the marker for the first container using {@code table} is drawn, if it's on screen. */
    public Optional<int[]> marker(String table) {
        for (Marker m : markerRects) {
            if (table.equals(m.container().lootTable())) {
                return Optional.of(new int[]{Math.round(m.x() + m.size() / 2f), Math.round(m.y() + m.size() / 2f)});
            }
        }
        return Optional.empty();
    }

    public Optional<int[]> oddsRow(Item item) {
        return Optional.ofNullable(info.oddsRow(item));
    }

    public Optional<int[]> chestSlotWithItem() {
        if (popup == null || popup.items == null) {
            return Optional.empty();
        }
        for (int i = 0; i < popup.items.size(); i++) {
            if (!popup.items.get(i).isEmpty()) {
                return Optional.of(new int[]{popup.x + 8 + (i % 9) * 18 + 8, popup.y + 18 + (i / 9) * 18 + 8});
            }
        }
        return Optional.empty();
    }

    public boolean containerOpen() {
        return popup != null && popup.items != null;
    }

    public boolean foundInOpen() {
        return foundIn != null;
    }

    public int sliceLayers() {
        return view == null ? 0 : view.size().getY();
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
        if (ClientState.spin && view != null && !(pressedInViewport && dragged)) {
            viewport.spin(seconds * 12f);
        }

        renderBackground(g);
        if (sides) {
            Gui.panel(g, listX, PAD, listW, height - PAD * 2);
            Gui.searchBox(g, listX + 6, searchY, listW - 12, 20);
            Gui.panel(g, infoX, PAD + TABS, infoW, height - PAD * 2 - TABS);
        }
        Gui.panel(g, centreX, PAD, centreW, height - PAD * 2);

        boolean popupOpen = popup != null;
        boolean anyPopup = popupOpen || foundIn != null;
        int mx = anyPopup ? -1 : mouseX;
        int my = anyPopup ? -1 : mouseY;
        if (sides) {
            list.render(g, font, mx, my);
            List<ResourceLocation> wanted = new ArrayList<>(list.visible());
            if (foundIn != null) {
                wanted.addAll(0, foundIn.visibleStructures());
            }
            thumbnails.tick(wanted, result == null || viewport.meshing());
        }
        renderHeader(g);
        StructureViewport.Hit hover = renderViewport(g, mx, my, partialTick);
        if (sides) {
            info.render(g, mx, my);
        }

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
        popupHovered = popupHover;
        if (foundIn != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            g.fill(0, 0, width, height, 0x88000000);
            foundIn.render(g, font, mouseX, mouseY);
            g.pose().popPose();
        }

        if (foundIn != null) {
            return;
        }
        // Above the popups, whose items are drawn a long way towards the viewer.
        g.pose().pushPose();
        g.pose().translate(0, 0, 600);
        renderTooltips(g, mouseX, mouseY, popupOpen, popupHover, hover);
        g.pose().popPose();
    }

    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY, boolean popupOpen, ItemStack popupHover, StructureViewport.Hit hover) {
        if (popupOpen) {
            if (!popupHover.isEmpty()) {
                g.renderComponentTooltip(font, itemTooltip(popupHover, List.of()), mouseX, mouseY);
            }
        } else if (overHeaderLine(mouseX, mouseY)) {
            g.renderTooltip(font, font.split(headerOverflow, 240), mouseX, mouseY);
        } else if (!headerTooltip(mouseX, mouseY).isEmpty()) {
            g.renderComponentTooltip(font, headerTooltip(mouseX, mouseY), mouseX, mouseY);
        } else if (sides && !info.hoveredStack().isEmpty()) {
            g.renderComponentTooltip(font, itemTooltip(info.hoveredStack(), info.hoveredExtra()), mouseX, mouseY);
        } else if (sides && !info.hoveredText().isEmpty()) {
            g.renderComponentTooltip(font, info.hoveredText(), mouseX, mouseY);
        } else if (sides && !list.tooltip().isEmpty()) {
            g.renderComponentTooltip(font, list.tooltip(), mouseX, mouseY);
        } else if (hover != null) {
            g.renderComponentTooltip(font, hoverLines(hover), mouseX, mouseY);
        }
    }

    private ItemStack popupHovered = ItemStack.EMPTY;

    /** The normal item tooltip with a line saying how to find where else it turns up. */
    private List<Component> itemTooltip(ItemStack stack, List<Component> extra) {
        List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, stack));
        lines.addAll(extra);
        lines.add(Component.translatable("screen.justenoughstructures.found_in_hint").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    /** JEI's title rows: the structure's name, then its mod's, each in white on a dark band. */
    private void renderHeader(GuiGraphics g) {
        int bandX = centreX + 19;
        int bandW = centreW - 38;
        String name = selected == null ? title.getString() : StructureNames.structure(selected.id());
        String mod = selected == null ? "" : StructureNames.mod(selected.id().getNamespace());
        Gui.band(g, font, name, bandX, PAD + 4, bandW, 13);
        Gui.band(g, font, mod, bandX, PAD + 19, bandW, 13);
    }

    /** The full name when a title row had to cut it short, for its tooltip. */
    private List<Component> headerTooltip(int mouseX, int mouseY) {
        if (selected == null || mouseX < centreX + 19 || mouseX >= centreX + centreW - 19) {
            return List.of();
        }
        String name = StructureNames.structure(selected.id());
        String mod = StructureNames.mod(selected.id().getNamespace());
        if (mouseY >= PAD + 4 && mouseY < PAD + 17 && font.width(name) > centreW - 42) {
            return List.of(Component.literal(name));
        }
        if (mouseY >= PAD + 19 && mouseY < PAD + 32 && font.width(mod) > centreW - 42) {
            return List.of(Component.literal(mod));
        }
        return List.of();
    }

    /** Previous or next structure in the list, or the first of the previous or next mod. */
    private void step(int dir, boolean byMod) {
        if (selected != null) {
            list.step(selected.id(), dir, byMod).ifPresent(e -> select(e, defaultSeed(e.id())));
        }
    }

    private Component headerOverflow;
    private int headerRoom;

    private boolean overHeaderLine(int mouseX, int mouseY) {
        return headerOverflow != null && mouseX >= viewX + 6 && mouseX < viewX + 6 + headerRoom && mouseY >= viewY + 16 && mouseY < viewY + 26;
    }

    private StructureViewport.Hit renderViewport(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Gui.inset(g, viewX - 1, viewY - 1, viewW + 2, viewH + 2, Gui.SLOT_DARK);
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

        // No block tooltip while the preview is being dragged around under the cursor.
        StructureViewport.Hit hover = pressedInViewport && dragged ? null : viewport.pick(mouseX, mouseY).orElse(null);
        List<BlockPos> outlines = new ArrayList<>();
        if (hover != null && hover.entity() == null) {
            outlines.add(hover.pos());
        }
        if (popup != null && !popup.container.entity()) {
            outlines.add(popup.container.pos());
        }
        viewport.render(g, viewX, viewY, viewW, viewH, partialTick, outlines);
        if (!viewport.meshing() && !Thumbnails.has(selected.id())) {
            TextureTarget thumbnail = viewport.renderThumbnail(64);
            if (thumbnail != null) {
                Thumbnails.put(selected.id(), thumbnail);
            }
        }

        StructureSnapshot s = result.snapshot();
        g.enableScissor(viewX, viewY, viewX + viewW, viewY + viewH);
        if (ClientState.markers) {
            placeMarkers(s);
            for (Marker m : markerRects) {
                boolean over = mouseX >= m.x() && mouseX < m.x() + m.size() && mouseY >= m.y() && mouseY < m.y() + m.size();
                int size = m.size();
                g.pose().pushPose();
                // Moved by the exact amount rather than to the nearest GUI pixel, which is what made
                // markers jitter against the smoothly turning preview.
                g.pose().translate(m.x(), m.y(), 200);
                g.fill(-1, -1, size + 1, size + 1, over ? 0xFFFFFF55 : 0xFF000000);
                g.fill(0, 0, size, size, 0xFF2B2B2B);
                g.pose().pushPose();
                g.pose().translate(0.5f, 0.5f, 0);
                float scale = (size - 1) / 16f;
                g.pose().scale(scale, scale, 1f);
                g.renderItem(InfoPanel.containerIcon(s, m.container()), 0, 0);
                g.pose().popPose();
                if (m.containers().size() > 1) {
                    String count = String.valueOf(m.containers().size());
                    g.pose().translate(0, 0, 200);
                    Gui.small(g, font, count, size - (int) (font.width(count) * 0.75f) + 1, size - 5, 0xFFFFFFFF);
                }
                g.pose().popPose();
            }
        }

        String stats = lootSecret()
                ? Component.translatable("screen.justenoughstructures.stats_blocks", String.format("%,d", s.blockCount())).getString()
                : Component.translatable("screen.justenoughstructures.stats", String.format("%,d", s.blockCount()),
                        s.containers().stream().filter(c -> c.lootTable() != null).count()).getString();
        int room = viewW - 12 - 46;
        g.drawString(font, Gui.clip(font, stats, room), viewX + 6, viewY + 6, 0xFFE8E8E8, true);
        // What locate found, or why it couldn't, under the stats. Failures fade after a while.
        if (locateText != null && Util.getMillis() > locateUntil) {
            locateText = null;
        }
        headerOverflow = null;
        if (locateText != null) {
            int colour = locateFound ? 0xFF9CE89C : locateUntil == Long.MAX_VALUE ? 0xFFE0E0E0 : 0xFFFF9C9C;
            g.drawString(font, Gui.clip(font, locateText.getString(), room), viewX + 6, viewY + 17, colour, true);
            headerOverflow = font.width(locateText) > room ? locateText : null;
            headerRoom = room;
        }
        if (viewport.meshing()) {
            int barW = Math.min(120, viewW - 20);
            int bx = viewX + (viewW - barW) / 2;
            int by = viewY + viewH - 12;
            String building = Component.translatable("screen.justenoughstructures.building").getString();
            Gui.small(g, font, building, viewX + (viewW - (int) (font.width(building) * 0.75f)) / 2, by - 9, 0xFFE0E0E0);
            g.fill(bx - 1, by - 1, bx + barW + 1, by + 5, 0xFF000000);
            g.fill(bx, by, bx + (int) (barW * viewport.meshProgress()), by + 4, 0xFF7FD06A);
        } else {
            String controls = lootSecret() ? "screen.justenoughstructures.controls_no_loot" : "screen.justenoughstructures.controls";
            String hint = Component.translatable(controls).getString();
            if (font.width(hint) * 0.75f > viewW - 12) {
                hint = Component.translatable(controls + "_short").getString();
            }
            Gui.small(g, font, Gui.clip(font, hint, (int) ((viewW - 12) / 0.75f)), viewX + 6, viewY + viewH - 10, 0xFFE0E0E0);
        }
        g.disableScissor();

        for (Marker m : markerRects) {
            if (mouseX >= m.x() && mouseX < m.x() + m.size() && mouseY >= m.y() && mouseY < m.y() + m.size()) {
                BlockPos pos = m.container().pos();
                return new StructureViewport.Hit(pos, view.rawState(pos.getX(), pos.getY(), pos.getZ()), null);
            }
        }
        return hover;
    }

    /**
     * Works out where each loot marker goes. Markers are all one size, set by the zoom, and
     * containers close enough in the structure for their markers to overlap share one marker with a
     * count. Neither changes while the preview turns, only when you zoom, so markers don't grow,
     * shrink or split apart as it spins. The nearest are drawn last, on top.
     */
    private void placeMarkers(StructureSnapshot s) {
        float perBlock = Math.max(0.01f, viewport.pixelsPerBlock());
        int chestSize = Math.round(Math.max(8f, Math.min(16f, perBlock * 0.8f)));
        int smallSize = Math.round(Math.max(6f, Math.min(10f, perBlock * 0.8f)));
        double reach = chestSize * 1.5 / perBlock;
        List<List<StructureSnapshot.Container>> groups = markerGroups(s, reach);

        record Placed(Marker marker, float depth) {
        }
        List<Placed> placed = new ArrayList<>();
        for (List<StructureSnapshot.Container> members : groups) {
            double x = 0;
            double z = 0;
            int top = Integer.MIN_VALUE;
            boolean chest = false;
            for (StructureSnapshot.Container c : members) {
                x += c.pos().getX() + 0.5;
                z += c.pos().getZ() + 0.5;
                top = Math.max(top, c.pos().getY());
                chest |= c.entity() || view.blockEntities().get(c.pos()) instanceof Container;
            }
            Optional<float[]> at = viewport.project(x / members.size(), top + 1.1, z / members.size());
            if (at.isEmpty()) {
                continue;
            }
            int size = chest ? chestSize : smallSize;
            placed.add(new Placed(new Marker(at.get()[0] - size / 2f, at.get()[1] - size, size, members), at.get()[2]));
        }
        placed.sort(Comparator.comparingDouble(pl -> -pl.depth()));
        for (Placed pl : placed) {
            markerRects.add(pl.marker());
        }
    }

    private StructureSnapshot groupedFor;
    private int groupedSlice;
    private double groupedReach;
    private List<List<StructureSnapshot.Container>> grouped = List.of();

    /**
     * Containers close enough in the structure to share a marker. Only zooming or the layer slider
     * changes the answer, so it's worked out again only then, not every frame.
     */
    private List<List<StructureSnapshot.Container>> markerGroups(StructureSnapshot s, double reach) {
        if (s == groupedFor && view.sliceY() == groupedSlice && reach == groupedReach) {
            return grouped;
        }
        List<StructureSnapshot.Container> shown = new ArrayList<>();
        for (StructureSnapshot.Container c : s.containers()) {
            if (c.lootTable() != null && c.pos().getY() < view.sliceY()) {
                shown.add(c);
            }
        }
        // Sorted so a group always has the same container first, whichever way it's facing.
        shown.sort(Comparator.comparing((StructureSnapshot.Container c) -> c.pos()));

        // Join containers closer than about a marker and a half, and anything joined to those, so
        // markers that would touch or overlap from some angle share one.
        int[] group = new int[shown.size()];
        for (int i = 0; i < group.length; i++) {
            group[i] = i;
        }
        for (int i = 0; i < shown.size(); i++) {
            for (int j = i + 1; j < shown.size(); j++) {
                if (shown.get(i).pos().distSqr(shown.get(j).pos()) < reach * reach) {
                    int a = root(group, i);
                    int b = root(group, j);
                    group[Math.max(a, b)] = Math.min(a, b);
                }
            }
        }
        Map<Integer, List<StructureSnapshot.Container>> groups = new LinkedHashMap<>();
        for (int i = 0; i < shown.size(); i++) {
            groups.computeIfAbsent(root(group, i), k -> new ArrayList<>()).add(shown.get(i));
        }
        grouped = List.copyOf(groups.values());
        groupedFor = s;
        groupedSlice = view.sliceY();
        groupedReach = reach;
        return grouped;
    }

    private static int root(int[] group, int i) {
        while (group[i] != i) {
            group[i] = group[group[i]];
            i = group[i];
        }
        return i;
    }

    private void centred(GuiGraphics g, Component text) {
        List<FormattedCharSequence> lines = font.split(text, viewW - 30);
        int y = viewY + (viewH - lines.size() * (font.lineHeight + 1)) / 2;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, viewX + (viewW - font.width(line)) / 2, y, 0xFFE0E0E0, false);
            y += font.lineHeight + 1;
        }
    }

    /** Short by default; holding Shift adds block states, ids and positions for people who want them. */
    private List<Component> hoverLines(StructureViewport.Hit hit) {
        List<Component> lines = new ArrayList<>();
        StructureSnapshot.Container container = containerAt(hit);
        boolean details = hasShiftDown();
        if (hit.entity() != null) {
            lines.add(hit.entity().getName());
        } else {
            BlockState state = hit.state();
            lines.add(state.getBlock().getName());
            if (details) {
                for (Property<?> property : state.getProperties()) {
                    lines.add(Component.literal(property.getName() + ": " + state.getValue(property)).withStyle(ChatFormatting.GRAY));
                }
            }
        }
        int grouped = markerAt(hit.pos());
        if (container != null) {
            if (container.lootTable() != null) {
                lines.add(Component.translatable("screen.justenoughstructures.hover_loot", StructureNames.lootTable(container.lootTable())).withStyle(ChatFormatting.AQUA));
            }
            if (grouped > 1) {
                lines.add(Component.translatable("screen.justenoughstructures.hover_grouped", grouped).withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable("screen.justenoughstructures.hover_open").withStyle(ChatFormatting.YELLOW));
        }
        ResourceLocation id = hit.entity() != null ? BuiltInRegistries.ENTITY_TYPE.getKey(hit.entity().getType())
                : BuiltInRegistries.BLOCK.getKey(hit.state().getBlock());
        if (details) {
            if (container != null && container.lootTable() != null) {
                lines.add(Component.literal(container.lootTable()).withStyle(ChatFormatting.DARK_GRAY));
            }
            lines.add(Component.literal(id + "  " + hit.pos().toShortString()).withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.literal(StructureNames.mod(id.getNamespace())).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
        return lines;
    }

    /** How many containers the marker covering {@code pos} stands for, or 0. */
    private int markerAt(BlockPos pos) {
        for (Marker m : markerRects) {
            for (StructureSnapshot.Container c : m.containers()) {
                if (c.pos().equals(pos)) {
                    return m.containers().size();
                }
            }
        }
        return 0;
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
        if (foundIn != null) {
            if (foundIn.contains(mouseX, mouseY)) {
                foundIn.click(mouseX, mouseY).ifPresent(this::pickFromFoundIn);
            } else {
                foundIn = null;
            }
            return true;
        }
        if (popup != null) {
            for (Button b : new Button[]{chestReroll, chestPrev, chestNext, chestClose}) {
                if (b.visible && b.isMouseOver(mouseX, mouseY)) {
                    return b.mouseClicked(mouseX, mouseY, button);
                }
            }
            if (!popup.contains(mouseX, mouseY)) {
                closePopup();
            } else if (!popupHovered.isEmpty()) {
                openFoundIn(popupHovered);
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
            Optional<StructureList.Pick> clicked = sides ? list.click(mouseX, mouseY) : Optional.empty();
            if (clicked.isPresent()) {
                StructureList.Pick pick = clicked.get();
                if (pick.entry() != null && pick.entry() != selected) {
                    select(pick.entry(), defaultSeed(pick.entry().id()));
                } else if (!pick.item().isEmpty()) {
                    openFoundIn(pick.item());
                }
                return true;
            }
            if (sides && info.click(mouseX, mouseY)) {
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
        if (foundIn != null) {
            foundIn.scroll(delta);
            return true;
        }
        if (popup != null) {
            return true;
        }
        if (view != null && viewport.contains(mouseX, mouseY)) {
            viewport.zoom(delta);
            return true;
        }
        return (sides && (list.scroll(mouseX, mouseY, delta) || info.scroll(mouseX, mouseY, delta))) || super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && foundIn != null) {
            foundIn = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && popup != null) {
            closePopup();
            return true;
        }
        if (key == GLFW.GLFW_KEY_U && !search.isFocused()) {
            ItemStack hovered = popup != null ? popupHovered : info.hoveredStack();
            if (!hovered.isEmpty()) {
                openFoundIn(hovered);
                return true;
            }
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
        thumbnails.close();
        // A big preview can be hundreds of megabytes; don't keep it around once the screen's gone.
        view = null;
        result = null;
        groupedFor = null;
        grouped = List.of();
        super.removed();
    }

    private Component spinLabel() {
        return Component.translatable(ClientState.spin ? "screen.justenoughstructures.spin_on" : "screen.justenoughstructures.spin_off");
    }

    private Component groundLabel() {
        return Component.translatable(ClientState.ground ? "screen.justenoughstructures.ground_on" : "screen.justenoughstructures.ground_off");
    }

    private Component markersLabel() {
        if (lootSecret()) {
            return Component.translatable("screen.justenoughstructures.markers_secret");
        }
        return Component.translatable(ClientState.markers ? "screen.justenoughstructures.markers_on" : "screen.justenoughstructures.markers_off");
    }

    /** Only there while the player holds a compass it can open. Ctrl-click is mentioned when the server can do it. */
    private void updateCompassButton() {
        if (compassButton == null) {
            return;
        }
        compassButton.visible = selected != null && CompassLink.get().holding(minecraft.player);
        boolean pointing = ClientRequests.canPointCompass();
        if (!Boolean.valueOf(pointing).equals(compassPointing)) {
            compassPointing = pointing;
            Component label = Component.translatable("screen.justenoughstructures.compass_open");
            if (pointing) {
                label = label.copy().append("\n").append(Component.translatable("screen.justenoughstructures.compass_point_hint")
                        .withStyle(ChatFormatting.GRAY));
            }
            compassButton.setLabel(label);
        }
    }

    /** Ctrl-click: the compass starts searching straight away, and the browser gets out of the way. */
    private void pointCompass() {
        if (selected == null) {
            return;
        }
        ResourceLocation id = selected.id();
        ClientRequests.pointCompass(id).thenAccept(reply -> {
            if (reply.getContents() instanceof TranslatableContents t && t.getKey().endsWith("compass_now_searching")) {
                minecraft.gui.setOverlayMessage(reply, false);
                minecraft.setScreen(null);
                return;
            }
            if (selected != null && selected.id().equals(id)) {
                locateText = reply;
                locateFound = false;
                locateUntil = System.currentTimeMillis() + LOCATE_FAILURE_MILLIS;
            }
        });
    }

    private void openInCompass() {
        if (selected != null && !CompassLink.get().open(minecraft.player, selected.id())) {
            locateText = Component.translatable("screen.justenoughstructures.compass_cant_open");
            locateFound = false;
            locateUntil = System.currentTimeMillis() + LOCATE_FAILURE_MILLIS;
        }
    }

    /** True when the selected structure keeps where its loot is a secret, so its previews come without loot. */
    private boolean lootSecret() {
        return selected != null && selected.info().hideLootLocations();
    }

    /** Markers mean nothing for a structure whose loot is kept secret, so the button says so instead. */
    private void updateMarkersButton() {
        boolean secret = lootSecret();
        if (secret != markersSecret) {
            markersSecret = secret;
            markersButton.setLabel(markersLabel());
        }
        markersButton.active = !secret;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
