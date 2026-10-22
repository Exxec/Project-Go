package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.core.model.TranslationProvenance;
import com.ssmt.project.CarryForwardService.Status;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class CarryForwardServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String HASH = "a".repeat(64);
    private final CarryForwardService service = new CarryForwardService();

    /** Synthetic unit: id is the bridgeforge id, group is blank for plain TEXT. */
    private record U(String id, String column, String role, String group, String source, String translation,
            TranslationProvenance provenance) {
        static U text(String id, String source, String translation) {
            return new U(id, "name", "TEXT", "", source, translation, TranslationProvenance.HUMAN_EDITED);
        }

        U in(String newColumn) {
            return new U(id, newColumn, role, group, source, translation, provenance);
        }

        U by(TranslationProvenance value) {
            return new U(id, column, role, group, source, translation, value);
        }
    }

    private static LocalizationProject project(List<U> units) throws Exception {
        ObjectNode document = (ObjectNode) JSON.readTree("{\"schema_version\":1,\"mode\":\"TRANSLATION_EXPORT\","
                + "\"mod_id\":\"m\",\"source_language\":\"zh\",\"target_language\":\"en\",\"instructions\":\"x\","
                + "\"file_hashes\":{\"data/a.csv\":\"" + HASH + "\"},\"unreadable\":[],\"glossary\":{},"
                + "\"entries\":[]}");
        ArrayNode entries = (ArrayNode) document.path("entries");
        List<ProjectEntry> projectEntries = new ArrayList<>();
        for (U unit : units) {
            ObjectNode entry = entries.addObject();
            entry.put("id", unit.id()).put("file", "data/a.csv").put("kind", "csv").put("source", unit.source())
                    .put("translation", unit.translation()).put("role", unit.role());
            entry.putObject("context").put("row", unit.id()).put("column", unit.column());
            if (unit.role().equals("LINKED")) {
                entry.put("group", unit.group());
            }
            projectEntries.add(new ProjectEntry(Path.of("data/a.csv"), "bf:" + unit.id(), unit.source(),
                    unit.translation(), unit.provenance()));
        }
        return new LocalizationProject(1, "m", "m.tr", "M", projectEntries, document);
    }

    private CarryForwardService.Result run(List<U> before, List<U> after) throws Exception {
        List<U> blank = new ArrayList<>();
        after.forEach(unit -> blank.add(new U(unit.id(), unit.column(), unit.role(), unit.group(), unit.source(), "",
                TranslationProvenance.MANUAL_IMPORT)));
        return service.carry(project(before), project(blank));
    }

    private static Status status(CarryForwardService.Result result, String id) {
        return result.findings().stream().filter(finding -> finding.key().equals("bf:" + id)).findFirst()
                .orElseThrow().status();
    }

    private static String translation(CarryForwardService.Result result, String id) {
        return result.project().entries().stream().filter(entry -> entry.key().equals("bf:" + id)).findFirst()
                .orElseThrow().translatedText();
    }

    @Test void eachClassIsReportedAndOnlyCarriedApprovalsAreReused() throws Exception {
        var result = run(
                List.of(U.text("same", "原文", "Text"), U.text("edit", "旧", "Old"), U.text("ctx", "文", "Doc"),
                        U.text("oldkey", "移动", "Moved"), U.text("gone", "删", "Gone"),
                        U.text("draft", "草", "Draft").by(TranslationProvenance.AI_TRANSLATED)),
                List.of(U.text("same", "原文", ""), U.text("edit", "新", ""), U.text("ctx", "文", "").in("desc"),
                        U.text("newkey", "移动", ""), U.text("fresh", "新增", ""), U.text("draft", "草", "")));

        assertThat(status(result, "same")).isEqualTo(Status.CARRIED);
        assertThat(status(result, "edit")).isEqualTo(Status.CHANGED);
        assertThat(status(result, "ctx")).isEqualTo(Status.CHANGED);
        assertThat(status(result, "newkey")).isEqualTo(Status.MOVED);
        assertThat(status(result, "fresh")).isEqualTo(Status.NEW);
        assertThat(status(result, "gone")).isEqualTo(Status.REMOVED);
        assertThat(status(result, "draft")).isEqualTo(Status.UNCHANGED_UNAPPROVED);
        assertThat(result.findings()).noneMatch(f -> f.key().equals("bf:oldkey"));
        assertThat(translation(result, "same")).isEqualTo("Text");
        for (String id : List.of("edit", "ctx", "newkey", "fresh", "draft")) {
            assertThat(translation(result, id)).as(id).isEmpty();
        }
        var moved = result.findings().stream().filter(f -> f.status() == Status.MOVED).findFirst().orElseThrow();
        assertThat(moved.hints()).extracting(CarryForwardService.Hint::translation).containsExactly("Moved");
        var changed = result.findings().stream().filter(f -> f.key().equals("bf:edit")).findFirst().orElseThrow();
        assertThat(changed.hints()).extracting(CarryForwardService.Hint::translation).containsExactly("Old");
    }

    @Test void aChangedSourceDigestNeverCarriesEvenWithSameIdentityAndContext() throws Exception {
        var result = run(List.of(U.text("a", "一", "One")), List.of(U.text("a", "二", "")));
        assertThat(status(result, "a")).isEqualTo(Status.CHANGED);
        assertThat(translation(result, "a")).isEmpty();
        assertThat(CarryForwardService.digest("一")).isNotEqualTo(CarryForwardService.digest("二"));
    }

    private static U linked(String id, String group, String translation) {
        return new U(id, "name", "LINKED", group, "星门", translation, TranslationProvenance.HUMAN_EDITED);
    }

    @Test void aLinkedGroupIsCarriedOnlyWhenEveryMemberQualifies() throws Exception {
        List<U> before = List.of(linked("l1", "g", "Gate"), linked("l2", "g", "Gate"));
        var whole = run(before, before);
        assertThat(status(whole, "l1")).isEqualTo(Status.CARRIED);
        assertThat(status(whole, "l2")).isEqualTo(Status.CARRIED);
        assertThat(translation(whole, "l1")).isEqualTo("Gate");

        List<U> oneUnapproved = List.of(linked("l1", "g", "Gate"),
                linked("l2", "g", "Gate").by(TranslationProvenance.AI_TRANSLATED));
        var held = run(oneUnapproved, before);
        assertThat(status(held, "l1")).isEqualTo(Status.GROUP_HELD);
        assertThat(status(held, "l2")).isEqualTo(Status.UNCHANGED_UNAPPROVED);
        assertThat(translation(held, "l1")).isEmpty();
        assertThat(translation(held, "l2")).isEmpty();

        var disagree = run(List.of(linked("l1", "g", "Gate"), linked("l2", "g", "Door")), before);
        assertThat(status(disagree, "l1")).isEqualTo(Status.GROUP_HELD);
        assertThat(status(disagree, "l2")).isEqualTo(Status.GROUP_HELD);
        assertThat(translation(disagree, "l1")).isEmpty();
    }

    @Test void aGroupThatGainedAMemberChangesContextForEveryExistingMember() throws Exception {
        var grown = run(List.of(linked("l1", "g", "Gate"), linked("l2", "g", "Gate")),
                List.of(linked("l1", "g", ""), linked("l2", "g", ""), linked("l3", "g", "")));
        assertThat(status(grown, "l1")).isEqualTo(Status.CHANGED);
        assertThat(status(grown, "l2")).isEqualTo(Status.CHANGED);
        assertThat(status(grown, "l3")).isEqualTo(Status.NEW);
        assertThat(grown.project().entries()).allMatch(entry -> entry.translatedText().isEmpty());
    }

    @Test void inputsAreNotModifiedAndOutputIsDeterministic() throws Exception {
        List<U> before = List.of(U.text("a", "一", "One"), U.text("b", "二", "Two"), U.text("c", "三", "Three"));
        List<U> after = List.of(U.text("a", "一", ""), U.text("b", "改", ""), U.text("d", "三", ""));
        var oldProject = project(before);
        var newProject = project(after);
        byte[] oldBytes = new LocalizationProjectService().serialize(oldProject);
        byte[] newBytes = new LocalizationProjectService().serialize(newProject);

        var first = service.carry(oldProject, newProject);
        var second = service.carry(oldProject, newProject);
        List<U> reversed = new ArrayList<>(after);
        Collections.reverse(reversed);
        var shuffled = service.carry(project(before), project(reversed));

        assertThat(new LocalizationProjectService().serialize(oldProject)).isEqualTo(oldBytes);
        assertThat(new LocalizationProjectService().serialize(newProject)).isEqualTo(newBytes);
        assertThat(CarryForwardService.toJson(first)).isEqualTo(CarryForwardService.toJson(second));
        // Only the new project's own serialization digest depends on the method document's entry order.
        assertThat(CarryForwardService.toJson(first).deepCopy().without("newProjectSha256").toString())
                .isEqualTo(CarryForwardService.toJson(shuffled).deepCopy().without("newProjectSha256").toString());
        assertThat(CarryForwardService.summary(first)).isEqualTo(CarryForwardService.summary(shuffled));
        assertThat(first.oldProjectSha256()).hasSize(64);
    }

    @Test void differentModsAreRefused() throws Exception {
        var other = new LocalizationProject(1, "other", "o.tr", "O", List.of());
        assertThatThrownBy(() -> service.carry(project(List.of()), other)).isInstanceOf(ProjectException.class);
    }
}
