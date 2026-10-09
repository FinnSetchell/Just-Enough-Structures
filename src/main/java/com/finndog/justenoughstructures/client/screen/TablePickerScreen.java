package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.FoundIn;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Picks the loot table for one container in a structure: any table the structures use, one typed
 * by id, or a new one made and saved in the editor first. The change is saved on the server as a
 * patch to the container's template and applies from the next /reload.
 */
public final class TablePickerScreen extends PickerScreen<ResourceLocation> {
    private final StructureSnapshot.Source source;
    private final String current;
    private final Component containerName;
    private ResourceLocation typed;
    private boolean indexed;

    TablePickerScreen(Screen parent, StructureSnapshot.Source source, String current, Component containerName) {
        super(Component.translatable("screen.justenoughstructures.picker.title", containerName), parent, "picker");
        this.source = source;
        this.current = current;
        this.containerName = containerName;
        ClientRequests.index();
    }

    /** Where the picker is, for Back and Forward: which container it's picking for. */
    private record PickerLayer(StructureSnapshot.Source source, String current, Component containerName) implements Nav.Layer {
        @Override
        public Object key() {
            return List.of("picker", source.template(), source.pos());
        }

        @Override
        public Component label() {
            return Component.translatable("screen.justenoughstructures.nav.picker");
        }

        @Override
        public Screen open(Screen below) {
            return new TablePickerScreen(below, source, current, containerName);
        }
    }

    @Override
    public Nav.Layer layer() {
        return new PickerLayer(source, current, containerName);
    }

    /** A table just saved in the editor, typed in here, which picks it. */
    void useSaved(ResourceLocation id) {
        search.setValue(id.toString());
    }

    @Override
    protected void addButtons(int left, int y) {
        Component label = Component.translatable("screen.justenoughstructures.picker.new");
        addRenderableWidget(Button.builder(label, b -> newTable())
                .bounds(left, y, font.width(label) + 12, 20).tooltip(Tooltip.create(Component.translatable("screen.justenoughstructures.picker.new_hint"))).build());
    }

    @Override
    protected List<ResourceLocation> matching(String query) {
        List<ResourceLocation> all = new ArrayList<>(FoundIn.allTables());
        all.sort(Comparator.comparing((ResourceLocation id) -> StructureNames.lootTable(id.toString())).thenComparing(ResourceLocation::toString));
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation id : all) {
            if (query.isEmpty() || id.toString().contains(query) || StructureNames.lootTable(id.toString()).toLowerCase(Locale.ROOT).contains(query)) {
                out.add(id);
            }
        }
        // An id typed in full, like a table just made in the editor, can be picked even if no structure
        // uses it yet. Typing one in full is taken as picking it.
        ResourceLocation full = query.contains(":") ? ResourceLocation.tryParse(query) : null;
        boolean pickedTyped = picked != null && picked.equals(typed);
        typed = full != null && !all.contains(full) ? full : null;
        if (typed != null) {
            out.add(0, typed);
        }
        if (full != null && (picked == null || pickedTyped)) {
            picked = full;
        }
        return out;
    }

    @Override
    protected boolean inUseNow(ResourceLocation table) {
        return table.toString().equals(current);
    }

    @Override
    protected ResourceLocation template() {
        return source.template();
    }

    @Override
    protected CompletableFuture<ClientRequests.EditReply> save(ResourceLocation table) {
        return ClientRequests.containerAction(source.template(), source.pos(), table);
    }

    @Override
    protected String savedReply() {
        return "container.saved";
    }

    @Override
    protected String name(ResourceLocation table) {
        return StructureNames.lootTable(table.toString());
    }

    @Override
    protected Component nothingShown() {
        return FoundIn.ready() ? super.nothingShown() : Component.translatable("screen.justenoughstructures.indexing");
    }

    @Override
    protected void drawRow(GuiGraphics g, ResourceLocation id, int left, int y, int right) {
        String name = id.equals(typed) ? Component.translatable("screen.justenoughstructures.picker.typed", id.toString()).getString()
                : StructureNames.lootTable(id.toString());
        Gui.fitted(g, font, name, left + 6, y + 3, right - left - 14, Gui.LABEL);
        Gui.fineClipped(g, font, id.toString(), left + 6, y + 13, right - left - 14, Gui.LABEL_SOFT);
    }

    /** Opens the editor on a new table, named as typed or after the container's template. Save it, then pick it here. */
    private void newTable() {
        ResourceLocation id = typed;
        if (id == null) {
            String path = source.template().getPath();
            id = Ids.of("justenoughstructures", "chests/" + path.substring(path.lastIndexOf('/') + 1));
        }
        search.setValue(id.toString());
        Nav.remember();
        // Saved under whatever id it ends up with, it's typed in here, which picks it.
        minecraft.setScreen(new LootEditorScreen(this, id, StructureNames.lootTable(id.toString()), this::useSaved));
    }

    @Override
    public void tick() {
        super.tick();
        if (!indexed && FoundIn.ready()) {
            indexed = true;
            refilter();
        }
    }

    /** Whether the list of tables is here. For the screenshot harness. */
    public boolean ready() {
        return indexed;
    }

    /** Picks a row of the list. For the screenshot harness. */
    public void pick(int row) {
        if (row >= 0 && row < shown.size()) {
            picked = shown.get(row);
            updateUse();
        }
    }

    /** Presses Use it. For the screenshot harness. */
    public void useIt() {
        apply(false);
    }
}
