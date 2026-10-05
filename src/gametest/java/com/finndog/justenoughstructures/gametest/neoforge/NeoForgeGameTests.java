package com.finndog.justenoughstructures.gametest.neoforge;

//? if >=26.1 {
/*import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.gametest.JesGameTests;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/^*
 * Registers each test {@link JesGameTests} marks with {@link GameTest}, on NeoForge from 26.1. A test
 * there is a function in one registry and its settings in another, and NeoForge only offers a way to
 * add them, not to find them.
 ^/
final class NeoForgeGameTests {
    private static final String NAMESPACE = "justenoughstructures_gametest";
    // For the tests that don't name one. The game's own can't be looked up while tests are added.
    private static final String DEFAULT_ENVIRONMENT = NAMESPACE + ":default";

    private NeoForgeGameTests() {
    }

    static void register(IEventBus modBus) {
        JesGameTests tests = new JesGameTests();
        Map<String, GameTest> settings = new LinkedHashMap<>();
        DeferredRegister<Consumer<GameTestHelper>> functions = DeferredRegister.create(Registries.TEST_FUNCTION, NAMESPACE);
        for (Method method : JesGameTests.class.getMethods()) {
            GameTest test = method.getAnnotation(GameTest.class);
            if (test == null) {
                continue;
            }
            String name = name(method);
            settings.put(name, test);
            functions.register(name, () -> helper -> run(tests, method, helper));
        }
        functions.register(modBus);

        modBus.addListener((RegisterGameTestsEvent event) -> {
            Map<String, Holder<TestEnvironmentDefinition<?>>> environments = new HashMap<>();
            settings.forEach((name, test) -> {
                String environment = test.environment().isEmpty() ? DEFAULT_ENVIRONMENT : test.environment();
                // Every environment here is the same empty one: they only keep their tests apart.
                Holder<TestEnvironmentDefinition<?>> holder = environments.computeIfAbsent(environment,
                        id -> event.registerEnvironment(Ids.parse(id)));
                ResourceLocation id = Ids.of(NAMESPACE, name);
                // Fabric's defaults for the rest, as the tests are written for.
                event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id),
                        new TestData<>(holder, Ids.parse(test.structure()), test.maxTicks(), 0, true, Rotation.NONE, false, 1, 1, false, 1)));
            });
        });
    }

    private static void run(JesGameTests tests, Method method, GameTestHelper helper) {
        try {
            method.invoke(tests, helper);
        } catch (InvocationTargetException e) {
            // The test's own failure, as the game expects to see it.
            if (e.getCause() instanceof RuntimeException failure) {
                throw failure;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    // modIsLoaded as mod_is_loaded, as a registry name must be.
    private static String name(Method method) {
        return method.getName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
*///?}
