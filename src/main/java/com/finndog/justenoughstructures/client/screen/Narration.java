package com.finndog.justenoughstructures.client.screen;

import java.util.Arrays;
import java.util.Objects;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarrationSupplier;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * What the narrator reads for a control drawn here rather than as a vanilla widget, in vanilla's own
 * words for its widgets, like "Done button" and "Left click to activate", then its tooltip.
 * {@code usage} and {@code hint} can be null.
 */
record Narration(Component title, Component usage, Component hint) implements NarrationSupplier {
    /** A button or link, with how to use it while it can be used. */
    static Narration button(Component label, boolean active) {
        return new Narration(Component.translatable("gui.narrate.button", label),
                active ? Component.translatable("narration.button.usage.hovered") : null, null);
    }

    static Narration button(Component label) {
        return button(label, true);
    }

    /** A tab, read by its name as vanilla reads one, after "Selected:" while it's the one open. */
    static Narration tab(Component label, boolean selected) {
        Component tab = Component.translatable("gui.narrate.tab", label);
        return new Narration(selected ? Component.translatable("narrator.select", tab) : tab, null, null);
    }

    /** A check box or switch, with whether it's on, the way vanilla reads an option that's on or off. */
    static Narration check(Component label, boolean on, boolean active) {
        return new Narration(Component.translatable("gui.narrate.button", CommonComponents.optionNameValue(label, CommonComponents.optionStatus(on))),
                active ? Component.translatable("narration.checkbox.usage.hovered") : null, null);
    }

    /** A row of a list, read as what it shows. */
    static Narration row(Component label) {
        return new Narration(label, null, null);
    }

    /** The same, followed by a tooltip's lines, as vanilla reads a widget's tooltip after it. */
    Narration withHint(Component... lines) {
        Component[] shown = lines == null ? new Component[0] : Arrays.stream(lines).filter(Objects::nonNull).toArray(Component[]::new);
        return shown.length == 0 ? this : new Narration(title, usage, CommonComponents.joinForNarration(shown));
    }

    @Override
    public void updateNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, title);
        if (hint != null) {
            output.add(NarratedElementType.HINT, hint);
        }
        if (usage != null) {
            output.add(NarratedElementType.USAGE, usage);
        }
    }
}
