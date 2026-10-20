package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.core.BytecodeTextAllowlist;
import com.ssmt.project.BytecodeCallSites.Verdict;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Classes are generated here with ASM from nothing; no mod bytes are used. */
class BytecodeCallSitesTest {
    private static final String KEY = "键";
    private static final String SHOWN = "你好";
    private static final String COMPARED = "比较";
    private static final String LOST = "孤立";
    private static final String MIXED = "混合";
    @TempDir Path temporary;

    /** Methods: lookup key, shown text, compared text, stored-and-forgotten text, and one literal used both ways. */
    private static byte[] generatedClass() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_5, Opcodes.ACC_PUBLIC, "gen/Probe", null, "java/lang/Object", null);
        MethodVisitor key = writer.visitMethod(Opcodes.ACC_PUBLIC, "key", "()V", null, null);
        key.visitCode();
        key.visitInsn(Opcodes.ACONST_NULL);
        key.visitLdcInsn(KEY);
        key.visitMethodInsn(Opcodes.INVOKEINTERFACE, "com/fs/starfarer/api/campaign/rules/MemoryAPI", "get",
                "(Ljava/lang/String;)Ljava/lang/Object;", true);
        key.visitInsn(Opcodes.POP);
        key.visitInsn(Opcodes.RETURN);
        key.visitMaxs(0, 0);
        key.visitEnd();
        MethodVisitor shown = writer.visitMethod(Opcodes.ACC_PUBLIC, "shown", "()V", null, null);
        shown.visitCode();
        shown.visitInsn(Opcodes.ACONST_NULL);
        shown.visitLdcInsn(SHOWN);
        shown.visitMethodInsn(Opcodes.INVOKEINTERFACE, "com/fs/starfarer/api/ui/TooltipMakerAPI", "addPara",
                "(Ljava/lang/String;)V", true);
        shown.visitLdcInsn(MIXED);
        shown.visitMethodInsn(Opcodes.INVOKEINTERFACE, "com/fs/starfarer/api/ui/TooltipMakerAPI", "addPara",
                "(Ljava/lang/String;)V", true);
        shown.visitInsn(Opcodes.RETURN);
        shown.visitMaxs(0, 0);
        shown.visitEnd();
        MethodVisitor compared = writer.visitMethod(Opcodes.ACC_PUBLIC, "compared", "()Z", null, null);
        compared.visitCode();
        compared.visitInsn(Opcodes.ACONST_NULL);
        compared.visitLdcInsn(COMPARED);
        compared.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        compared.visitInsn(Opcodes.IRETURN);
        compared.visitMaxs(0, 0);
        compared.visitEnd();
        MethodVisitor lost = writer.visitMethod(Opcodes.ACC_PUBLIC, "lost", "()V", null, null);
        lost.visitCode();
        lost.visitLdcInsn(LOST);
        lost.visitVarInsn(Opcodes.ASTORE, 1);
        lost.visitInsn(Opcodes.RETURN);
        lost.visitMaxs(0, 0);
        lost.visitEnd();
        MethodVisitor mixed = writer.visitMethod(Opcodes.ACC_PUBLIC, "mixed", "()V", null, null);
        mixed.visitCode();
        mixed.visitInsn(Opcodes.ACONST_NULL);
        mixed.visitLdcInsn(MIXED);
        mixed.visitMethodInsn(Opcodes.INVOKEINTERFACE, "com/fs/starfarer/api/campaign/rules/MemoryAPI", "get",
                "(Ljava/lang/String;)Ljava/lang/Object;", true);
        mixed.visitInsn(Opcodes.POP);
        mixed.visitInsn(Opcodes.RETURN);
        mixed.visitMaxs(0, 0);
        mixed.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private Path mod(boolean inJar) throws IOException {
        Path mod = temporary.resolve(inJar ? "jarmod" : "classmod");
        Files.createDirectories(mod.resolve("jars"));
        Files.createDirectories(mod.resolve("classes/gen"));
        Files.writeString(mod.resolve("mod_info.json"), "{\"id\":\"cs\",\"name\":\"Cs\",\"version\":\"1\"}");
        if (inJar) {
            try (var zip = new ZipOutputStream(Files.newOutputStream(mod.resolve("jars/probe.jar")))) {
                zip.putNextEntry(new ZipEntry("gen/Probe.class"));
                zip.write(generatedClass());
                zip.closeEntry();
            }
        } else {
            Files.write(mod.resolve("classes/gen/Probe.class"), generatedClass());
        }
        return mod;
    }

    private static Verdict verdict(List<BytecodeCallSites.Site> sites, String text) {
        return sites.stream().filter(site -> site.text().equals(text)).map(BytecodeCallSites.Site::verdict)
                .findFirst().orElseThrow();
    }

    @Test void theFirstCallAfterALiteralDecidesItsVerdict() throws Exception {
        var sites = BytecodeCallSites.withDefaultRules().scan(mod(false), new ArrayList<>());
        assertThat(verdict(sites, KEY)).isEqualTo(Verdict.PROTECT);
        assertThat(verdict(sites, SHOWN)).isEqualTo(Verdict.TEXT);
        assertThat(verdict(sites, COMPARED)).isEqualTo(Verdict.REVIEW);
        assertThat(verdict(sites, LOST)).isEqualTo(Verdict.REVIEW);
        assertThat(sites).filteredOn(site -> site.text().equals(KEY)).singleElement()
                .satisfies(site -> assertThat(site.callee()).endsWith("MemoryAPI.get"));
        assertThat(sites).filteredOn(site -> site.text().equals(LOST)).singleElement()
                .satisfies(site -> assertThat(site.callee()).isEmpty());
    }

    @Test void proposalListsOnlyTextRowsInTheRealAllowlistFormatAndKeysMatchTheLegacyExtractor() throws Exception {
        Path mod = mod(true);
        var sites = BytecodeCallSites.withDefaultRules().scan(mod, new ArrayList<>());
        String proposal = BytecodeCallSites.proposal(sites);
        Files.writeString(mod.resolve(BytecodeTextAllowlist.FILE_NAME), proposal);
        var allowlist = BytecodeTextAllowlist.read(mod);
        Path jar = Path.of("jars/probe.jar");
        String shownKey = sites.stream().filter(site -> site.text().equals(SHOWN)).findFirst().orElseThrow().key();
        assertThat(allowlist.permits(jar, shownKey, SHOWN)).isTrue();
        assertThat(allowlist.permits(jar, sites.stream().filter(site -> site.text().equals(KEY)).findFirst()
                .orElseThrow().key(), KEY)).isFalse();
        for (var site : sites) {
            if (site.text().equals(MIXED)) {
                assertThat(allowlist.permits(jar, site.key(), MIXED)).isFalse();
            }
        }
        assertThat(proposal).contains("# PROTECT").contains("# REVIEW").contains(BytecodeTextAllowlist.sha256(SHOWN));
        var legacy = new com.ssmt.extractor.bytecode.ClassStringExtractor().extract(
                new com.ssmt.core.plugin.ExtractionRequest("cs", mod, mod.resolve("jars/probe.jar")));
        assertThat(legacy).extracting(com.ssmt.core.model.ExtractedString::key).contains(shownKey);
    }

    @Test void aLiteralUsedBothAsKeyAndTextStaysProtectedRegardlessOfSiteOrder() throws Exception {
        var sites = BytecodeCallSites.withDefaultRules().scan(mod(false), new ArrayList<>());
        var protectedLiterals = BytecodeCallSites.withDefaultRules().protectedLiterals(sites);
        assertThat(protectedLiterals).containsKey("classes/gen/Probe.class||" + KEY)
                .containsKey("classes/gen/Probe.class||" + MIXED)
                .doesNotContainKey("classes/gen/Probe.class||" + SHOWN);
        assertThat(BytecodeCallSites.withDefaultRules().protectedLiterals(sites.reversed()))
                .isEqualTo(protectedLiterals);
    }

    @Test void lookupLiteralsBecomeProtectedIdentifiersInTheExportAndApplyKeepsThemUntouched() throws Exception {
        for (boolean inJar : new boolean[] {false, true}) {
            Path mod = mod(inJar);
            var document = new BridgeForgeTranslationService().exportWithRoles(mod);
            var roles = new java.util.HashMap<String, String>();
            document.path("entries").forEach(entry -> roles.put(entry.path("source").asText(),
                    entry.path("role").asText("TEXT")));
            assertThat(roles).containsEntry(KEY, "IDENTIFIER").containsEntry(SHOWN, "TEXT")
                    .containsEntry(COMPARED, "TEXT").containsEntry(MIXED, "IDENTIFIER");
            document.path("entries").forEach(entry -> ((com.fasterxml.jackson.databind.node.ObjectNode) entry)
                    .put("translation", "English"));
            var report = new BridgeForgeTranslationApply().apply(mod, document,
                    temporary.resolve(inJar ? "out-jar" : "out-class"), false);
            assertThat(report.path("problems").toString()).contains("protected identifier");
            assertThat(report.path("leftover").path("by_reason").path("PROTECTED_IDENTIFIER").asInt()).isEqualTo(2);
            Path output = temporary.resolve(inJar ? "out-jar" : "out-class");
            var remaining = new BridgeForgeTranslationService().export(output);
            assertThat(remaining.path("entries")).hasSize(2);
            assertThat(remaining.path("entries").toString()).contains(KEY).contains(MIXED);
        }
    }
}
