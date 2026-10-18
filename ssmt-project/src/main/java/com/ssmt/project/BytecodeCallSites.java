package com.ssmt.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Residual bytecode audit: for every CJK string constant in a mod's classes, which method consumes it. Classes are
 * only read (ASM), never loaded. The first call after a literal is loaded decides its verdict through the data rules
 * in {@code unit-roles.json}: PROTECT (a lookup key), TEXT (shown to the player) or REVIEW (a person decides). Locations
 * use the keys of the legacy bytecode extractor so a proposed {@code ssmt-bytecode-allowlist.tsv} works as written.
 */
public final class BytecodeCallSites {
    /** What a literal is used for. */
    public enum Verdict { PROTECT, TEXT, REVIEW }

    /**
     * One CJK literal and where it is used.
     *
     * @param file mod-relative jar or class path
     * @param className class name as the export names it (jar entry name without .class; empty for a loose class)
     * @param key legacy extractor key, for example {@code class:a/B#method:m()V:ldc:0}
     * @param text the literal
     * @param callee {@code owner.name} of the consuming call, or empty when none was seen
     * @param verdict decided by the rules
     * @param rule id of the deciding rule, or {@code default}
     */
    public record Site(String file, String className, String key, String text, String callee, Verdict verdict,
            String rule) { }

    private record CallRule(String id, Pattern owner, Pattern name, Verdict verdict) { }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long MAX_CLASS_BYTES = 64L * 1024L * 1024L;
    private final List<CallRule> rules = new ArrayList<>();
    private final Verdict defaultVerdict;

    private BytecodeCallSites(JsonNode data) {
        for (JsonNode rule : data.path("call_sites")) {
            rules.add(new CallRule(rule.path("id").asText(), Pattern.compile(rule.path("owner").asText()),
                    Pattern.compile(rule.path("name").asText()), Verdict.valueOf(rule.path("verdict").asText())));
        }
        defaultVerdict = Verdict.valueOf(data.path("default_call_verdict").asText("REVIEW"));
    }

    /** The rules shipped with the program. */
    public static BytecodeCallSites withDefaultRules() {
        try (InputStream input = BytecodeCallSites.class.getResourceAsStream("/com/ssmt/project/unit-roles.json")) {
            if (input == null) {
                throw new IllegalStateException("Missing unit-roles.json");
            }
            return new BytecodeCallSites(JSON.readTree(input));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read unit-roles.json", exception);
        }
    }

    /** Caller-supplied rule data (same schema as {@code unit-roles.json}). */
    public static BytecodeCallSites fromRules(JsonNode data) {
        return new BytecodeCallSites(data);
    }

