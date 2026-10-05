import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

// Lets only one node set up its Minecraft at a time. Forge and NeoForge decompile the whole game for
// that, and several at once is more than the machine has memory for.
interface MinecraftSetupMutex : BuildService<BuildServiceParameters.None>

val mutex = gradle.sharedServices.registerIfAbsent("createMinecraftArtifactsMutex", MinecraftSetupMutex::class.java) {
    maxParallelUsages.set(1)
}

tasks.named { it == "createMinecraftArtifacts" }.configureEach {
    usesService(mutex)
}
