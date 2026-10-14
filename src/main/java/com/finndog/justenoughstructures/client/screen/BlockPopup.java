package com.finndog.justenoughstructures.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One block of a kind the structure has only a few of, opened from its row on the Blocks tab, for
 * finding a particular block: its arrows go through the others as the camera turns to each. It's
 * drawn like a mob's popup, with the block in the first slot.
 */
final class BlockPopup extends SidePopup {
    final BlockState state;
    final BlockPos pos;

    BlockPopup(BlockState state, BlockPos pos, int index, int count) {
        super(state.getBlock().getName(), index, count);
        this.state = state;
        this.pos = pos;
    }

    private int infoHeight(Font font) {
        int height = 5 + 3 + 20 + 6;
        if (Gui.advanced()) {
            height += Gui.fineLine(font) + 1;
        }
        return height;
    }

    @Override
    int height(Font font) {
        return 17 + 18 + 7 + infoHeight(font);
    }

    @Override
    ItemStack render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        hoveredTip = null;
        // The chest's frame: its title bar, a row of slots with the block in the first, then its bottom edge.
        Gui.blit(g, TEXTURE, x, y, 0, 0, WIDTH, 17);
        Gui.blit(g, TEXTURE, x, y + 17, 0, 17, WIDTH, 18);
        Gui.blit(g, TEXTURE, x, y + 35, 0, 215, WIDTH, 7);
        renderTitle(g, font);

        ItemStack stack = InfoPanel.blockIcon(state.getBlock());
        ItemStack hovered = ItemStack.EMPTY;
        for (int slot = 0; slot < 9; slot++) {
            int sx = x + 8 + slot * 18;
            int sy = y + 18;
            if (slot > 0) {
                Gui.tone(g, sx - 1, sy - 1, sx + 17, sy + 17, Gui.Tone.SLOT);
                continue;
            }
            g.renderItem(stack, sx, sy);
            if (mouseX >= sx - 1 && mouseX < sx + 17 && mouseY >= sy - 1 && mouseY < sy + 17) {
                g.fill(sx, sy, sx + 16, sy + 16, 0x80FFFFFF);
                hovered = stack;
            }
        }

        int infoTop = y + 17 + 18 + 7;
        Gui.panel(g, x, infoTop, WIDTH, infoHeight(font));
        if (Gui.advanced()) {
            Gui.fineClipped(g, font, BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), x + 7, infoTop + 5, WIDTH - 14, Gui.labelSoftSmall());
        }
        return hovered;
    }
}
