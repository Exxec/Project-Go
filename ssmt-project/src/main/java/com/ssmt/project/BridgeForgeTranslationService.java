package com.ssmt.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.scanner.ModInfoReader;
import com.ssmt.core.exception.SsmtParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipFile;

/** Source-safe implementation of BridgeForge's versioned translation method in Java. */
public final class BridgeForgeTranslationService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> EXCLUDED = Set.of("src", "out", "build", "target", ".idea", ".vscode",
            ".git", "__macosx", "meta-inf", "disabled_files", "reports", "scratch");
    private static final Set<String> JSON_SUFFIXES = Set.of("json", "faction", "ship", "variant", "wpn",
            "proj", "skin", "system");
    private static final long MAX_TEXT_BYTES = 64L * 1024L * 1024L;

    /** Finds CJK units without a schema opt-in; never writes into the selected mod. */
    public ObjectNode export(Path source) throws ProjectException {
        try {
            Path root = source.toRealPath();
            var mod = new ModInfoReader().read(root);
            ObjectNode document = JSON.createObjectNode();
            document.put("schema_version", 1).put("mode", "TRANSLATION_EXPORT").put("mod_id", mod.id())
                    .put("source_language", "zh").put("target_language", "en")
                    .put("instructions", "Translate entries[].source into entries[].translation or glossary[source]. "
                            + "Keep format arguments, $variables, highlight markers and protected identity fields.");
            ObjectNode hashes = document.putObject("file_hashes");
            var unreadable = document.putArray("unreadable");
            var entries = document.putArray("entries");
            document.putObject("glossary");
            Set<Path> jars = new HashSet<>();
            for (String jar : mod.jars()) {
                Path path = contained(root, jar);
                if (Files.isRegularFile(path)) {
                    jars.add(path);
                }
            }
            List<Path> files;
            try (var walk = Files.walk(root)) {
                files = walk.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .sorted().toList();
            }
            boolean fallbackJars = jars.isEmpty();
            Set<String> sources = new HashSet<>();
            for (Path path : files) {
                WorkflowOperation.checkCancellation();
                String file = relative(root, path);
                if (excluded(file)) {
                    continue;
                }
                int before = entries.size();
                String suffix = suffix(file);
                if (suffix.equals("jar") && fallbackJars
                        && java.util.Arrays.stream(file.split("/")).anyMatch(part -> part.equalsIgnoreCase("tmp"))) {
                    continue;
                }
                if (suffix.equals("jar") && (fallbackJars || jars.contains(path))) {
                    try (var archive = new ZipFile(path.toFile())) {
                        var members = archive.entries();
                        while (members.hasMoreElements()) {
                            WorkflowOperation.checkCancellation();
                            var member = members.nextElement();
                            if (!member.getName().endsWith(".class")) {
                                continue;
                            }
                            try (var input = archive.getInputStream(member)) {
                                byte[] bytes = input.readNBytes((int) MAX_TEXT_BYTES + 1);
                                if (bytes.length > MAX_TEXT_BYTES) {
                                    throw new IOException("Class exceeds size limit");
                                }
                                java.util.zip.CRC32 crc = new java.util.zip.CRC32();
                                crc.update(bytes);
                                if (member.getCrc() >= 0 && crc.getValue() != member.getCrc()) {
                                    throw new IOException("Class CRC mismatch");
                                }
                                var pool = BridgeForgeClassFile.parse(bytes);
                                String className = member.getName().substring(0, member.getName().length() - 6);
                                for (var literal : new java.util.TreeMap<>(pool.literals()).entrySet()) {
                                    if (BridgeForgeTextUnits.cjk(literal.getValue())) {
                                        ObjectNode context = JSON.createObjectNode().put("class", className)
                                                .put("cp_index", literal.getKey());
                                        add(entries, "jar:" + file + "!" + className + "#" + literal.getKey(),
                                                file, "jar", context, literal.getValue());
                                        sources.add(literal.getValue());
                                    }
                                }
                            } catch (IOException exception) {
                                unreadable.add(file + "!" + member.getName() + ": " + exception.getMessage());
                            }
                        }
                    } catch (IOException exception) {
                        unreadable.add(file + ": " + exception.getMessage());
                    }
                } else if (suffix.equals("csv") || suffix.equals("java") || JSON_SUFFIXES.contains(suffix)) {
                    try {
                        String text = readText(path);
                        for (var unit : units(file, text)) {
                            if (BridgeForgeTextUnits.cjk(unit.source())) {
                                add(entries, unit.id(), file, unit.kind(), unit.context(), unit.source());
                                sources.add(unit.source());
                            }
                        }
                    } catch (IOException exception) {
                        unreadable.add(file + ": " + exception.getMessage());
                    }
                }
                if (entries.size() > before) {
                    hashes.put(file, hash(path));
                }
            }
            document.put("entry_count", entries.size()).put("unique_source_count", sources.size());
            new BridgeForgeTranslationDocument().validate(document);
            return document;
        } catch (IOException | SsmtParseException | IllegalArgumentException exception) {
            throw new ProjectException("Could not export BridgeForge translation: " + exception.getMessage(), exception);
        }
    }

    private static void add(com.fasterxml.jackson.databind.node.ArrayNode entries, String id,
            String file, String kind, ObjectNode context, String source) {
        ObjectNode entry = entries.addObject().put("id", id).put("file", file).put("kind", kind)
                .put("source", source).put("translation", "");
        entry.set("context", context);
    }

    static List<BridgeForgeTextUnits.Unit> units(String file, String text) {
        return switch (suffix(file)) {
            case "csv" -> BridgeForgeTextUnits.csv(file, text);
            case "java" -> BridgeForgeTextUnits.java(file, text);
            default -> BridgeForgeTextUnits.json(file, text);
        };
    }

    static String readText(Path path) throws IOException {
        if (Files.size(path) > MAX_TEXT_BYTES) {
            throw new IOException("Text exceeds size limit");
        }
        byte[] bytes = Files.readAllBytes(path);
        String text = encoding(path, bytes).newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        return text.startsWith("\ufeff") ? text.substring(1) : text;
    }

    static java.nio.charset.Charset encoding(Path path, byte[] bytes) throws IOException {
        try {
            StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes));
            return StandardCharsets.UTF_8;
        } catch (java.nio.charset.CharacterCodingException exception) {
            if (!suffix(path.toString()).equals("csv")
                    || bytes.length >= 3 && bytes[0] == (byte) 0xef && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf) {
                throw exception;
            }
            var legacy = java.nio.charset.Charset.forName("GB18030");
            legacy.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes));
            return legacy;
        }
    }

    static String suffix(String file) {
        int index = file.lastIndexOf('.');
        return index < 0 ? "" : file.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    static boolean excluded(String file) {
        String[] parts = file.split("/");
        for (int index = 0; index < parts.length - 1; index++) {
            String part = parts[index].toLowerCase(Locale.ROOT);
            if (EXCLUDED.contains(part) || part.startsWith("src-decompiled")) {
                return true;
            }
        }
        return false;
    }

    static Path contained(Path root, String file) throws IOException {
        Path relative = BridgeForgeTranslationDocument.safePath(file);
        Path current = root;
        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Linked source path " + file);
            }
        }
        return current;
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    static String hash(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536];
                int length;
                while ((length = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, length);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
