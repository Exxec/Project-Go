package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssmt.project.UnitRoleClassifier.Role;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** One fixture per rule; all inputs are synthetic. */
class UnitRoleClassifierTest {
    private static final Path CONFORMANCE = Path.of("../fixtures/translation-conformance/input");
    private static final Path CLASS_JAR = CONFORMANCE.resolve("jars/test.jar");
    @TempDir Path temporary;

    private Path mod(String name) throws IOException {
        Path mod = temporary.resolve(name);
        Files.createDirectories(mod);
        Files.writeString(mod.resolve("mod_info.json"), "{\"id\":\"" + name + "\",\"name\":\"N\",\"version\":\"1\"}");
        return mod;
    }

    private static void write(Path mod, String file, String text) throws IOException {
        Files.createDirectories(mod.resolve(file).getParent());
        Files.writeString(mod.resolve(file), text);
    }

    private UnitRoleClassifier.Result classify(Path mod) throws Exception {
        return UnitRoleClassifier.withDefaultRules().classify(new BridgeForgeTranslationService().export(mod));
    }

    private static Role role(UnitRoleClassifier.Result result, String id) {
        return result.role(id);
    }

    @Test void csvIdentifierColumnsAreProtectedWhateverTheirSpelling() throws Exception {
        Path mod = mod("csvid");
        write(mod, "data/a.csv", "id,hull_id,tags,hints,name\n甲,乙,丙,丁,戊\n");
        var result = classify(mod);
        assertThat(role(result, "csv:data/a.csv#甲:id")).isEqualTo(Role.IDENTIFIER);
        assertThat(role(result, "csv:data/a.csv#甲:hull_id")).isEqualTo(Role.IDENTIFIER);
        assertThat(role(result, "csv:data/a.csv#甲:tags")).isEqualTo(Role.IDENTIFIER);
        assertThat(role(result, "csv:data/a.csv#甲:hints")).isEqualTo(Role.IDENTIFIER);
        assertThat(role(result, "csv:data/a.csv#甲:name")).isEqualTo(Role.TEXT);
        assertThat(result.rules().get("csv:data/a.csv#甲:hull_id")).isEqualTo("csv-suffix-id");
    }

    @Test void jsonIdFieldsAndTagsAreProtected() throws Exception {
        Path mod = mod("jsonid");
        write(mod, "data/x.json", "{\"id\":\"甲\",\"hullId\":\"乙\",\"tags\":[\"丙\"],\"title\":\"丁\"}");
        var result = classify(mod);
        var roles = result.roles();
        assertThat(roles.get("json:data/x.json@0")).isEqualTo(Role.IDENTIFIER);
        assertThat(roles.get("json:data/x.json@1")).isEqualTo(Role.IDENTIFIER);
        assertThat(roles.get("json:data/x.json@2")).isEqualTo(Role.IDENTIFIER);
        assertThat(roles.get("json:data/x.json@3")).isEqualTo(Role.TEXT);
    }

    @Test void aKeyThatAValueRepeatsIsLinkedWithItAndABareKeyStaysUnknown() throws Exception {
        var result = classify(CONFORMANCE);
        assertThat(role(result, "json:data/settings.json@1")).isEqualTo(Role.LINKED);
        assertThat(role(result, "json:data/settings.json@2")).isEqualTo(Role.LINKED);
        assertThat(result.groups().get("json:data/settings.json@1"))
                .isEqualTo(result.groups().get("json:data/settings.json@2")).startsWith("link:");
        assertThat(role(result, "json:data/settings.json@0")).isEqualTo(Role.UNKNOWN);
        assertThat(role(result, "csv:data/bare.csv#x:name")).isEqualTo(Role.TEXT);
        assertThat(UnitRoleClassifier.members(result)).hasSize(1);
    }

    @Test void designTypeCellsAreLinkedToTheMapKeyThatNamesThem() throws Exception {
        Path mod = mod("design");
        write(mod, "data/hulls/ship_data.csv", "id,name,design type\nhull1,Name,新设计\nhull2,Other,新设计\n");
        write(mod, "data/config/colors.json", "{\"designTypeColors\":{\"新设计\":\"#ff0000\"}}");
        var result = classify(mod);
        String key = "json:data/config/colors.json@0";
        assertThat(role(result, "csv:data/hulls/ship_data.csv#hull1:design type")).isEqualTo(Role.LINKED);
        assertThat(role(result, "csv:data/hulls/ship_data.csv#hull2:design type")).isEqualTo(Role.LINKED);
        assertThat(result.roles().get(key)).isEqualTo(Role.LINKED);
        assertThat(result.groups()).containsKey(key);
        assertThat(UnitRoleClassifier.members(result)).hasSize(1);
        assertThat(UnitRoleClassifier.members(result).values().iterator().next()).hasSize(3);
    }

