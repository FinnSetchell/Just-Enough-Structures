package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Director.wheel;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.client.ClientState;
import com.finndog.justenoughstructures.client.render.StructureViewport;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.finndog.justenoughstructures.gametest.FlyInputs;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Flying through a desert pyramid. The fly button takes the mouse without closing the browser or
 * taking the game's own grab, and the picture doesn't jump. The mouse turns the camera and not the
 * player, the forward key moves it level, and Space, which would press the fly button the click left
 * with the keyboard, doesn't stop it. The wheel changes the speed. A click opens the chest at the
 * crosshair without the camera moving, the popup gives the mouse back, and its arrows turn the
 * camera on the spot to the next chest. Escape closes the popup, which takes the mouse again, then
 * stops flying with the picture as it is. The window losing the focus stops it too, without moving
 * the cursor, and the cursor is given back before the browser closes. The real cursor is never
 * touched: the run's hook on the camera's input records each call instead ({@link FlyInputs}).
 */
final class FlyCameraScenario {
    private FlyCameraScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("desert_pyramid"));
        boolean markers = ClientState.markers;
        Matrix4f[] before = new Matrix4f[1];
        Vector3f[] eye = new Vector3f[1];
        float[] turned = new float[4];
        float[] speed = new float[1];
        Director d = new Director(mc);
        d.then(run(() -> {
                    // Without the hook, flying would take the PC's real cursor.
                    if (!FlyInputs.hooked()) {
                        throw new IllegalStateException("The fly camera's input isn't hooked, so this run would take the real cursor");
                    }
                    FlyInputs.clear();
                }))
                .then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> Screens.ready(mc), 600))
                .then(run(() -> {
                    browser(mc).setSpin(false);
                    // The chests are clicked by their markers, which show through the floor over them.
                    ClientState.markers = true;
                }))
                .then(moveTo(() -> browser(mc).button("fly"), 10))
                .then(pause(10))
                .then(run(() -> before[0] = viewport(mc).viewMatrix()))

                // The fly button takes the mouse, the picture staying as it was.
                .then(click())
                .then(pause(4))
                .then(check(() -> browser(mc) != null && browser(mc).flying(), "the fly button didn't start flying"))
                .then(run(() -> {
                    if (!FlyInputs.calls().equals(List.of(FlyInputs.GRAB))) {
                        throw new IllegalStateException("Starting to fly should take the mouse once, but the cursor calls were " + FlyInputs.calls());
                    }
                    sameView(before[0], viewport(mc).viewMatrix(), "starting to fly");
                }))
                .then(check(() -> !mc.mouseHandler.isMouseGrabbed(), "flying took the game's own grab, which turns the player and closes screens"))
                .then(shoot("fl01_flying"))

                // The mouse turns the camera, as far as it would turn the player, and not the player.
                .then(run(() -> {
                    StructureViewport.Camera camera = viewport(mc).camera();
                    turned[0] = camera.yaw();
                    turned[1] = camera.pitch();
                    turned[2] = mc.player.getYRot();
                    turned[3] = mc.player.getXRot();
                    FlyInputs.look(100, 40);
                }))
                .then(pause(3))
                .then(run(() -> {
                    double s = mc.options.sensitivity().get() * 0.6 + 0.2;
                    float perPixel = (float) (s * s * s * 8.0 * 0.15);
                    StructureViewport.Camera camera = viewport(mc).camera();
                    float yaw = Math.abs(camera.yaw() - turned[0]);
                    float pitch = Math.abs(camera.pitch() - turned[1]);
                    if (Math.abs(yaw - 100 * perPixel) > 0.01f || Math.abs(pitch - 40 * perPixel) > 0.01f) {
                        throw new IllegalStateException("The mouse turned the camera by " + yaw + " and " + pitch + " degrees, not "
                                + 100 * perPixel + " and " + 40 * perPixel + " as the game turns the player");
                    }
                    if (mc.player.getYRot() != turned[2] || mc.player.getXRot() != turned[3]) {
                        throw new IllegalStateException("The mouse turned the player behind the browser while flying");
                    }
                    eye[0] = viewport(mc).eye();
                    FlyInputs.hold(mc.options.keyUp, true);
                }))

                // The forward key moves the camera level, the way it faces.
                .then(pause(20))
                .then(run(() -> {
                    FlyInputs.hold(mc.options.keyUp, false);
                    Vector3f now = viewport(mc).eye();
                    float dx = now.x() - eye[0].x(), dy = now.y() - eye[0].y(), dz = now.z() - eye[0].z();
                    float level = (float) Math.sqrt(dx * dx + dz * dz);
                    double yaw = Math.toRadians(viewport(mc).camera().yaw());
                    float along = level == 0 ? 0 : (float) ((dx * Math.sin(yaw) - dz * Math.cos(yaw)) / level);
                    if (level < 0.5f || Math.abs(dy) > 1e-3f || along < 0.99f) {
                        throw new IllegalStateException("Holding forward moved the camera " + level + " blocks level, " + dy
                                + " up, " + along + " along where it faces; it should move level and forward");
                    }
                }))
                .then(shoot("fl02_moved"))
                .then(pressKey(InputConstants.KEY_SPACE))
                .then(pause(3))
                .then(check(() -> browser(mc).flying() && FlyInputs.GRAB.equals(FlyInputs.lastCall()),
                        "Space stopped flying or gave the mouse back, as if it pressed the fly button"))

                // The wheel doubles the speed, up to eight times.
                .then(run(() -> speed[0] = browser(mc).flySpeedFactor()))
                .then(wheel(1))
                .then(pause(3))
                .then(check(() -> browser(mc).flySpeedFactor() == Math.min(8f, speed[0] * 2f), "the wheel didn't change the fly speed"))
                .then(shoot("fl03_speed"))
                .then(wheel(-1))
                .then(pause(3))
                .then(check(() -> browser(mc).flySpeedFactor() == speed[0], "the wheel didn't put the fly speed back"))

                // The crosshair on a chest's marker: the hover follows it, and a click opens the chest without moving the camera.
                .then(run(() -> aimAtMarker(mc, chests(mc).get(0).pos())))
                .then(pause(4))
                .then(run(() -> {
                    StructureViewport.Hit hover = browser(mc).previewHover();
                    if (hover == null || chests(mc).stream().noneMatch(c -> c.pos().equals(hover.pos()))) {
                        throw new IllegalStateException("The crosshair is on a chest's marker, but the preview answered with " + hover);
                    }
                    before[0] = viewport(mc).viewMatrix();
                }))
                .then(click())
                .then(until(() -> browser(mc).containerOpen(), 40))
                .then(check(() -> browser(mc).containerOpen(), "a click with the crosshair on a chest didn't open it"))
                .then(pause(10))
                .then(run(() -> sameView(before[0], viewport(mc).viewMatrix(), "opening the chest at the crosshair")))
                .then(check(() -> FlyInputs.RELEASE.equals(FlyInputs.lastCall()), "the chest's popup didn't give the mouse back"))
                .then(shoot("fl04_chest"))

                // The popup's arrows turn the camera on the spot to face the next chest.
                .then(run(() -> eye[0] = viewport(mc).eye()))
                .then(moveTo(() -> browser(mc).button("next"), 10))
                .then(click())
                .then(pause(10))
                .then(run(() -> {
                    StructureSnapshot.Container next = browser(mc).shownContainer();
                    if (next == null) {
                        throw new IllegalStateException("The popup's next arrow didn't show one chest");
                    }
                    Vector3f now = viewport(mc).eye();
                    if (now.distance(eye[0]) > 1e-3f) {
                        throw new IllegalStateException("The popup's next arrow moved the flying camera by " + now.distance(eye[0]) + " blocks");
                    }
                    Vector3f to = new Vector3f(next.pos().getX() + 0.5f, next.pos().getY() + 0.5f, next.pos().getZ() + 0.5f).sub(now).normalize();
                    Vector3f facing = viewport(mc).viewMatrix().invert().transformDirection(new Vector3f(0, 0, -1)).normalize();
                    if (facing.dot(to) < 0.9998f) {
                        throw new IllegalStateException("The popup's next arrow didn't turn the camera to the next chest: "
                                + Math.toDegrees(Math.acos(Math.min(1, facing.dot(to)))) + " degrees off");
                    }
                }))
                .then(shoot("fl05_next_chest"))

                // Escape closes the popup and the mouse is taken again; the next one stops flying, the picture as it is.
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(pause(4))
                .then(check(() -> !browser(mc).containerOpen() && browser(mc).flying() && FlyInputs.GRAB.equals(FlyInputs.lastCall()),
                        "Escape didn't close the popup and take the mouse again"))
                .then(run(() -> before[0] = viewport(mc).viewMatrix()))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(pause(4))
                .then(check(() -> browser(mc) != null, "the first Escape while flying closed the browser"))
                .then(check(() -> !browser(mc).flying() && FlyInputs.RELEASE.equals(FlyInputs.lastCall()),
                        "Escape didn't stop flying and give the mouse back"))
                .then(run(() -> sameView(before[0], viewport(mc).viewMatrix(), "stopping flying")))
                .then(shoot("fl06_stopped"))

                // The window losing the focus stops flying, leaving the cursor where it is.
                .then(pressKey(InputConstants.KEY_F))
                .then(pause(4))
                .then(check(() -> browser(mc).flying() && FlyInputs.GRAB.equals(FlyInputs.lastCall()), "F didn't start flying"))
                .then(run(() -> loseFocus(mc)))
                .then(until(() -> !browser(mc).flying(), 20))
                .then(check(() -> !browser(mc).flying() && FlyInputs.RELEASE_IN_PLACE.equals(FlyInputs.lastCall()),
                        "the window losing the focus didn't stop flying and give the mouse back where it was"))
                .then(pause(4))

                // Flying again, the browser closes only after the cursor is given back.
                .then(pressKey(InputConstants.KEY_F))
                .then(pause(4))
                .then(check(() -> browser(mc).flying(), "F didn't start flying again"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(pause(4))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> mc.screen == null, 40))
                .then(run(() -> {
                    List<String> calls = FlyInputs.calls();
                    long grabs = calls.stream().filter(FlyInputs.GRAB::equals).count();
                    if (mc.screen != null) {
                        throw new IllegalStateException("Two Escapes after flying left " + mc.screen.getClass().getSimpleName() + " open");
                    }
                    if (grabs * 2 != calls.size() || FlyInputs.GRAB.equals(FlyInputs.lastCall())) {
                        throw new IllegalStateException("The mouse wasn't given back as often as it was taken: " + calls);
                    }
                }))
                .then(run(() -> {
                    ClientState.markers = markers;
                    FlyInputs.clear();
                }));
        return d;
    }

    private static StructureViewport viewport(Minecraft mc) {
        return (StructureViewport) Reflect.get(browser(mc), "viewport");
    }

    /** The pyramid's chests with a loot table. */
    private static List<StructureSnapshot.Container> chests(Minecraft mc) {
        List<StructureSnapshot.Container> chests = browser(mc).result().snapshot().containers().stream()
                .filter(c -> !c.entity() && c.lootTable() != null).toList();
        if (chests.size() < 2) {
            throw new IllegalStateException("The desert pyramid has " + chests.size() + " chests with a loot table, and this needs two");
        }
        return chests;
    }

    /**
     * Turns the flying camera so the crosshair is in the middle of the marker over a chest. A marker
     * stands on the point just above the chest's top, so the camera faces that, then looks up by half
     * a marker, which moves the point down the screen by as much.
     */
    private static void aimAtMarker(Minecraft mc, BlockPos chest) {
        StructureViewport viewport = viewport(mc);
        viewport.face(chest.getX() + 0.5, chest.getY() + 1.1, chest.getZ() + 0.5);
        int size = (Integer) Reflect.call(browser(mc), "markerSize");
        double focal = Reflect.getInt(browser(mc), "viewH") / (2 * Math.tan(Math.toRadians(25)));
        viewport.look(0, (float) -Math.toDegrees(Math.atan(size / 2.0 / focal)));
    }

    /** Throws unless two view matrices are the same picture, to within rounding. */
    private static void sameView(Matrix4f before, Matrix4f after, String what) {
        if (!before.equals(after, 1e-3f)) {
            throw new IllegalStateException(what + " moved the picture: the view went from " + before + " to " + after);
        }
    }

    /** As the window does when another one is clicked. The run gives the focus back in its next tick. */
    private static void loseFocus(Minecraft mc) {
        //? if >=26.1 {
        /*Reflect.set(mc.getWindow(), "focused", false);
        *///?} else {
        mc.setWindowActive(false);
        //?}
    }

    private static Director.Action check(BooleanSupplier holds, String failure) {
        return run(() -> {
            if (!holds.getAsBoolean()) {
                throw new IllegalStateException(failure);
            }
        });
    }
}
