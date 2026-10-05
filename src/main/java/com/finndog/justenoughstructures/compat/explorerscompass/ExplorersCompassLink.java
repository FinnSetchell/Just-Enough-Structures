package com.finndog.justenoughstructures.compat.explorerscompass;

// Its Forge and NeoForge builds' names differ, see stonecutter.gradle.kts.
//~ compass_names

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.gui.ExplorersCompassScreen;
import com.chaosthedude.explorerscompass.gui.StructureSearchEntry;
import com.chaosthedude.explorerscompass.gui.StructureSearchList;
import com.chaosthedude.explorerscompass.gui.TransparentButton;
import com.chaosthedude.explorerscompass.items.ExplorersCompassItem;
import com.chaosthedude.explorerscompass.util.ItemUtils;
import com.chaosthedude.explorerscompass.util.StructureUtils;
import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.CompassLink;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import java.util.List;
import java.util.Map;
//? if forge && >=1.21 {
/*import java.lang.reflect.Field;
*///?}
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Explorer's Compass and the browser, one click apart. Its structure screen gets a Preview button
 * that opens the picked structure in the browser, and the browser gets a button that opens the
 * compass's screen on the structure being looked at. Searching stays with the compass, along with
 * its costs and whatever it's set up to refuse. Client only, and only loaded with the mod installed.
 */
public final class ExplorersCompassLink implements CompassLink {
    private static final ResourceLocation ICON = Ids.of(ExplorersCompass.MODID, "textures/item/explorerscompass_00.png");
    /** How long to wait for the compass's list from the server before picking from what it has. */
    private static final int SYNC_TICKS = 40;

    /** A structure to pick in the compass's screen, and the search text that brings it into view. */
    private record Pick(ResourceLocation structure, String search) {
    }

    private final Map<Screen, Button> previewButtons = new WeakHashMap<>();
    // Where each compass screen was when a preview was opened from it, to put back on return.
    private final Map<Screen, Pick> returning = new WeakHashMap<>();
    private Pick pending;
    private List<ResourceLocation> listWhenOpened;
    private int waited;

    @Override
    public ResourceLocation icon() {
        return ICON;
    }

    @Override
    public boolean holding(Player player) {
        return player != null && !ItemUtils.getHeldItem(player, ExplorersCompass.EXPLORERS_COMPASS_ITEM).isEmpty();
    }

    @Override
    public boolean open(Player player, ResourceLocation structure) {
        Minecraft minecraft = Minecraft.getInstance();
        // Right-clicking while sneaking clears the compass rather than opening it.
        if (!holding(player) || player.isShiftKeyDown() || minecraft.gameMode == null) {
            return false;
        }
        InteractionHand hand = player.getMainHandItem().is(ExplorersCompass.EXPLORERS_COMPASS_ITEM) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        pending = new Pick(structure, StructureUtils.getStructureName(structure));
        listWhenOpened = ExplorersCompass.allowedStructureIDs;
        waited = 0;
        // Exactly a right-click: opens the compass's screen and asks the server for what it may search for.
        minecraft.gameMode.useItem(player, hand);
        return true;
    }

    @Override
    public Component status(Player player, ResourceLocation structure) {
        if (player == null) {
            return null;
        }
        ItemStack stack = ItemUtils.getHeldItem(player, ExplorersCompass.EXPLORERS_COMPASS_ITEM);
        ExplorersCompassItem compass = ExplorersCompass.EXPLORERS_COMPASS_ITEM;
        ResourceLocation target;
        try {
            target = stack.isEmpty() ? null : compass.getStructureID(stack);
        } catch (RuntimeException e) {
            // A compass whose saved target doesn't read as an id points at nothing.
            target = null;
        }
        if (!structure.equals(target)) {
            return null;
        }
        return switch (compass.getState(stack)) {
            case SEARCHING -> Component.translatable("screen.justenoughstructures.compass_searching");
            case FOUND -> Component.translatable("screen.justenoughstructures.compass_found", String.format("%,d",
                    StructureUtils.getHorizontalDistanceToLocation(player, compass.getFoundStructureX(stack), compass.getFoundStructureZ(stack))));
            case NOT_FOUND -> Component.translatable("screen.justenoughstructures.compass_not_found");
            case INACTIVE -> null;
        };
    }

