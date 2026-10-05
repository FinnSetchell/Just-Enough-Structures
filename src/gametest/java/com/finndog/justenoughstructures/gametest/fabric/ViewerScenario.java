package com.finndog.justenoughstructures.gametest.fabric;

import static com.finndog.justenoughstructures.gametest.fabric.Director.click;
import static com.finndog.justenoughstructures.gametest.fabric.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.fabric.Director.pause;
import static com.finndog.justenoughstructures.gametest.fabric.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.fabric.Director.run;
import static com.finndog.justenoughstructures.gametest.fabric.Director.shoot;
import static com.finndog.justenoughstructures.gametest.fabric.Director.until;

import com.finndog.justenoughstructures.client.screen.JesScreen;
//? if emi {
import com.finndog.justenoughstructures.compat.emi.JesEmiPlugin;
//?}
import com.finndog.justenoughstructures.compat.rei.JesReiPlugin;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * The "Found in structures" page in EMI or REI, whichever the dev runtime has (-Pviewer=emi or rei):
 * a saddle's page, a row's tooltip, and clicking it through to the browser.
 */
final class ViewerScenario {
    private ViewerScenario() {
    }

    static Director build(Minecraft mc) {
        //? if emi {
        boolean emi = FabricLoader.getInstance().isModLoaded("emi");
        BooleanSupplier ready = emi ? JesEmiPlugin::ready : JesReiPlugin::ready;
        Consumer<ItemStack> show = emi ? JesEmiPlugin::showFoundIn : JesReiPlugin::showFoundIn;
        Supplier<int[]> firstRow = emi ? JesEmiPlugin::firstRow : JesReiPlugin::firstRow;
        //?} else {
        /*// No EMI for this version, so it's REI's page.
        boolean emi = false;
        BooleanSupplier ready = JesReiPlugin::ready;
        Consumer<ItemStack> show = JesReiPlugin::showFoundIn;
        Supplier<int[]> firstRow = JesReiPlugin::firstRow;
        *///?}
        String name = emi ? "emi" : "rei";
        Director d = new Director(mc, null);
        d.then(pause(40))
                .then(pressKey(GLFW.GLFW_KEY_E))
                .then(until(() -> mc.screen != null, 40))
                .then(until(ready, 12000))
                .then(pause(40))
                .then(shoot("v01_" + name + "_inventory"))
                .then(run(() -> show.accept(new ItemStack(Items.SADDLE))))
                .then(pause(60))
                .then(shoot("v02_" + name + "_found_in_saddle"))
                .then(moveTo(firstRow, 6))
                .then(pause(10))
                .then(shoot("v03_" + name + "_row"))
                .then(click())
                .then(pause(40))
                .then(shoot("v04_" + name + "_after_click"))
                .then(run(() -> {
                    if (mc.screen instanceof JesScreen) {
                        mc.screen.onClose();
                    }
                }))
                .then(pause(20))
                .then(shoot("v05_" + name + "_back"));
        return d;
    }
}
