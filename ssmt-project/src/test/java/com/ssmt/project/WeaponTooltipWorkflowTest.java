package com.ssmt.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WeaponTooltipWorkflowTest {
    @TempDir Path root;

    @Test void exportsImportsAndBuildsTooltipGapsWithoutChangingTechnicalBytesOrEncoding() throws Exception {
        Path source = Files.createDirectories(root.resolve("source"));
        Files.writeString(source.resolve("mod_info.json"), "{\"id\":\"tooltip_test\",\"name\":\"Tooltip test\"}");
        Path weapons = Files.createDirectories(source.resolve("data/weapons"));
        Path file = weapons.resolve("weapon_data.csv");
        Charset encoding = Charset.forName("GB18030");
        String text = "id,name,customPrimary,customPrimaryHL,customAncillary,speedStr,groupTag,tags,damage/shot\r\n"
                + "test_weapon,名称,造成 %s 装甲伤害,%s | 装甲,可越过友舰,快速,技术分组,技术标签,123.00,extra\r\n";
        byte[] original = text.getBytes(encoding);
        Files.write(file, original);
        var workflow = new TranslationWorkflow(root.resolve("workspaces"));
        var session = workflow.loadMod(source);
        assertThat(session.project().entries()).hasSize(5);
        Path response = root.resolve("response.json");
        workflow.exportTranslation(session, response);
        var json = new ObjectMapper();
        var document = json.readTree(response.toFile());
        Map<String, String> translated = Map.of("名称", "Name", "造成 %s 装甲伤害", "Deals %s armor damage",
                "%s | 装甲", "%s | armor", "可越过友舰", "Can fire over friendly ships", "快速", "Fast");
        for (var entry : document.path("entries")) {
            ((ObjectNode) entry).put("translation", translated.get(entry.path("source").asText()));
        }
        json.writeValue(response.toFile(), document);
        var completed = workflow.importTranslation(session, response);
        Path output = root.resolve("output");
        workflow.buildPatch(completed, output);
        String expected = text;
        for (var pair : translated.entrySet()) {
            expected = expected.replace(pair.getKey(), pair.getValue());
        }
        assertThat(Files.readAllBytes(output.resolve("data/weapons/weapon_data.csv")))
                .isEqualTo(expected.getBytes(encoding));
        assertThat(Files.readAllBytes(file)).isEqualTo(original);
    }
}
