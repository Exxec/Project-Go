package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.core.model.TranslationProvenance;
import com.ssmt.tm.SqliteTranslationMemory;
import com.ssmt.tm.TranslationDraft;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Synthetic mods only. */
class GlossaryServiceTest {
    private static final String FACTION = "星门共和国";
    private static final String REPEATED = "维修";
    @TempDir Path temporary;

    private Path mod() throws IOException {
        Path mod = temporary.resolve("mod");
        Files.createDirectories(mod.resolve("data/world"));
        Files.writeString(mod.resolve("mod_info.json"), "{\"id\":\"gl\",\"name\":\"Gl\",\"version\":\"1\"}");
        Files.writeString(mod.resolve("data/world/factions.csv"), "id,displayName\nrepublic," + FACTION + "\n");
        Files.writeString(mod.resolve("data/world/names_en.csv"), "id,displayName\nrepublic,Gate Republic\n");
        Files.writeString(mod.resolve("data/world/ships.csv"), "id,name\ns1," + REPEATED + "\ns2," + REPEATED
                + "\ns3," + REPEATED + "\ns4,另一个\n");
        return mod;
    }

    private LocalizationProject project(Path mod) throws Exception {
        return new LocalizationProjectService().createUsingBridgeForge(mod, "p", "P");
    }

    private GlossaryService service(Path tm) {
        return new GlossaryService(temporary.resolve("mod-glossary.json"), temporary.resolve("global-glossary.json"),
                tm, GlossaryService.DEFAULT_REPEAT_THRESHOLD);
    }

    @Test void aNameTheModSpellsInEnglishInAParallelRowIsApprovedAndARepeatedUnitOnlyProposed() throws Exception {
        Path mod = mod();
        var proposal = service(null).propose(mod, project(mod));
        assertThat(proposal.approved()).singleElement().satisfies(term -> {
            assertThat(term.source()).isEqualTo(FACTION);
            assertThat(term.target()).isEqualTo("Gate Republic");
            assertThat(term.origin()).isEqualTo("MOD_ENGLISH");
        });
        assertThat(proposal.proposed()).singleElement().satisfies(term -> {
            assertThat(term.source()).isEqualTo(REPEATED);
            assertThat(term.occurrences()).isEqualTo(3);
            assertThat(term.origin()).isEqualTo("REPEATED");
            assertThat(term.target()).isEmpty();
        });
        assertThat(proposal.approvedMap()).doesNotContainKey(REPEATED);
    }

    @Test void onlyHumanApprovedTranslationMemoryEntriesBecomeTerms() throws Exception {
        Path mod = mod();
        Path tm = temporary.resolve("tm.db");
        try (var memory = SqliteTranslationMemory.open(tm)) {
            memory.upsertAll(List.of(
                    new TranslationDraft(REPEATED, "zh", "en", "Repair", "ctx", TranslationProvenance.HUMAN_EDITED),
                    new TranslationDraft("另一个", "zh", "en", "Another", "ctx",
                            TranslationProvenance.AI_TRANSLATED)));
        }
        var proposal = service(tm).propose(mod, project(mod));
        assertThat(proposal.approvedMap()).containsEntry(REPEATED, "Repair").doesNotContainKey("另一个");
        assertThat(proposal.proposed()).isEmpty();
    }

    @Test void disagreeingSourcesBecomeAConflictProposalNotAnApproval() throws Exception {
        Path mod = mod();
        Path tm = temporary.resolve("tm.db");
        try (var memory = SqliteTranslationMemory.open(tm)) {
            memory.upsertAll(List.of(new TranslationDraft(FACTION, "zh", "en", "Stargate Republic", "ctx",
                    TranslationProvenance.HUMAN_EDITED)));
        }
        var proposal = service(tm).propose(mod, project(mod));
        assertThat(proposal.approvedMap()).doesNotContainKey(FACTION);
        assertThat(proposal.proposed()).anySatisfy(term -> {
            assertThat(term.origin()).isEqualTo("CONFLICT");
            assertThat(term.note()).contains("Gate Republic").contains("Stargate Republic");
        });
    }

    @Test void userApprovalsArePerModAndGlobalAndTheModWinsAndTheyFillTheExportGlossary() throws Exception {
        Path mod = mod();
        var service = service(null);
        service.approve(REPEATED, "Maintenance", true);
        assertThat(service.propose(mod, project(mod)).approvedMap()).containsEntry(REPEATED, "Maintenance");
        service.approve(REPEATED, "Upkeep", false);
        var proposal = service.propose(mod, project(mod));
        assertThat(proposal.approvedMap()).containsEntry(REPEATED, "Upkeep").containsEntry(FACTION, "Gate Republic");
        assertThat(proposal.proposed()).isEmpty();
        var applied = service.apply(mod, project(mod));
        assertThat(applied.methodDocument().path("glossary").path(REPEATED).asText()).isEqualTo("Upkeep");
        assertThat(applied.methodDocument().path("glossary").path(FACTION).asText()).isEqualTo("Gate Republic");
        assertThat(applied.entries()).hasSameSizeAs(project(mod).entries());
        // A later user approval is stored durably and read back by a new service instance.
        assertThat(new GlossaryInterchangeService().read(temporary.resolve("mod-glossary.json")).terms())
                .singleElement().satisfies(term -> assertThat(term.target()).isEqualTo("Upkeep"));
    }

    @Test void theWorkflowPutsApprovedTermsIntoTheRequestAndOneClickApprovalMovesAProposalIntoIt() throws Exception {
        Path mod = mod();
        var workflow = new TranslationWorkflow(temporary.resolve("owned"));
        var session = workflow.loadMod(mod);
        Path request = temporary.resolve("request.json");
        workflow.exportTranslation(session, request);
        var glossary = new com.fasterxml.jackson.databind.ObjectMapper().readTree(request.toFile()).path("glossary");
        assertThat(glossary.path(FACTION).asText()).isEqualTo("Gate Republic");
        assertThat(glossary.has(REPEATED)).isFalse();
        assertThat(workflow.glossary(session).proposed()).extracting(GlossaryService.Term::source)
                .containsExactly(REPEATED);
        var approved = workflow.approveGlossaryTerm(session, REPEATED, "Repairs", false);
        assertThat(workflow.glossary(approved).proposed()).isEmpty();
        Path second = temporary.resolve("second.json");
        workflow.exportTranslation(approved, second);
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(second.toFile()).path("glossary")
                .path(REPEATED).asText()).isEqualTo("Repairs");
        // The approval survives reopening the mod.
        assertThat(new TranslationWorkflow(temporary.resolve("owned")).loadMod(mod).project().methodDocument()
                .path("glossary").path(REPEATED).asText()).isEqualTo("Repairs");
    }
}
