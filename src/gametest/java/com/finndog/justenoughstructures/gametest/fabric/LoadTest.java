package com.finndog.justenoughstructures.gametest.fabric;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.LootIndexStore;
import com.finndog.justenoughstructures.server.SavedPreviews;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/**
 * Dev-only: a dedicated server where crowds of simulated players use the browser, to see how a busy
 * server copes. Enabled with {@code -Djes.loadtest=30,60,100}; see the runLoadTest task. It times
 * building every structure and rolling every loot table first, then each crowd browses from empty
 * caches, as after a restart, asking through the same handlers real packets reach. What players would
 * be sent is counted rather than sent, and the results go in report.md and the CSVs beside it.
 */
public final class LoadTest implements DedicatedServerModInitializer {
    private static final int SCREEN = 12;
    private static final int POPULAR = 20;
    private static final long ARRIVALS = seconds(30);
    private static final long GIVE_UP = seconds(30);
    private static final int ODDS_ROLLS = 2000;
    private static final long ODDS_BUDGET = seconds(3);
    private static final List<String> THREADS = List.of("Server thread", "Just Enough Structures capture",
            "Just Enough Structures pictures", "Just Enough Structures loot index");

    private static final Recorder RECORDER = new Recorder();
    // Set and read on the server thread, apart from the phase being swapped.
    private static long tickStarted;
    private static long lastTickEnded;
    private static volatile Phase ticking;