    /**
     * Scans every class of the mod (jars named anywhere under it and loose classes). A class that cannot be parsed is
     * skipped and reported in {@code unreadable}.
     */
    public List<Site> scan(Path mod, List<String> unreadable) throws IOException {
        List<Path> files;
        try (var walk = Files.walk(mod)) {
            files = walk.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).sorted().toList();
        }
        List<Site> sites = new ArrayList<>();
        for (Path path : files) {
            String file = mod.relativize(path).toString().replace(java.io.File.separatorChar, '/');
            if (BridgeForgeTranslationService.excluded(file)) {
                continue;
            }
            String suffix = BridgeForgeTranslationService.suffix(file);
            try {
                if (suffix.equals("class")) {
                    sites.addAll(visit(file, "", BridgeForgeTranslationService.readLimited(path)));
                } else if (suffix.equals("jar")) {
                    scanJar(path, file, sites, unreadable);
                }
            } catch (IOException | RuntimeException exception) {
                unreadable.add(file + ": " + exception.getMessage());
            }
        }
        sites.sort(Comparator.comparing(Site::file).thenComparing(Site::className).thenComparing(Site::key));
        return List.copyOf(sites);
    }

    private void scanJar(Path path, String file, List<Site> sites, List<String> unreadable) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            var entries = zip.stream().filter(entry -> !entry.isDirectory()
                    && entry.getName().toLowerCase(Locale.ROOT).endsWith(".class"))
                    .sorted(Comparator.comparing(ZipEntry::getName)).toList();
            for (ZipEntry entry : entries) {
                try (InputStream input = zip.getInputStream(entry)) {
                    byte[] bytes = input.readNBytes((int) MAX_CLASS_BYTES + 1);
                    if (bytes.length > MAX_CLASS_BYTES) {
                        throw new IOException("Class exceeds size limit");
                    }
                    String name = entry.getName().substring(0, entry.getName().length() - 6);
                    sites.addAll(visit(file, name, bytes));
                } catch (IOException | RuntimeException exception) {
                    unreadable.add(file + "!" + entry.getName() + ": " + exception.getMessage());
                }
            }
        }
    }

    private List<Site> visit(String file, String exportClassName, byte[] bytes) {
        List<Site> found = new ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            private String className = "";

            @Override public void visit(int version, int access, String name, String signature, String superName,
                    String[] interfaces) {
                className = name;
            }

            @Override public FieldVisitor visitField(int access, String name, String descriptor, String signature,
                    Object value) {
                if (value instanceof String text && !text.isEmpty() && BridgeForgeTextUnits.cjk(text)) {
                    found.add(site(file, exportClassName, "class:" + className + "#field:" + name + ":" + descriptor,
                            text, "", defaultVerdict, "default"));
                }
                return null;
            }

            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                return new MethodScan(file, exportClassName, className, name + descriptor, found);
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found;
    }

    private static Site site(String file, String className, String key, String text, String callee, Verdict verdict,
            String rule) {
        return new Site(file, className, key, text, callee, verdict, rule);
    }

    private Verdict verdictOf(String owner, String name, String[] ruleId) {
        for (CallRule rule : rules) {
            if (rule.owner().matcher(owner).matches() && rule.name().matcher(name).matches()) {
                ruleId[0] = rule.id();
                return rule.verdict();
            }
        }
        ruleId[0] = "default";
        return defaultVerdict;
    }

    /** Tracks literals loaded since the last call; the next call consumes them, anything else forgets them. */
    private final class MethodScan extends MethodVisitor {
        private final String file;
        private final String exportClassName;
        private final String className;
        private final String method;
        private final List<Site> found;
        private final List<String[]> pending = new ArrayList<>();
        private int ordinal;
        private int indyOrdinal;

        MethodScan(String file, String exportClassName, String className, String method, List<Site> found) {
            super(Opcodes.ASM9);
            this.file = file;
            this.exportClassName = exportClassName;
            this.className = className;
            this.method = method;
            this.found = found;
        }

        @Override public void visitLdcInsn(Object value) {
            if (value instanceof String text && !text.isEmpty()) {
                if (BridgeForgeTextUnits.cjk(text)) {
                    pending.add(new String[] {"class:" + className + "#method:" + method + ":ldc:" + ordinal, text});
                }
                ordinal++;
            }
        }

        @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                boolean isInterface) {
            String[] ruleId = new String[1];
            Verdict verdict = verdictOf(owner, name, ruleId);
            for (String[] literal : pending) {
                found.add(site(file, exportClassName, literal[0], literal[1], owner + "." + name, verdict, ruleId[0]));
            }
            pending.clear();
        }

        @Override public void visitInvokeDynamicInsn(String name, String descriptor, Handle handle, Object... args) {
            for (int index = 0; index < args.length; index++) {
                if (args[index] instanceof String text && !text.isEmpty() && BridgeForgeTextUnits.cjk(text)) {
                    found.add(site(file, exportClassName, "class:" + className + "#method:" + method + ":indy:"
                            + indyOrdinal + ":bootstrap:" + index, text, "invokedynamic", Verdict.REVIEW, "indy"));
                }
            }
            indyOrdinal++;
        }

        @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            flush();
        }

        @Override public void visitVarInsn(int opcode, int varIndex) {
            if (opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE) {
                flush();
            }
        }

        @Override public void visitInsn(int opcode) {
            if (opcode == Opcodes.ARETURN || opcode == Opcodes.AASTORE) {
                flush();
            }
        }

        @Override public void visitEnd() {
            flush();
        }

        /** The literal went somewhere this audit does not follow: no call evidence, so the default verdict. */
        private void flush() {
            for (String[] literal : pending) {
                found.add(site(file, exportClassName, literal[0], literal[1], "", defaultVerdict, "default"));
            }
            pending.clear();
        }
    }

    /**
     * Allowlist proposal in the format {@code BytecodeTextAllowlist} reads: only TEXT locations are listed;
     * PROTECT and REVIEW locations appear as comment lines so a reviewer sees what was left out and why.
     */
    public static String proposal(List<Site> sites) {
        StringBuilder text = new StringBuilder("# ssmt-bytecode-allowlist-v1\n");
        text.append("# PROPOSAL (REVIEW): generated from call sites; check before use. Only TEXT rows are active.\n");
        for (Site site : sites) {
            if (site.verdict() == Verdict.TEXT) {
                text.append(site.file()).append('\t').append(site.key()).append('\t')
                        .append(com.ssmt.core.BytecodeTextAllowlist.sha256(site.text())).append('\n');
            }
        }
        for (Site site : sites) {
            if (site.verdict() != Verdict.TEXT) {
                text.append("# ").append(site.verdict()).append('\t').append(site.file()).append('\t')
                        .append(site.key()).append('\t').append(site.rule()).append('\t').append(site.callee())
                        .append('\n');
            }
        }
        return text.toString();
    }

    /**
     * Literals that every call site proves to be lookup keys: {@code file|class|text} to the deciding rule id.
     * A literal used anywhere else (or not followed to a call) is not listed.
     */
    public Map<String, String> protectedLiterals(List<Site> sites) {
        Map<String, String> rulesByLiteral = new HashMap<>();
        Set<String> mixed = new HashSet<>();
        for (Site site : sites) {
            String literal = site.file() + "|" + site.className() + "|" + site.text();
            if (site.verdict() == Verdict.PROTECT && !mixed.contains(literal)) {
                rulesByLiteral.putIfAbsent(literal, "call:" + site.rule());
            } else {
                mixed.add(literal);
                rulesByLiteral.remove(literal);
            }
        }
        return rulesByLiteral;
    }
}
