package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.core.model.TranslationProvenance;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PolicyApprovalTest {
    private static final PolicyApproval RULES = PolicyApproval.withDefaultRules();
    @TempDir Path temporary;

    @Test void aPassingDraftIsApprovedAndEachRuleFailingAloneSendsItToReview() {
        String source = "增加装甲 %s";
        Map<String, String> glossary = Map.of("装甲", "armor");
        assertThat(RULES.evaluate("TEXT", "csv", source, "Increases armor %s", glossary).approved()).isTrue();
        assertThat(RULES.evaluate("IDENTIFIER", "csv", source, "Increases armor %s", glossary).reason())
                .contains("never approved");
        assertThat(RULES.evaluate("LINKED", "csv", source, "Increases armor %s", glossary).approved()).isFalse();
        assertThat(RULES.evaluate("UNKNOWN", "json-key", source, "Increases armor %s", glossary).approved()).isFalse();
        assertThat(RULES.evaluate("TEXT", "csv", source, "Increases 装甲 %s", glossary).reason())
                .contains("CJK");
        assertThat(RULES.evaluate("TEXT", "csv", source, "Increases plating %s", glossary).reason())
                .contains("glossary term");
        assertThat(RULES.evaluate("TEXT", "csv", source, "Increases armor %s\nand more", glossary).reason())
                .contains("line breaks");
        assertThat(RULES.evaluate("TEXT", "csv", "长长长长长长长长长长",
                "x".repeat(200), Map.of()).reason()).contains("length ratio");
        assertThat(RULES.evaluate("TEXT", "csv", "长长长长长长长长长长", "x",
                Map.of()).reason()).contains("length ratio");
        assertThat(RULES.evaluate("TEXT", "csv", source, "", glossary).approved()).isFalse();
        assertThat(RULES.rulesVersion()).isEqualTo("policy-1");
    }

    @Test void aWholeUnitGlossaryTermMustBeRenderedExactly() {
        Map<String, String> glossary = Map.of("星门", "Gate");
        assertThat(RULES.evaluate("TEXT", "csv", "星门", "Gate", glossary).approved()).isTrue();
        assertThat(RULES.evaluate("TEXT", "csv", "星门", "Stargate", glossary).approved()).isFalse();
    }

    private Path mod() throws IOException {
        Path mod = temporary.resolve("mod");
        Files.createDirectories(mod.resolve("data"));
        Files.writeString(mod.resolve("mod_info.json"), "{\"id\":\"pol\",\"name\":\"Pol\",\"version\":\"1\"}");
        Files.writeString(mod.resolve("data/a.csv"), "id,name,desc\n甲,名字一,说明一\n"
                + "b,名字二,说明二\n");
        return mod;
    }

    @Test void importMarksPassingUnitsPolicyApprovedKeepsOthersAsDraftsAndRevokeUndoesIt() throws Exception {
        Path source = mod();
        var service = new LocalizationProjectService();
        var project = service.createUsingBridgeForge(source, "p", "P");
        assertThat(project.entries()).hasSize(4); // the id column cell is protected and not offered
        Path request = temporary.resolve("req.json");
        var exchange = new AiTranslationExchangeService();
        exchange.exportPackage(request, project, "Pol", "zh", "en");
        var mapper = new ObjectMapper();
        var document = (ObjectNode) mapper.readTree(request.toFile());
        for (JsonNode entry : document.path("entries")) {
            String sourceText = entry.path("source").asText();
            String translation = sourceText.equals("说明二") ? "说明" : "Translated " + sourceText.length();
            ((ObjectNode) entry).put("translation", translation.startsWith("Translated") ? "Name text" : translation);
        }
        Path response = temporary.resolve("resp.json");
        mapper.writeValue(response.toFile(), document);
        Path tm = temporary.resolve("tm.db");
        var imported = exchange.importResponse(response, project, tm, AiImportPolicy.APPROVE_BY_RULES);
        assertThat(imported.policyApproved()).isEqualTo(3);
        assertThat(imported.policyReview()).singleElement().asString().contains("CJK");
        assertThat(imported.project().entries()).filteredOn(entry -> entry.provenance()
                == TranslationProvenance.POLICY_APPROVED).hasSize(3);
        assertThat(imported.project().entries()).filteredOn(entry -> entry.provenance()
                == TranslationProvenance.AI_TRANSLATED).hasSize(1);
        try (var memory = com.ssmt.tm.SqliteTranslationMemory.open(tm)) {
            assertThat(memory.findAll()).anyMatch(entry -> entry.provenance() == TranslationProvenance.POLICY_APPROVED);
            // A rule-approved draft is not a human-approved glossary entry.
            assertThat(memory.findExactApprovedTranslations("名字一", "zh", "en")).isEmpty();
        }
        var revoked = PolicyApproval.revoke(imported.project());
        assertThat(revoked.entries()).noneMatch(entry -> entry.provenance() == TranslationProvenance.POLICY_APPROVED);
        assertThat(revoked.entries()).extracting(ProjectEntry::translatedText)
                .isEqualTo(imported.project().entries().stream().map(ProjectEntry::translatedText).toList());
    }

    @Test void reviewDraftsPolicyStillNeverApproves() throws Exception {
        Path source = mod();
        var project = new LocalizationProjectService().createUsingBridgeForge(source, "p", "P");
        Path request = temporary.resolve("req.json");
        var exchange = new AiTranslationExchangeService();
        exchange.exportPackage(request, project, "Pol", "zh", "en");
        var mapper = new ObjectMapper();
        var document = (ObjectNode) mapper.readTree(request.toFile());
        document.path("entries").forEach(entry -> ((ObjectNode) entry).put("translation", "Name text"));
        Path response = temporary.resolve("resp.json");
        mapper.writeValue(response.toFile(), document);
        var imported = exchange.importResponse(response, project, null, AiImportPolicy.REVIEW_DRAFTS);
        assertThat(imported.policyApproved()).isZero();
        assertThat(imported.project().entries()).allMatch(entry -> entry.provenance()
                == TranslationProvenance.AI_TRANSLATED);
    }
}
