package com.finndog.justenoughstructures.client.screen;

import java.util.function.IntConsumer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** Hides every layer at or above the chosen height. */
final class LayerSlider extends AbstractSliderButton {
    /** How wide it has to be for its full label; narrower, it only shows the numbers. */
    static final int LABEL_WIDTH = 100;

    private int layers = 1;
    private final IntConsumer onChange;

    LayerSlider(int x, int y, int width, int height, IntConsumer onChange) {
        super(x, y, width, height, Component.empty(), 1.0);
        this.onChange = onChange;
        updateMessage();
    }

    void setLayers(int layers, int shown) {
        this.layers = Math.max(1, layers);
        this.value = layers <= 1 ? 1.0 : (double) (shown - 1) / (this.layers - 1);
        updateMessage();
    }

    int shown() {
        return 1 + (int) Math.round(value * (layers - 1));
    }

    @Override
    protected void updateMessage() {
        boolean narrow = width < LABEL_WIDTH;
        if (shown() >= layers) {
            setMessage(Component.translatable(narrow ? "screen.justenoughstructures.layers_all_short" : "screen.justenoughstructures.layers_all"));
        } else {
            setMessage(Component.translatable(narrow ? "screen.justenoughstructures.layers_short" : "screen.justenoughstructures.layers", shown(), layers));
        }
    }

    /** The scroll wheel steps one layer at a time. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (layers <= 1 || delta == 0) {
            return false;
        }
        setLayers(layers, Math.max(1, Math.min(layers, shown() + (delta > 0 ? 1 : -1))));
        applyValue();
        return true;
    }

    @Override
    protected void applyValue() {
        onChange.accept(shown());
    }
}
