package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.Nbt;
import com.finndog.justenoughstructures.Regs;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.ClientState;
import com.finndog.justenoughstructures.client.CompassLink;
import com.finndog.justenoughstructures.client.Thumbnails;
import com.finndog.justenoughstructures.client.render.Highlight;
import com.finndog.justenoughstructures.client.render.SnapshotView;
import com.finndog.justenoughstructures.client.render.StructureViewport;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.network.Codecs;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
//? if >=26.1 {
/*import net.minecraft.nbt.NbtOps;
*///?}

/** The structure browser: a list on the left, the 3D preview in the middle and details on the right. */
public class JesScreen extends BackdropScreen implements Nav.Page {
    private static final int PAD = 6;
    /** Where the panels start, below the Back and Forward bar. */
    private static final int TOP = NavBar.TOP;
    /** How far the details panel sits down to make room for its tabs, as JEI's recipe panel does. */
    private static final int TABS = 21;
    private static final ResourceLocation RESET_ICON = JustEnoughStructures.id("textures/gui/reset_view.png");
    private static final ItemStack MARKERS_ICON = new ItemStack(Items.CHEST);
    private static final ItemStack MOB_MARKERS_ICON = new ItemStack(Items.SPAWNER);
    private static final ItemStack GROUND_ICON = new ItemStack(Items.GRASS_BLOCK);
    private static final ResourceLocation MAXIMISE_ICON = JustEnoughStructures.id("textures/gui/maximise.png");
    private static final ResourceLocation RESTORE_ICON = JustEnoughStructures.id("textures/gui/restore.png");
    /** How long a locate that found nothing stays in the header. */
    private static final long LOCATE_FAILURE_MILLIS = 8000;
    // One frame of the recovery compass. The item itself spins forever when there's no death point.
    private static final ResourceLocation LOCATE_ICON = Ids.parse("textures/item/recovery_compass_20.png");
    private static ResourceLocation lastSelected;

    private final Screen parent;
    private final StructureList list = new StructureList();
    private final StructureViewport viewport = new StructureViewport();
    private final ThumbnailQueue thumbnails = new ThumbnailQueue();
    private InfoPanel info;

    private EditBox search;
    private Button rerollButton;
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
    /** A spawner's popup. Only one popup is open at a time, this or {@link #popup}. */
    private SpawnerPopup spawnerPopup;
    private MobPopup mobPopup;
    private FoundInPopup foundIn;
    private String pendingTable;
    /** Where the camera goes once the structure being gone back to arrives, rather than fitting it to the view. */
    private StructureViewport.Camera pendingCamera;
    /** A popup to open once the structure being gone back to arrives. */
    private Popup pendingPopup;
    /**
     * Picking something in the preview for Pack tools to change. For a chest, markers show and a
     * chest's popup offers Change; for a spawner, every spawner is ringed.
     */
    private Picking picking = Picking.NONE;
    private Button toolsButton;
    /** Where the Cancel button on the picking strip was last drawn. */
    private int[] pickCancel;
    private final NavBar navBar = new NavBar(this);
    /** The blocks the details panel's hovered row is about, tinted in the preview, and what they were found for. */
    private Highlight highlight;
    private String highlightKey;
    private StructureSnapshot highlightFor;

    private Component locateText;
    private boolean locateFound;
    private boolean locating;
    private int locateAccess;
    private int seenReloads = ClientRequests.reloads();
    private int seenStructureChanges = ClientRequests.structureChanges();
    private int captureRequest;
    private CompletableFuture<Codecs.CaptureReply> capturing;
    /** The camera before the popup's arrows turned it to a container, to go back to when the popup closes. */
    private StructureViewport.Camera tourFrom;
    /** The layers showing when the popup's arrows first turned the camera, to put back when it closes, or -1. */
    private int tourSlice = -1;
    /** Set when the preview changed size while a popup had the camera, so closing it fits the structure to the new size. */
    private boolean refitAfterTour;
    /** How far from a container the arrows bring the camera, the same for every one. */
    private static final float TOUR_DISTANCE = 10f;
    private boolean messageUntilReload;
    /** Set when the preview was let go because another screen opened over this one. */
    private boolean dropped;
    /** What the markers button last said it was about, to change what it says when that changes. */
    private String markersFor;
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

    /** What the player is picking in the preview for Pack tools, if anything. */
    enum Picking {
        NONE, CHEST, SPAWNER
    }

    /**
     * What markers mark, which follows what the player is doing: containers, or on the Mobs tab, and
     * with a spawner's popup open or one being picked, mobs and spawners. Anything else worth marking
     * later is another kind here.
     */
    private enum MarkerKind { CONTAINERS, MOBS }

    /** Something a marker stands for, and where: a container, a spawner, or a mob the structure places. */
    private record Spot(BlockPos pos, Object what) {
    }

    /**
     * A marker on screen, at an exact (not pixel-rounded) position so it keeps up with the preview
     * as it turns. Things close together share one marker, so {@code spots} can hold several.
     */
    private record Marker(float x, float y, int size, List<Spot> spots) {
        List<StructureSnapshot.Container> containers() {
            List<StructureSnapshot.Container> out = new ArrayList<>();
            spots.forEach(spot -> {
                if (spot.what() instanceof StructureSnapshot.Container c) {
                    out.add(c);
                }
            });
            return out;
        }

        /** The first container it stands for, or null. */
        StructureSnapshot.Container container() {
            return first(StructureSnapshot.Container.class);
        }

        StructureSnapshot.Spawner spawner() {
            return first(StructureSnapshot.Spawner.class);
        }

        Entity mob() {
            return first(Entity.class);
        }

        private <T> T first(Class<T> type) {
            for (Spot spot : spots) {
                if (type.isInstance(spot.what())) {
                    return type.cast(spot.what());
                }
            }
            return null;
        }

        boolean marks(BlockPos pos) {
            return spots.stream().anyMatch(spot -> spot.pos().equals(pos));
        }

        boolean marks(Entity mob) {
            return spots.stream().anyMatch(spot -> spot.what() == mob);
        }

        /** The mobs it stands for. */
        List<Entity> mobs() {
            List<Entity> out = new ArrayList<>();
            spots.forEach(spot -> {
                if (spot.what() instanceof Entity mob) {
                    out.add(mob);
                }
            });
            return out;
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
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
            info.onOpenTable(this::openTable);
            info.onOpenSpawner(this::openSpawnerPopup);
            info.onOpenMobs(this::openMobs);
            info.onMove(Nav::remember);
        }
        // Side panels need room; below that, or when maximised, the preview takes the whole width.
        sides = !ClientState.maximised && width >= 330;
        // Up to a set width each, a little wider on very big screens so their text isn't a thin column.
        // Given the room, the details are as wide as a popup, which then sits over them, beside the preview.
        int leftW = sides ? clamp(width / 4, 118, Math.max(175, width / 7)) : 0;
        int rightW = sides ? clamp(width >= 560 ? Math.max(width / 4, ChestPopup.WIDTH + 12) : width / 4, 138, Math.max(200, width / 6)) : 0;
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
        listY = TOP + 6;
        listH = searchY - 4 - listY;
        list.layout(listX + 6, listY, listW - 12, listH);

        // Two title rows: previous/next mod around the mod's name, previous/next structure around its own.
        addRenderableWidget(new ArrowButton(centreX + 6, TOP + 4, true,
                Component.translatable("screen.justenoughstructures.previous_mod"), b -> step(-1, true)));
        addRenderableWidget(new ArrowButton(centreX + centreW - 19, TOP + 4, false,
                Component.translatable("screen.justenoughstructures.next_mod"), b -> step(1, true)));
        addRenderableWidget(new ArrowButton(centreX + 6, TOP + 19, true,
                Component.translatable("screen.justenoughstructures.previous_structure"), b -> step(-1, false)));
        addRenderableWidget(new ArrowButton(centreX + centreW - 19, TOP + 19, false,
                Component.translatable("screen.justenoughstructures.next_structure"), b -> step(1, false)));

        viewX = centreX + 6;
        viewY = TOP + 35;
        int toolbarY = height - PAD - 26;
        viewW = centreW - 12;
        // When the buttons leave the layer slider too little room for its label, it gets a row of its
        // own under them, as wide as the preview, rather than spilling past the panel's edge. "New
        // layout" is shortened to "New" only when that keeps the slider beside the buttons.
        int others = 22 + 22 + 24;
        boolean sliderRow = viewW - (36 + others) < LayerSlider.LABEL_WIDTH;
        boolean roomy = sliderRow ? viewW >= 68 + others - 4 : viewW - (68 + others) >= LayerSlider.LABEL_WIDTH;
        int buttonsY = sliderRow ? toolbarY - 22 : toolbarY;
        viewH = buttonsY - 4 - viewY;
        if (viewW != lastViewW || viewH != lastViewH) {
            // A bigger or smaller preview (maximised, or the window resized) gets zoomed to fit again,
            // though not while a popup has it turned to a container or spawner: that waits until it closes.
            if (tourFrom == null) {
                viewport.refit();
            } else {
                refitAfterTour = true;
            }
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
        rerollButton = addRenderableWidget(Button.builder(Component.translatable(roomy ? "screen.justenoughstructures.reroll" : "screen.justenoughstructures.reroll_short"), b -> reroll())
                .bounds(bx, buttonsY, roomy ? 66 : 34, 20).tooltip(Tooltip.create(
                        Component.translatable("screen.justenoughstructures.reroll_tooltip"))).build());
        bx += roomy ? 68 : 36;
        addRenderableWidget(new IconButton(bx, buttonsY, RESET_ICON,
                Component.translatable("screen.justenoughstructures.reset"), b -> viewport.resetCamera()));
        bx += 22;
        markersButton = addRenderableWidget(new IconButton(bx, buttonsY, () -> markerKind() == MarkerKind.MOBS ? MOB_MARKERS_ICON : MARKERS_ICON,
                () -> ClientState.markers && !markersSecret(), markersLabel(), b -> {
            ClientState.markers = !ClientState.markers;
            ClientState.save();
            markersButton.setLabel(markersLabel());
        }));
        markersFor = markersSecret() + "|" + markerKind();
        markersButton.active = !markersSecret();
        bx += 22;
        groundButton = addRenderableWidget(new IconButton(bx, buttonsY, () -> GROUND_ICON, () -> ClientState.ground, groundLabel(), b -> {
            ClientState.ground = !ClientState.ground;
            ClientState.save();
            groundButton.setLabel(groundLabel());
            updateGround();
        }));
        bx += 24;
        int sliderX = sliderRow ? viewX : bx;
        slider = addRenderableWidget(new LayerSlider(sliderX, toolbarY, viewX + viewW - sliderX, 20, shown -> {
            if (view != null) {
                view.setSliceY(shown);
            }
        }));
        updateSlider();

        // The details panel starts lower, so its tabs can sit on top of it the way JEI's do.
        info.layout(infoX + 6, TOP + TABS + 6, infoW - 12, height - TOP - PAD - TABS - 12, infoX + 2, TOP);
        // Pack tools, for those who can use it, at the end of the row of tabs.
        int toolsX = infoX + 2 + InfoPanel.Tab.values().length * 24 + 4;
        int toolsW = infoX + infoW - 2 - toolsX;
        toolsButton = addRenderableWidget(new ToolsButton(toolsX, TOP + 1, Math.max(20, toolsW), b -> openTools(PackToolsScreen.Section.OVERVIEW, null)));
        updateToolsButton();

        chestReroll = addRenderableWidget(Button.builder(Component.translatable("screen.justenoughstructures.reroll_loot"), b -> rerollLoot())
                .bounds(0, 0, 70, 20).build());
        chestPrev = addRenderableWidget(Button.builder(Component.literal("<"), b -> stepContainer(-1)).bounds(0, 0, 20, 20).build());
        chestNext = addRenderableWidget(Button.builder(Component.literal(">"), b -> stepContainer(1)).bounds(0, 0, 20, 20).build());
        chestClose = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> closePopup())
                .bounds(0, 0, 44, 20).build());
        if (popup != null) {
            placePopup();
        }
        if (foundIn != null) {
            foundIn.place(width, height);
        }
        if (side() != null) {
            side().placeAt(besideX(SidePopup.WIDTH), height, font);
        }
        layoutPopupButtons();

