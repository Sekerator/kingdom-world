package io.github.sekerator.kingdomworld.generator;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Just enough of the NBT format to write structure files the game reads: compounds, lists, ints and strings, gzipped the
 * way the game writes them. The output only depends on what is written, so the same buildings give the same bytes.
 */
final class Nbt {
    private static final byte END = 0;
    private static final byte INT = 3;
    private static final byte STRING = 8;
    private static final byte LIST = 9;
    private static final byte COMPOUND = 10;

    private Nbt() {
    }

    /**
     * A compound tag: named values in the order they were put.
     */
    static final class Compound {
        private final Map<String, Object> values = new LinkedHashMap<>();

        Compound put(String name, Object value) {
            values.put(name, value);
            return this;
        }
    }

    /**
     * A list tag of one element type.
     */
    static final class ListTag {
        private final List<Object> values = new ArrayList<>();

        ListTag add(Object value) {
            values.add(value);
            return this;
        }
    }

    static ListTag ints(int... values) {
        ListTag list = new ListTag();
        for (int value : values) {
            list.add(value);
        }
        return list;
    }

    /**
     * The compressed bytes of a root compound with an empty name.
     */
    static byte[] gzip(Compound root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream zip = new GZIPOutputStream(bytes); DataOutputStream out = new DataOutputStream(zip)) {
            out.writeByte(COMPOUND);
            out.writeUTF("");
            writeCompound(out, root);
        }
        return bytes.toByteArray();
    }

    private static byte type(Object value) {
        if (value instanceof Integer) {
            return INT;
        }
        if (value instanceof String) {
            return STRING;
        }
        if (value instanceof ListTag) {
            return LIST;
        }
        if (value instanceof Compound) {
            return COMPOUND;
        }
        throw new IllegalArgumentException("Not an NBT value: " + value);
    }

    private static void writeCompound(DataOutputStream out, Compound compound) throws IOException {
        for (Map.Entry<String, Object> entry : compound.values.entrySet()) {
            out.writeByte(type(entry.getValue()));
            out.writeUTF(entry.getKey());
            writePayload(out, entry.getValue());
        }
        out.writeByte(END);
    }

    private static void writePayload(DataOutputStream out, Object value) throws IOException {
        if (value instanceof Integer number) {
            out.writeInt(number);
        } else if (value instanceof String text) {
            out.writeUTF(text);
        } else if (value instanceof ListTag list) {
            byte element = list.values.isEmpty() ? END : type(list.values.get(0));
            out.writeByte(element);
            out.writeInt(list.values.size());
            for (Object item : list.values) {
                writePayload(out, item);
            }
        } else if (value instanceof Compound compound) {
            writeCompound(out, compound);
        }
    }
}
