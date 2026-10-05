package com.finndog.justenoughstructures;

import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

/**
 * Registry lookups the same way on every version. 1.21.2 renamed most of them, and gave {@code get}
 * a new meaning: it hands back the holder now, where it used to hand back the value.
 */
public final class Regs {
    private Regs() {
    }

    /** The entry with this id, or null. A registry with a default entry gives that instead of null. */
    public static <T> T value(Registry<T> registry, ResourceLocation id) {
        //? if >=1.21.2 {
        /*return registry.getValue(id);
        *///?} else {
        return registry.get(id);
        //?}
    }

    public static <T> T value(Registry<T> registry, ResourceKey<T> key) {
        //? if >=1.21.2 {
        /*return registry.getValue(key);
        *///?} else {
        return registry.get(key);
        //?}
    }

    public static <T> Optional<Holder.Reference<T>> holder(Registry<T> registry, ResourceKey<T> key) {
        //? if >=1.21.2 {
        /*return registry.get(key);
        *///?} else {
        return registry.getHolder(key);
        //?}
    }

    public static <T> Optional<Holder.Reference<T>> holder(Registry<T> registry, ResourceLocation id) {
        //? if >=1.21.2 {
        /*return registry.get(id);
        *///?} else {
        return registry.getHolder(ResourceKey.create(registry.key(), id));
        //?}
    }

    public static <T> Holder.Reference<T> holderOrThrow(Registry<T> registry, ResourceKey<T> key) {
        //? if >=1.21.2 {
        /*return registry.getOrThrow(key);
        *///?} else {
        return registry.getHolderOrThrow(key);
        //?}
    }

    public static <T> Optional<HolderSet.Named<T>> tag(Registry<T> registry, TagKey<T> tag) {
        //? if >=1.21.2 {
        /*return registry.get(tag);
        *///?} else {
        return registry.getTag(tag);
        //?}
    }

    /** Every tag the registry has, including empty ones. */
    public static <T> Stream<TagKey<T>> tagIds(Registry<T> registry) {
        //? if >=1.21.2 {
        /*return registry.listTagIds();
        *///?} else {
        return registry.getTagNames();
        //?}
    }

    /** The registry as code that reads NBT wants it. From 1.21.2 the registry is that itself. */
    public static <T> HolderGetter<T> getter(Registry<T> registry) {
        //? if >=1.21.2 {
        /*return registry;
        *///?} else {
        return registry.asLookup();
        //?}
    }
}