    /** After any screen is set up: gives the compass's screen its Preview button, and puts back where it was. */
    public void afterInit(Screen screen, Consumer<AbstractWidget> addWidget) {
        if (!(screen instanceof ExplorersCompassScreen compass) || !ClientRequests.serverSupported()) {
            return;
        }
        Button preview = new TransparentButton(10, screen.height - 55, 110, 20,
                Component.translatable("screen.justenoughstructures.compass_preview"), b -> preview(compass));
        preview.setTooltip(Tooltip.create(Component.translatable("screen.justenoughstructures.compass_preview_hint")));
        preview.active = false;
        addWidget.accept(preview);
        previewButtons.put(screen, preview);
        Pick back = returning.remove(screen);
        if (back != null) {
            pick(compass, back);
        }
    }

    /** Each tick a screen is open: keeps the Preview button in step, and picks what the browser asked for. */
    public void afterTick(Screen screen) {
        if (!(screen instanceof ExplorersCompassScreen compass)) {
            return;
        }
        Button preview = previewButtons.get(screen);
        StructureSearchList list = find(compass, StructureSearchList.class);
        if (preview != null) {
            preview.active = list != null && list.getSelected() != null;
        }
        if (pending != null) {
            // The screen rebuilds its list when the server's arrives, which would undo the pick,
            // so it waits for that.
            //? if forge && >=1.21 {
            /*// Its Forge build for 1.21 just swaps in the new list.
            boolean synced = ExplorersCompass.allowedStructureIDs != listWhenOpened;
            *///?} else {
            boolean synced = ExplorersCompass.allowedStructureIDs != listWhenOpened && !ExplorersCompass.synced;
            //?}
            if (synced || ++waited > SYNC_TICKS) {
                pick(compass, pending);
                pending = null;
            }
        }
    }

    private void preview(ExplorersCompassScreen compass) {
        StructureSearchList list = find(compass, StructureSearchList.class);
        StructureSearchEntry entry = list == null ? null : list.getSelected();
        ResourceLocation structure = entry == null ? null : structure(entry);
        if (structure == null) {
            return;
        }
        EditBox search = find(compass, EditBox.class);
        returning.put(compass, new Pick(structure, search == null ? "" : search.getValue()));
        JesScreen.startOn(structure);
        Minecraft.getInstance().setScreen(new JesScreen(compass));
    }

    /** Searches the compass's list so the structure is in view, then picks it. */
    private static void pick(ExplorersCompassScreen compass, Pick pick) {
        EditBox search = find(compass, EditBox.class);
        if (search != null) {
            search.setValue(pick.search());
            compass.processSearchTerm();
        }
        StructureSearchList list = find(compass, StructureSearchList.class);
        if (list == null) {
            return;
        }
        for (StructureSearchEntry entry : list.children()) {
            if (pick.structure().equals(structure(entry))) {
                list.setSelected(entry);
                return;
            }
        }
    }

    /** The structure a row of its list stands for, or null if it can't be told. */
    private static ResourceLocation structure(StructureSearchEntry entry) {
        //? if forge && >=1.21 {
        /*// Its Forge build for 1.21 keeps it to itself.
        try {
            Field field = StructureSearchEntry.class.getDeclaredField("structureKey");
            field.setAccessible(true);
            return (ResourceLocation) field.get(entry);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
        *///?} else {
        return entry.getStructureID();
        //?}
    }

    private static <T> T find(Screen screen, Class<T> type) {
        for (GuiEventListener child : screen.children()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
        }
        return null;
    }
}
