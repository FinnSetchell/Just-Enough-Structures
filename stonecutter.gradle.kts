plugins {
    id("dev.kikugie.stonecutter")
}

// Must stay a string literal: Stonecutter rewrites this line when switching versions.
stonecutter active "1.20.1-fabric"

stonecutter parameters {
    val (version, loader) = current.project.split('-', limit = 2)

    // Lets `[fabric."1.20.1"]` tables in stonecutter.properties.toml resolve for this node.
    properties {
        tags(version, loader)
    }

    // `//? if fabric {` and friends, for the few places loader code shares a file with common code.
    // Likewise `//? if cloth_config {` and the rest for each mod JES links up with: a node only builds
    // the pages and links for those that have a build for its version and loader, by their version
    // being set in stonecutter.properties.toml.
    constants {
        match(loader, "fabric", "forge", "neoforge")
        for (mod in listOf("jei", "emi", "rei", "cloth_config", "explorers_compass", "modmenu")) {
            put(mod, !properties.getOrNull<String>("deps.$mod").isNullOrEmpty())
        }
    }

    replacements {
        // 1.21.11 renamed ResourceLocation to Identifier across the whole codebase. Below that it runs
        // the other way, which would also catch names that merely contain Identifier, like REI's
        // CategoryIdentifier, so those lines turn it off with `//~ !identifier`.
        string(current.parsed >= "1.21.11", "!identifier") {
            replace("ResourceLocation", "Identifier")
        }

        // Screens: 1.20.2 gave scrolling a sideways amount, and moving to the end of a text box whether to select.
        string(current.parsed >= "1.21") {
            replace("mouseScrolled(double mouseX, double mouseY, double delta)", "mouseScrolled(double mouseX, double mouseY, double scrollX, double delta)")
            replace("super.mouseScrolled(mouseX, mouseY, delta)", "super.mouseScrolled(mouseX, mouseY, scrollX, delta)")
            replace(".moveCursorToEnd()", ".moveCursorToEnd(false)")
        }

        // Classes that only moved package.
        string(current.parsed >= "1.21") {
            replace("net.minecraft.world.level.chunk.ChunkStatus;", "net.minecraft.world.level.chunk.status.ChunkStatus;")
        }

        // 1.21.2 renamed getting a registry out of the game's registries. What else it renamed on
        // registries is in Regs, as those names now mean something else.
        string(current.parsed >= "1.21.2") {
            replace(".registryOrThrow(", ".lookupOrThrow(")
            replace(".registryAccess().registry(", ".registryAccess().lookup(")
        }

        // 26.1 moved Util, and screens now collect what to draw rather than drawing it, so the
        // graphics they're handed got a new name and its methods new names. These only catch calls
        // on a graphics named g, which every screen here uses. What changed more than its name is
        // in Gui and the bridges in BackdropScreen and JesButton.
        string(current.parsed >= "26.1") {
            replace("net.minecraft.Util", "net.minecraft.util.Util")
            replace("GuiGraphics", "GuiGraphicsExtractor")
            replace("g.drawString(", "g.text(")
            replace("g.drawCenteredString(", "g.centeredText(")
            replace("g.drawWordWrap(", "g.textWithWordWrap(")
            replace("g.renderItem(", "g.item(")
            replace("g.renderFakeItem(", "g.fakeItem(")
            replace("g.renderItemDecorations(", "g.itemDecorations(")
            replace("g.renderTooltip(", "g.setTooltipForNextFrame(")
            replace("g.renderComponentTooltip(", "g.setComponentTooltipForNextFrame(")
            replace("g.hLine(", "g.horizontalLine(")
            replace("g.vLine(", "g.verticalLine(")
            replace("g.renderOutline(", "g.outline(")
        }

        // 26.2 moved the screen being shown into the game's Gui, with the HUD a Hud inside it, a few
        // registries' constants into classes of their own, and the GPU's primitives out of
        // VertexFormat. Mobs JES only shows are made whatever the difficulty, as a spawner shows its.
        // Only on these nodes, as elsewhere Stonecutter would run the rules backwards, and the blend
        // factors can't be told apart that way.
        if (current.parsed >= "26.2") string(true) {
            // Where 26.3 moved the GPU's classes, as Stonecutter makes every replacement in one go.
            val renderpearl = current.parsed >= "26.3"
            replace("minecraft.setScreen(", "minecraft.gui.setScreen(")
            replace("mc.setScreen(", "mc.gui.setScreen(")
            replace("Minecraft.getInstance().setScreen(", "Minecraft.getInstance().gui.setScreen(")
            replace("minecraft.screen", "minecraft.gui.screen()")
            replace("mc.screen", "mc.gui.screen()")
            replace("Minecraft.getInstance().screen", "Minecraft.getInstance().gui.screen()")
            replace("mc.getOverlay()", "mc.gui.overlay()")
            replace("mc.getMainRenderTarget()", "mc.gameRenderer.mainRenderTarget()")
            replace("minecraft.gui.setOverlayMessage(", "minecraft.gui.hud.setOverlayMessage(")
            replace("minecraft.gui.extractDeferredSubtitles(", "minecraft.gui.hud.extractDeferredSubtitles(")
            replace("EntityType.ARMOR_STAND", "net.minecraft.world.entity.EntityTypes.ARMOR_STAND")
            replace("EntityType.PIG", "net.minecraft.world.entity.EntityTypes.PIG")
            replace("BlockEntityType.MOB_SPAWNER", "net.minecraft.world.level.block.entity.BlockEntityTypes.MOB_SPAWNER")
            replace(".markPosForPostprocessing(", ".markPosForPostProcessing(")
            replace("EntitySpawnReason.LOAD)", "new net.minecraft.world.entity.EntitySpawnRequest(EntitySpawnReason.LOAD, true))")
            replace("VertexFormat.Mode", if (renderpearl) "com.mojang.renderpearl.api.pipeline.PrimitiveTopology" else "com.mojang.blaze3d.PrimitiveTopology")
            replace("VertexFormat.IndexType", if (renderpearl) "com.mojang.renderpearl.api.pipeline.IndexType" else "com.mojang.blaze3d.IndexType")
            replace("drawState().mode()", "drawState().primitiveTopology()")
            // Blend factors for colour and alpha became the one kind, a render pass clears to a colour
            // rather than a packed int, a vertex buffer is bound as a slice of one, draws name how much
            // they draw before where they start, and a buffer is mapped by itself.
            val blendFactor = if (renderpearl) "com.mojang.renderpearl.api.pipeline.BlendFactor" else "com.mojang.blaze3d.platform.BlendFactor"
            replace("com.mojang.blaze3d.platform.SourceFactor", blendFactor)
            replace("com.mojang.blaze3d.platform.DestFactor", blendFactor)
            replace("SourceFactor.", "BlendFactor.")
            replace("DestFactor.", "BlendFactor.")
            replace("OptionalInt.empty(), depth, OptionalDouble.empty()", "java.util.Optional.empty(), depth, OptionalDouble.empty()")
            replace("pass.setVertexBuffer(0, buffer.vertices())", "pass.setVertexBuffer(0, buffer.vertices().slice())")
            replace("pass.setVertexBuffer(0, fill.vertices())", "pass.setVertexBuffer(0, fill.vertices().slice())")
            replace("pass.setVertexBuffer(0, edges.vertices())", "pass.setVertexBuffer(0, edges.vertices().slice())")
            replace("pass.drawIndexed(0, 0, buffer.indexCount(), 1)", "pass.drawIndexed(buffer.indexCount(), 1, 0, 0, 0)")
            replace("pass.drawIndexed(0, 0, fill.count(), 1)", "pass.drawIndexed(fill.count(), 1, 0, 0, 0)")
            replace("pass.draw(0, edges.count())", "pass.draw(edges.count(), 1, 0, 0)")
            replace("getFormat().pixelSize()", "getFormat().blockSize()")
            replace("GpuBuffer.MappedView read = encoder.mapBuffer(buffer, true, false)",
                    (if (renderpearl) "com.mojang.renderpearl.api" else "com.mojang.blaze3d") + ".buffers.GpuBufferSlice.MappedView read = buffer.map(true, false)")
        }

        // 26.3 moved the GPU's classes to a package of their own and binds the projection and the
        // model-view separately. Input comes through SDL rather than GLFW, so the keys JES names by
        // GLFW's constants take the game's own, and structure templates' manager is called that.
        // Only on these nodes, as elsewhere Stonecutter would run the rules backwards.
        if (current.parsed >= "26.3") string(true) {
            replace("import com.mojang.blaze3d.buffers.GpuBuffer;", "import com.mojang.renderpearl.api.buffers.GpuBuffer;")
            replace("import com.mojang.blaze3d.buffers.GpuBufferSlice;", "import com.mojang.renderpearl.api.buffers.GpuBufferSlice;")
            replace("import com.mojang.blaze3d.pipeline.RenderPipeline;", "import com.mojang.renderpearl.api.pipeline.RenderPipeline;")
            replace("import com.mojang.blaze3d.pipeline.BlendFunction;", "import com.mojang.renderpearl.api.pipeline.BlendFunction;")
            replace("import com.mojang.blaze3d.pipeline.ColorTargetState;", "import com.mojang.renderpearl.api.pipeline.ColorTargetState;")
            replace("import com.mojang.blaze3d.pipeline.DepthStencilState;", "import com.mojang.renderpearl.api.pipeline.DepthStencilState;")
            replace("import com.mojang.blaze3d.platform.CompareOp;", "import com.mojang.renderpearl.api.pipeline.CompareOp;")
            replace("import com.mojang.blaze3d.shaders.UniformType;", "import com.mojang.renderpearl.api.pipeline.UniformType;")
            replace("import com.mojang.blaze3d.systems.RenderPass;", "import com.mojang.renderpearl.api.commands.RenderPass;")
            replace("import com.mojang.blaze3d.systems.CommandEncoder;", "import com.mojang.renderpearl.api.commands.CommandEncoder;")
            replace("import com.mojang.blaze3d.systems.GpuDevice;", "import com.mojang.renderpearl.api.device.GpuDevice;")
            replace("import com.mojang.blaze3d.textures.", "import com.mojang.renderpearl.api.textures.")
            replace("import com.mojang.blaze3d.GpuFormat;", "import com.mojang.renderpearl.api.GpuFormat;")
            replace("import com.mojang.blaze3d.vertex.VertexFormat;", "import com.mojang.renderpearl.api.vertex.VertexFormat;")
            replace(".withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)",
                    ".withBindGroupLayout(BindGroupLayouts.PROJECTION).withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)")
            replace("import org.lwjgl.glfw.GLFW;", "import com.mojang.blaze3d.platform.InputConstants;")
            replace("GLFW.GLFW_KEY_LEFT_SUPER", "InputConstants.KEY_LGUI")
            replace("GLFW.GLFW_KEY_RIGHT_SUPER", "InputConstants.KEY_RGUI")
            replace("GLFW.GLFW_MOUSE_BUTTON_4", "InputConstants.MOUSE_BUTTON_4")
            replace("GLFW.GLFW_MOUSE_BUTTON_5", "InputConstants.MOUSE_BUTTON_5")
            replace("GLFW.GLFW_MOD_SHIFT", "InputConstants.MOD_SHIFT")
            replace("GLFW.glfwHideWindow(", "org.lwjgl.sdl.SDLVideo.SDL_HideWindow(")
            replace("InputConstants.Type.KEYSYM", "InputConstants.Type.KEYBOARD")
            replace(".getStructureManager()", ".getStructureTemplateManager()")
            replace("VanillaRegistries.createLookup()", "VanillaRegistries.createWorldLookup()")
            // A pass takes a pipeline once it's compiled, and textures as uniforms. A key event names the
            // key's code rather than its scancode, and whether a key is down needs no window.
            replace("pass.setPipeline(seen ? FACES_SEEN : FACES_THROUGH)", "pass.setPipeline(RenderSystem.getCompiledPipeline(seen ? FACES_SEEN : FACES_THROUGH))")
            replace("pass.setPipeline(seen ? EDGES_SEEN : EDGES_THROUGH)", "pass.setPipeline(RenderSystem.getCompiledPipeline(seen ? EDGES_SEEN : EDGES_THROUGH))")
            replace("pass.setPipeline(pipeline(layer))", "pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline(layer)))")
            replace("pass.bindTexture(", "pass.setUniform(")
            replace("event.scancode()", "event.keycode()")
            replace("InputConstants.isKeyDown(window, ", "InputConstants.isKeyDown(")
            replace("packs.get(0).open()", "packs.get(0).open().findFirst().orElseThrow()")
            // A test names the dimension it runs in.
            replace("new TestData<>(holder, Ids.parse(test.structure())", "new TestData<>(holder, net.minecraft.world.level.Level.OVERWORLD, Ids.parse(test.structure())")
        }

        // Fabric API for 26.1 took the game's own names for these.
        if (loader == "fabric") {
            string(current.parsed >= "26.1") {
                replace("PayloadTypeRegistry.playC2S()", "PayloadTypeRegistry.serverboundPlay()")
                replace("PayloadTypeRegistry.playS2C()", "PayloadTypeRegistry.clientboundPlay()")
                replace("client.keybinding.v1.KeyBindingHelper", "client.keymapping.v1.KeyMappingHelper")
                replace("KeyBindingHelper.registerKeyBinding(", "KeyMappingHelper.registerKeyMapping(")
                replace("Screens.getButtons(", "Screens.getWidgets(")
            }
            // Its game tests take Fabric's own annotation, with the vanilla one's settings under new
            // names. A test's batch becomes the environment it runs in, one for each batch.
            string(current.parsed >= "26.1") {
                replace("import net.minecraft.gametest.framework.GameTest;", "import net.fabricmc.fabric.api.gametest.v1.GameTest;")
                replace("@GameTest(template = ", "@GameTest(structure = ")
                replace("timeoutTicks = ", "maxTicks = ")
                replace("batch = \"", "environment = \"justenoughstructures_gametest:")
            }
        }
        // Forge for 26.1 made ModList all static, and puts its own game test annotation where the game
        // had one, with Fabric's settings. Only on these nodes, as elsewhere Stonecutter would run the
        // rules backwards.
        if (loader == "forge" && current.parsed >= "26.1") {
            string(true) {
                replace("ModList.get().", "ModList.")
                replace("import net.minecraft.gametest.framework.GameTest;", "import net.minecraftforge.gametest.GameTest;")
                replace("@GameTest(template = ", "@GameTest(structure = ")
                replace("timeoutTicks = ", "maxTicks = ")
                replace("batch = \"", "environment = \"justenoughstructures_gametest:")
            }
        }
        // NeoForge for 26.1 has no annotation for game tests, so the test mod has one of its own, with
        // the same settings as Fabric's, and registers what it marks. Only on these nodes, as elsewhere
        // Stonecutter would run the rules backwards.
        if (loader == "neoforge" && current.parsed >= "26.1") {
            string(true) {
                replace("import net.minecraft.gametest.framework.GameTest;", "import com.finndog.justenoughstructures.gametest.neoforge.GameTest;")
                replace("@GameTest(template = ", "@GameTest(structure = ")
                replace("timeoutTicks = ", "maxTicks = ")
                replace("batch = \"", "environment = \"justenoughstructures_gametest:")
            }
        }

        // Explorer's Compass names a few things differently from its Fabric 1.20.1 build. Only the files
        // that use it turn these on, with `//~ compass_names`. Each loader only gets its own: Stonecutter
        // also matches a rule's other side, which would get in the way of another loader's rule. Its
        // NeoForge build names them as its Forge one does, until 26.1.
        if (loader == "forge" || loader == "neoforge" && current.parsed < "26.1") {
            string(true, "compass_names") {
                replace("EXPLORERS_COMPASS_ITEM", "explorersCompass")
                replace("getAllowedStructureIDs", "getAllowedStructureKeys")
                replace("allowedStructureIDs", "allowedStructureKeys")
                replace("getStructureName", "getPrettyStructureName")
                replace("getStructureID", "getStructureKey")
            }
        }
        if (loader == "fabric") {
            string(current.parsed >= "1.21", "compass_names") {
                replace("getStructureID()", "getStructureId()")
                replace("compass.getState(", "compass.getCompassState(")
            }
            // Its build for 26.1 moved its item to another package, and spells ID as Id.
            string(current.parsed >= "26.1", "compass_names") {
                replace("explorerscompass.items.", "explorerscompass.item.")
                replace("getAllowedStructureIDs", "getAllowedStructureIds")
                replace("ExplorersCompass.allowedStructureIDs", "ExplorersCompass.allowedStructures")
            }
        }
        // Its NeoForge build for 26.1 took the Fabric build's names, keeping only its item's own. Only on
        // these nodes, as run backwards on older NeoForge ones it would undo the rule above.
        if (loader == "neoforge" && current.parsed >= "26.1") {
            string(true, "compass_names") {
                replace("EXPLORERS_COMPASS_ITEM", "explorersCompass")
                replace("explorerscompass.items.", "explorerscompass.item.")
                replace("getAllowedStructureIDs", "getAllowedStructureIds")
                replace("ExplorersCompass.allowedStructureIDs", "ExplorersCompass.allowedStructures")
                replace("getStructureID()", "getStructureId()")
                replace("compass.getState(", "compass.getCompassState(")
            }
        }
    }
}
