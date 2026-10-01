package com.finndog.justenoughstructures.mixin;

import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The text and cursor of a multi-line box, so a click in the loot editor's JSON can place the cursor. */
@Mixin(MultiLineEditBox.class)
public interface MultiLineEditBoxAccessor {
    @Accessor("textField")
    MultilineTextField justenoughstructures$textField();

    @Invoker("seekCursorScreen")
    void justenoughstructures$seekCursorScreen(double mouseX, double mouseY);
}
