package com.ssmt.project;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.DataFormatException;

/** ZIP32 rewrite matching Python ZipFile's known-size headers and retained member metadata. */
final class BridgeForgeJarRewrite {
    private static final int MAX_EXPANDED = 512 * 1024 * 1024;

    static byte[] rewrite(byte[] original, Map<String, Map<Integer, String>> replacements) throws IOException {
        int end = -1;
        for (int index = original.length - 22; index >= Math.max(0, original.length - 65557); index--) {
            if (u32(original, index) == 0x06054b50L && index + 22 + u16(original, index + 20) == original.length) {
                end = index;
                break;
            }
        }
        if (end < 0 || u16(original, end + 4) != 0 || u16(original, end + 6) != 0
                || u16(original, end + 8) != u16(original, end + 10)) {
            throw new IOException("Unsupported or malformed JAR directory");
        }
        int count = u16(original, end + 10);
        long directoryOffset = u32(original, end + 16);
        if (count == 65535 || directoryOffset > Integer.MAX_VALUE) {
            throw new IOException("ZIP64 JAR requires review");
        }
        int cursor = (int) directoryOffset;
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        ByteArrayOutputStream directory = new ByteArrayOutputStream();
        Set<String> names = new HashSet<>();
        Set<String> applied = new HashSet<>();
        long expanded = 0;
        for (int index = 0; index < count; index++) {
            if (u32(original, cursor) != 0x02014b50L) {
                throw new IOException("Malformed JAR central header");
            }
            int filenameLength = u16(original, cursor + 28);
            int extraLength = u16(original, cursor + 30);
            int commentLength = u16(original, cursor + 32);
            int headerLength = 46 + filenameLength + extraLength + commentLength;
            checked(original, cursor, headerLength);
            byte[] central = Arrays.copyOfRange(original, cursor, cursor + headerLength);
            int flags = u16(central, 8);
            int method = u16(central, 10);
            long compressedSize = u32(central, 20);
            long size = u32(central, 24);
            long offset = u32(central, 42);
            if ((flags & 1) != 0 || method != 0 && method != 8 || size > MAX_EXPANDED
                    || compressedSize > Integer.MAX_VALUE || offset > Integer.MAX_VALUE) {
                throw new IOException("Unsupported or oversized JAR member");
            }
            expanded += size;
            if (expanded > MAX_EXPANDED) {
                throw new IOException("JAR expanded size exceeds limit");
            }
            String name = new String(central, 46, filenameLength,
                    (flags & 2048) != 0 ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName("IBM437"));
            if (!names.add(name)) {
                throw new IOException("Duplicate JAR member " + name);
            }
            // ZipFile.writestr resets flags and encodes non-ASCII filenames as UTF-8.
            byte[] filename = name.getBytes(StandardCharsets.UTF_8);
            flags = name.chars().anyMatch(ch -> ch > 127) ? 2048 : 0;
            byte[] resized = new byte[46 + filename.length + extraLength + commentLength];
            System.arraycopy(central, 0, resized, 0, 46);
            System.arraycopy(filename, 0, resized, 46, filename.length);
            System.arraycopy(central, 46 + filenameLength, resized, 46 + filename.length, extraLength + commentLength);
            central = resized;
            filenameLength = filename.length;
            put16(central, 28, filenameLength);
            if (u32(central, 38) == 0) {
                put32(central, 38, 0x01800000L);
            }

            int localOffset = (int) offset;
            if (u32(original, localOffset) != 0x04034b50L) {
                throw new IOException("Malformed JAR local header");
            }
            int localFilenameLength = u16(original, localOffset + 26);
            int localExtraLength = u16(original, localOffset + 28);
            int dataOffset = localOffset + 30 + localFilenameLength + localExtraLength;
            checked(original, dataOffset, (int) compressedSize);
            byte[] encoded = Arrays.copyOfRange(original, dataOffset, dataOffset + (int) compressedSize);
            byte[] bytes = method == 0 ? encoded : inflate(encoded, (int) size);
            CRC32 crc = new CRC32();
            crc.update(bytes);
            if (bytes.length != size || crc.getValue() != u32(central, 16)) {
                throw new IOException("JAR CRC mismatch: " + name);
            }
            if (replacements.containsKey(name)) {
                bytes = BridgeForgeClassFile.rewrite(bytes, replacements.get(name));
                applied.add(name);
            } else if (name.endsWith(".class")) {
                BridgeForgeClassFile.parse(bytes);
            }
            crc.reset();
            crc.update(bytes);
            encoded = method == 0 ? bytes : deflate(bytes);
            // Python writes the preserved ZipInfo extra field to both headers.
            byte[] local = new byte[30 + filenameLength + extraLength];
            put32(local, 0, 0x04034b50L);
            put16(local, 4, u16(central, 6));
            put16(local, 6, flags & ~8);
            put16(local, 8, method);
            put16(local, 10, u16(central, 12));
            put16(local, 12, u16(central, 14));
            put32(local, 14, crc.getValue());
            put32(local, 18, encoded.length);
            put32(local, 22, bytes.length);
            put16(local, 26, filenameLength);
            put16(local, 28, extraLength);
            System.arraycopy(central, 46, local, 30, filenameLength + extraLength);
            put16(central, 8, flags & ~8);
            put32(central, 16, crc.getValue());
            put32(central, 20, encoded.length);
            put32(central, 24, bytes.length);
            put32(central, 42, body.size());
            body.writeBytes(local);
            body.writeBytes(encoded);
            directory.writeBytes(central);
            cursor += headerLength;
        }
        if (!applied.containsAll(replacements.keySet())) {
            throw new IOException("Translation class is missing from JAR");
        }
        byte[] footer = new byte[22];
        put32(footer, 0, 0x06054b50L);
        put16(footer, 8, count);
        put16(footer, 10, count);
        put32(footer, 12, directory.size());
        put32(footer, 16, body.size());
        body.writeBytes(directory.toByteArray());
        body.writeBytes(footer);
        return body.toByteArray();
    }

    private static byte[] inflate(byte[] bytes, int size) throws IOException {
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(bytes);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[65536];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && !inflater.finished()) {
                    throw new IOException("Truncated or stalled DEFLATE member");
                }
                output.write(buffer, 0, count);
                if (output.size() > size) {
                    throw new IOException("DEFLATE size exceeds header");
                }
            }
            return output.toByteArray();
        } catch (DataFormatException exception) {
            throw new IOException("Malformed DEFLATE member", exception);
        } finally {
            inflater.end();
        }
    }

    private static byte[] deflate(byte[] bytes) {
        Deflater deflater = new Deflater(6, true);
        try {
            deflater.setInput(bytes);
            deflater.finish();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[65536];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static void checked(byte[] bytes, int offset, int size) throws IOException {
        if (offset < 0 || size < 0 || (long) offset + size > bytes.length) {
            throw new IOException("Truncated ZIP data");
        }
    }
    private static int u16(byte[] bytes, int offset) throws IOException {
        checked(bytes, offset, 2);
        return (bytes[offset] & 255) | (bytes[offset + 1] & 255) << 8;
    }
    private static long u32(byte[] bytes, int offset) throws IOException {
        return Integer.toUnsignedLong(u16(bytes, offset) | u16(bytes, offset + 2) << 16);
    }
    private static void put16(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
    }
    private static void put32(byte[] bytes, int offset, long value) {
        put16(bytes, offset, (int) value);
        put16(bytes, offset + 2, (int) (value >>> 16));
    }
}
