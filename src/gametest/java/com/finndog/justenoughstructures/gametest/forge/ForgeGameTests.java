package com.finndog.justenoughstructures.gametest.forge;

//? if >=26.1 {
/*import com.finndog.justenoughstructures.gametest.JesGameTests;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.registries.DeferredRegister;

/^*
 * Registers the tests in {@link JesGameTests} on Forge from 26.1, which finds them by its annotation
 * but leaves registering them to the mod. Each test's function goes in the game's registry for them,
 * and its settings in the registry of tests loaded with the world's data, as that registry is about
 * to be frozen, which the game test mixin reports.
 ^/
public final class ForgeGameTests {
    private static Map<ResourceLocation, ForgeGameTestHooks.TestReference> tests = Map.of();
    private static Registry<TestEnvironmentDefinition<?>> environments;

    private ForgeGameTests() {
    }

    static void register(BusGroup modBus) {
        if (!ForgeGameTestHooks.isGametestEnabled()) {
            return;
        }
        tests = ForgeGameTestHooks.gatherTests(JesGameTests.class, new JesGameTests());
        DeferredRegister<Consumer<GameTestHelper>> functions = DeferredRegister.create(Registries.TEST_FUNCTION, "justenoughstructures_gametest");
        tests.forEach((id, test) -> functions.register(id.getPath(), test::consumer));
        functions.register(modBus);
    }

    /^* Called as each registry loaded with the world's data is about to be frozen. ^/
    @SuppressWarnings("unchecked")
    public static void beforeFreeze(WritableRegistry<?> registry) {
        if (tests.isEmpty()) {
            return;
        }
        if (registry.key().equals(Registries.TEST_ENVIRONMENT)) {
            // Frozen just before the tests', whose environments are looked up in it.
            environments = (Registry<TestEnvironmentDefinition<?>>) registry;
        } else if (registry.key().equals(Registries.TEST_INSTANCE) && environments != null) {
            WritableRegistry<GameTestInstance> instances = (WritableRegistry<GameTestInstance>) registry;
            tests.forEach((id, test) -> instances.register(ResourceKey.create(Registries.TEST_INSTANCE, id),
                    new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id),
                            test.data().<Holder<TestEnvironmentDefinition<?>>>map(environment ->
                                    environments.getOrThrow(ResourceKey.create(Registries.TEST_ENVIRONMENT, environment)))),
                    RegistrationInfo.BUILT_IN));
        }
    }
}
*///?}
