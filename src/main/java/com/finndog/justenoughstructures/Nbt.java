package com.finndog.justenoughstructures;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
//? if >=1.21.5 {
/*import net.minecraft.nbt.NumericTag;
*///?}

/**
 * NBT read the same way on every version. 1.21.5 made the getters hand back an Optional, so here
 * what's missing reads as empty: "", 0, an empty compound or list, as it always did before.
 */
public final class Nbt {
    private Nbt() {
    }

    public static String string(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getStringOr(key, "");
        *///?} else {
        return tag.getString(key);
        //?}
    }

    public static int getInt(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getIntOr(key, 0);
        *///?} else {
        return tag.getInt(key);
        //?}
    }

    public static short getShort(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getShortOr(key, (short) 0);
        *///?} else {
        return tag.getShort(key);
        //?}
    }

    public static byte getByte(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getByteOr(key, (byte) 0);
        *///?} else {
        return tag.getByte(key);
        //?}
    }

    public static long getLong(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getLongOr(key, 0L);
        *///?} else {
        return tag.getLong(key);
        //?}
    }

    public static CompoundTag compound(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getCompoundOrEmpty(key);
        *///?} else {
        return tag.getCompound(key);
        //?}
    }

    /** The list at {@code key} if its entries are all of {@code type}, and otherwise an empty one. */
    public static ListTag list(CompoundTag tag, String key, int type) {
        //? if >=1.21.5 {
        /*ListTag list = tag.getListOrEmpty(key);
        for (Tag entry : list) {
            if (entry.getId() != type) {
                return new ListTag();
            }
        }
        return list;
        *///?} else {
        return tag.getList(key, type);
        //?}
    }

    public static boolean hasString(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.get(key) instanceof StringTag;
        *///?} else {
        return tag.contains(key, Tag.TAG_STRING);
        //?}
    }

    public static boolean hasNumber(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.get(key) instanceof NumericTag;
        *///?} else {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC);
        //?}
    }

    public static boolean hasCompound(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.get(key) instanceof CompoundTag;
        *///?} else {
        return tag.contains(key, Tag.TAG_COMPOUND);
        //?}
    }

    public static boolean hasList(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.get(key) instanceof ListTag;
        *///?} else {
        return tag.contains(key, Tag.TAG_LIST);
        //?}
    }

    public static CompoundTag compound(ListTag list, int index) {
        //? if >=1.21.5 {
        /*return list.getCompoundOrEmpty(index);
        *///?} else {
        return list.getCompound(index);
        //?}
    }

    public static double getDouble(ListTag list, int index) {
        //? if >=1.21.5 {
        /*return list.getDoubleOr(index, 0.0);
        *///?} else {
        return list.getDouble(index);
        //?}
    }

    public static Set<String> keys(CompoundTag tag) {
        //? if >=1.21.5 {
        /*return tag.keySet();
        *///?} else {
        return tag.getAllKeys();
        //?}
    }

    public static String value(StringTag tag) {
        //? if >=1.21.5 {
        /*return tag.value();
        *///?} else {
        return tag.getAsString();
        //?}
    }
}
