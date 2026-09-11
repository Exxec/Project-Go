package com.ssmt.patcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Hash-bound inspection and conservative recovery for interrupted clone publication. */
public final class PatchRecoveryService {
    private static final int MAX_PATHS = 20_000;

    /** The only action that can be derived without choosing between two complete outputs. */
    public enum Action { NONE, RESTORE_PREVIOUS, REVIEW_REQUIRED }

    /** Exact publication state; callers must pass the same preview back to recover. */
    public record Preview(Path output, boolean outputExists, String outputHash,
            boolean previousExists, String previousHash, boolean stagingExists,
            String stagingHash, Action action) { }

    /** Inspects one translated-copy destination without changing it. */
    public Preview inspect(Path output) throws PatchBuilderException {
        Path normalized = output.toAbsolutePath().normalize();
        Path previous = sibling(normalized, ".ssmt-previous-translated");
        Path staging = sibling(normalized, ".ssmt-translated-staging");
        boolean outputExists = Files.exists(normalized, LinkOption.NOFOLLOW_LINKS);
        boolean previousExists = Files.exists(previous, LinkOption.NOFOLLOW_LINKS);
        boolean stagingExists = Files.exists(staging, LinkOption.NOFOLLOW_LINKS);
        Action action = !outputExists && previousExists && !stagingExists
                ? Action.RESTORE_PREVIOUS
                : previousExists || stagingExists ? Action.REVIEW_REQUIRED : Action.NONE;
        return new Preview(normalized, outputExists, hashIfPresent(normalized),
                previousExists, hashIfPresent(previous), stagingExists, hashIfPresent(staging), action);
    }

    /** Restores a missing output only when the exact previously previewed tree is unchanged. */
    public void recover(Preview approved) throws PatchBuilderException {
        if (approved.action() != Action.RESTORE_PREVIOUS) {
            throw new PatchBuilderException("Recovery requires an unambiguous restore preview");
        }
        Preview current = inspect(approved.output());
        if (!current.equals(approved)) {
            throw new PatchBuilderException("Publication recovery data changed after preview");
        }
        Path previous = sibling(approved.output(), ".ssmt-previous-translated");
        try {
            try {
                Files.move(previous, approved.output(), StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(previous, approved.output());
            }
        } catch (IOException exception) {
            throw new PatchBuilderException("Could not restore the previous translated copy", exception);
        }
    }

    private static Path sibling(Path output, String suffix) {
        Path name = Objects.requireNonNull(output.getFileName(), "output filename");
        return output.resolveSibling("." + name + suffix);
    }

    private static String hashIfPresent(Path root) throws PatchBuilderException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return "";
        }
        if (Files.isSymbolicLink(root)) {
            throw new PatchBuilderException("Publication recovery data must not be a symbolic link");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (var paths = Files.walk(root)) {
                List<Path> entries = paths.sorted(Comparator.comparing(path ->
                        root.relativize(path).toString().replace('\\', '/'))).toList();
                if (entries.size() > MAX_PATHS) {
                    throw new PatchBuilderException("Publication recovery tree contains too many paths");
                }
                for (Path path : entries) {
                    if (Files.isSymbolicLink(path)) {
                        throw new PatchBuilderException("Publication recovery tree contains a symbolic link");
                    }
                    String relative = root.relativize(path).toString().replace('\\', '/');
                    digest.update(relative.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    digest.update((byte) 0);
                    if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        digest.update((byte) 'F');
                        try (InputStream input = Files.newInputStream(path)) {
                            int read;
                            while ((read = input.read(buffer)) >= 0) {
                                digest.update(buffer, 0, read);
                            }
                        }
                    } else {
                        digest.update((byte) 'D');
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new PatchBuilderException("Could not inspect publication recovery data", exception);
        }
    }
}