        // Which tables have edits, to mark them, asked again whenever the browser shows, like after the editor.
        ClientRequests.requestOverrides();
        if (dropped && catalog != null && selected != null) {
            dropped = false;
            refetch();
        }
        if (catalog == null && catalogError == null) {
            if (!ClientRequests.serverSupported()) {
                catalogError = ClientRequests.serverOnOtherVersion() ? "screen.justenoughstructures.other_version" : "screen.justenoughstructures.no_server";
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

    private void updateToolsButton() {
        if (toolsButton != null) {
            toolsButton.visible = sides && ClientRequests.showsPackTools() && toolsButton.getWidth() >= 20;
        }
    }

    /** Opens Pack tools on a section, and on something in it if given. Anything being picked for it has been. */
    void openTools(PackToolsScreen.Section section, Object selection) {
        Nav.remember();
        picking = Picking.NONE;
        minecraft.setScreen(new PackToolsScreen(this, section, selection));
    }

    @Override
    public void tick() {
        super.tick();
        updateToolsButton();
        updateLocateButton();
        updateMarkersButton();
        updateCompassButton();
        // After a /reload the structure may have changed, like a container pointed at another table, so it's generated again.
        if (ClientRequests.reloads() != seenReloads) {
            seenReloads = ClientRequests.reloads();
            if (messageUntilReload) {
                locateText = null;
                messageUntilReload = false;
            }
            ClientRequests.requestOverrides();
            if (selected != null) {
                select(selected, seed);
            }
        }
        // Structures hidden or shown, or what's said about them changed, by Pack tools or a /reload.
        if (ClientRequests.structureChanges() != seenStructureChanges && catalog != null) {
            seenStructureChanges = ClientRequests.structureChanges();
            ClientRequests.catalog().thenAccept(entries -> {
                if (minecraft != null && minecraft.screen == this) {
                    onNewCatalog(entries);
                }
            });
        }
    }

    /**
     * The list again, after structures were hidden, shown or changed. The one on show stays unless
     * it's gone, and is generated again if what players see of it changed, like its loot becoming a
     * secret.
     */
    private void onNewCatalog(List<StructureCatalog.Entry> entries) {
        List<StructureCatalog.Entry> before = catalog;
        catalog = entries;
        list.setEntries(entries);
        StructureCatalog.Entry now = selected == null ? null : find(selected.id());
        if (now == null) {
            // Gone, like a structure just hidden: the next one along that's still here takes its place.
            StructureCatalog.Entry next = null;
            int at = before == null || selected == null ? -1 : before.indexOf(selected);
            for (int i = at + 1; at >= 0 && i < before.size() && next == null; i++) {
                next = find(before.get(i).id());
            }
            if (next == null && !entries.isEmpty()) {
                next = entries.get(0);
            }
            if (next != null) {
                select(next, defaultSeed(next.id()));
            }
            return;
        }
        boolean changed = !now.info().equals(selected.info());
        if (changed) {
            selected = null;
            select(now, seed);
        } else {
            selected = now;
            info.updateEntry(now);
        }
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
        // Only offered to players allowed to locate. The compass button moves up to take its place.
        locateButton.visible = access > 0;
        if (compassButton != null) {
            compassButton.setX((locateButton.visible ? locateButton.getX() : maximiseButton.getX()) - 22);
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
                if (entry != selected) {
                    Nav.remember();
                }
                select(entry, defaultSeed(id));
                return true;
            }
        }
        return false;
    }

    private StructureCatalog.Entry find(ResourceLocation id) {
        if (catalog != null) {
            for (StructureCatalog.Entry entry : catalog) {
                if (entry.id().equals(id)) {
                    return entry;
                }
            }
        }
        return null;
    }

    private void select(StructureCatalog.Entry entry, long newSeed) {
        if (selected != entry) {
            locateText = null;
            locateUntil = Long.MAX_VALUE;
        }
        selected = entry;
        lastSelected = entry.id();
        seed = newSeed;
        pendingCamera = null;
        pendingTable = null;
        pendingPopup = null;
        list.setSelected(entry.id());
        list.revealSelected();
        info.setEntry(entry);
        closePopup();
        result = null;
        view = null;
        viewport.setView(null);
        updateSlider();
        requestCapture(entry.id(), newSeed);
    }

    /**
     * Asks again for the structure on show, after another screen was opened over this one and it
     * was let go. The server still has it, so it's quick, and the open chest and picked table stay.
     */
    private void refetch() {
        if (info.selectedTable() != null) {
            pendingTable = info.selectedTable();
        }
        requestCapture(selected.id(), seed);
    }

    /**
     * Only the newest request counts: an older one for the same structure can come back after it,
     * skipped by the server because this one replaced it. The older one is cancelled so it isn't
     * unpacked when it arrives. A big structure takes a while to lay out, so that's done off the
     * render thread.
     */
    private void requestCapture(ResourceLocation id, long wanted) {
        int request = ++captureRequest;
        if (capturing != null) {
            capturing.cancel(false);
        }
        capturing = ClientRequests.capture(id, wanted, true);
        capturing.thenApplyAsync(reply -> request == captureRequest && reply.id().equals(id) ? Prepared.of(reply.result()) : null,
                        Util.backgroundExecutor())
                .whenCompleteAsync((prepared, error) -> {
                    if (request != captureRequest || selected == null || !selected.id().equals(id) || seed != wanted) {
                        return;
                    }
                    if (error != null) {
                        // Say so rather than showing "Generating" for ever.
                        JesLog.debug("Couldn't show {}", id, error);
                        onCaptured(CaptureResult.failure(Component.translatable("screen.justenoughstructures.unreadable"), List.of(), 0), null);
                    } else if (prepared != null) {
                        onCaptured(prepared.result(), prepared.view());
                    }
                }, Minecraft.getInstance());
    }

    /** A capture with its preview laid out, when it worked. */
    private record Prepared(CaptureResult result, SnapshotView view) {
        static Prepared of(CaptureResult result) {
            if (!result.succeeded()) {
                return new Prepared(result, null);
            }
            StructureSnapshot snapshot = result.snapshot();
            // The containers' icons look their blocks up by position, which needs a lookup built first.
            if (snapshot.containers().stream().anyMatch(c -> !c.entity())) {
                snapshot.prepareLookup();
            }
            return new Prepared(result, new SnapshotView(snapshot));
        }
    }

    private void onCaptured(CaptureResult captured, SnapshotView prepared) {
        if (minecraft == null || minecraft.screen != this) {
            // Arrived after another screen was opened over this one: it's asked for again on coming back.
            dropped = true;
            return;
        }
        result = captured;
        info.setResult(captured);
        if (pendingTable != null) {
            selectTable(pendingTable);
            pendingTable = null;
        }
        if (prepared != null && minecraft != null && minecraft.level != null) {
            view = prepared;
            view.createRenderables(minecraft.level);
            viewport.setView(view);
            if (pendingCamera != null) {
                viewport.setCamera(pendingCamera);
                pendingCamera = null;
            }
            updateGround();
        }
        updateSlider();
        if (pendingPopup != null) {
            showPopup(pendingPopup);
        }
    }

    /** Where the flat ground the structure was generated on sits, as a local height, if it cuts through it. */
    private void updateGround() {
        if (view == null) {
            return;
        }
        StructureSnapshot s = view.snapshot();
        int surface = s.terrain().surface();
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
        openContainer(container, false);
    }

    /**
     * With {@code group}, it was opened from something standing for several containers, a row in the
     * Loot tab or a marker they share, so it shows all of them until one is picked.
     */
    private void openContainer(StructureSnapshot.Container container, boolean group) {
        if (result == null || !result.succeeded()) {
            return;
        }
        Nav.remember();
        showContainer(container, group);
        if (!popup.overview()) {
            lookAt(container.pos());
        }
    }

    /** Shows a container, or with {@code all}, every container with its table, {@code container} standing for them. */
    private void showContainer(StructureSnapshot.Container container, boolean all) {
        List<StructureSnapshot.Container> same = sameTable(container);
        spawnerPopup = null;
        mobPopup = null;
        ChestPopup previous = popup;
        ChestPopup.View keep = previous != null && previous.container != null ? previous.view : ChestPopup.View.ROLL;
        popup = ChestPopup.forContainer(container, containerTitle(container), containerSize(container),
                all && same.size() > 1 ? -1 : same.indexOf(container), same.size());
        popup.view = keep;
        popup.picking = picking == Picking.CHEST;
        popup.icon = InfoPanel.containerIcon(result.snapshot(), container);
        placePopup();
        layoutPopupButtons();
        if (container.lootTable() == null) {
            // Each was saved with its own items, so there's nothing to show until one is picked.
            popup.items = popup.overview() ? List.of() : prefilledItems(container);
            return;
        }
        if (previous != null && previous.container != null && same.contains(previous.container)) {
            // Another of the same: they share the table, so a new roll would only be another roll of it.
            popup.seed = previous.seed;
            popup.items = previous.items;
            popup.odds = previous.odds;
            if (popup.items == null) {
                rollPopup();
            }
            if (popup.odds == null) {
                fetchPopupOdds();
            }
            return;
        }
        popup.seed = container.lootSeed() != 0 ? container.lootSeed()
                : seed ^ (container.pos().asLong() * 0x9E3779B97F4A7C15L);
        rollPopup();
        fetchPopupOdds();
    }

    /**
     * Container popups sit beside the preview, over the details if they're showing, so the containers
     * they're about can be seen. A table on its own isn't anywhere, so it goes in the middle.
     */
    private void placePopup() {
        if (popup.container == null) {
            popup.place(width, height, font);
            return;
        }
        popup.placeAt(besideX(ChestPopup.WIDTH), height, font);
    }

    /** Where a popup {@code w} wide goes beside the preview: over the details if they're showing, or the preview's right edge. */
    private int besideX(int w) {
        return sides ? Math.min(width - 4 - w, infoX + Math.max(0, (infoW - w) / 2)) : viewX + viewW - w - 4;
    }

    /**
     * Turns the camera to the container or spawner the popup is showing, from the same distance every
     * time, so it's clear which one it is and dragging the preview turns around it.
     */
    private void lookAt(BlockPos pos) {
        lookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, pos, pos.getY() + 1);
    }