    @Override
    public void onInitializeServer() {
        String crowds = System.getProperty("jes.loadtest", "");
        if (crowds.isBlank()) {
            return;
        }
        ServerTickEvents.START_SERVER_TICK.register(server -> tickStarted = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(server -> tickEnded());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Thread thread = new Thread(() -> run(server, crowds), "Just Enough Structures load test");
            thread.setDaemon(true);
            thread.start();
        });
    }

    private static void tickEnded() {
        long now = System.nanoTime();
        Phase phase = ticking;
        if (phase != null) {
            phase.tick(now - tickStarted, lastTickEnded == 0 ? -1 : now - lastTickEnded);
        }
        lastTickEnded = now;
    }

    private static void run(MinecraftServer server, String crowds) {
        Path dir = Path.of("").toAbsolutePath();
        Report report = new Report(server, crowds);
        try {
            say("Waiting for the loot index");
            waitForIndex();
            report.saved = waitForSavedPreviews();
            RECORDER.install(server);
            report.survey = buildEveryStructure(server);
            report.odds = rollEveryTable(server, report.survey);
            report.phases.add(idle("Idle", seconds(30)));
            double minutes = Double.parseDouble(System.getProperty("jes.loadtest.minutes", "3"));
            for (String size : crowds.split(",")) {
                int players = Integer.parseInt(size.trim());
                report.phases.add(crowd(server, players, report.survey, minutes));
                report.phases.add(idle("Idle after " + players, seconds(20)));
            }
        } catch (Throwable t) {
            report.failure = t;
            JustEnoughStructures.LOGGER.error("The load test failed", t);
        } finally {
            try {
                report.write(dir);
                say("Done, see " + dir.resolve("report.md"));
            } catch (IOException e) {
                JustEnoughStructures.LOGGER.error("Couldn't write the load test report", e);
            }
            server.execute(() -> server.halt(false));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Getting ready

    private static void waitForIndex() throws InterruptedException {
        long started = System.nanoTime();
        long said = started;
        while (true) {
            boolean checking = (boolean) staticField(LootIndexStore.class, "checking");
            boolean building = (boolean) staticField(LootIndexStore.class, "building");
            boolean broken = (boolean) staticField(LootIndexStore.class, "broken");
            boolean ready = staticField(LootIndexStore.class, "payload") != null;
            long waited = System.nanoTime() - started;
            if (!checking && !building && (ready || broken) && waited > seconds(5)) {
                say(ready ? "The loot index is ready" : "The loot index couldn't be built, carrying on without it");
                return;
            }
            if (waited > seconds(90 * 60)) {
                throw new IllegalStateException("The loot index took over 90 minutes");
            }
            if (System.nanoTime() - said > seconds(30)) {
                say("Loot index: " + staticField(LootIndexStore.class, "done") + " of " + staticField(LootIndexStore.class, "total"));
                said = System.nanoTime();
            }
            Thread.sleep(1000);
        }
    }

    /** Waits for every first view to be saved, then says how many there are, how much room they take and how long they took. */
    private static String waitForSavedPreviews() throws InterruptedException {
        long started = System.nanoTime();
        long count = -1;
        long changed = started;
        while (true) {
            Path dir = SavedPreviews.current();
            long now = System.nanoTime();
            if (dir == null) {
                if (now - started > seconds(60)) {
                    return "not in use";
                }
            } else {
                long files = savedFiles(dir).filter(file -> !file.toString().endsWith(".picture.bin")).count();
                if (files != count) {
                    count = files;
                    changed = now;
                    say("Saved first views: " + files);
                } else if (now - changed > seconds(10) && !filling()) {
                    long bytes = savedFiles(dir).mapToLong(file -> file.toFile().length()).sum();
                    long pictures = savedFiles(dir).filter(file -> file.toString().endsWith(".picture.bin")).mapToLong(file -> file.toFile().length()).sum();
                    return String.format(Locale.ROOT, "%d, taking %.1f MB with their list pictures (%.1f MB of it), all saved %d s after the loot index was ready",
                            count, bytes / 1048576.0, pictures / 1048576.0, (changed - started) / 1_000_000_000L);
                }
            }
            Thread.sleep(2000);
        }
    }

    /** Whether the thread that fills in the saved first views is making one, rather than waiting for work. */
    private static boolean filling() {
        for (Map.Entry<Thread, StackTraceElement[]> thread : Thread.getAllStackTraces().entrySet()) {
            if (thread.getKey().getName().equals("Just Enough Structures filling saved previews")) {
                for (StackTraceElement frame : thread.getValue()) {
                    if (frame.getClassName().startsWith("com.finndog.justenoughstructures")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Stream<Path> savedFiles(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(file -> file.toString().endsWith(".bin")).toList().stream();
        } catch (IOException e) {
            return Stream.empty();
        }
    }

    /** Every structure built once, as its first preview would be, with how long that took and how big it is to send. */
    private static List<Built> buildEveryStructure(MinecraftServer server) {
        List<ResourceLocation> ids = server.registryAccess().registryOrThrow(Registries.STRUCTURE).keySet().stream()
                .sorted(Comparator.comparing(ResourceLocation::toString)).toList();
        List<Built> built = new ArrayList<>();
        for (ResourceLocation id : ids) {
            if (built.size() % 25 == 0) {
                say("Building every structure once: " + built.size() + " of " + ids.size());
            }
            long seed = StructureCapture.defaultSeed(id);
            long started = System.nanoTime();
            CaptureResult result;
            try {
                result = StructureCapture.capture(server, id, seed);
            } catch (RuntimeException | LinkageError e) {
                built.add(new Built(id, false, 0, 0, System.nanoTime() - started, 0, List.of(), "threw " + e));
                continue;
            }
            long nanos = System.nanoTime() - started;
            if (!result.succeeded()) {
                built.add(new Built(id, false, 0, 0, nanos, 0, List.of(), result.error()));
                continue;
            }
            CaptureResult sent = JesServer.forPlayers(id, result);
            int bytes = Blobs.deflate(Blobs.toBytes(server.registryAccess(), buf -> Codecs.writeCapture(buf, id, seed, sent))).length;
            List<ResourceLocation> tables = result.snapshot().containers().stream()
                    .map(StructureSnapshot.Container::lootTable)
                    .filter(Objects::nonNull)
                    .map(ResourceLocation::tryParse)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            built.add(new Built(id, true, result.snapshot().blockCount(), result.snapshot().containers().size(), nanos, bytes, tables, ""));
        }
        return built;
    }

    /** Every table the structures use rolled for its odds, on the server thread, as the browser's Loot tab has them rolled. */
    private static List<Rolled> rollEveryTable(MinecraftServer server, List<Built> built) throws Exception {
        List<ResourceLocation> tables = built.stream().flatMap(b -> b.tables().stream()).distinct()
                .sorted(Comparator.comparing(ResourceLocation::toString)).toList();
        say("Rolling the odds of " + tables.size() + " loot tables");
        List<Rolled> rolled = new ArrayList<>();
        for (ResourceLocation table : tables) {
            rolled.add(CompletableFuture.supplyAsync(() -> {
                long started = System.nanoTime();
                try {
                    LootRolls.Roller roller = new LootRolls.Roller(server.overworld(), table, ODDS_ROLLS, table.hashCode());
                    boolean finished = roller.rollFor(ODDS_BUDGET);
                    return new Rolled(table, roller.odds().rolls(), finished, System.nanoTime() - started, "");
                } catch (RuntimeException | LinkageError | StackOverflowError e) {
                    return new Rolled(table, 0, false, System.nanoTime() - started, String.valueOf(e));
                }
            }, server).get(1, TimeUnit.MINUTES));
        }
        return rolled;
    }

    // ---------------------------------------------------------------------------------------------
    // Phases

    private static Phase idle(String name, long nanos) throws InterruptedException {
        say(name);
        Phase phase = new Phase(name, 0);
        phase.begin();
        long end = System.nanoTime() + nanos;
        while (System.nanoTime() < end) {
            phase.sampleHeap();
            Thread.sleep(500);
        }
        phase.end();
        return phase;
    }

    private static Phase crowd(MinecraftServer server, int players, List<Built> built, double minutes) throws Exception {
        say(players + " players browsing for " + minutes + " minutes");
        // Previews, the structure list and odds start over, as they do when a server restarts.
        CompletableFuture.runAsync(JesServer::invalidate, server).get(1, TimeUnit.MINUTES);
        Phase phase = new Phase(players + " players", players);
        Crowd crowd = new Crowd(server, phase, built, new Random(players));
        List<Sim> sims = join(server, players);
        sims.forEach(sim -> crowd.sims.put(sim.player.getUUID(), sim));
        RECORDER.phase = phase;
        sims.forEach(sim -> RECORDER.sims.put(sim.player.getUUID(), sim));
        phase.begin();
        for (Sim sim : sims) {
            sim.next = phase.started + (long) (crowd.random.nextDouble() * ARRIVALS);
        }
        long activeUntil = phase.started + (long) (minutes * 60e9);
        long drainUntil = activeUntil + seconds(180);
        long heapAt = 0;
        while (true) {
            long now = System.nanoTime();
            for (Done done; (done = RECORDER.done.poll()) != null; ) {
                crowd.finished(done);
            }
            if (now < activeUntil) {
                for (Sim sim : sims) {
                    sim.act(now, crowd);
                }
            } else if (sims.stream().allMatch(sim -> sim.waiting.isEmpty()) || now > drainUntil) {
                break;
            }
            if (now - heapAt > seconds(1) / 2) {
                phase.sampleHeap();
                heapAt = now;
            }
            Thread.sleep(10);
        }
        phase.unanswered = sims.stream().mapToInt(sim -> sim.waiting.size()).sum();
        phase.end();
        RECORDER.phase = null;
        leave(server, sims);
        return phase;
    }

    private static List<Sim> join(MinecraftServer server, int players) throws Exception {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, ServerPlayer> byId = playersById(server.getPlayerList());
            List<Sim> sims = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                String name = "jes_load_" + i;
                GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name);
                ServerPlayer player = FakePlayer.get(server.overworld(), profile);
                // Looked up by id like an online player, without joining the world.
                byId.put(player.getUUID(), player);
                sims.add(new Sim(i, player));
            }
            return sims;
        }, server).get(1, TimeUnit.MINUTES);
    }

    private static void leave(MinecraftServer server, List<Sim> sims) throws Exception {
        CompletableFuture.runAsync(() -> {
            Map<UUID, ServerPlayer> byId = playersById(server.getPlayerList());
            for (Sim sim : sims) {
                JesServer.left(sim.player);
                byId.remove(sim.player.getUUID());
            }
        }, server).get(1, TimeUnit.MINUTES);
        sims.forEach(sim -> RECORDER.sims.remove(sim.player.getUUID()));
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> playersById(PlayerList list) {
        for (Field field : PlayerList.class.getDeclaredFields()) {
            if (Map.class.isAssignableFrom(field.getType()) && field.getGenericType() instanceof ParameterizedType type) {
                Type[] args = type.getActualTypeArguments();
                if (args.length == 2 && args[0] == UUID.class && args[1] == ServerPlayer.class) {
                    try {
                        field.setAccessible(true);
                        return (Map<UUID, ServerPlayer>) field.get(list);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }
        throw new IllegalStateException("PlayerList has no map of players by id");
    }

    // ---------------------------------------------------------------------------------------------
    // The simulated players

    private enum Kind { CATALOG, PREVIEW, NEW_LAYOUT, PICTURE, ODDS }

    private enum Outcome { OK, SUPERSEDED, TOO_MANY, NO_MEMORY, BUSY, FAILED }

    private record Req(Kind kind, ResourceLocation id, long at) {
    }

    private record Done(UUID player, int requestId, long at, long bytes, Outcome outcome) {
    }

    /**
     * A player new to the server: arrives in the first half minute and sees a screen of list pictures,
     * then every 10 to 25 seconds picks a structure, half the time a popular one, and gives up on it
     * after 30. It looks at the odds of half of them, asks one in ten for a new layout and scrolls
     * three in ten.
     */
    private static final class Sim {
        final int number;
        final ServerPlayer player;
        /** What it's asked for and not yet been sent, by request id. The structure list is always 0. */
        final Map<Integer, Req> waiting = new HashMap<>();
        final ArrayDeque<ResourceLocation> pictures = new ArrayDeque<>();
        int lastRequest;
        boolean arrived;
        long arrivedAt;
        boolean seenFirst;
        long next;
        int preview = -1;
        long previewAt;
        ResourceLocation showing;
        long newLayoutAt = -1;
        int picture = -1;
        long pictureAgainAt;
        int pictureTries;
        int scrolled;

        Sim(int number, ServerPlayer player) {
            this.number = number;
            this.player = player;
        }

        void act(long now, Crowd crowd) {
            if (!arrived) {
                if (now >= next) {
                    arrived = true;
                    arrivedAt = now;
                    crowd.askForList(this, now);
                    scroll(crowd);
                    next = now + crowd.between(2, 6);
                }
                return;
            }
            // One picture at a time, and none while its own preview is on the way, as the browser does.
            if (picture < 0 && preview < 0 && !pictures.isEmpty() && now >= pictureAgainAt) {
                ResourceLocation id = pictures.peek();
                picture = crowd.askForCapture(this, Kind.PICTURE, id, StructureCapture.defaultSeed(id), now);
            }
            if (newLayoutAt > 0 && now >= newLayoutAt && preview < 0 && showing != null) {
                newLayoutAt = -1;
                preview = crowd.askForCapture(this, Kind.NEW_LAYOUT, showing, crowd.random.nextLong(), now);
                previewAt = now;
            }
            if (now >= next) {
                if (preview >= 0 && now - previewAt < GIVE_UP) {
                    next = now + seconds(1);
                    return;
                }
                if (preview >= 0) {
                    crowd.phase.gaveUp++;
                }
                showing = crowd.pick();
                preview = crowd.askForCapture(this, Kind.PREVIEW, showing, StructureCapture.defaultSeed(showing), now);
                previewAt = now;
                if (crowd.random.nextDouble() < 0.3) {
                    scroll(crowd);
                }
                newLayoutAt = crowd.random.nextDouble() < 0.1 ? now + seconds(5) : -1;
                next = now + crowd.between(10, 25);
            }
        }

        void scroll(Crowd crowd) {
            for (int i = 0; i < SCREEN && scrolled < crowd.list.size(); i++) {
                pictures.add(crowd.list.get(scrolled++));
            }
        }
    }

    /** One crowd's world: who's in it, what they can look at, and what happens when an answer comes. */
    private static final class Crowd {
        final MinecraftServer server;
        final Phase phase;
        final Random random;
        final List<ResourceLocation> list;
        final List<ResourceLocation> popular;
        final Map<ResourceLocation, List<ResourceLocation>> tables = new HashMap<>();
        final Map<UUID, Sim> sims = new HashMap<>();

        Crowd(MinecraftServer server, Phase phase, List<Built> built, Random random) {
            this.server = server;
            this.phase = phase;
            this.random = random;
            this.list = built.stream().map(Built::id).toList();
            List<ResourceLocation> shown = new ArrayList<>(built.stream().filter(Built::ok).map(Built::id).toList());
            Collections.shuffle(shown, random);
            this.popular = List.copyOf(shown.subList(0, Math.min(POPULAR, shown.size())));
            built.forEach(b -> tables.put(b.id(), b.tables()));
        }

        long between(int fromSeconds, int toSeconds) {
            return seconds(fromSeconds) + (long) (random.nextDouble() * seconds(toSeconds - fromSeconds));
        }

        ResourceLocation pick() {
            return random.nextBoolean() && !popular.isEmpty() ? popular.get(random.nextInt(popular.size())) : list.get(random.nextInt(list.size()));
        }

        void askForList(Sim sim, long now) {
            sim.waiting.put(0, new Req(Kind.CATALOG, null, now));
            phase.asked(Kind.CATALOG);
            server.execute(() -> JesServer.onRequestCatalog(sim.player));
        }

        int askForCapture(Sim sim, Kind kind, ResourceLocation id, long seed, long now) {
            int requestId = ++sim.lastRequest;
            sim.waiting.put(requestId, new Req(kind, id, now));
            phase.asked(kind);
            boolean preview = kind != Kind.PICTURE;
            server.execute(() -> JesServer.onRequestCapture(sim.player, requestId, id, seed, preview, 0L));
            return requestId;
        }

        void askForOdds(Sim sim, ResourceLocation table, long now) {
            int requestId = ++sim.lastRequest;
            sim.waiting.put(requestId, new Req(Kind.ODDS, table, now));
            phase.asked(Kind.ODDS);
            server.execute(() -> JesServer.onRequestOdds(sim.player, requestId, table));
        }

        void finished(Done done) {
            Sim sim = sims.get(done.player());
            Req req = sim == null ? null : sim.waiting.remove(done.requestId());
            if (req == null) {
                return;
            }
            phase.answered(sim, req, done);
            switch (req.kind()) {
                case PREVIEW, NEW_LAYOUT -> {
                    if (done.requestId() == sim.preview) {
                        sim.preview = -1;
                    }
                    if (req.kind() == Kind.PREVIEW && done.outcome() == Outcome.OK) {
                        if (!sim.seenFirst) {
                            sim.seenFirst = true;
                            phase.firstPreview.add(done.at() - sim.arrivedAt);
                        }
                        if (random.nextBoolean()) {
                            List<ResourceLocation> its = tables.getOrDefault(req.id(), List.of());
                            its.stream().limit(2).forEach(table -> askForOdds(sim, table, done.at()));
                        }
                    }
                }
                case PICTURE -> {
                    sim.picture = -1;
                    boolean again = done.outcome() != Outcome.OK && done.outcome() != Outcome.FAILED && ++sim.pictureTries < 3;
                    if (again) {
                        sim.pictureAgainAt = done.at() + seconds(1);
                    } else {
                        sim.pictures.poll();
                        sim.pictureTries = 0;
                    }
                }
                default -> {
                }
            }
        }
    }

    /** Stands in for the network: counts what the simulated players would be sent and when each answer is complete. */
    private static final class Recorder implements JesNetwork.ServerSender {
        final Map<UUID, Sim> sims = new ConcurrentHashMap<>();
        final ConcurrentLinkedQueue<Done> done = new ConcurrentLinkedQueue<>();
        // Server thread only.
        final Map<Integer, Long> transfers = new HashMap<>();
        volatile Phase phase;
        private MinecraftServer server;

        void install(MinecraftServer server) {
            this.server = server;
            JesNetwork.setServerSender(this);
            JesNetwork.setServerCanSend((player, channel) -> true);
        }

        @Override
        public void send(ServerPlayer player, ResourceLocation channel, FriendlyByteBuf buf) {
            long now = System.nanoTime();
            if (!sims.containsKey(player.getUUID())) {
                return;
            }
            int bytes = buf.readableBytes();
            Phase current = phase;
            if (current != null) {
                current.sent.addAndGet(bytes);
            }
            if (channel.equals(JesNetwork.TRANSFER)) {
                Blobs.Part part = Blobs.Part.read(buf);
                long total = transfers.merge(part.transferId(), (long) part.data().length, Long::sum);
                if (part.index() < part.count() - 1) {
                    return;
                }
                transfers.remove(part.transferId());
                Outcome outcome = part.kind() == JesNetwork.KIND_CAPTURE && part.count() == 1 && part.data().length < 4096
                        ? outcomeOf(part.data()) : Outcome.OK;
                done.add(new Done(player.getUUID(), part.kind() == JesNetwork.KIND_CATALOG ? 0 : part.requestId(), now, total, outcome));
            } else if (channel.equals(JesNetwork.ODDS)) {
                done.add(new Done(player.getUUID(), buf.readVarInt(), now, bytes, Outcome.OK));
            }
        }

        /** What a small capture answer says: only failures and the very smallest structures are this small. */
        private Outcome outcomeOf(byte[] compressed) {
            try {
                FriendlyByteBuf buf = Blobs.buffer(server.registryAccess());
                buf.writeBytes(Blobs.inflate(compressed));
                CaptureResult result = Codecs.readCapture(buf).result();
                if (result.succeeded()) {
                    return Outcome.OK;
                }
                if (!result.temporary()) {
                    return Outcome.FAILED;
                }
                String key = result.reason() != null && result.reason().getContents() instanceof TranslatableContents t ? t.getKey() : "";
                if (key.endsWith(".superseded")) {
                    return Outcome.SUPERSEDED;
                }
                if (key.endsWith(".too_many")) {
                    return Outcome.TOO_MANY;
                }
                if (key.endsWith(".low_memory") || key.endsWith(".out_of_memory")) {
                    return Outcome.NO_MEMORY;
                }
                return Outcome.BUSY;
            } catch (RuntimeException e) {
                return Outcome.OK;
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // What's measured

    private record Built(ResourceLocation id, boolean ok, int blocks, int containers, long nanos, int bytes,
                         List<ResourceLocation> tables, String error) {
    }

    private record Rolled(ResourceLocation table, int rolls, boolean finished, long nanos, String error) {
    }

    private static final class Phase {
        final String name;
        final int players;
        long started;
        long ended;
        final List<Long> ticks = new ArrayList<>();
        final List<Long> gaps = new ArrayList<>();
        final AtomicLong sent = new AtomicLong();
        long heapMost;
        long heapTotal;
        int heapSamples;
        final Map<Kind, Integer> asked = new EnumMap<>(Kind.class);
        final Map<Kind, Map<Outcome, Integer>> outcomes = new EnumMap<>(Kind.class);
        final Map<Kind, List<Long>> waits = new EnumMap<>(Kind.class);
        final List<Long> firstPreview = new ArrayList<>();
        final List<String> rows = new ArrayList<>();
        int gaveUp;
        int unanswered;
        Map<String, Long> cpuAtStart = Map.of();
        Map<String, Long> cpu = Map.of();

        Phase(String name, int players) {
            this.name = name;
            this.players = players;
        }

        void begin() {
            cpuAtStart = cpuTimes();
            started = System.nanoTime();
            ticking = this;
        }

        void end() {
            ticking = null;
            ended = System.nanoTime();
            Map<String, Long> now = cpuTimes();
            Map<String, Long> used = new HashMap<>();
            now.forEach((thread, nanos) -> used.put(thread, nanos - cpuAtStart.getOrDefault(thread, 0L)));
            cpu = used;
        }

        synchronized void tick(long nanos, long gap) {
            ticks.add(nanos);
            if (gap >= 0) {
                gaps.add(gap);
            }
        }

        void sampleHeap() {
            Runtime runtime = Runtime.getRuntime();
            long used = runtime.totalMemory() - runtime.freeMemory();
            heapMost = Math.max(heapMost, used);
            heapTotal += used;
            heapSamples++;
        }

        void asked(Kind kind) {
            asked.merge(kind, 1, Integer::sum);
        }

        void answered(Sim sim, Req req, Done done) {
            long wait = done.at() - req.at();
            outcomes.computeIfAbsent(req.kind(), k -> new EnumMap<>(Outcome.class)).merge(done.outcome(), 1, Integer::sum);
            if (done.outcome() == Outcome.OK) {
                waits.computeIfAbsent(req.kind(), k -> new ArrayList<>()).add(wait);
            }
            rows.add(String.join(",", csv(name), String.valueOf(sim.number), req.kind().name().toLowerCase(Locale.ROOT),
                    csv(req.id() == null ? "" : req.id().toString()), ms(req.at() - started), ms(wait),
                    done.outcome().name().toLowerCase(Locale.ROOT), String.valueOf(done.bytes())));
        }

        int count(Kind kind, Outcome outcome) {
            return outcomes.getOrDefault(kind, Map.of()).getOrDefault(outcome, 0);
        }

        List<Long> waits(Kind kind) {
            return waits.getOrDefault(kind, List.of());
        }
    }

    private static Map<String, Long> cpuTimes() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        Map<String, Long> times = new HashMap<>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (THREADS.contains(thread.getName())) {
                times.merge(thread.getName(), Math.max(0, bean.getThreadCpuTime(thread.getId())), Long::sum);
            }
        }
        return times;
    }

    // ---------------------------------------------------------------------------------------------
    // The report

    private static final class Report {
        final MinecraftServer server;
        final String crowds;
        String saved = "not in use";
        List<Built> survey = List.of();
        List<Rolled> odds = List.of();
        final List<Phase> phases = new ArrayList<>();
        Throwable failure;

        Report(MinecraftServer server, String crowds) {
            this.server = server;
            this.crowds = crowds;
        }

        void write(Path dir) throws IOException {
            StringBuilder md = new StringBuilder();
            String minecraft = FabricLoader.getInstance().getModContainer("minecraft")
                    .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
            md.append("# Load test\n\n");
            md.append("- Run ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append(" on Minecraft ")
                    .append(minecraft).append(" Fabric, ").append(FabricLoader.getInstance().getAllMods().size()).append(" mods loaded\n");
            md.append("- ").append(Runtime.getRuntime().availableProcessors()).append(" CPU threads, ")
                    .append(Runtime.getRuntime().maxMemory() >> 20).append(" MB most memory, ")
                    .append(System.getProperty("os.name")).append(", Java ").append(System.getProperty("java.version")).append('\n');
            md.append("- Crowds: ").append(crowds).append(", each browsing for ").append(System.getProperty("jes.loadtest.minutes", "3"))
                    .append(" minutes\n");
            md.append("- Saved first views: ").append(saved).append('\n');
            if (failure != null) {
                md.append("\n**The test stopped early:** ").append(failure).append("\n");
            }

            List<Built> ok = survey.stream().filter(Built::ok).toList();
            md.append("\n## Building every structure once\n\n");
            md.append("What every structure's first preview costs. These are the default views a saved preview cache would hold.\n\n");
            md.append("| | |\n|---|---|\n");
            md.append("| Structures | ").append(survey.size()).append(" (").append(ok.size()).append(" built, ")
                    .append(survey.size() - ok.size()).append(" not)\n");
            md.append("| All default views, compressed as sent | ").append(mb(ok.stream().mapToLong(Built::bytes).sum())).append(" MB\n");
            md.append("| Size each (median / 95th percentile / largest) | ").append(kb(pctl(ok.stream().map(b -> (long) b.bytes()).toList(), 50)))
                    .append(" / ").append(kb(pctl(ok.stream().map(b -> (long) b.bytes()).toList(), 95))).append(" / ")
                    .append(kb(ok.stream().mapToLong(Built::bytes).max().orElse(0))).append(" KB\n");
            md.append("| Build time each (median / 95th / longest) | ").append(ms(pctl(ok.stream().map(Built::nanos).toList(), 50))).append(" / ")
                    .append(ms(pctl(ok.stream().map(Built::nanos).toList(), 95))).append(" / ")
                    .append(ms(ok.stream().mapToLong(Built::nanos).max().orElse(0))).append(" ms\n");
            md.append("| All of them, one after another | ").append(String.format(Locale.ROOT, "%.1f", survey.stream().mapToLong(Built::nanos).sum() / 60e9))
                    .append(" minutes\n");
            md.append("\nLargest:\n\n");
            ok.stream().sorted(Comparator.comparingInt(Built::bytes).reversed()).limit(10).forEach(b -> md.append("- ").append(b.id())
                    .append(": ").append(kb(b.bytes())).append(" KB, ").append(b.blocks()).append(" blocks, ").append(ms(b.nanos())).append(" ms\n"));
            md.append("\nSlowest to build:\n\n");
            ok.stream().sorted(Comparator.comparingLong(Built::nanos).reversed()).limit(10).forEach(b -> md.append("- ").append(b.id())
                    .append(": ").append(ms(b.nanos())).append(" ms, ").append(b.blocks()).append(" blocks\n"));

            md.append("\n## Rolling loot odds\n\n");
            md.append("Rolled on the server thread, ").append(ODDS_ROLLS).append(" rolls a table, at most ")
                    .append(ODDS_BUDGET / 1_000_000_000L).append(" s a table. The browser rolls them in 10 ms slices.\n\n");
            List<Long> rollTimes = odds.stream().map(Rolled::nanos).toList();
            md.append("- ").append(odds.size()).append(" tables: median ").append(ms(pctl(rollTimes, 50))).append(" ms, 95th percentile ")
                    .append(ms(pctl(rollTimes, 95))).append(" ms, slowest ").append(ms(rollTimes.stream().mapToLong(Long::longValue).max().orElse(0)))
                    .append(" ms, all of them ").append(ms(rollTimes.stream().mapToLong(Long::longValue).sum())).append(" ms\n");
            md.append("- Tables that ran out of time: ").append(odds.stream().filter(r -> !r.finished()).count()).append('\n');
            md.append("\nSlowest:\n\n");
            odds.stream().sorted(Comparator.comparingLong(Rolled::nanos).reversed()).limit(10).forEach(r -> md.append("- ").append(r.table())
                    .append(": ").append(ms(r.nanos())).append(" ms for ").append(r.rolls()).append(" rolls\n"));

            md.append("\n## What players saw\n\n");
            md.append("Waits are from asking to the whole answer being ready to send. Only answers that arrived count.\n\n");
            md.append("| Crowd | Previews asked | Preview wait (median / 90th / longest) | First preview after joining (median / longest) "
                    + "| Superseded | Gave up waiting | List pictures (made / asked) | Picture wait (median / 90th) | Turned away | Odds wait (median / longest) | Unanswered |\n");
            md.append("|---|---|---|---|---|---|---|---|---|---|---|\n");
            for (Phase p : phases) {
                if (p.players == 0) {
                    continue;
                }
                List<Long> previews = new ArrayList<>(p.waits(Kind.PREVIEW));
                previews.addAll(p.waits(Kind.NEW_LAYOUT));
                int previewsAsked = p.asked.getOrDefault(Kind.PREVIEW, 0) + p.asked.getOrDefault(Kind.NEW_LAYOUT, 0);
                int superseded = p.count(Kind.PREVIEW, Outcome.SUPERSEDED) + p.count(Kind.NEW_LAYOUT, Outcome.SUPERSEDED);
                int turnedAway = 0;
                for (Kind kind : Kind.values()) {
                    turnedAway += p.count(kind, Outcome.TOO_MANY) + p.count(kind, Outcome.NO_MEMORY) + p.count(kind, Outcome.BUSY);
                }
                md.append("| ").append(p.name).append(" | ").append(previewsAsked)
                        .append(" | ").append(sec(pctl(previews, 50))).append(" / ").append(sec(pctl(previews, 90))).append(" / ").append(sec(most(previews)))
                        .append(" | ").append(sec(pctl(p.firstPreview, 50))).append(" / ").append(sec(most(p.firstPreview)))
                        .append(" | ").append(superseded).append(" | ").append(p.gaveUp)
                        .append(" | ").append(p.count(Kind.PICTURE, Outcome.OK)).append(" / ").append(p.asked.getOrDefault(Kind.PICTURE, 0))
                        .append(" | ").append(sec(pctl(p.waits(Kind.PICTURE), 50))).append(" / ").append(sec(pctl(p.waits(Kind.PICTURE), 90)))
                        .append(" | ").append(turnedAway)
                        .append(" | ").append(sec(pctl(p.waits(Kind.ODDS), 50))).append(" / ").append(sec(most(p.waits(Kind.ODDS))))
                        .append(" | ").append(p.unanswered).append(" |\n");
            }
            md.append("\nWaits are in seconds.\n");

            md.append("\n## What the server felt\n\n");
            md.append("A tick is the server's own work; the gap is from one tick ending to the next, which includes anything run between ticks. 20 TPS means 50 ms gaps.\n\n");
            md.append("| Phase | Tick ms (median / 95th / 99th / longest) | Ticks over 50 ms | TPS | Longest gap ms | Most memory MB | Sent MB "
                    + "| Sent per player per minute MB | CPU seconds: capture / pictures / loot index / server thread |\n");
            md.append("|---|---|---|---|---|---|---|---|---|\n");
            for (Phase p : phases) {
                double minutes = (p.ended - p.started) / 60e9;
                double meanGap = p.gaps.stream().mapToLong(Long::longValue).average().orElse(0);
                md.append("| ").append(p.name)
                        .append(" | ").append(ms1(pctl(p.ticks, 50))).append(" / ").append(ms1(pctl(p.ticks, 95))).append(" / ")
                        .append(ms1(pctl(p.ticks, 99))).append(" / ").append(ms1(most(p.ticks)))
                        .append(" | ").append(p.ticks.stream().filter(t -> t > 50_000_000L).count())
                        .append(" | ").append(meanGap == 0 ? "?" : String.format(Locale.ROOT, "%.1f", Math.min(20, 1e9 / meanGap)))
                        .append(" | ").append(ms(most(p.gaps)))
                        .append(" | ").append(p.heapMost >> 20)
                        .append(" | ").append(mb(p.sent.get()))
                        .append(" | ").append(p.players == 0 ? "" : String.format(Locale.ROOT, "%.2f", p.sent.get() / 1048576.0 / p.players / minutes))
                        .append(" | ").append(THREADS.stream().skip(1).map(t -> String.format(Locale.ROOT, "%.1f", p.cpu.getOrDefault(t, 0L) / 1e9))
                                .reduce((a, b) -> a + " / " + b).orElse("")).append(" / ")
                        .append(String.format(Locale.ROOT, "%.1f", p.cpu.getOrDefault(THREADS.get(0), 0L) / 1e9))
                        .append(" |\n");
            }
            Files.writeString(dir.resolve("report.md"), md.toString());

            StringBuilder built = new StringBuilder("structure,ok,blocks,containers,tables,build_ms,bytes,error\n");
            survey.forEach(b -> built.append(csv(b.id().toString())).append(',').append(b.ok()).append(',').append(b.blocks()).append(',')
                    .append(b.containers()).append(',').append(b.tables().size()).append(',').append(ms(b.nanos())).append(',').append(b.bytes())
                    .append(',').append(csv(b.error() == null ? "" : b.error())).append('\n'));
            Files.writeString(dir.resolve("structures.csv"), built.toString());

            StringBuilder rolled = new StringBuilder("table,rolls,finished,ms,error\n");
            odds.forEach(r -> rolled.append(csv(r.table().toString())).append(',').append(r.rolls()).append(',').append(r.finished()).append(',')
                    .append(ms(r.nanos())).append(',').append(csv(r.error())).append('\n'));
            Files.writeString(dir.resolve("odds.csv"), rolled.toString());

            StringBuilder requests = new StringBuilder("phase,player,kind,id,asked_at_ms,wait_ms,outcome,bytes\n");
            StringBuilder ticks = new StringBuilder("phase,tick_ms,gap_ms\n");
            for (Phase p : phases) {
                p.rows.forEach(row -> requests.append(row).append('\n'));
                for (int i = 0; i < p.ticks.size(); i++) {
                    ticks.append(csv(p.name)).append(',').append(ms1(p.ticks.get(i))).append(',')
                            .append(i < p.gaps.size() ? ms1(p.gaps.get(i)) : "").append('\n');
                }
            }
            Files.writeString(dir.resolve("requests.csv"), requests.toString());
            Files.writeString(dir.resolve("ticks.csv"), ticks.toString());
        }
    }

    // ---------------------------------------------------------------------------------------------

    private static Object staticField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Can't read " + owner.getSimpleName() + "." + name, e);
        }
    }

    private static long pctl(List<Long> values, double percentile) {
        if (values.isEmpty()) {
            return -1;
        }
        List<Long> sorted = values.stream().sorted().toList();
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    private static long most(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).max().orElse(-1);
    }

    private static long seconds(long seconds) {
        return TimeUnit.SECONDS.toNanos(seconds);
    }

    private static String ms(long nanos) {
        return nanos < 0 ? "-" : String.valueOf(nanos / 1_000_000L);
    }

    private static String ms1(long nanos) {
        return nanos < 0 ? "-" : String.format(Locale.ROOT, "%.1f", nanos / 1e6);
    }

    private static String sec(long nanos) {
        return nanos < 0 ? "-" : String.format(Locale.ROOT, "%.2f", nanos / 1e9);
    }

    private static String kb(long bytes) {
        return bytes < 0 ? "-" : String.format(Locale.ROOT, "%.1f", bytes / 1024.0);
    }

    private static String mb(long bytes) {
        return String.format(Locale.ROOT, "%.1f", bytes / 1048576.0);
    }

    private static String csv(String value) {
        return value.contains(",") || value.contains("\"") ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }

    private static void say(String message) {
        JustEnoughStructures.LOGGER.info("[load test] {}", message);
    }
}
