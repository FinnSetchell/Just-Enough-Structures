package com.finndog.justenoughstructures.client.screen;

import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** A small square button showing an item, with its label as a tooltip. Can show an on/off state. */
final class IconButton extends Button {
    private final Supplier<ItemStack> icon;
    private final Supplier<Boolean> on;

    IconButton(int x, int y, Supplier<ItemStack> icon, Supplier<Boolean> on, Component label, OnPress onPress) {
        super(x, y, 20, 20, Component.empty(), onPress, DEFAULT_NARRATION);
        this.icon = icon;
        this.on = on;
        setTooltip(Tooltip.create(label));
    }

    void setLabel(Component label) {
        setTooltip(Tooltip.create(label));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(g, mouseX, mouseY, partialTick);
        if (on != null && on.get()) {
            g.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, 0x5055FF55);
        }
        g.renderItem(icon.get(), getX() + 2, getY() + 2);
        if (on != null && !on.get()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, 0x90303030);
            g.pose().popPose();
        }
    }
}