    /** The same for a mob, turning to its middle. */
    private void lookAt(Entity mob) {
        lookAt(mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), null, (int) Math.ceil(mob.getY() + mob.getBbHeight()));
    }

    /**
     * Turns the camera to x, y, z, where {@code target} is when it's a block. {@code top} is the layer
     * just above it, which the layers are cut down to when, from where the camera ends up, something
     * solid is in the way: a shulker in an End City room is behind the roof. Otherwise the layers stay
     * as the player left them, or all show if they hid it.
     */
    private void lookAt(double x, double y, double z, BlockPos target, int top) {
        if (view == null) {
            return;
        }
        StructureViewport.Camera now = viewport.camera();
        if (tourFrom == null) {
            tourFrom = now;
            tourSlice = view.sliceY();
        }
        StructureViewport.Camera to = new StructureViewport.Camera(now.yaw(), now.pitch(), TOUR_DISTANCE, (float) x, (float) y, (float) z);
        int keep = top > tourSlice ? view.size().getY() : tourSlice;
        int slice = viewport.blocked(to, target, keep) ? top : keep;
        if (slice != view.sliceY()) {
            view.setSliceY(slice);
            updateSlider();
        }
        viewport.glideTo(to);
    }

    /** The block a mob's middle is in, which stands for where it is, as a block does for a container. */
    private static BlockPos mobBlock(Entity mob) {
        return BlockPos.containing(mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ());
    }

    /** Opens a loot table this layout doesn't have in a popup on its own: its chances, and a roll of it. */
    public void openTable(String table) {
        if (ResourceLocation.tryParse(table) == null) {
            return;
        }
        Nav.remember();
        showTable(table);
    }

    private void showTable(String table) {
        foundIn = null;
        popup = ChestPopup.forTable(table);
        popup.place(width, height, font);
        layoutPopupButtons();
        popup.seed = ThreadLocalRandom.current().nextLong();
        rollPopup();
        fetchPopupOdds();
    }

    private void fetchPopupOdds() {
        ChestPopup current = popup;
        ResourceLocation table = current.table == null ? null : ResourceLocation.tryParse(current.table);
        if (table != null) {
            ClientRequests.odds(table).thenAccept(odds -> current.odds = odds);
        }
    }

    private void rollPopup() {
        ChestPopup current = popup;
        ResourceLocation table = current.table == null ? null : ResourceLocation.tryParse(current.table);
        if (table == null) {
            current.items = List.of();
            return;
        }
        current.items = null;
        ClientRequests.loot(table, current.seed, current.size).thenAccept(items -> current.items = items);
    }

    /** Rolls the table again, showing the roll. */
    private void rerollLoot() {
        if (popup != null && popup.table != null) {
            popup.switchTo(ChestPopup.View.ROLL);
            placePopup();
            popup.seed = ThreadLocalRandom.current().nextLong();
            rollPopup();
        }
    }

    private void stepContainer(int direction) {
        if (spawnerPopup != null) {
            stepSpawner(direction);
            return;
        }
        if (mobPopup != null) {
            stepMob(direction);
            return;
        }
        if (popup == null || popup.container == null) {
            return;
        }
        List<StructureSnapshot.Container> same = sameTable(popup.container);
        if (same.size() > 1) {
            // From all of them, the first step goes to the first or the last.
            int at = popup.overview() ? (direction > 0 ? -1 : same.size()) : same.indexOf(popup.container);
            StructureSnapshot.Container next = same.get(Math.floorMod(at + direction, same.size()));
            showContainer(next, false);
            lookAt(next.pos());
        }
    }

    private List<StructureSnapshot.Container> sameTable(StructureSnapshot.Container container) {
        List<StructureSnapshot.Container> out = new ArrayList<>();
        for (StructureSnapshot.Container c : result.snapshot().containers()) {
            if (Objects.equals(c.lootTable(), container.lootTable()) && c.id().equals(container.id())) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * Opens a spawner, from the preview or the Mobs tab. With {@code group}, it was picked from a row
     * standing for every spawner like it, so it shows all of them until one is picked.
     */
    private void openSpawnerPopup(StructureSnapshot.Spawner spawner, boolean group) {
        if (result == null || !result.succeeded()) {
            return;
        }
        Nav.remember();
        showSpawner(spawner, group);
        if (!spawnerPopup.overview()) {
            lookAt(spawner.pos());
        }
    }

    /** Opens a spawner's popup, as clicking it in the preview does. For the screenshot harness. */
    public void openSpawnerPopup(StructureSnapshot.Spawner spawner) {
        openSpawnerPopup(spawner, false);
    }

    /** Shows a spawner, or with {@code all}, every spawner that makes the same, {@code spawner} standing for them. */
    private void showSpawner(StructureSnapshot.Spawner spawner, boolean all) {
        List<StructureSnapshot.Spawner> same = SpawnerKind.same(result.snapshot(), spawner);
        popup = null;
        mobPopup = null;
        BlockPos pos = spawner.pos();
        Component title = view != null ? view.rawState(pos.getX(), pos.getY(), pos.getZ()).getBlock().getName()
                : Component.translatable("block.minecraft.spawner");
        spawnerPopup = new SpawnerPopup(spawner, SpawnerKind.tags(result.snapshot()).get(pos), title,
                all && same.size() > 1 ? -1 : same.indexOf(spawner), same.size());
        spawnerPopup.picking = picking == Picking.SPAWNER;
        spawnerPopup.placeAt(besideX(SpawnerPopup.WIDTH), height, font);
        layoutPopupButtons();
    }

    private void stepSpawner(int direction) {
        List<StructureSnapshot.Spawner> same = SpawnerKind.same(result.snapshot(), spawnerPopup.spawner);
        if (same.size() > 1) {
            // From all of them, the first step goes to the first or the last.
            int at = spawnerPopup.overview() ? (direction > 0 ? -1 : same.size()) : same.indexOf(spawnerPopup.spawner);
            StructureSnapshot.Spawner next = same.get(Math.floorMod(at + direction, same.size()));
            showSpawner(next, false);
            lookAt(next.pos());
        }
    }

    private void spawnerAction(SpawnerPopup open, SpawnerPopup.Action action) {
        if (action == SpawnerPopup.Action.TOOLS) {
            openSpawner(open.spawner);
        }
    }

    /**
     * Opens a mob the structure places, from the preview or the Mobs tab. With {@code group}, it was
     * picked from something standing for every one of its kind, so it shows all of them until one is
     * picked.
     */
    private void openMobPopup(Entity mob, boolean group) {
        if (result == null || !result.succeeded() || view == null) {
            return;
        }
        Nav.remember();
        showMob(mob, group);
        if (!mobPopup.overview()) {
            lookAt(mob);
        }
    }

    /** Opens a mob's popup, as clicking it in the preview does. For the screenshot harness. */
    public void openMobPopup(Entity mob) {
        openMobPopup(mob, false);
    }

    /** Opens every mob of a kind, as clicking its row on the Mobs tab does. */
    private void openMobs(String type) {
        if (view == null) {
            return;
        }
        for (Entity mob : view.entities()) {
            if (BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString().equals(type)) {
                openMobPopup(mob, true);
                return;
            }
        }
    }

    /** Shows a mob, or with {@code all}, every one of its kind, {@code mob} standing for them. */
    private void showMob(Entity mob, boolean all) {
        List<Entity> same = sameMobs(mob);
        popup = null;
        spawnerPopup = null;
        mobPopup = new MobPopup(mob, all && same.size() > 1 ? -1 : same.indexOf(mob), same.size());
        mobPopup.placeAt(besideX(SidePopup.WIDTH), height, font);
        layoutPopupButtons();
    }

    private void stepMob(int direction) {
        if (view == null) {
            return;
        }
        List<Entity> same = sameMobs(mobPopup.mob);
        if (same.size() > 1) {
            // From all of them, the first step goes to the first or the last.
            int at = mobPopup.overview() ? (direction > 0 ? -1 : same.size()) : same.indexOf(mobPopup.mob);
            Entity next = same.get(Math.floorMod(at + direction, same.size()));
            showMob(next, false);
            lookAt(next);
        }
    }

    /** The mobs in the layout of the same kind as {@code mob}, it among them. */
    private List<Entity> sameMobs(Entity mob) {
        List<Entity> out = new ArrayList<>();
        for (Entity other : view.entities()) {
            if (other.getType() == mob.getType()) {
                out.add(other);
            }
        }
        return out;
    }

    /** The spawner's or mob's popup that's open, or null. */
    private SidePopup side() {
        return spawnerPopup != null ? spawnerPopup : mobPopup;
    }

    /** Shows the structures whose loot can give this item. */
    public void openFoundIn(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        Nav.remember();
        showFoundIn(stack);
    }

    private void showFoundIn(ItemStack stack) {
        closePopup();
        foundIn = new FoundInPopup(stack);
        foundIn.place(width, height);
        ClientRequests.index();
    }

    private void pickFromFoundIn(FoundInPopup.Row row) {
        StructureCatalog.Entry entry = find(row.structure());
        if (entry != null) {
            // Remembered with the list still open, so Back comes back to it.
            Nav.remember();
            foundIn = null;
            select(entry, defaultSeed(entry.id()));
            info.setTab(InfoPanel.Tab.LOOT);
            pendingTable = row.tables().iterator().next().toString();
        }
        foundIn = null;
    }

    // ------------------------------------------------------------------ back and forward

    /** A popup open over the browser, as history keeps it. */
    private sealed interface Popup {
        Object key();
    }

    /** A container's popup, found again by where the container is, as the same layout comes out the same. */
    private record ContainerPopup(BlockPos pos, boolean entity, ChestPopup.View view, Component title, boolean all) implements Popup {
        @Override
        public Object key() {
            return List.of("container", pos, entity);
        }
    }

    /** A spawner's popup, found again by where the spawner is. */
    private record SpawnerPlace(BlockPos pos, Component title, boolean all) implements Popup {
        @Override
        public Object key() {
            return List.of("spawner", pos);
        }
    }

    /** A mob's popup, found again by its kind and where it is. */
    private record MobPlace(EntityType<?> type, BlockPos pos, Component title, boolean all) implements Popup {
        @Override
        public Object key() {
            return List.of("mob", BuiltInRegistries.ENTITY_TYPE.getKey(type), pos);
        }
    }

    private record TablePopup(String table) implements Popup {
        @Override
        public Object key() {
            return List.of("table", table);
        }
    }

    private record FoundInList(ItemStack item) implements Popup {
        @Override
        public Object key() {
            return List.of("found_in", BuiltInRegistries.ITEM.getKey(item.getItem()));
        }
    }

    /** The browser as it was: the structure and layout, the tab, the picked table, the camera and any popup. */
    private record BrowserLayer(ResourceLocation id, long seed, InfoPanel.Tab tab, String table, StructureViewport.Camera camera,
                                Popup popup, Component label, Picking picking) implements Nav.Layer {
        @Override
        public Object key() {
            return Arrays.asList(id, seed, tab, popup == null ? null : popup.key(), picking);
        }

        @Override
        public Screen open(Screen below) {
            throw new UnsupportedOperationException("The browser is always under the other screens");
        }
    }

    @Override
    public Nav.Layer layer() {
        if (selected == null) {
            return null;
        }
        Popup open = currentPopup();
        String name = StructureNames.structure(selected.id());
        Component label;
        if (open instanceof FoundInList list) {
            label = Component.translatable("screen.justenoughstructures.nav.found_in", list.item().getHoverName());
        } else if (open instanceof ContainerPopup container) {
            label = Component.translatable("screen.justenoughstructures.nav.part", name, container.title());
        } else if (open instanceof SpawnerPlace spawner) {
            label = Component.translatable("screen.justenoughstructures.nav.part", name, spawner.title());
        } else if (open instanceof MobPlace mob) {
            label = Component.translatable("screen.justenoughstructures.nav.part", name, mob.title());
        } else if (open instanceof TablePopup table) {
            label = Component.translatable("screen.justenoughstructures.nav.part", name, StructureNames.lootTable(table.table()));
        } else if (info.tab() != InfoPanel.Tab.OVERVIEW) {
            label = Component.translatable("screen.justenoughstructures.nav.part", name, info.tab().label());
        } else {
            label = Component.literal(name);
        }
        if (picking != Picking.NONE && open == null) {
            label = Component.translatable(picking == Picking.CHEST ? "screen.justenoughstructures.nav.picking"
                    : "screen.justenoughstructures.nav.picking_spawner", name);
        }
        String table = info.selectedTable() != null ? info.selectedTable() : pendingTable;
        return new BrowserLayer(selected.id(), seed, info.tab(), table, view == null ? pendingCamera : viewport.camera(), open, label, picking);
    }

    @Override
    public Screen below() {
        return null;
    }

    /** The popup showing, or the one waiting for its structure. */
    private Popup currentPopup() {
        if (foundIn != null) {
            return new FoundInList(foundIn.item);
        }
        if (spawnerPopup != null) {
            return new SpawnerPlace(spawnerPopup.spawner.pos(), spawnerPopup.title, spawnerPopup.overview());
        }
        if (mobPopup != null) {
            return new MobPlace(mobPopup.mob.getType(), mobBlock(mobPopup.mob), mobPopup.title, mobPopup.overview());
        }
        if (popup != null) {
            return popup.container != null
                    ? new ContainerPopup(popup.container.pos(), popup.container.entity(), popup.view, popup.title, popup.overview())
                    : new TablePopup(popup.table);
        }
        return pendingPopup;
    }

    /** Whether history can come back to {@code layer}: its structure can be gone since, after a /reload. */
    boolean canRestore(Nav.Layer layer) {
        return layer instanceof BrowserLayer place && find(place.id()) != null;
    }

    /**
     * Comes back to a place in history. Behind another screen, or about to be, it only notes where to
     * be, and the structure is generated once it shows.
     */
    void restorePlace(Nav.Layer layer, boolean shownAfter) {
        StructureCatalog.Entry entry = layer instanceof BrowserLayer place ? find(place.id()) : null;
        if (entry == null) {
            return;
        }
        BrowserLayer place = (BrowserLayer) layer;
        closePopup();
        foundIn = null;
        picking = place.picking();
        boolean showing = shownAfter && minecraft != null && minecraft.screen == this;
        if (!showing) {
            if (selected != entry) {
                locateText = null;
                locateUntil = Long.MAX_VALUE;
                info.setEntry(entry);
            }
            selected = entry;
            lastSelected = entry.id();
            seed = place.seed();
            list.setSelected(entry.id());
            list.revealSelected();
            // Anything still on its way is for where it was.
            captureRequest++;
            if (capturing != null) {
                capturing.cancel(false);
            }
            info.setTab(place.tab());
            info.setSelectedTable(place.table());
            pendingCamera = place.camera();
            pendingPopup = place.popup();
            dropped = true;
            return;
        }
        if (selected != entry || seed != place.seed()) {
            select(entry, place.seed());
            info.setTab(place.tab());
            pendingCamera = place.camera();
            pendingTable = place.table();
            pendingPopup = place.popup();
            return;
        }
        info.setTab(place.tab());
        if (place.table() != null && !place.table().equals(info.selectedTable())) {
            selectTable(place.table());
        }
        if (place.popup() != null) {
            showPopup(place.popup());
        }
    }

    /** Opens a popup history kept, once there's a structure to open it on. */
    private void showPopup(Popup open) {
        pendingPopup = null;
        if (open instanceof FoundInList list) {
            showFoundIn(list.item());
            return;
        }
        if (result == null) {
            pendingPopup = open;
            return;
        }
        if (open instanceof TablePopup table) {
            showTable(table.table());
        } else if (open instanceof ContainerPopup wanted && result.succeeded()) {
            for (StructureSnapshot.Container c : result.snapshot().containers()) {
                if (c.pos().equals(wanted.pos()) && c.entity() == wanted.entity()) {
                    showContainer(c, wanted.all());
                    popup.switchTo(wanted.view());
                    placePopup();
                    layoutPopupButtons();
                    return;
                }
            }
        } else if (open instanceof SpawnerPlace wanted && result.succeeded()) {
            for (StructureSnapshot.Spawner spawner : result.snapshot().spawners()) {
                if (spawner.pos().equals(wanted.pos())) {
                    showSpawner(spawner, wanted.all());
                    return;
                }
            }
        } else if (open instanceof MobPlace wanted && result.succeeded() && view != null) {
            for (Entity mob : view.entities()) {
                if (mob.getType() == wanted.type() && mobBlock(mob).equals(wanted.pos())) {
                    showMob(mob, wanted.all());
                    return;
                }
            }
        }
    }

    /**
     * The blocks the row under the mouse in the details panel is about, such as every block of a kind
     * or a group of chests, found once and kept while that row stays hovered. It's from the panel as
     * last drawn, a frame behind, which can't be seen.
     */
    private Highlight highlight() {
        StructureSnapshot shown = view == null ? null : view.snapshot();
        String key = null;
        Function<StructureSnapshot, LongSet> wanted = null;
        if (popup != null && popup.container != null && popup.count > 1 && foundIn == null) {
            // Every container the popup steps through, so where they all are shows beside it, but not
            // the one on show: its marker's white outline marks it, and the tint would hide it close up.
            StructureSnapshot.Container anchor = popup.container;
            BlockPos showing = popup.overview() ? null : anchor.pos();
            key = "popup:" + anchor.lootTable() + "|" + anchor.id() + "|" + showing;
            wanted = s -> {
                LongSet out = new LongOpenHashSet();
                sameTable(anchor).forEach(c -> out.add(c.pos().asLong()));
                if (showing != null) {
                    out.remove(showing.asLong());
                }
                return out;
            };
        } else if (spawnerPopup != null && spawnerPopup.count > 1 && foundIn == null) {
            // The same for a spawner's popup: every spawner its arrows step through, but the one on show.
            StructureSnapshot.Spawner anchor = spawnerPopup.spawner;
            BlockPos showing = spawnerPopup.overview() ? null : anchor.pos();
            key = "spawner_popup:" + anchor.pos() + "|" + showing;
            wanted = s -> {
                LongSet out = new LongOpenHashSet();
                SpawnerKind.same(s, anchor).forEach(spawner -> out.add(spawner.pos().asLong()));
                if (showing != null) {
                    out.remove(showing.asLong());
                }
                return out;
            };
        } else if (mobPopup != null && mobPopup.count > 1 && foundIn == null && view != null) {
            // And for a mob's: every one of its kind but the one on show.
            Entity anchor = mobPopup.mob;
            BlockPos showing = mobPopup.overview() ? null : mobBlock(anchor);
            key = "mob_popup:" + BuiltInRegistries.ENTITY_TYPE.getKey(anchor.getType()) + "|" + showing;
            List<Entity> same = sameMobs(anchor);
            wanted = s -> {
                LongSet out = new LongOpenHashSet();
                same.forEach(mob -> out.add(mobBlock(mob).asLong()));
                if (showing != null) {
                    out.remove(showing.asLong());
                }
                return out;
            };
        } else if (sides && popup == null && side() == null && foundIn == null) {
            InfoPanel.Hovered hovered = info.hoveredBlocks();
            if (hovered != null) {
                key = hovered.key();
                wanted = hovered.positions();
            }
        }
        if (key == null && picking == Picking.SPAWNER && popup == null && side() == null && foundIn == null) {
            key = "picking:spawners";
            wanted = s -> {
                LongSet out = new LongOpenHashSet();
                s.spawners().forEach(spawner -> out.add(spawner.pos().asLong()));
                return out;
            };
        }
        if (key == null || shown == null) {
            clearHighlight();
            return null;
        }
        if (!key.equals(highlightKey) || shown != highlightFor) {
            clearHighlight();
            highlightKey = key;
            highlightFor = shown;
            LongSet positions = wanted.apply(shown);
            // Mobs are found by their markers alone: a block-sized box over one hides it.
            boolean mobs = key.startsWith("mob_popup:") || key.startsWith("placed:");
            highlight = positions == null || positions.isEmpty() || positions.size() > Highlight.MAX_BLOCKS ? null : new Highlight(positions, !mobs);
        }
        return highlight;
    }

    /** Up to this many highlighted things each get a marker of their own, as from far out they can be a few pixels across. */
    private static final int MARKED_HIGHLIGHTS = 64;
    /** The edge of a marker lit up by the mouse or by a row about what it marks. */
    private static final int LIT = 0xFFFFFF55;
    /** The edge of the marker for what a popup is showing. */
    private static final int SHOWING = 0xFFFFFFFF;

    /**
     * A lit marker over each of a handful of highlighted things that have no marker showing, like
     * every bell while the Blocks tab's row for them is hovered, or spawners while markers are off,
     * so a lone one deep inside a big structure can still be found.
     */
    private void markHighlighted(GuiGraphics g) {
        if (highlight == null || highlight.positions().size() > MARKED_HIGHLIGHTS) {
            return;
        }
        Set<BlockPos> marked = new HashSet<>();
        markerRects.forEach(m -> m.spots().forEach(spot -> marked.add(spot.pos())));
        Map<BlockPos, Entity> mobs = new HashMap<>();
        view.entities().forEach(mob -> mobs.putIfAbsent(mobBlock(mob), mob));
        BlockPos shown = shownPos();
        int slice = view.sliceY();
        int size = markerSize();
        for (long packed : highlight.positions()) {
            BlockPos pos = BlockPos.of(packed);
            if (pos.getY() >= slice || marked.contains(pos) || pos.equals(shown)) {
                continue;
            }
            Entity mob = mobs.get(pos);
            ItemStack icon = mob != null ? mobIcon(mob) : new ItemStack(view.rawState(pos.getX(), pos.getY(), pos.getZ()).getBlock());
            Optional<float[]> at = mob != null ? viewport.project(mob.getX(), mob.getY() + mob.getBbHeight() + 0.2, mob.getZ())
                    : viewport.project(pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5);
            at.ifPresent(p -> drawMarker(g, p[0] - size / 2f, p[1] - size, size, icon, LIT, 1));
        }
    }

    /** Darkens the whole screen except one rectangle. */
    private void dimAround(GuiGraphics g, int x, int y, int w, int h) {
        int shade = 0x88000000;
        g.fill(0, 0, width, y, shade);
        g.fill(0, y + h, width, height, shade);
        g.fill(0, y, x, y + h, shade);
        g.fill(x + w, y, width, y + h, shade);
    }

    /** Where the container, spawner or mob a popup is showing is, or null. */
    private BlockPos shownPos() {
        if (spawnerPopup != null && !spawnerPopup.overview()) {
            return spawnerPopup.spawner.pos();
        }
        if (mobPopup != null && !mobPopup.overview()) {
            return mobBlock(mobPopup.mob);
        }
        if (popup != null && popup.container != null && !popup.overview()) {
            return popup.container.pos();
        }
        return null;
    }

    /**
     * The container, spawner or mob a popup is showing gets a marker edged in white, when markers are
     * off or it has none, so it's clear which one it is.
     */
    private void markShown(GuiGraphics g, StructureSnapshot s) {
        BlockPos pos = shownPos();
        if (pos == null || pos.getY() >= view.sliceY()) {
            return;
        }
        int size = markerSize();
        ItemStack icon;
        Optional<float[]> at;
        if (spawnerPopup != null) {
            if (markerRects.stream().anyMatch(m -> m.spawner() != null && m.marks(pos))) {
                return;
            }
            icon = MOB_MARKERS_ICON;
            at = viewport.project(pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5);
        } else if (mobPopup != null) {
            Entity mob = mobPopup.mob;
            if (markerRects.stream().anyMatch(m -> m.marks(mob))) {
                return;
            }
            icon = mobIcon(mob);
            at = viewport.project(mob.getX(), mob.getY() + mob.getBbHeight() + 0.2, mob.getZ());
        } else {
            StructureSnapshot.Container container = popup.container;
            if (markerRects.stream().anyMatch(m -> m.containers().contains(container))) {
                return;
            }
            icon = InfoPanel.containerIcon(s, container);
            at = viewport.project(pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5);
        }
        at.ifPresent(p -> drawMarker(g, p[0] - size / 2f, p[1] - size, size, icon, SHOWING, 1));
    }

    /** A marker: {@code icon} in a dark box with its top left at left, top, edged in {@code edge}, and how many it stands for when that's more than one. */
    private void drawMarker(GuiGraphics g, float left, float top, int size, ItemStack icon, int edge, int count) {
        Gui.push(g);
        // Moved by the exact amount rather than to the nearest GUI pixel, which is what made
        // markers jitter against the smoothly turning preview.
        Gui.translate(g, left, top);
        Gui.lift(g, 200);
        g.fill(-1, -1, size + 1, size + 1, edge);
        g.fill(0, 0, size, size, 0xFF2B2B2B);
        Gui.push(g);
        Gui.translate(g, 0.5f, 0.5f);
        float scale = (size - 1) / 16f;
        Gui.scale(g, scale);
        g.renderItem(icon, 0, 0);
        Gui.pop(g);
        if (count > 1) {
            String text = String.valueOf(count);
            Gui.lift(g, 200);
            Gui.small(g, font, text, size - Gui.smallWidth(font, text) + 1, size - 5, 0xFFFFFFFF);
        }
        Gui.pop(g);
    }

    /** How big markers are, which follows the zoom between limits. */
    private int markerSize() {
        float perBlock = Math.max(0.01f, viewport.pixelsPerBlock());
        return Math.round(Math.max(8f, Math.min(16f, perBlock * 0.8f)));
    }

    private void clearHighlight() {
        if (highlight != null) {
            highlight.close();
        }
        highlight = null;
        highlightKey = null;
        highlightFor = null;
    }

    /** Where text along the top of the preview starts. */
    private int headerTextX() {
        return viewX + 6;
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

    /** The chest popup's tabs: one roll, or every item's chance. */
    private void popupAction(ChestPopup open, ChestPopup.Action action) {
        switch (action) {
            case ROLL, ODDS -> {
                open.switchTo(action == ChestPopup.Action.ODDS ? ChestPopup.View.ODDS : ChestPopup.View.ROLL);
                // Where it was: beside the preview for a container, in the middle for a table on its own.
                placePopup();
                layoutPopupButtons();
            }
            case TOOLS_CONTAINER -> openTools(PackToolsScreen.Section.CHESTS, chestRef(open));
            case TOOLS_TABLE -> {
                ResourceLocation table = ResourceLocation.tryParse(open.table);
                if (table != null) {
                    openTools(PackToolsScreen.Section.LOOT, table);
                }
            }
        }
    }

    private ToolsChests.ChestRef chestRef(ChestPopup open) {
        return ToolsChests.ChestRef.of(selected.id(), seed, open.container, open.title, open.size);
    }

    /** Starts picking a chest or a spawner to change, from Pack tools. */
    void startPicking(Picking what) {
        picking = what;
        closePopup();
        foundIn = null;
    }

    public boolean picking() {
        return picking != Picking.NONE;
    }

    /** Starts picking a chest, as Pack tools' button does. For the screenshot harness. */
    public void pickForTools() {
        startPicking(Picking.CHEST);
    }

    /** The spawner at a spot in the preview, or null. */
    private StructureSnapshot.Spawner spawnerAt(StructureViewport.Hit hit) {
        if (result == null || !result.succeeded() || hit.entity() != null) {
            return null;
        }
        for (StructureSnapshot.Spawner spawner : result.snapshot().spawners()) {
            if (spawner.pos().equals(hit.pos())) {
                return spawner;
            }
        }
        return null;
    }

    /** Opens a spawner in Pack tools, where its mob can be changed. */
    private void openSpawner(StructureSnapshot.Spawner spawner) {
        if (selected == null) {
            return;
        }
        openTools(PackToolsScreen.Section.SPAWNERS, ToolsSpawners.SpawnerRef.of(selected.id(), seed, spawner));
        picking = Picking.NONE;
    }

    /** The structure on show, or null. */
    ResourceLocation selectedStructure() {
        return selected == null ? null : selected.id();
    }

    /** Opens a structure from Pack tools, on a tab and with a table picked if given. */
    void showFromTools(ResourceLocation structure, InfoPanel.Tab tab, String table) {
        long layout = selected != null && selected.id().equals(structure) ? seed : defaultSeed(structure);
        restorePlace(new BrowserLayer(structure, layout, tab, table, null, null, Component.empty(), Picking.NONE), true);
    }

    /** Opens a container from Pack tools, in the layout it was picked from, with its popup open. */
    void showContainerFromTools(ToolsChests.ChestRef ref) {
        Popup open = ref.pos() == null ? null : new ContainerPopup(ref.pos(), ref.entity(), ChestPopup.View.ROLL, ref.title(), false);
        restorePlace(new BrowserLayer(ref.structure(), ref.seed(), InfoPanel.Tab.LOOT, ref.table(), null, open, Component.empty(), Picking.NONE), true);
    }

    /**
     * Where a tab or link on the open popup is, for the screenshot harness, or null: "roll", "odds",
     * "tools_container" or "tools_table" on a chest's, "tools" on a spawner's.
     */
    public int[] popupLink(String name) {
        if (popup != null) {
            for (ChestPopup.Action action : ChestPopup.Action.values()) {
                if (action.name().equalsIgnoreCase(name)) {
                    return popup.linkCentre(action);
                }
            }
        }
        if (spawnerPopup != null) {
            for (SpawnerPopup.Action action : SpawnerPopup.Action.values()) {
                if (action.name().equalsIgnoreCase(name)) {
                    return spawnerPopup.linkCentre(action);
                }
            }
        }
        return null;
    }

    /** Says something in the preview's top line, where locate results go, for a few seconds. */
    public void showMessage(Component text) {
        showMessage(text, true, false);
    }

    /**
     * Says something in the preview's top line, green if it went well and red if not. A change that
     * only applies from the next /reload stays until then, so it can't be missed.
     */
    public void showMessage(Component text, boolean good, boolean untilReload) {
        locateText = text;
        locateFound = good;
        locateUntil = untilReload ? Long.MAX_VALUE - 1 : System.currentTimeMillis() + LOCATE_FAILURE_MILLIS;
        messageUntilReload = untilReload;
    }

    /** Whether a reply from the server says it did what was asked, by the message's key. */
    static boolean replyIs(Component reply, String keyEnd) {
        return reply != null && reply.getContents() instanceof TranslatableContents t && t.getKey().endsWith(keyEnd);
    }

    /** The loot table editor, from the Loot tab's Edit link. Coming back returns here. */
    private void openEditor(String table) {
        ResourceLocation id = ResourceLocation.tryParse(table);
        if (id != null) {
            Nav.remember();
            minecraft.setScreen(new LootEditorScreen(this, id, StructureNames.lootTable(table)));
        }
    }

    /**
     * The editor on a new table, named after the structure on show. The id can be changed before
     * it's saved; if a table by that name was already made, it opens that one to edit instead.
     */
    public void openNewTable() {
        String path = selected == null ? "custom" : selected.id().getPath();
        ResourceLocation id = Ids.of(JustEnoughStructures.MOD_ID, "chests/" + path.substring(path.lastIndexOf('/') + 1));
        Nav.remember();
        minecraft.setScreen(new LootEditorScreen(this, id, StructureNames.lootTable(id.toString())));
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
    }

    public Optional<int[]> structureRow(ResourceLocation id) {
        return list.rowCentre(id);
    }

    /** Where the details panel last drew a row that tints the preview, such as "block:minecraft:chest". */
    public Optional<int[]> highlightRow(String key) {
        return Optional.ofNullable(info.highlightRow(key));
    }

    public boolean canGoBack() {
        return Nav.canGoBack();
    }

    public boolean canGoForward() {
        return Nav.canGoForward();
    }

    public int[] backButton() {
        return navBar.backCentre();
    }

    public int[] forwardButton() {
        return navBar.forwardCentre();
    }

    public Optional<int[]> foundInRow(int index) {
        return foundIn == null ? Optional.empty() : foundIn.rowCentre(index);
    }

    public Optional<int[]> favouriteStar(ResourceLocation id) {
        return list.starCentre(id);
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
            case "markers" -> markersButton;
            case "ground" -> groundButton;
            case "maximise" -> maximiseButton;
            case "locate" -> locateButton;
            case "compass" -> compassButton;
            case "reroll_loot" -> chestReroll;
            case "next" -> chestNext;
            case "done" -> chestClose;
            case "tools" -> toolsButton;
            default -> throw new IllegalArgumentException(name);
        };
        return new int[]{b.getX() + b.getWidth() / 2, b.getY() + b.getHeight() / 2};
    }

    public int[] tab(String name) {
        return info.tabCentre(InfoPanel.Tab.valueOf(name.toUpperCase(Locale.ROOT)));
    }

    /** Where the marker for a mob is drawn, if it's on screen. For the screenshot harness. */
    public Optional<int[]> marker(Entity mob) {
        for (Marker m : markerRects) {
            if (m.marks(mob)) {
                return Optional.of(new int[]{Math.round(m.x() + m.size() / 2f), Math.round(m.y() + m.size() / 2f)});
            }
        }
        return Optional.empty();
    }

    /** Where the marker for the first container using {@code table} is drawn, if it's on screen. */
    public Optional<int[]> marker(String table) {
        for (Marker m : markerRects) {
            if (m.container() != null && table.equals(m.container().lootTable())) {
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

    public boolean spawnerPopupOpen() {
        return spawnerPopup != null;
    }

    public boolean mobPopupOpen() {
        return mobPopup != null;
    }

    /** The mobs the layout on show places, for the screenshot harness. */
    public List<Entity> placedMobs() {
        return view == null ? List.of() : List.copyOf(view.entities());
    }

    /** Shows only the bottom {@code shown} layers, as dragging the slider there would. */
    public void setLayers(int shown) {
        if (view != null) {
            view.setSliceY(shown);
            updateSlider();
        }
    }

    public int sliceLayers() {
        return view == null ? 0 : view.size().getY();
    }

    private void closePopup() {
        popup = null;
        spawnerPopup = null;
        mobPopup = null;
        layoutPopupButtons();
        if (tourFrom != null) {
            // Back to the zoom and centre from before, but keeping whatever angle the player turned to.
            StructureViewport.Camera now = viewport.camera();
            StructureViewport.Camera back = new StructureViewport.Camera(now.yaw(), now.pitch(), tourFrom.distance(),
                    tourFrom.focusX(), tourFrom.focusY(), tourFrom.focusZ());
            if (refitAfterTour) {
                // The zoom from before was for another size of preview: fit to this one instead.
                viewport.setCamera(back);
                viewport.refit();
            } else {
                viewport.glideTo(back);
            }
            tourFrom = null;
        }
        // The layers back as they were too, when the tour cut it open to show something inside.
        if (tourSlice >= 0 && view != null && view.sliceY() != tourSlice) {
            view.setSliceY(tourSlice);
            updateSlider();
        }
        tourSlice = -1;
        refitAfterTour = false;
    }

    private void layoutPopupButtons() {
        SidePopup side = side();
        boolean show = popup != null || side != null;
        for (Button b : new Button[]{chestReroll, chestPrev, chestNext, chestClose}) {
            if (b != null) {
                b.visible = show;
            }
        }
        if (!show || chestReroll == null) {
            return;
        }
        if (side != null) {
            // Nothing to roll: the arrows take Roll again's place.
            int rowY = side.buttonRowY(font);
            chestReroll.visible = false;
            chestPrev.setX(side.x + 6);
            chestPrev.setY(rowY);
            chestNext.setX(side.x + 28);
            chestNext.setY(rowY);
            chestPrev.active = chestNext.active = side.count > 1;
            chestClose.setX(side.x + SidePopup.WIDTH - 50);
            chestClose.setY(rowY);
            return;
        }
        int rowY = popup.buttonRowY(font);
        chestReroll.setX(popup.x + 6);
        chestReroll.setY(rowY);
        chestReroll.active = popup.table != null;
        // A table on its own has no others like it to step through, so Roll again takes their room.
        boolean steps = popup.container != null;
        chestReroll.setWidth(steps ? 70 : ChestPopup.WIDTH - 12 - 48);
        chestPrev.visible = chestNext.visible = steps;
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
                    ? Regs.value(BuiltInRegistries.ENTITY_TYPE, id).getDescription()
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
            if (Nbt.getInt(tag, "x") == container.pos().getX() && Nbt.getInt(tag, "y") == container.pos().getY() && Nbt.getInt(tag, "z") == container.pos().getZ()) {
                ListTag list = Nbt.list(tag, "Items", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag item = Nbt.compound(list, i);
                    int slot = Nbt.getByte(item, "Slot") & 255;
                    if (slot < items.size()) {
                        //? if >=26.1 {
                        /*items.set(slot, ItemStack.OPTIONAL_CODEC.parse(minecraft.level.registryAccess().createSerializationContext(NbtOps.INSTANCE), item)
                                .result().orElse(ItemStack.EMPTY));
                        *///?} else if >=1.21 {
                        /*items.set(slot, ItemStack.parseOptional(minecraft.level.registryAccess(), item));
                        *///?} else {
                        items.set(slot, ItemStack.of(item));
                        //?}
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
        // Turns slowly while the mouse is away, and stops where it is once the mouse is over it.
        boolean overPreview = mouseX >= viewX && mouseX < viewX + viewW && mouseY >= viewY && mouseY < viewY + viewH;
        if (ClientState.spin && view != null && !overPreview && !(pressedInViewport && dragged)) {
            viewport.spin(seconds * 12f);
        }

        Gui.beginClipped();
        backdrop(g);
        if (sides) {
            Gui.panel(g, listX, TOP, listW, height - TOP - PAD);
            Gui.searchBox(g, listX + 6, searchY, listW - 12, 20);
            Gui.panel(g, infoX, TOP + TABS, infoW, height - TOP - PAD - TABS);
        }
        Gui.panel(g, centreX, TOP, centreW, height - TOP - PAD);

        SidePopup side = side();
        boolean popupOpen = popup != null || side != null;
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
        // The preview still answers the mouse beside a container's popup, so another container can be picked.
        boolean beside = foundIn == null && (side != null ? !side.contains(mouseX, mouseY, font)
                : popup != null && popup.container != null && !popup.contains(mouseX, mouseY, font));
        StructureViewport.Hit hover = renderViewport(g, beside ? mouseX : mx, beside ? mouseY : my, partialTick);
        if (sides) {
            info.render(g, mx, my);
        }

        for (Button b : new Button[]{chestReroll, chestPrev, chestNext, chestClose}) {
            b.visible = false;
        }
        super.render(g, mx, my, partialTick);

        ItemStack popupHover = ItemStack.EMPTY;
        if (popupOpen) {
            // Only what's cut short in the popup counts now: the rest is behind it.
            Gui.beginClipped();
            Gui.push(g);
            Gui.lift(g, 400);
            if ((side != null || popup.container != null) && view != null) {
                // The preview stays clear beside a container's, spawner's or mob's popup: it's where they're shown.
                dimAround(g, viewX, viewY, viewW, viewH);
            } else {
                g.fill(0, 0, width, height, 0x88000000);
            }
            popupHover = side != null ? side.render(g, font, mouseX, mouseY) : popup.render(g, font, mouseX, mouseY);
            layoutPopupButtons();
            for (Button b : new Button[]{chestReroll, chestPrev, chestNext, chestClose}) {
                if (b.visible) {
                    Gui.render(g, b, mouseX, mouseY, partialTick);
                }
            }
            Gui.pop(g);
        }
        popupHovered = popupHover;
        if (foundIn != null) {
            Gui.beginClipped();
            Gui.push(g);
            Gui.lift(g, 400);
            g.fill(0, 0, width, height, 0x88000000);
            foundIn.render(g, font, mouseX, mouseY);
            Gui.pop(g);
        }
        navBar.render(g, font, mouseX, mouseY, partialTick);

        if (foundIn != null) {
            Gui.push(g);
            Gui.lift(g, 600);
            Gui.clippedTooltip(g, font, mouseX, mouseY);
            Gui.pop(g);
            return;
        }
        // Above the popups, whose items are drawn a long way towards the viewer.
        Gui.push(g);
        Gui.lift(g, 600);
        renderTooltips(g, mouseX, mouseY, popupOpen, popupHover, hover);
        Gui.pop(g);
    }

    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY, boolean popupOpen, ItemStack popupHover, StructureViewport.Hit hover) {
        if (popupOpen) {
            List<Component> tip = side() != null ? side().hoveredTip : popup.hoveredTip;
            if (!popupHover.isEmpty()) {
                g.renderComponentTooltip(font, itemTooltip(popupHover, popup != null ? popup.hoveredExtra : List.of()), mouseX, mouseY);
            } else if (tip != null) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                tip.forEach(line -> lines.addAll(font.split(line, 220)));
                g.renderTooltip(font, lines, mouseX, mouseY);
            } else if (Gui.clippedAt(mouseX, mouseY) != null) {
                Gui.clippedTooltip(g, font, mouseX, mouseY);
            } else if (hover != null) {
                // The preview beside a container's popup, where another container can be clicked.
                g.renderComponentTooltip(font, hoverLines(hover), mouseX, mouseY);
            }
        } else if (clippedHeaderLine(mouseX, mouseY) != null) {
            g.renderTooltip(font, font.split(clippedHeaderLine(mouseX, mouseY), 240), mouseX, mouseY);
        } else if (!headerTooltip(mouseX, mouseY).isEmpty()) {
            g.renderComponentTooltip(font, headerTooltip(mouseX, mouseY), mouseX, mouseY);
        } else if (sides && !info.hoveredStack().isEmpty()) {
            g.renderComponentTooltip(font, itemTooltip(info.hoveredStack(), info.hoveredExtra()), mouseX, mouseY);
        } else if (sides && !info.hoveredText().isEmpty()) {
            g.renderComponentTooltip(font, info.hoveredText(), mouseX, mouseY);
        } else if (sides && !list.tooltip().isEmpty()) {
            g.renderComponentTooltip(font, list.tooltip(), mouseX, mouseY);
        } else if (Gui.clippedAt(mouseX, mouseY) != null) {
            Gui.clippedTooltip(g, font, mouseX, mouseY);
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

    /** The title rows: the mod's name, then the structure's, each in white on a dark band. */
    private void renderHeader(GuiGraphics g) {
        int bandX = centreX + 19;
        int bandW = centreW - 38;
        String top = selected == null ? title.getString() : StructureNames.mod(selected.id().getNamespace());
        String bottom = selected == null ? "" : StructureNames.structure(selected.id());
        Gui.band(g, font, top, bandX, TOP + 4, bandW, 13);
        Gui.band(g, font, bottom, bandX, TOP + 19, bandW, 13);
    }

    /** The full name when a title row had to cut it short, for its tooltip. */
    private List<Component> headerTooltip(int mouseX, int mouseY) {
        if (selected == null || mouseX < centreX + 19 || mouseX >= centreX + centreW - 19) {
            return List.of();
        }
        String name = StructureNames.structure(selected.id());
        String mod = StructureNames.mod(selected.id().getNamespace());
        if (mouseY >= TOP + 4 && mouseY < TOP + 17 && font.width(mod) > centreW - 42) {
            return List.of(Component.literal(mod));
        }
        if (mouseY >= TOP + 19 && mouseY < TOP + 32 && font.width(name) > centreW - 42) {
            return List.of(Component.literal(name));
        }
        return List.of();
    }

    /** Previous or next structure in the list, or the first of the previous or next mod. */
    private void step(int dir, boolean byMod) {
        if (selected != null) {
            list.step(selected.id(), dir, byMod).ifPresent(e -> {
                Nav.remember();
                select(e, defaultSeed(e.id()));
            });
        }
    }

    private Component headerOverflow;
    private int headerRoom;
    private int headerLines = 1;

    /** The locate line under the mouse when it's cut short, to show in full as a tooltip. */
    private Component clippedHeaderLine(int mouseX, int mouseY) {
        boolean over = mouseX >= headerTextX() && mouseX < headerTextX() + headerRoom && mouseY >= viewY + 5
                && mouseY < viewY + 6 + headerLines * (font.lineHeight + 1);
        return over ? headerOverflow : null;
    }

    /** Where the buttons over the preview's top right corner start, counting only those showing. */
    private int overlayLeft() {
        int left = maximiseButton.getX();
        for (Button b : new Button[]{locateButton, compassButton}) {
            if (b != null && b.visible) {
                left = Math.min(left, b.getX());
            }
        }
        return left;
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
            lines.add(result.reason().copy().withStyle(ChatFormatting.GRAY));
            for (Component attempt : result.attempts()) {
                lines.add(attempt.copy().withStyle(ChatFormatting.DARK_GRAY));
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
        viewport.render(g, viewX, viewY, viewW, viewH, partialTick, outlines, highlight());
        if (!viewport.meshing() && !Thumbnails.has(selected.id())) {
            TextureTarget thumbnail = viewport.renderThumbnail(Thumbnails.renderSize());
            if (thumbnail != null) {
                Thumbnails.put(selected.id(), thumbnail);
            }
        }

        StructureSnapshot s = result.snapshot();
        Gui.scissor(g, viewX, viewY, viewX + viewW, viewY + viewH);
        if (markersShown()) {
            placeMarkers(s);
            for (Marker m : markerRects) {
                // Lit up like one under the mouse while a row about what it marks is hovered, as it hides the block.
                boolean over = m.contains(mouseX, mouseY) || highlight != null && m.spots().stream().anyMatch(spot -> highlight.contains(spot.pos()));
                boolean showing = popup != null && popup.container != null && !popup.overview() && m.containers().contains(popup.container)
                        || spawnerPopup != null && !spawnerPopup.overview() && m.spawner() != null && m.marks(spawnerPopup.spawner.pos())
                        || mobPopup != null && !mobPopup.overview() && m.marks(mobPopup.mob);
                drawMarker(g, m.x(), m.y(), m.size(), markerIcon(s, m), showing ? SHOWING : over ? LIT : 0xFF000000, m.spots().size());
            }
        }

        markHighlighted(g);
        markShown(g, s);

        // What locate found, or why it couldn't. Failures fade after a while. It shares the top of
        // the preview with the corner buttons, so it stops short of them.
        if (locateText != null && Util.getMillis() > locateUntil) {
            locateText = null;
        }
        headerOverflow = null;
        headerLines = 1;
        if (locateText != null && picking == Picking.NONE) {
            int room = Math.max(0, overlayLeft() - 4 - headerTextX());
            headerRoom = room;
            int colour = locateFound ? 0xFF9CE89C : locateUntil == Long.MAX_VALUE ? 0xFFE0E0E0 : 0xFFFF9C9C;
            List<FormattedCharSequence> lines = room > 20 ? font.split(locateText, room) : List.of();
            int shown = Math.min(3, lines.size());
            for (int i = 0; i < shown; i++) {
                g.drawString(font, lines.get(i), headerTextX(), viewY + 6 + i * (font.lineHeight + 1), colour, true);
            }
            headerLines = Math.max(1, shown);
            headerOverflow = lines.size() > shown ? locateText : null;
        }
        if (viewport.meshing()) {
            int barW = Math.min(120, viewW - 20);
            int bx = viewX + (viewW - barW) / 2;
            int by = viewY + viewH - 12;
            String building = Component.translatable("screen.justenoughstructures.building").getString();
            Gui.small(g, font, building, viewX + (viewW - Gui.smallWidth(font, building)) / 2, by - 9, 0xFFE0E0E0);
            g.fill(bx - 1, by - 1, bx + barW + 1, by + 5, 0xFF000000);
            g.fill(bx, by, bx + (int) (barW * viewport.meshProgress()), by + 4, 0xFF7FD06A);
        } else if (picking != Picking.NONE) {
            pickingStrip(g, mouseX, mouseY);
        } else {
            Gui.fineClipped(g, font, Component.translatable("screen.justenoughstructures.controls").getString(),
                    viewX + 6, viewY + viewH - 3 - Gui.fineLine(font), viewW - 12, 0xFFE0E0E0);
        }
        Gui.endScissor(g);

        for (Marker m : markerRects) {
            if (m.contains(mouseX, mouseY)) {
                BlockPos pos = m.spots().get(0).pos();
                return new StructureViewport.Hit(pos, view.rawState(pos.getX(), pos.getY(), pos.getZ()), m.spawner() == null ? m.mob() : null);
            }
        }
        return hover;
    }

    /** Along the bottom of the preview while picking a chest or spawner: what to do, and a way out. */
    private void pickingStrip(GuiGraphics g, int mouseX, int mouseY) {
        int top = viewY + viewH - 18;
        g.fill(viewX, top, viewX + viewW, viewY + viewH, 0xFF6AB0E9);
        Component cancel = Component.translatable("gui.cancel");
        int cancelW = font.width(cancel) + 10;
        int cancelX = viewX + viewW - cancelW - 2;
        boolean over = mouseX >= cancelX && mouseX < cancelX + cancelW && mouseY >= top + 2 && mouseY < top + 16;
        Gui.buttonBackground(g, cancelX, top + 2, cancelW, 14, over ? 2 : 1);
        g.drawString(font, cancel, cancelX + 5, top + 5, 0xFFFFFFFF, true);
        pickCancel = new int[]{cancelX, top + 2, cancelW, 14};
        String text = Component.translatable(picking == Picking.CHEST ? "screen.justenoughstructures.tools.picking"
                : "screen.justenoughstructures.tools.picking_spawner").getString();
        Gui.drawClipped(g, font, text, viewX + 5, top + 5, cancelX - viewX - 10, 0xFF04263F, false);
    }

    /** Stops picking, back to Pack tools' chests or spawners. */
    private void cancelPicking() {
        Nav.remember();
        PackToolsScreen.Section section = picking == Picking.SPAWNER ? PackToolsScreen.Section.SPAWNERS : PackToolsScreen.Section.CHESTS;
        picking = Picking.NONE;
        minecraft.setScreen(new PackToolsScreen(this, section, null));
    }

    /**
     * Works out where each marker goes. Markers are all one size, set by the zoom, and things close
     * enough in the structure for their markers to overlap share one marker with a count. Neither
     * changes while the preview turns, only when you zoom, so markers don't grow, shrink or split
     * apart as it spins. The nearest are drawn last, on top.
     */
    private void placeMarkers(StructureSnapshot s) {
        float perBlock = Math.max(0.01f, viewport.pixelsPerBlock());
        int chestSize = markerSize();
        int smallSize = Math.round(Math.max(6f, Math.min(10f, perBlock * 0.8f)));
        double reach = chestSize * 1.5 / perBlock;
        List<List<Spot>> groups = markerGroups(s, markerKind(), reach);

        record Placed(Marker marker, float depth) {
        }
        List<Placed> placed = new ArrayList<>();
        for (List<Spot> members : groups) {
            double x = 0;
            double z = 0;
            double top = -Double.MAX_VALUE;
            boolean small = true;
            for (Spot spot : members) {
                if (spot.what() instanceof Entity mob) {
                    // Over its head, rather than at its feet.
                    x += mob.getX();
                    z += mob.getZ();
                    top = Math.max(top, mob.getY() + mob.getBbHeight() + 0.2);
                    small = false;
                } else {
                    x += spot.pos().getX() + 0.5;
                    z += spot.pos().getZ() + 0.5;
                    top = Math.max(top, spot.pos().getY() + 1.1);
                    // Suspicious sand and the like, which hold a single item, get the smaller marker.
                    small &= spot.what() instanceof StructureSnapshot.Container c && !c.entity() && !(view.blockEntities().get(c.pos()) instanceof Container);
                }
            }
            Optional<float[]> at = viewport.project(x / members.size(), top, z / members.size());
            if (at.isEmpty()) {
                continue;
            }
            int size = small ? smallSize : chestSize;
            placed.add(new Placed(new Marker(at.get()[0] - size / 2f, at.get()[1] - size, size, members), at.get()[2]));
        }
        placed.sort(Comparator.comparingDouble(pl -> -pl.depth()));
        // None over the buttons in the preview's top right corner, which they'd hide and take clicks from.
        int cornerLeft = overlayLeft() - 1;
        int cornerBottom = viewY + 23;
        for (Placed pl : placed) {
            Marker m = pl.marker();
            if (m.x() + m.size() < cornerLeft || m.y() > cornerBottom) {
                markerRects.add(m);
            }
        }
    }

    private StructureSnapshot groupedFor;
    private MarkerKind groupedKind;
    private int groupedSlice;
    private double groupedReach;
    private List<List<Spot>> grouped = List.of();

    /** Everything a kind of marker marks in the layout on show. */
    private List<Spot> spots(StructureSnapshot s, MarkerKind kind) {
        List<Spot> out = new ArrayList<>();
        if (kind == MarkerKind.CONTAINERS) {
            s.containers().forEach(c -> out.add(new Spot(c.pos(), c)));
        } else {
            s.spawners().forEach(spawner -> out.add(new Spot(spawner.pos(), spawner)));
            for (Entity entity : view.entities()) {
                if (entity instanceof Mob) {
                    out.add(new Spot(mobBlock(entity), entity));
                }
            }
        }
        return out;
    }

    /**
     * Things close enough in the structure to share a marker. Only zooming, the layer slider or
     * another kind of marker changes the answer, so it's worked out again only then, not every frame.
     */
    private List<List<Spot>> markerGroups(StructureSnapshot s, MarkerKind kind, double reach) {
        if (s == groupedFor && kind == groupedKind && view.sliceY() == groupedSlice && reach == groupedReach) {
            return grouped;
        }
        List<Spot> shown = new ArrayList<>();
        for (Spot spot : spots(s, kind)) {
            if (spot.pos().getY() < view.sliceY()) {
                shown.add(spot);
            }
        }
        // Sorted so a group always has the same thing first, whichever way it's facing.
        shown.sort(Comparator.comparing(Spot::pos));

        // Join things closer than about a marker and a half, and anything joined to those, so
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
        Map<Integer, List<Spot>> groups = new LinkedHashMap<>();
        for (int i = 0; i < shown.size(); i++) {
            groups.computeIfAbsent(root(group, i), k -> new ArrayList<>()).add(shown.get(i));
        }
        grouped = List.copyOf(groups.values());
        groupedFor = s;
        groupedKind = kind;
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
        boolean details = Gui.advanced();
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
        }
        StructureSnapshot.Spawner spawner = container == null ? spawnerAt(hit) : null;
        if (spawner != null) {
            lines.add(spawns(spawner).withStyle(ChatFormatting.AQUA));
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

    /** "Spawns Zombie", or what a spawner with no mob or a mix of them makes. */
    private static MutableComponent spawns(StructureSnapshot.Spawner spawner) {
        if (spawner.mob().isEmpty()) {
            return Component.translatable("screen.justenoughstructures.hover_spawns_nothing");
        }
        Component mob = StructureNames.mob(spawner.mob());
        return spawner.others() > 0 ? Component.translatable("screen.justenoughstructures.hover_spawns_mix", mob, spawner.others())
                : Component.translatable("screen.justenoughstructures.hover_spawns", mob);
    }

    /**
     * Opens what a marker stands for: its container, its spawner or its mob. One standing for several
     * opens all of their kind. Returns whether one opened.
     */
    private boolean openMarker(Marker m) {
        if (m.container() != null) {
            List<StructureSnapshot.Container> containers = m.containers();
            openContainer(containers.get(0), containers.size() > 1);
            return true;
        }
        if (m.spawner() != null) {
            openSpawnerPopup(m.spawner(), false);
            return true;
        }
        if (m.mob() != null) {
            List<Entity> mobs = m.mobs();
            openMobPopup(mobs.get(0), mobs.size() > 1);
            return true;
        }
        return false;
    }

    /** What a marker shows: its container's own item, a spawner, or the mob's egg or item. */
    private ItemStack markerIcon(StructureSnapshot s, Marker m) {
        Spot first = m.spots().get(0);
        if (first.what() instanceof StructureSnapshot.Container c) {
            return InfoPanel.containerIcon(s, c);
        }
        if (first.what() instanceof Entity mob) {
            return mobIcon(mob);
        }
        return MOB_MARKERS_ICON;
    }

    /** A mob's spawn egg, or the item that stands for it. */
    private static ItemStack mobIcon(Entity mob) {
        ItemStack icon = InfoPanel.entityIcon(mob.getType());
        return icon.isEmpty() ? MOB_MARKERS_ICON : icon;
    }

    private MarkerKind markerKind() {
        if (picking != Picking.NONE) {
            return picking == Picking.SPAWNER ? MarkerKind.MOBS : MarkerKind.CONTAINERS;
        }
        if (side() != null) {
            return MarkerKind.MOBS;
        }
        if (popup != null && popup.container != null) {
            return MarkerKind.CONTAINERS;
        }
        return info != null && info.tab() == InfoPanel.Tab.ENTITIES ? MarkerKind.MOBS : MarkerKind.CONTAINERS;
    }

    /** Markers show when they're on, or always while picking something for Pack tools, but not for loot kept secret. */
    private boolean markersShown() {
        return picking != Picking.NONE || ClientState.markers && !markersSecret();
    }

    /** Container markers mean nothing for a structure whose loot is kept secret. */
    private boolean markersSecret() {
        return lootSecret() && markerKind() == MarkerKind.CONTAINERS;
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

    /**
     * Beside a container's, spawner's or mob's popup, opens the container, spawner or mob under the
     * mouse in the preview instead, by its marker or by itself. False if there's none.
     */
    private boolean openClicked(double mouseX, double mouseY) {
        boolean beside = side() != null || popup != null && popup.container != null;
        if (!beside || view == null || !viewport.contains(mouseX, mouseY)) {
            return false;
        }
        for (Marker m : markerRects) {
            if (m.contains(mouseX, mouseY) && openMarker(m)) {
                return true;
            }
        }
        return openPicked(viewport.pick(mouseX, mouseY));
    }

    /** Opens the container, spawner or mob the mouse is on in the preview. False if it's on none of them. */
    private boolean openPicked(Optional<StructureViewport.Hit> hit) {
        StructureSnapshot.Container clicked = hit.map(this::containerAt).orElse(null);
        if (clicked != null) {
            openContainer(clicked);
            return true;
        }
        StructureSnapshot.Spawner spawner = hit.map(this::spawnerAt).orElse(null);
        if (spawner != null) {
            openSpawnerPopup(spawner, false);
            return true;
        }
        Entity mob = hit.map(StructureViewport.Hit::entity).orElse(null);
        if (mob != null) {
            openMobPopup(mob, false);
            return true;
        }
        return false;
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
        if (navBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (foundIn != null) {
            if (foundIn.contains(mouseX, mouseY)) {
                foundIn.click(mouseX, mouseY).ifPresent(this::pickFromFoundIn);
            } else {
                foundIn = null;
            }
            return true;
        }
        SidePopup side = side();
        if (picking != Picking.NONE && popup == null && side == null && pickCancel != null && button == InputConstants.MOUSE_BUTTON_LEFT && mouseX >= pickCancel[0] && mouseX < pickCancel[0] + pickCancel[2]
                && mouseY >= pickCancel[1] && mouseY < pickCancel[1] + pickCancel[3]) {
            cancelPicking();
            return true;
        }
        if (side != null) {
            for (Button b : new Button[]{chestPrev, chestNext, chestClose}) {
                if (b.visible && b.isMouseOver(mouseX, mouseY)) {
                    return Gui.click(b, mouseX, mouseY, button);
                }
            }
            SpawnerPopup.Action action = spawnerPopup == null ? null : spawnerPopup.actionAt(mouseX, mouseY);
            if (action != null) {
                spawnerAction(spawnerPopup, action);
                return true;
            }
            if (!side.contains(mouseX, mouseY, font)) {
                if (view != null && viewport.contains(mouseX, mouseY)) {
                    // As beside a container's popup: a drag turns around the spawner or mob, a click
                    // opens what's under it or closes the popup.
                    pressedInViewport = true;
                    dragged = false;
                } else {
                    closePopup();
                }
            } else if (!popupHovered.isEmpty()) {
                openFoundIn(popupHovered);
            }
            return true;
        }
        if (popup != null) {
            for (Button b : new Button[]{chestReroll, chestPrev, chestNext, chestClose}) {
                if (b.visible && b.isMouseOver(mouseX, mouseY)) {
                    return Gui.click(b, mouseX, mouseY, button);
                }
            }
            ChestPopup.Action action = popup.actionAt(mouseX, mouseY);
            if (action != null) {
                popupAction(popup, action);
                return true;
            }
            if (!popup.contains(mouseX, mouseY, font)) {
                if (popup.container != null && view != null && viewport.contains(mouseX, mouseY)) {
                    // In the preview beside a container's popup: a drag turns the camera around the
                    // container, and a click opens the container under it or closes the popup.
                    pressedInViewport = true;
                    dragged = false;
                } else {
                    closePopup();
                }
            } else if (!popupHovered.isEmpty()) {
                openFoundIn(popupHovered);
            }
            return true;
        }
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            for (Marker m : markerRects) {
                if (m.contains(mouseX, mouseY) && openMarker(m)) {
                    return true;
                }
            }
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            Optional<StructureList.Pick> clicked = sides ? list.click(mouseX, mouseY) : Optional.empty();
            if (clicked.isPresent()) {
                StructureList.Pick pick = clicked.get();
                if (pick.entry() != null && pick.entry() != selected) {
                    Nav.remember();
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
        if (pressedInViewport) {
            dragged |= Math.abs(dx) + Math.abs(dy) > 0.5;
            if (popup != null || side() != null) {
                // Only turning: moving would take the container out of the middle.
                viewport.rotate(dx, dy);
            } else if (button == InputConstants.MOUSE_BUTTON_RIGHT || button == InputConstants.MOUSE_BUTTON_MIDDLE || hasShiftDown()) {
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
            if (popup != null || side() != null) {
                if (!dragged && (button != InputConstants.MOUSE_BUTTON_LEFT || !openClicked(mouseX, mouseY))) {
                    closePopup();
                }
            } else if (!dragged && button == InputConstants.MOUSE_BUTTON_LEFT) {
                openPicked(viewport.pick(mouseX, mouseY));
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
        if (side() != null) {
            side().scroll(mouseX, mouseY, delta);
            return true;
        }
        if (popup != null) {
            popup.scroll(mouseX, mouseY, delta);
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
        if (key == InputConstants.KEY_ESCAPE && foundIn != null) {
            foundIn = null;
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE && (popup != null || side() != null)) {
            closePopup();
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE && picking != Picking.NONE) {
            picking = Picking.NONE;
            return true;
        }
        if (key == InputConstants.KEY_U && !search.isFocused()) {
            ItemStack hovered = popup != null || side() != null ? popupHovered : info.hoveredStack();
            if (!hovered.isEmpty()) {
                openFoundIn(hovered);
                return true;
            }
        }
        if (search.isFocused()) {
            if (key == InputConstants.KEY_RETURN) {
                list.firstShown().filter(e -> e != selected).ifPresent(e -> {
                    Nav.remember();
                    select(e, defaultSeed(e.id()));
                });
                return true;
            }
            return super.keyPressed(key, scanCode, modifiers);
        }
        if (key == InputConstants.KEY_R && popup == null && side() == null) {
            reroll();
            return true;
        }
        if (navBar.keyPressed(key, modifiers)) {
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
        clearHighlight();
        // A big preview can be hundreds of megabytes; don't keep it around once the screen's gone.
        // Coming back to it, from the loot editor say, fetches it again.
        dropped = dropped || result != null;
        view = null;
        result = null;
        groupedFor = null;
        grouped = List.of();
        super.removed();
    }


    private Component groundLabel() {
        return Component.translatable(ClientState.ground ? "screen.justenoughstructures.ground_on" : "screen.justenoughstructures.ground_off");
    }

    private Component markersLabel() {
        if (markersSecret()) {
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

    /**
     * The button says what markers mark now, which changes with the tab; container markers mean
     * nothing for a structure whose loot is kept secret, so it says so instead.
     */
    private void updateMarkersButton() {
        String now = markersSecret() + "|" + markerKind();
        if (!now.equals(markersFor)) {
            markersFor = now;
            markersButton.setLabel(markersLabel());
        }
        markersButton.active = !markersSecret();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Pack tools' button at the end of the tabs: a wrench, and its name when there's room. */
    private static final class ToolsButton extends JesButton {
        ToolsButton(int x, int y, int width, OnPress onPress) {
            super(x, y, width, 18, Component.translatable("screen.justenoughstructures.tools.title"), onPress);
            setTooltip(Tooltip.create(Component.translatable("screen.justenoughstructures.tools.title")));
        }

        @Override
        public void renderString(GuiGraphics g, net.minecraft.client.gui.Font font, int colour) {
            boolean label = font.width(getMessage()) + 12 + 10 <= getWidth();
            int contentW = label ? 12 + 3 + font.width(getMessage()) : 12;
            int x = getX() + (getWidth() - contentW) / 2;
            Gui.blit(g, PackToolsScreen.WRENCH, x, getY() + 3, 0, 0, 12, 12, 12, 12);
            if (label) {
                g.drawString(font, getMessage(), x + 15, getY() + 5, colour, true);
            }
        }
    }
}
