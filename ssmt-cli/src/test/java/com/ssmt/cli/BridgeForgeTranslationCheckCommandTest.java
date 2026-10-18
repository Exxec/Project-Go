package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class BridgeForgeTranslationCheckCommandTest {
    @TempDir Path temporary;

    private Path modWithUnreadableFileOnly() throws IOException {
        Path mod = temporary.resolve("mod");
        Files.createDirectories(mod.resolve("data/config"));
        Files.writeString(mod.resolve("mod_info.json"), "{\"id\":\"chk\",\"name\":\"Chk\",\"version\":\"1\"}");
        Files.write(mod.resolve("data/config/bad.json"), new byte[] {'{', '"', (byte) 0xb5, (byte) 0xe7, (byte) 0xff, '"', '}'});
        return mod;
    }

    @Test void checkFailsOnUnreadableInputsUntilTheyAreAcknowledged() throws Exception {
        Path mod = modWithUnreadableFileOnly();
        assertThat(new CommandLine(new BridgeForgeTranslationCommand()).execute("check", mod.toString())).isEqualTo(2);
        assertThat(new CommandLine(new BridgeForgeTranslationCommand())
                .execute("check", mod.toString(), "--accept-unreadable")).isZero();
        assertThat(new CommandLine(new BridgeForgeTranslationCommand())
                .execute("export", mod.toString(), "--out", temporary.resolve("x.json").toString(),
                        "--accept-unreadable")).isEqualTo(1);
    }

    @Test void projectCreateAcceptsUnreadableInputsOnlyWhenAcknowledged() throws Exception {
        Path mod = modWithUnreadableFileOnly();
        Files.writeString(mod.resolve("data/good.csv"), "id,name\nx,中文\n");
        Path project = temporary.resolve("project.json");
        assertThat(new CommandLine(new ProjectCommand()).execute("create", mod.toString(), project.toString(),
                "--patch-id", "p", "--patch-name", "P")).isZero();
        assertThat(Files.readString(project)).contains("data/config/bad.json").doesNotContain("unreadable_acknowledged");
        Path accepted = temporary.resolve("accepted.json");
        assertThat(new CommandLine(new ProjectCommand()).execute("create", mod.toString(), accepted.toString(),
                "--patch-id", "p", "--patch-name", "P", "--accept-unreadable")).isZero();
        assertThat(Files.readString(accepted)).contains("unreadable_acknowledged");
    }

    @Test void checkJsonGivesEachLeftoverItemItsReason() throws Exception {
        Path mod = modWithUnreadableFileOnly();
        Files.writeString(mod.resolve("data/good.csv"), "id,name\nx,中文\n");
        Files.writeString(mod.resolve("readme.md"), "中文");
        Path report = temporary.resolve("leftover.json");
        assertThat(new CommandLine(new BridgeForgeTranslationCommand())
                .execute("check", mod.toString(), "--json", report.toString())).isEqualTo(2);
        var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(report.toFile());
        assertThat(tree.path("by_reason").path("UNTRANSLATED").asInt()).isEqualTo(1);
        assertThat(tree.path("by_reason").path("UNREADABLE").asInt()).isEqualTo(1);
        assertThat(tree.path("by_reason").path("UNSUPPORTED_TYPE").asInt()).isEqualTo(1);
    }

    @Test void bytecodeAuditWritesAnAllowlistProposalWithTextRowsAndAJsonReport() throws Exception {
        Path mod = temporary.resolve("auditmod");
        Files.createDirectories(mod.resolve("classes"));
        Files.writeString(mod.resolve("mod_info.json"), "{\"id\":\"aud\",\"name\":\"Aud\",\"version\":\"1\"}");
        var writer = new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
        writer.visit(org.objectweb.asm.Opcodes.V1_5, org.objectweb.asm.Opcodes.ACC_PUBLIC, "gen/Probe", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC, "show", "()V", null, null);
        method.visitCode();
        method.visitInsn(org.objectweb.asm.Opcodes.ACONST_NULL);
        method.visitLdcInsn("你好");
        method.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKEINTERFACE, "x/TooltipMakerAPI", "addPara",
                "(Ljava/lang/String;)V", true);
        method.visitInsn(org.objectweb.asm.Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        Files.write(mod.resolve("classes/Probe.class"), writer.toByteArray());
        Path proposal = temporary.resolve("proposal.tsv");
        Path report = temporary.resolve("audit.json");
        assertThat(new CommandLine(new BridgeForgeTranslationCommand()).execute("bytecode-audit", mod.toString(),
                "--out", proposal.toString(), "--json", report.toString())).isZero();
        assertThat(Files.readString(proposal)).startsWith("# ssmt-bytecode-allowlist-v1")
                .contains("classes/Probe.class	class:gen/Probe#method:show()V:ldc:0	");
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(report.toFile()).path("sites")).hasSize(1);
        assertThat(new CommandLine(new BridgeForgeTranslationCommand()).execute("bytecode-audit", mod.toString(),
                "--out", proposal.toString())).isEqualTo(1);
    }

    @Test void glossaryProposesTermsAndApprovalsAreStoredAndUsed() throws Exception {
        Path mod = temporary.resolve("glmod");
        Files.createDirectories(mod.resolve("data"));
        Files.writeString(mod.resolve("mod_info.json"), "{\"id\":\"gl\",\"name\":\"Gl\",\"version\":\"1\"}");
        Files.writeString(mod.resolve("data/a.csv"), "id,name\ns1,维修\ns2,维修\ns3,维修\n");
        Path proposal = temporary.resolve("glossary.json");
        Path store = temporary.resolve("mine.json");
        assertThat(new CommandLine(new BridgeForgeTranslationCommand()).execute("glossary", mod.toString(),
                "--out", proposal.toString(), "--mod-glossary", store.toString())).isZero();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        assertThat(mapper.readTree(proposal.toFile()).path("proposed")).hasSize(1);
        assertThat(new CommandLine(new BridgeForgeTranslationCommand()).execute("glossary-approve", store.toString(),
                "--term", "维修=Repairs")).isZero();
        Path second = temporary.resolve("glossary2.json");
        assertThat(new CommandLine(new BridgeForgeTranslationCommand()).execute("glossary", mod.toString(),
                "--out", second.toString(), "--mod-glossary", store.toString())).isZero();
        var tree = mapper.readTree(second.toFile());
        assertThat(tree.path("proposed")).isEmpty();
        assertThat(tree.path("approved").get(0).path("target").asText()).isEqualTo("Repairs");
        assertThat(new CommandLine(new BridgeForgeTranslationCommand()).execute("glossary-approve", store.toString(),
                "--term", "broken")).isEqualTo(1);
    }
}
