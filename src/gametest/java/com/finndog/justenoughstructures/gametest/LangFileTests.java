package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.gametest.framework.GameTestHelper;

/** JES's English lang file, which every other language falls back on. */
public final class LangFileTests {
    private static final String LANG = "assets/justenoughstructures/lang/en_us.json";
    /** Where the mod's code is, below the checkout's root. */
    private static final String SOURCES = "src/main/java/com/finndog/justenoughstructures";
    /** A JES lang key written out whole in quotes. One followed by + is the start of a key built in code. */
    private static final Pattern KEY = Pattern.compile(
            "\"((?:screen|config|commands|viewer|key|pack)\\.justenoughstructures\\.[a-z0-9_.]*[a-z0-9_])\"(\\s*\\+)?");
    /** A translatable line with its key and fallback both written out. */
    private static final Pattern FALLBACK = Pattern.compile(
            "Component\\.translatableWithFallback\\(\\s*\"([^\"]+)\"\\s*,\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\)");

    private LangFileTests() {
    }

    /**
     * The file reads as JSON, every value is text, and no key is there twice. The game keeps the
     * last of two lines with the same key without saying so, so the first would never show.
     */
    public static void langFileHasEachKeyOnce(GameTestHelper helper) {
        Map<String, String> lines = read();
        JustEnoughStructures.LOGGER.info("In the test of the lang file: en_us.json has {} lines, each key once", lines.size());
        helper.succeed();
    }

    /**
     * Every lang key the code writes out whole is in en_us.json. Keys put together in code, like a
     * hint's from its row's name, aren't checked. Where a line has a fallback for players without
     * the mod, the fallback says the same as the English line. The keys are read from the checkout's
     * sources, which the test runs inside.
     */
    public static void everyKeyInTheCodeIsInTheLangFile(GameTestHelper helper) {
        Map<String, String> lang = read();
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve(SOURCES))) {
            root = root.getParent();
        }
        helper.assertTrue(root != null, "couldn't find " + SOURCES + " above " + Path.of("").toAbsolutePath());
        List<String> missing = new ArrayList<>();
        List<String> different = new ArrayList<>();
        int keys = 0;
        int fallbacks = 0;
        try (Stream<Path> files = Files.walk(root.resolve("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String code = Files.readString(file, StandardCharsets.UTF_8);
                String name = file.getFileName().toString();
                Matcher key = KEY.matcher(code);
                while (key.find()) {
                    if (key.group(2) != null) {
                        continue;
                    }
                    keys++;
                    if (!lang.containsKey(key.group(1))) {
                        missing.add(key.group(1) + " (" + name + ")");
                    }
                }
                Matcher fallback = FALLBACK.matcher(code);
                while (fallback.find()) {
                    String english = lang.get(fallback.group(1));
                    String given = unescape(fallback.group(2));
                    if (english != null) {
                        fallbacks++;
                        if (!english.equals(given)) {
                            different.add(fallback.group(1) + " (" + name + "): \"" + given + "\" but en_us.json has \"" + english + "\"");
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new AssertionError("couldn't read the sources under " + root, e);
        }
        JustEnoughStructures.LOGGER.info("In the test of the lang file: {} keys written out in the code, {} missing; {} fallbacks, {} different",
                keys, missing.size(), fallbacks, different.size());
        helper.assertTrue(keys > 0, "found no lang keys under " + root.resolve("src/main/java"));
        helper.assertTrue(missing.isEmpty(), "en_us.json has no line for " + String.join(", ", missing));
        helper.assertTrue(different.isEmpty(), "a fallback doesn't match en_us.json: " + String.join("; ", different));
        helper.succeed();
    }

    /** The lines of en_us.json, read from the mod's own resources, failing on a key that's there twice. */
    private static Map<String, String> read() {
        try (InputStream in = JustEnoughStructures.class.getClassLoader().getResourceAsStream(LANG)) {
            if (in == null) {
                throw new AssertionError("JES's en_us.json isn't on the classpath");
            }
            JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            Map<String, String> lines = new HashMap<>();
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (reader.peek() != JsonToken.STRING) {
                    throw new AssertionError("en_us.json's " + key + " isn't text");
                }
                if (lines.put(key, reader.nextString()) != null) {
                    throw new AssertionError("en_us.json has " + key + " twice");
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new AssertionError("en_us.json goes on after its last }");
            }
            return lines;
        } catch (IOException | IllegalStateException e) {
            throw new AssertionError("en_us.json doesn't read as JSON: " + e.getMessage(), e);
        }
    }

    /** A Java string literal's text, for the escapes a lang line could have. */
    private static String unescape(String literal) {
        return literal.replace("\\\"", "\"").replace("\\'", "'").replace("\\n", "\n").replace("\\\\", "\\");
    }
}