    @Test void aGroupTagColumnUsedByCodeIsLinkedAndUnusedOnesAreProtected() throws Exception {
        Path mod = mod("mirfak");
        write(mod, "data/tags.csv", "id,groupTag\na,中文字符串\nb,另一个\n");
        var unused = classify(mod);
        assertThat(role(unused, "csv:data/tags.csv#a:groupTag")).isEqualTo(Role.IDENTIFIER);
        assertThat(role(unused, "csv:data/tags.csv#b:groupTag")).isEqualTo(Role.IDENTIFIER);
        try (var jar = new java.util.zip.ZipFile(CLASS_JAR.toFile());
                var input = jar.getInputStream(jar.getEntry("Fixture.class"))) {
            Files.createDirectories(mod.resolve("classes"));
            Files.write(mod.resolve("classes/Fixture.class"), input.readAllBytes());
        }
        var used = classify(mod);
        assertThat(role(used, "csv:data/tags.csv#a:groupTag")).isEqualTo(Role.LINKED);
        assertThat(role(used, "class:classes/Fixture.class#5")).isEqualTo(Role.LINKED);
        assertThat(used.groups().get("csv:data/tags.csv#a:groupTag"))
                .isEqualTo(used.groups().get("class:classes/Fixture.class#5"));
        assertThat(role(used, "csv:data/tags.csv#b:groupTag")).isEqualTo(Role.IDENTIFIER);
    }

    @Test void aColumnWhoseEveryValueIsAnIdElsewhereHoldsIdentifiers() throws Exception {
        Path mod = mod("refs");
        write(mod, "data/factions.csv", "id,name\n甲,甲名\n乙,乙名\n");
        write(mod, "data/ships.csv", "id,faction,title\ns1,甲,标题\ns2,乙,标题二\n");
        var result = classify(mod);
        assertThat(role(result, "csv:data/ships.csv#s1:faction")).isEqualTo(Role.IDENTIFIER);
        assertThat(result.rules().get("csv:data/ships.csv#s1:faction")).isEqualTo("column-values-are-ids");
        assertThat(role(result, "csv:data/ships.csv#s1:title")).isEqualTo(Role.TEXT);
        assertThat(role(result, "csv:data/factions.csv#甲:name")).isEqualTo(Role.TEXT);
    }

    @Test void aKeyEqualToAnIdValueIsProtectedNotLinked() throws Exception {
        Path mod = mod("keyid");
        write(mod, "data/a.csv", "id,name\n甲,名字\n");
        write(mod, "data/map.json", "{\"甲\":\"x\"}");
        var result = classify(mod);
        assertThat(role(result, "json:data/map.json@0")).isEqualTo(Role.IDENTIFIER);
        assertThat(result.rules().get("json:data/map.json@0")).isEqualTo("shares-value-with-identifier");
        assertThat(result.groups()).isEmpty();
    }

    @Test void theRulesAreDataSoABareKeyPolicyAndANewColumnNeedNoCode() throws Exception {
        ObjectNode rules = (ObjectNode) new ObjectMapper().readTree(
                "{\"rules_version\":\"t\",\"csv_columns\":[{\"id\":\"mine\",\"pattern\":\"speaker\",\"linkable\":false}],"
                        + "\"json_fields\":[],\"bare_json_keys\":\"IDENTIFIER\"}");
        Path mod = mod("data");
        write(mod, "data/a.csv", "id,speaker,text\nr,说话人,内容\n");
        write(mod, "data/k.json", "{\"键\":1}");
        JsonNode document = new BridgeForgeTranslationService().export(mod);
        var result = UnitRoleClassifier.fromRules(rules).classify(document);
        assertThat(result.role("csv:data/a.csv#r:speaker")).isEqualTo(Role.IDENTIFIER);
        assertThat(result.rules().get("csv:data/a.csv#r:speaker")).isEqualTo("mine");
        assertThat(result.role("csv:data/a.csv#r:text")).isEqualTo(Role.TEXT);
        assertThat(result.role("json:data/k.json@0")).isEqualTo(Role.IDENTIFIER);
        assertThat(result.rulesVersion()).isEqualTo("t");
    }

    @Test void annotateAddsRoleFieldsOnlyToNonTextEntries() throws Exception {
        ObjectNode document = new BridgeForgeTranslationService().export(CONFORMANCE);
        UnitRoleClassifier.withDefaultRules().annotate(document);
        for (JsonNode entry : document.path("entries")) {
            if (entry.path("id").asText().equals("csv:data/bare.csv#x:name")) {
                assertThat(entry.has("role")).isFalse();
            }
            if (entry.path("id").asText().equals("json:data/settings.json@2")) {
                assertThat(entry.path("role").asText()).isEqualTo("LINKED");
                assertThat(entry.path("group").asText()).startsWith("link:");
            }
        }
        assertThat(document.path("roles_version").asText()).isNotBlank();
    }
}
