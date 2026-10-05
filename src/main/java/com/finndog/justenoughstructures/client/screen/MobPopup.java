package com.finndog.justenoughstructures.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;

/**
 * A mob or other entity the structure places, opened by clicking it or its marker in the preview, or
 * its row on the Mobs tab: what it wears and holds, and what sets it apart, like a name. It sits
 * beside the preview like a spawner's popup, with arrows to the others of its kind. Its title is the
 * mob's own name, so stepping through villagers shows each one's job.
 */
final class MobPopup extends SidePopup {
    /** Worn, then held: the order the slots go in. */
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    final Entity mob;
    /** What it wears and holds, or none when it has nothing, or while this stands for all of them. */
    private final List<ItemStack> items;
    /** What sets this one apart, a line each. */
    private final List<Component> details;

    MobPopup(Entity mob, int index, int count) {
        super(index < 0 || mob.hasCustomName() ? mob.getType().getDescription() : mob.getName(), index, count);
        this.mob = mob;
        items = overview() ? List.of() : items(mob);
        details = overview() ? List.of() : details(mob);
    }

    private static List<ItemStack> items(Entity mob) {
        List<ItemStack> out = new ArrayList<>();
        if (mob instanceof LivingEntity living) {
            for (EquipmentSlot slot : SLOTS) {
                out.add(living.getItemBySlot(slot));
            }
        } else if (mob instanceof ItemFrame frame) {
            out.add(frame.getItem());
        }
        return out.stream().allMatch(ItemStack::isEmpty) ? List.of() : out;
    }

    private static List<Component> details(Entity mob) {
        List<Component> out = new ArrayList<>();
        if (mob.hasCustomName()) {
            out.add(Component.translatable("screen.justenoughstructures.mob.named", mob.getCustomName()));
        }
        if (mob instanceof LivingEntity living && living.isBaby()) {
            out.add(Component.translatable("screen.justenoughstructures.mob.baby"));
        }
        return out;
    }

    private int bodyHeight() {
        return items.isEmpty() ? 0 : 18;
    }

    private int infoHeight(Font font) {
        int height = 5 + details.size() * (font.lineHeight + 1) + 3 + 20 + 6;
        if (Gui.advanced()) {
            height += Gui.fineLine(font) + 1;
        }
        return height;
    }

    @Override
    int height(Font font) {
        return 17 + bodyHeight() + 7 + infoHeight(font);
    }

    @Override
    ItemStack render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        hoveredTip = null;
        int body = bodyHeight();

        // The chest's frame: its title bar, a row of slots for what it wears and holds, then its bottom edge.
        Gui.blit(g, TEXTURE, x, y, 0, 0, WIDTH, 17);
        if (body > 0) {
            Gui.blit(g, TEXTURE, x, y + 17, 0, 17, WIDTH, body);
        }
        Gui.blit(g, TEXTURE, x, y + 17 + body, 0, 215, WIDTH, 7);
        renderTitle(g, font);

        ItemStack hovered = ItemStack.EMPTY;
        if (body > 0) {
            for (int slot = 0; slot < 9; slot++) {
                int sx = x + 8 + slot * 18;
                int sy = y + 18;
                if (slot >= items.size()) {
                    g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF8B8B8B);
                    continue;
                }
                ItemStack stack = items.get(slot);
                if (!stack.isEmpty()) {
                    g.renderItem(stack, sx, sy);
                    g.renderItemDecorations(font, stack, sx, sy);
                }
                if (mouseX >= sx - 1 && mouseX < sx + 17 && mouseY >= sy - 1 && mouseY < sy + 17) {
                    g.fill(sx, sy, sx + 16, sy + 16, 0x80FFFFFF);
                    hovered = stack;
                }
            }
        }

        int infoTop = y + 17 + body + 7;
        Gui.panel(g, x, infoTop, WIDTH, infoHeight(font));
        int cy = infoTop + 5;
        for (Component line : details) {
            Gui.drawClipped(g, font, line.getString(), x + 7, cy, WIDTH - 14, 0xFF202020, false);
            cy += font.lineHeight + 1;
        }
        if (Gui.advanced()) {
            Gui.fineClipped(g, font, BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString(), x + 7, cy, WIDTH - 14, 0xFF555555);
        }
        return hovered;
    }
}
