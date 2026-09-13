package com.ssmt.scanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/** Local source-state attestation, separate from portable file-byte identity. */
public final class SourceTreeManifest {
    /** Access time is deliberately excluded: read-only inspection may update it. */
    public record Node(String path, String kind, long bytes, String sha256,
            String modified, String created, String fileKey, String dosFlags, String posixPermissions) { }

    /** Captures regular files and directories, including empty directories and root. */
    public List<Node> capture(Path candidate) throws IOException {
        Path root = candidate.toAbsolutePath().normalize();
        var inventory = new CandidateInventory().capture(root);
        var files = new HashMap<String, CandidateInventory.Entry>();
        inventory.forEach(entry -> files.put(entry.path(), entry));
        var nodes = new ArrayList<Node>();
        try (var paths = Files.walk(root)) {
            var iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (nodes.size() >= 100_000) { throw new IOException("Source manifest node limit exceeded"); }
                if (Files.isSymbolicLink(path) || !path.toRealPath().equals(path.toAbsolutePath().normalize())) {
                    throw new IOException("Linked source manifest node requires review: " + path);
                }
                var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                String relative = root.relativize(path).toString().replace('\\', '/');
                var file = files.remove(relative);
                if (!attributes.isDirectory() && (file == null || !attributes.isRegularFile())) {
                    throw new IOException("Source tree changed or contains a non-regular node: " + path);
                }
                if (file != null && file.bytes() != attributes.size()) {
                    throw new IOException("Source file changed during manifest: " + path);
                }
                String dos = "NOT_SUPPORTED";
                if (Files.getFileStore(path).supportsFileAttributeView("dos")) {
                    var flags = Files.readAttributes(path, DosFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    dos = flags.isReadOnly() + ":" + flags.isHidden() + ":" + flags.isSystem() + ":" + flags.isArchive();
                }
                String posix = "NOT_SUPPORTED";
                if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
                    posix = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS).stream()
                            .map(Enum::name).sorted().collect(java.util.stream.Collectors.joining(","));
                }
                nodes.add(new Node(relative, attributes.isDirectory() ? "DIRECTORY" : "FILE",
                        file == null ? 0 : file.bytes(), file == null ? "" : file.sha256(),
                        attributes.lastModifiedTime().toString(), attributes.creationTime().toString(),
                        String.valueOf(attributes.fileKey()), dos, posix));
            }
        }
        if (!files.isEmpty() || !inventory.equals(new CandidateInventory().capture(root))) {
            throw new IOException("Source tree changed during manifest capture");
        }
        nodes.sort(Comparator.comparing(Node::path));
        return List.copyOf(nodes);
    }
}
