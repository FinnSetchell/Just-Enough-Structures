package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.Regs;
import com.finndog.justenoughstructures.capture.TrialSpawners;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.overrides.SpawnerPatches;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Picks the mob for one spawner or trial spawner in a structure, or none: any mob the game has,
 * modded ones too. Like the loot table picker, the change is saved on the server as a patch to the
 * spawner's template and applies from the next /reload.
 */
public final class MobPickerScreen extends PickerScreen<String> {
    /** The row for no mob at all. */
    private static final String NONE = "";

    private final ResourceLocation template;
    private final BlockPos pos;
    private final String current;
    /** The block the spawner is set to be, which it stays. */
    private final ResourceLocation block;

    MobPickerScreen(Screen parent, ResourceLocation template, BlockPos pos, String current, ResourceLocation block) {
        super(Component.translatable(TrialSpawners.isBlock(block) ? "screen.justenoughstructures.mob_picker.title_trial" : "screen.justenoughstructures.mob_picker.title"),
                parent, "mob_picker");
        this.template = template;
        this.pos = pos;
        this.current = current;
        this.block = block;
    }

    /** Where the picker is, for Back and Forward: which spawner it's picking for. */
    private record PickerLayer(ResourceLocation template, BlockPos pos, String current, ResourceLocation block) implements Nav.Layer {
        @Override
        public Object key() {
            return List.of("mob_picker", template, pos);
        }

        @Override
        public Component label() {
            return Component.translatable("screen.justenoughstructures.nav.mob_picker");
        }

        @Override
        public Screen open(Screen below) {
            return new MobPickerScreen(below, template, pos, current, block);
        }
    }

    @Override
    public Nav.Layer layer() {
        return new PickerLayer(template, pos, current, block);
    }

    /** Every mob a spawner can make, by mod and then by name, with no mob at all first. */
    private static List<String> mobs() {
        List<EntityType<?>> types = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (SpawnerPatches.spawnable(type)) {
                types.add(type);
            }
        }
        types.sort(Comparator.comparing((EntityType<?> type) -> !BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("minecraft"))
                .thenComparing(type -> StructureNames.mod(BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace()))
                .thenComparing(type -> type.getDescription().getString()));
        List<String> out = new ArrayList<>();
        out.add(NONE);
        types.forEach(type -> out.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()));
        return out;
    }

    @Override
    protected List<String> matching(String query) {
        List<String> out = new ArrayList<>();
        for (String id : mobs()) {
            String mod = id.isEmpty() ? "" : StructureNames.mod(ResourceLocation.tryParse(id).getNamespace());
            if (query.isEmpty() || id.contains(query) || name(id).toLowerCase(Locale.ROOT).contains(query) || mod.toLowerCase(Locale.ROOT).contains(query)) {
                out.add(id);
            }
        }
        return out;
    }

    @Override
    protected String name(String id) {
        return id.isEmpty() ? Component.translatable("screen.justenoughstructures.mob_picker.nothing").getString() : StructureNames.mob(id).getString();
    }

    @Override
    protected boolean inUseNow(String mob) {
        return mob.equals(current);
    }

    @Override
    protected ResourceLocation template() {
        return template;
    }

    @Override
    protected CompletableFuture<ClientRequests.EditReply> save(String mob) {
        return ClientRequests.spawnerAction(template, pos, mob, block);
    }

    @Override
    protected String savedReply() {
        return "spawner.saved";
    }

    /** Above the buttons, with a line under the list for a note about the mob picked. */
    @Override
    protected int listBottom() {
        return super.listBottom() - font.lineHeight - 4;
    }

    @Override
    protected void drawRow(GuiGraphics g, String id, int left, int y, int right) {
        g.renderItem(ToolsSpawners.mobIcon(id), left + 5, y + 3);
        Gui.fitted(g, font, name(id), left + 25, y + 3, right - left - 33, Gui.LABEL);
        String detail = id.isEmpty() ? Component.translatable("screen.justenoughstructures.hover_spawns_nothing").getString()
                : id + " · " + StructureNames.mod(ResourceLocation.tryParse(id).getNamespace());
        Gui.fineClipped(g, font, detail, left + 25, y + 13, right - left - 33, Gui.LABEL_SOFT);
    }

    /** A word about the mob picked when it isn't a monster: spawners still check where it would normally spawn. */
    @Override
    protected Component note() {
        ResourceLocation id = picked == null || picked.isEmpty() ? null : ResourceLocation.tryParse(picked);
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return null;
        }
        EntityType<?> type = Regs.value(BuiltInRegistries.ENTITY_TYPE, id);
        return type.getCategory() == MobCategory.MONSTER ? null
                : Component.translatable("screen.justenoughstructures.mob_picker.not_monster", type.getDescription());
    }
}
