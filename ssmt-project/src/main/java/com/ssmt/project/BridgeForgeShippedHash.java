package com.ssmt.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * BridgeForge's identity of a shipped mod copy, recomputed here so a BridgeForge live run record can be matched to a
 * candidate: one SHA-256 over {@code relative path NUL file sha256 LF} for every shipped file in path order
 * ({@code live_trust.shipped_hash} over {@code copy_drift._collect}). Ported 2026-10-18 from BridgeForge item 36; the
 * conformance test pins the value BridgeForge computed for a synthetic tree. Name matching is case-insensitive, as
 * Python's fnmatch is on Windows, where BridgeForge runs; its file-set algorithm is to be versioned by BridgeForge
 * item 75 R1, so a change there needs the same change here.
 */
public final class BridgeForgeShippedHash {
    /** Label recorded with imported evidence so a later algorithm change cannot be mistaken for a match. */
    public static final String ALGORITHM = "bridgeforge-copy-drift-2026-10-18";
    private static final List<String> INCLUDED_DIRS = List.of("data", "jars", "graphics", "sounds");
    private static final Set<String> EXCLUDED_TOP_DIRS = Set.of("scratch", "out", "build", "disabled_files", "bin",
            "gradle", "meta-inf", "production", "test");
    private static final List<String> EXCLUDED_DIR_GLOBS = List.of("reports", "src*", "__macosx", ".git", ".svn",
            ".idea", ".vscode");
    private static final List<String> EXCLUDED_FILE_GLOBS = List.of("*.bak", "*.iml", "*.pre-*", "*orig-backup*",
            "src.zip", "thumbs.db", "desktop.ini", ".ds_store");
    private static final Pattern JARS_ARRAY = Pattern.compile("\"jars\"\\s*:\\s*\\[([^\\]]*)\\]", Pattern.DOTALL);
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");

    private BridgeForgeShippedHash() { }

    /** The hash of the shipped file set of a mod root. */
    public static String of(Path root) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (var file : collect(root).entrySet()) {
                digest.update((file.getKey() + "\0" + sha256(file.getValue()) + "\n").getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    /** Shipped files by portable relative path. */
    static TreeMap<String, Path> collect(Path root) throws IOException {
        TreeMap<String, Path> files = new TreeMap<>();
        List<Path> topDirectories;
        try (Stream<Path> children = Files.list(root)) {
            topDirectories = children.filter(child -> Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS))
                    .sorted().toList();
        }
        for (Path directory : topDirectories) {
            String name = String.valueOf(directory.getFileName());
            String lower = name.toLowerCase(Locale.ROOT);
            boolean included = INCLUDED_DIRS.contains(lower);
            if (!included && (name.startsWith(".") || EXCLUDED_TOP_DIRS.contains(lower) || matches(EXCLUDED_DIR_GLOBS, name))) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(directory)) {
                for (Path item : walk.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList()) {
                    String relative = root.relativize(item).toString().replace(java.io.File.separatorChar, '/');
                    if (!excluded(relative) && !excluded(relative.substring(relative.indexOf('/') + 1))) {
                        files.put(relative, item);
                    }
                }
            }
        }
        try (Stream<Path> children = Files.list(root)) {
            for (Path item : children.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList()) {
                if (!excluded(String.valueOf(item.getFileName()))) {
                    files.put(String.valueOf(item.getFileName()), item);
                }
            }
        }
        Path modInfo = root.resolve("mod_info.json");
        if (Files.isRegularFile(modInfo)) {
            files.put("mod_info.json", modInfo);
            for (String jar : declaredJars(modInfo)) {
                Path item = root.resolve(jar);
                if (Files.isRegularFile(item) && !excluded(jar)) {
                    files.put(jar, item);
                }
            }
        }
        return files;
    }

    private static List<String> declaredJars(Path modInfo) throws IOException {
        String text = new String(Files.readAllBytes(modInfo), StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        Matcher array = JARS_ARRAY.matcher(text);
        List<String> jars = new java.util.ArrayList<>();
        if (array.find()) {
            Matcher entry = QUOTED.matcher(array.group(1));
            while (entry.find()) {
                String jar = entry.group(1).replace('\\', '/');
                int start = 0;
                while (start < jar.length() && (jar.charAt(start) == '.' || jar.charAt(start) == '/')) {
                    start++;
                }
                jars.add(jar.substring(start));
            }
        }
        return jars;
    }

    private static boolean excluded(String relative) {
        String[] parts = relative.split("/");
        for (int index = 0; index < parts.length - 1; index++) {
            if (matches(EXCLUDED_DIR_GLOBS, parts[index])) {
                return true;
            }
        }
        return matches(EXCLUDED_FILE_GLOBS, parts[parts.length - 1]);
    }

    private static boolean matches(List<String> globs, String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String glob : globs) {
            if (Pattern.matches(Pattern.quote(glob).replace("*", "\\E.*\\Q").replace("?", "\\E.\\Q"), lower)) {
                return true;
            }
        }
        return false;
    }

    private static String sha256(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
