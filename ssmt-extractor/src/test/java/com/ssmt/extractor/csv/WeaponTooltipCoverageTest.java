package com.ssmt.extractor.csv;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssmt.core.model.ExtractedString;
import com.ssmt.core.plugin.ExtractionRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WeaponTooltipCoverageTest {
    @TempDir Path root;

    @Test void selectsAllPublicTooltipOverridesButNeverGroupingTagsStatsOrIds() throws Exception {
        Path folder = Files.createDirectories(root.resolve("data/weapons"));
        Path file = folder.resolve("weapon_data.csv");
        String text = "id,name,tech/manufacturer,primaryRoleStr,speedStr,trackingStr,turnRateStr,accuracyStr,"
                + "customPrimary,customPrimaryHL,customAncillary,customAncillaryHL,groupTag,tags,type,damage/shot\n"
                + "test_weapon,测试,厂商,角色,快,追踪,转速,精度,造成 %s 装甲伤害,%s | 装甲,可越过友舰,友舰,"
                + "技术分组,技术标签,ENERGY,123.00\n";
        Files.writeString(file, text);
        var extractor = new StandardCsvFileExtractor();
        var request = new ExtractionRequest("test", root, file);
        var entries = extractor.extract(request);
        assertThat(entries).hasSize(11);
        assertThat(entries).extracting(ExtractedString::key).contains(
                "csv:id=test_weapon:speedStr", "csv:id=test_weapon:trackingStr",
                "csv:id=test_weapon:turnRateStr", "csv:id=test_weapon:accuracyStr",
                "csv:id=test_weapon:customPrimary", "csv:id=test_weapon:customPrimaryHL",
                "csv:id=test_weapon:customAncillary", "csv:id=test_weapon:customAncillaryHL");
        assertThat(entries).extracting(ExtractedString::originalText)
                .contains("造成 %s 装甲伤害", "%s | 装甲")
                .doesNotContain("test_weapon", "技术分组", "技术标签", "ENERGY", "123.00");
        assertThat(entries).isEqualTo(extractor.extract(request));
        assertThat(Files.readString(file)).isEqualTo(text);
    }

    @Test void legacyMinimalHeadersAndUnidentifiedSentinelsRemainSupported() throws Exception {
        Path folder = Files.createDirectories(root.resolve("data/weapons"));
        Path file = folder.resolve("weapon_data.csv");
        Files.writeString(file, "id,name\n, # 小型武器\ntest_weapon,Weapon\n");
        var entries = new StandardCsvFileExtractor().extract(new ExtractionRequest("test", root, file));
        assertThat(entries).extracting(ExtractedString::key).containsExactly("csv:id=test_weapon:name");
        assertThat(StandardCsvSchemas.find(Path.of("data/campaign/zgrstuff.csv"))).isEmpty();
    }
}
