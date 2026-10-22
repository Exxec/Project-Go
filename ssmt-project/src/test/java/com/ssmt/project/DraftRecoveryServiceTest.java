package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DraftRecoveryServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DraftRecoveryService service = new DraftRecoveryService();
    @TempDir Path temporary;
    private Path packet;
    private Path workspace;

    @BeforeEach
    void createPacket() throws Exception {
        packet = temporary.resolve("packet");
        workspace = temporary.resolve("work");
        Files.createDirectories(packet);
        ObjectNode review = JSON.createObjectNode();
        ArrayNode entries = review.putArray("entries");
        for (int index = 1; index <= 5; index++) {
            entry(entries, "text:" + index, "TEXT", "", "源" + index, "");
        }
        for (String member : List.of("link:a:1", "link:a:2", "link:a:3")) {
            entry(entries, member, "LINKED", "link:a", "共享甲", "");
        }
        for (String member : List.of("link:b:1", "link:b:2")) {
            entry(entries, member, "LINKED", "link:b", "共享乙", "");
        }
        entry(entries, "ident:1", "IDENTIFIER", "", "key", "");
        entry(entries, "text:done", "TEXT", "", "已译", "Done");
        Files.writeString(packet.resolve("review.json"), JSON.writeValueAsString(review), StandardCharsets.UTF_8);
        Files.writeString(packet.resolve("approved.json"), "{\"entries\":[{\"id\":\"ok:1\",\"target\":\"Fine\"}]}",
                StandardCharsets.UTF_8);
    }

    @Test
    void batchingKeepsLinkedGroupsWhole() throws Exception {
        DraftRecoveryPlan plan = service.plan(packet, workspace, 4);

        assertThat(plan.units()).hasSize(10);
        assertThat(plan.groupCount()).isEqualTo(2);
        assertThat(plan.batches().stream().mapToInt(List::size).sum()).isEqualTo(10);
        plan.groups().values().forEach(members ->
                assertThat(plan.batches().stream().filter(batch -> batch.containsAll(members))).hasSize(1));
        assertThat(plan.units()).doesNotContainKey("ident:1").doesNotContainKey("text:done");
    }

    @Test
    void aGroupLargerThanTheBatchSizeGetsItsOwnBatch() throws Exception {
        DraftRecoveryPlan plan = service.plan(packet, workspace, 2);

        assertThat(plan.batches()).anyMatch(batch -> batch.size() == 3 && batch.contains("link:a:1"));
    }

    @Test
    void outputIsDeterministic() throws Exception {
        service.export(packet, workspace, 3);
        Path other = temporary.resolve("other");
        service.export(packet, other, 3);

        assertThat(snapshot(workspace)).isEqualTo(snapshot(other));
        assertThat(Files.readString(workspace.resolve("recovery-manifest.json"))).doesNotContain("\r");
    }

    @Test
    void resumesAfterInterruptionWithoutTouchingImportedOrFilledBatches() throws Exception {
        service.export(packet, workspace, 3);
        Path first = workspace.resolve("batches/batch-0001.json");
        fill(first);
        service.importBatch(packet, workspace, first);
        // Simulate a crash after the first batch: later batches are missing from the manifest and disk.
        ObjectNode manifest = (ObjectNode) JSON.readTree(workspace.resolve("recovery-manifest.json").toFile());
        ArrayNode batches = (ArrayNode) manifest.get("batches");
        int total = batches.size();
        while (batches.size() > 1) {
            batches.remove(batches.size() - 1);
        }
        Files.writeString(workspace.resolve("recovery-manifest.json"), JSON.writeValueAsString(manifest));
        try (Stream<Path> files = Files.list(workspace.resolve("batches"))) {
            for (Path file : files.filter(item -> !item.equals(first)).toList()) {
                Files.delete(file);
            }
        }
        String importedBefore = Files.readString(first);

        var resumed = service.export(packet, workspace, 3);

        assertThat(resumed.written()).isEqualTo(total - 1);
        assertThat(resumed.skipped()).isEqualTo(1);
        assertThat(Files.readString(first)).isEqualTo(importedBefore);
        assertThat(service.status(workspace).importedBatches()).isEqualTo(1);
        for (int batch = 2; batch <= total; batch++) {
            Path file = workspace.resolve(String.format("batches/batch-%04d.json", batch));
            fill(file);
            service.importBatch(packet, workspace, file);
        }
        var status = service.status(workspace);
        assertThat(status.draftedUnits()).isEqualTo(10);
        assertThat(status.remainingUnits()).isZero();
        assertThat(service.importBatch(packet, workspace, first).alreadyImported()).isTrue();
    }

    @Test
    void importedDraftsAreUnapprovedAndInputsStayByteIdentical() throws Exception {
        String reviewBefore = DraftRecoveryPlan.sha256File(packet.resolve("review.json"));
        String approvedBefore = DraftRecoveryPlan.sha256File(packet.resolve("approved.json"));
        service.export(packet, workspace, 100);
        Path batch = workspace.resolve("batches/batch-0001.json");
        fill(batch);

        service.importBatch(packet, workspace, batch);

        JsonNode drafts = JSON.readTree(workspace.resolve("drafts.json").toFile()).path("drafts");
        assertThat(drafts).hasSize(10);
        drafts.forEach(draft -> assertThat(draft.path("status").asText()).isEqualTo("DRAFT_NEEDS_REVIEW"));
        assertThat(Files.readString(workspace.resolve("drafts.json"))).doesNotContain("APPROVED");
        assertThat(DraftRecoveryPlan.sha256File(packet.resolve("review.json"))).isEqualTo(reviewBefore);
        assertThat(DraftRecoveryPlan.sha256File(packet.resolve("approved.json"))).isEqualTo(approvedBefore);
    }

    @Test
    void rejectsUnknownStalePartialAndInconsistentIdentitiesAtomically() throws Exception {
        service.export(packet, workspace, 100);
        Path batch = workspace.resolve("batches/batch-0001.json");

        mutateAndExpectRejection(batch, units -> ((ObjectNode) units.get(0)).put("id", "text:nope"), "unknown identity");
        mutateAndExpectRejection(batch, units -> ((ObjectNode) units.get(0)).put("sourceDigest", "00"), "stale source");
        mutateAndExpectRejection(batch, units -> removeById(units, "link:a:2"), "partial LINKED group link:a");
        mutateAndExpectRejection(batch, units -> setTarget(units, "link:a:2", "different"), "different texts");
        mutateAndExpectRejection(batch, units -> setTarget(units, "text:1", " "), "blank or invalid");

        assertThat(workspace.resolve("drafts.json")).doesNotExist();
        assertThat(service.status(workspace).importedBatches()).isZero();
    }

    @Test
    void aChangedPacketMakesTheWorkspaceStale() throws Exception {
        service.export(packet, workspace, 100);
        Path batch = workspace.resolve("batches/batch-0001.json");
        fill(batch);
        String changed = Files.readString(packet.resolve("review.json")).replace("源1", "源1改");
        Files.writeString(packet.resolve("review.json"), changed, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.importBatch(packet, workspace, batch)).hasMessageContaining("stale");
        assertThatThrownBy(() -> service.export(packet, workspace, 100)).hasMessageContaining("stale");
    }

    @Test
    void aChangedApprovedCopyIsDetected() throws Exception {
        service.export(packet, workspace, 100);
        Files.writeString(packet.resolve("approved.json"), "{}", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.export(packet, workspace, 100)).hasMessageContaining("changed");
    }

    @Test
    void refusesAWorkspaceInsideThePacket() {
        assertThatThrownBy(() -> service.plan(packet, packet.resolve("work"), 5)).hasMessageContaining("separate");
    }

    @Test
    void refusesADifferentBatchSizeForAnExistingWorkspace() throws Exception {
        service.export(packet, workspace, 3);

        assertThatThrownBy(() -> service.export(packet, workspace, 5)).hasMessageContaining("re-batch");
    }

    private void mutateAndExpectRejection(Path batch, java.util.function.Consumer<ArrayNode> change, String message)
            throws Exception {
        Path edited = temporary.resolve("edited.json");
        Files.copy(batch, edited, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        fill(edited);
        ObjectNode document = (ObjectNode) JSON.readTree(edited.toFile());
        change.accept((ArrayNode) document.get("units"));
        Files.writeString(edited, JSON.writeValueAsString(document), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.importBatch(packet, workspace, edited)).hasMessageContaining(message);
    }

    private static void removeById(ArrayNode units, String id) {
        for (int index = 0; index < units.size(); index++) {
            if (units.get(index).path("id").asText().equals(id)) {
                units.remove(index);
                return;
            }
        }
    }

    private static void setTarget(ArrayNode units, String id, String text) {
        units.forEach(unit -> {
            if (unit.path("id").asText().equals(id)) {
                ((ObjectNode) unit).put("target", text);
            }
        });
    }

    private static void fill(Path batch) throws Exception {
        ObjectNode document = (ObjectNode) JSON.readTree(batch.toFile());
        for (JsonNode unit : document.get("units")) {
            String group = unit.path("group").asText();
            ((ObjectNode) unit).put("target", group.isEmpty() ? "T " + unit.path("id").asText() : "G " + group);
        }
        Files.writeString(batch, JSON.writeValueAsString(document), StandardCharsets.UTF_8);
    }

    private static void entry(ArrayNode entries, String id, String role, String group, String source, String target) {
        ObjectNode node = entries.addObject();
        node.put("id", id).put("role", role).put("kind", "csv").put("file", "f.csv").put("source", source);
        node.put("group", group).put("current_target", target).put("retained_target", target);
        node.putObject("context").put("row", id);
    }

    private static List<String> snapshot(Path root) throws Exception {
        List<String> lines = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                lines.add(root.relativize(file) + "=" + Files.readString(file));
            }
        }
        return lines;
    }
}
