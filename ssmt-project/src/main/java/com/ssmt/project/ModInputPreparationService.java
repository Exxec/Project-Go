package com.ssmt.project;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Normalizes user-selected mod inputs without writing to the supplied source. */
public final class ModInputPreparationService {
    private static final int DEFAULT_MAX_ARCHIVE_ENTRIES = 10_000;
    private static final long DEFAULT_MAX_ARCHIVE_BYTES = 1024L * 1024L * 1024L;
    private static final String MOD_INFO = "mod_info.json";
    private final int maxArchiveEntries;
    private final long maxArchiveBytes;

    /** Describes how the supplied input was interpreted. */
    public enum InputKind {
        DIRECTORY,
        MOD_INFO_FILE,
        ZIP_ARCHIVE
    }

    /** A validated mod root and provenance for the original user selection. */
    public record PreparedMod(Path originalInput, Path modRoot, InputKind kind,
            Optional<String> archiveSha256) {
        public PreparedMod {
            Objects.requireNonNull(originalInput, "originalInput");
            Objects.requireNonNull(modRoot, "modRoot");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(archiveSha256, "archiveSha256");
        }
    }

    public ModInputPreparationService() {
        this(DEFAULT_MAX_ARCHIVE_ENTRIES, DEFAULT_MAX_ARCHIVE_BYTES);
    }

    ModInputPreparationService(int maxArchiveEntries, long maxArchiveBytes) {
        if (maxArchiveEntries < 1 || maxArchiveBytes < 1) {
            throw new IllegalArgumentException("Archive limits must be positive");
        }
        this.maxArchiveEntries = maxArchiveEntries;
        this.maxArchiveBytes = maxArchiveBytes;
    }

    /**
     * Accepts a mod directory, its {@code mod_info.json}, or a ZIP containing one mod.
     * ZIP contents are published beneath {@code cacheRoot} only after full validation.
     */
    public PreparedMod prepare(Path supplied, Path cacheRoot) throws ProjectException {
        Objects.requireNonNull(supplied, "supplied");
        Objects.requireNonNull(cacheRoot, "cacheRoot");
        Path input = realPath(supplied, "Could not open selected mod input");
        if (Files.isDirectory(input)) {
            Path modRoot = requireDirectModRoot(input);
            requireCacheOutsideSource(modRoot, cacheRoot);
            return new PreparedMod(input, modRoot, InputKind.DIRECTORY,
                    Optional.empty());
        }
        if (!Files.isRegularFile(input)) {
            throw new ProjectException("Selected mod input is not a regular file or directory");
        }
        String name = fileName(input).toLowerCase(Locale.ROOT);
        if (MOD_INFO.equals(name)) {
            Path modRoot = Objects.requireNonNull(input.getParent(), "mod_info.json parent");
            requireCacheOutsideSource(modRoot, cacheRoot);
            return new PreparedMod(input, requireDirectModRoot(modRoot), InputKind.MOD_INFO_FILE,
                    Optional.empty());
        }
        if (!name.endsWith(".zip")) {
            throw new ProjectException("Choose a mod folder, mod_info.json, or ZIP archive");
        }
        return prepareArchive(input, cacheRoot);
    }

    private PreparedMod prepareArchive(Path archive, Path suppliedCacheRoot)
            throws ProjectException {
        String digest = sha256(archive);
        Path cacheRoot;
        try {
            Files.createDirectories(suppliedCacheRoot.toAbsolutePath().normalize());
            cacheRoot = suppliedCacheRoot.toRealPath();
        } catch (IOException exception) {
            throw new ProjectException("Could not create mod archive cache", exception);
        }
        Path extraction = cacheRoot.resolve("archive-source-" + digest).normalize();
        if (!extraction.startsWith(cacheRoot)) {
            throw new ProjectException("Could not create a safe mod archive cache path");
        }
        if (Files.exists(extraction)) {
            return archiveResult(archive, extraction, digest);
        }

        Path staging = null;
        try {
            staging = Files.createTempDirectory(cacheRoot, "archive-source-staging-");
            extract(archive, staging);
            Path stagedRoot = findArchiveModRoot(staging);
            Path relativeRoot = staging.relativize(stagedRoot);
            if (!publish(staging, extraction)) {
                deleteTree(staging);
                staging = null;
                return archiveResult(archive, extraction, digest);
            }
            staging = null;
            return new PreparedMod(archive, extraction.resolve(relativeRoot).toRealPath(),
                    InputKind.ZIP_ARCHIVE, Optional.of(digest));
        } catch (ProjectException exception) {
            throw exception;
        } catch (IOException exception) {
            if (Files.isDirectory(extraction)) {
                return archiveResult(archive, extraction, digest);
            }
            throw new ProjectException("Could not unpack selected mod archive", exception);
        } finally {
            deleteTree(staging);
        }
    }

