package com.ssmt.project;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.HashSet;

/** Preserves constant-pool indices and every byte outside selected literal UTF8 entries. */
final class BridgeForgeClassFile {
    record Pool(int count, Map<Integer, String> utf8, Map<Integer, String> literals) { }

    static Pool parse(byte[] bytes) throws IOException {
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != 0xcafebabe) {
                throw new IOException("Invalid class magic");
            }
            input.readUnsignedShort();
            input.readUnsignedShort();
            int count = input.readUnsignedShort();
            Map<Integer, String> utf8 = new TreeMap<>();
            Set<Integer> references = new HashSet<>();
            for (int index = 1; index < count; index++) {
                int tag = input.readUnsignedByte();
                if (tag == 1) {
                    utf8.put(index, input.readUTF());
                } else if (tag == 8) {
                    references.add(input.readUnsignedShort());
                } else {
                    input.skipNBytes(payload(tag));
                    if (tag == 5 || tag == 6) {
                        index++;
                    }
                }
            }
            Map<Integer, String> literals = new TreeMap<>();
            for (int reference : references) {
                if (!utf8.containsKey(reference)) {
                    throw new IOException("Invalid CONSTANT_String reference");
                }
                literals.put(reference, utf8.get(reference));
            }
            // Verify the remainder, including member/attribute lengths, without changing bytes.
            input.readUnsignedShort();
            input.readUnsignedShort();
            input.readUnsignedShort();
            input.skipNBytes(2L * input.readUnsignedShort());
            members(input);
            members(input);
            attributes(input);
            if (input.available() != 0) {
                throw new IOException("Trailing class bytes");
            }
            return new Pool(count, Map.copyOf(utf8), Map.copyOf(literals));
        }
    }

    private static void members(DataInputStream input) throws IOException {
        int count = input.readUnsignedShort();
        for (int index = 0; index < count; index++) {
            input.skipNBytes(6);
            attributes(input);
        }
    }

    private static void attributes(DataInputStream input) throws IOException {
        int count = input.readUnsignedShort();
        for (int index = 0; index < count; index++) {
            input.readUnsignedShort();
            input.skipNBytes(Integer.toUnsignedLong(input.readInt()));
        }
    }

    static byte[] rewrite(byte[] bytes, Map<Integer, String> replacements) throws IOException {
        Pool pool = parse(bytes);
        if (!pool.literals().keySet().containsAll(replacements.keySet())) {
            throw new IOException("Replacement targets a non-literal constant");
        }
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes));
                var buffer = new ByteArrayOutputStream();
                var output = new DataOutputStream(buffer)) {
            output.write(input.readNBytes(10));
            for (int index = 1; index < pool.count(); index++) {
                int tag = input.readUnsignedByte();
                output.writeByte(tag);
                if (tag == 1) {
                    int length = input.readUnsignedShort();
                    byte[] value = input.readNBytes(length);
                    if (value.length != length) {
                        throw new IOException("Truncated UTF8 constant");
                    }
                    if (replacements.containsKey(index)) {
                        output.writeUTF(replacements.get(index));
                    } else {
                        output.writeShort(length);
                        output.write(value);
                    }
                } else {
                    output.write(input.readNBytes(payload(tag)));
                    if (tag == 5 || tag == 6) {
                        index++;
                    }
                }
            }
            input.transferTo(output);
            byte[] result = buffer.toByteArray();
            parse(result);
            return result;
        }
    }

    private static int payload(int tag) throws IOException {
        return switch (tag) {
            case 3, 4, 9, 10, 11, 12, 17, 18 -> 4;
            case 5, 6 -> 8;
            case 7, 8, 16, 19, 20 -> 2;
            case 15 -> 3;
            default -> throw new IOException("Unknown constant-pool tag " + tag);
        };
    }
}