    private void extract(Path archive, Path staging) throws IOException, ProjectException {
        int entries = 0;
        long expandedBytes = 0;
        Set<Path> destinations = new HashSet<>();
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                entries++;
                if (entries > maxArchiveEntries) {
                    throw new ProjectException("Mod archive contains too many entries");
                }
                Path destination = safeDestination(staging, entry.getName());
                if (!destinations.add(destination)) {
                    throw new ProjectException("Mod archive contains duplicate file paths");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(Objects.requireNonNull(destination.getParent()));
                    try (var output = Files.newOutputStream(destination)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            expandedBytes = Math.addExact(expandedBytes, read);
                            if (expandedBytes > maxArchiveBytes) {
                                throw new ProjectException(
                                        "Mod archive expands beyond the safety limit");
                            }
                            output.write(buffer, 0, read);
                        }
                    }
                }
                input.closeEntry();
            }
        } catch (ArithmeticException exception) {
            throw new ProjectException("Mod archive expands beyond the safety limit", exception);
        }
    }

    private static Path safeDestination(Path root, String entryName) throws ProjectException {
        if (entryName.isBlank() || entryName.startsWith("/") || entryName.startsWith("\\")
                || entryName.indexOf('\\') >= 0 || entryName.indexOf('\0') >= 0
                || entryName.matches("^[A-Za-z]:.*")
                || List.of(entryName.split("/", -1)).contains("..")) {
            throw new ProjectException("Mod archive contains an unsafe file path");
        }
        try {
            Path relative = Path.of(entryName).normalize();
            Path destination = root.resolve(relative).normalize();
            if (relative.isAbsolute() || !destination.startsWith(root)) {
                throw new ProjectException("Mod archive contains an unsafe file path");
            }
            return destination;
        } catch (InvalidPathException exception) {
            throw new ProjectException("Mod archive contains an unsafe file path");
        }
    }

    private static boolean publish(Path staging, Path extraction) throws IOException {
        return publish(staging, extraction, (from, to, atomic) -> {
            if (atomic) {
                Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
            } else {
                Files.move(from, to);
            }
        }, Thread::sleep);
    }

    static boolean publish(Path staging, Path extraction, ArchiveMover mover,
            RetryPause pause) throws IOException {
        boolean atomic = true;
        for (int attempt = 0; ; attempt++) {
            try {
                mover.move(staging, extraction, atomic);
                return true;
            } catch (AtomicMoveNotSupportedException exception) {
                if (!atomic) {
                    throw exception;
                }
                atomic = false;
                attempt--;
            } catch (FileAlreadyExistsException race) {
                if (!Files.isDirectory(extraction)) {
                    throw race;
                }
                return false;
            } catch (AccessDeniedException exception) {
                if (attempt >= 50) {
                    throw exception;
                }
                try {
                    pause.sleep(100);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    exception.addSuppressed(interrupted);
                    throw exception;
                }
            }
        }
    }

    @FunctionalInterface
    interface ArchiveMover {
        void move(Path staging, Path extraction, boolean atomic) throws IOException;
    }

    @FunctionalInterface
    interface RetryPause {
        void sleep(long milliseconds) throws InterruptedException;
    }

    private static PreparedMod archiveResult(Path archive, Path extraction, String digest)
            throws ProjectException {
        try {
            return new PreparedMod(archive, findArchiveModRoot(extraction).toRealPath(),
                    InputKind.ZIP_ARCHIVE, Optional.of(digest));
        } catch (IOException exception) {
            throw new ProjectException("Could not read cached mod archive", exception);
        }
    }

    private static Path requireDirectModRoot(Path directory) throws ProjectException {
        try (var files = Files.list(directory)) {
            List<Path> metadata = files.filter(Files::isRegularFile)
                    .filter(path -> MOD_INFO.equalsIgnoreCase(fileName(path))).toList();
            if (metadata.size() != 1) {
                throw new ProjectException("Mod folder must contain exactly one mod_info.json file. "
                        + "Choose the mod's own folder containing that file, not Desktop or a parent folder, "
                        + "or choose the mod ZIP archive.");
            }
            return directory.toRealPath();
        } catch (IOException exception) {
            throw new ProjectException("Could not inspect selected mod folder", exception);
        }
    }

    private static Path findArchiveModRoot(Path extraction)
            throws IOException, ProjectException {
        try (var paths = Files.walk(extraction)) {
            List<Path> metadata = paths.filter(Files::isRegularFile)
                    .filter(path -> MOD_INFO.equalsIgnoreCase(fileName(path))).toList();
            if (metadata.size() != 1) {
                throw new ProjectException(
                        "Mod archive must contain exactly one mod_info.json file");
            }
            return Objects.requireNonNull(metadata.getFirst().getParent(), "mod archive root");
        }
    }

    private static void requireCacheOutsideSource(Path source, Path suppliedCacheRoot)
            throws ProjectException {
        Path cache = suppliedCacheRoot.toAbsolutePath().normalize();
        Path existing = cache;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        try {
            if (existing != null) {
                cache = existing.toRealPath().resolve(existing.relativize(cache)).normalize();
            }
            if (cache.startsWith(source.toRealPath())) {
                throw new ProjectException("Mod archive cache must be outside the source mod");
            }
        } catch (IOException exception) {
            throw new ProjectException("Could not resolve mod archive cache", exception);
        }
    }

    private static String sha256(Path file) throws ProjectException {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new ProjectException("Could not fingerprint selected mod archive", exception);
        }
    }

    private static Path realPath(Path path, String message) throws ProjectException {
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            throw new ProjectException(message, exception);
        }
    }

    private static String fileName(Path path) {
        return Objects.requireNonNull(path.getFileName(), "file name").toString();
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            System.getLogger(ModInputPreparationService.class.getName()).log(
                    System.Logger.Level.WARNING, "Archive staging cleanup failed", exception);
        }
    }
}
