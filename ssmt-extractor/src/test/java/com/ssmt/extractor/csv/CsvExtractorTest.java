package com.ssmt.extractor.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssmt.core.exception.SsmtParseException;
import com.ssmt.core.model.ExtractedString;
import com.ssmt.core.plugin.ExtractionRequest;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvExtractorTest {

    private final CsvExtractor extractor =
            new CsvExtractor(new CsvExtractionSpec("id", List.of("name", "description")));

    @TempDir
    Path temporaryDirectory;

    @Test
    void extractsConfiguredColumnsWithStableSortedKeys() throws Exception {
        List<ExtractedString> strings = extract("valid.csv");

        assertThat(strings)
                .extracting(ExtractedString::key)
                .containsExactly(
                        "csv:id=laser%3Aheavy:description",
                        "csv:id=laser%3Aheavy:name",
                        "csv:id=pulse%25laser:description",
                        "csv:id=pulse%25laser:name");
        assertThat(strings)
                .filteredOn(value -> value.key().equals("csv:id=pulse%25laser:description"))
                .singleElement()
                .extracting(ExtractedString::originalText)
                .isEqualTo("Deals $damage.\\nSecond line  ");
        assertThat(strings)
                .extracting(ExtractedString::sourceFile)
                .containsOnly(Path.of("data", "valid.csv"));
    }

    @Test
    void preservesEmbeddedNewlinesAndNonEnglishText() throws Exception {
        List<ExtractedString> strings = extract("multiline.csv");

        assertThat(strings)
                .filteredOn(value -> value.key().endsWith(":description"))
                .singleElement()
                .extracting(ExtractedString::originalText)
                .satisfies(value -> assertThat(value.replace("\r\n", "\n"))
                        .isEqualTo("\u7b2c\u4e00\u884c\n\u7b2c\u4e8c\u884c"));
    }

    @Test
    void skipsOnlyTrulyEmptyCells() throws Exception {
        List<ExtractedString> strings = extract("empty-and-spaces.csv");

        assertThat(strings).extracting(ExtractedString::originalText)
                .containsExactly("   ");
    }

    @Test
    void extractsWeaponAndShipOptionalColumnsWhenPresentButToleratesTheirAbsence() throws Exception {
        CsvExtractor weaponExtractor = new CsvExtractor(
                StandardCsvSchemas.find(Path.of("data/weapons/weapon_data.csv")).orElseThrow());
        Path withOptionalColumns = weaponFixture("""
                id,name,tech/manufacturer,primaryRoleStr,customAncillaryHL
                placeholder_weapon,Placeholder Weapon,Placeholder Manufacturer,Placeholder Role,Placeholder Tooltip
                """);
        List<ExtractedString> withOptional = weaponExtractor.extract(
                new ExtractionRequest("test_mod", temporaryDirectory, withOptionalColumns));

        assertThat(withOptional)
                .extracting(ExtractedString::key)
                .containsExactlyInAnyOrder(
                        "csv:id=placeholder_weapon:customAncillaryHL",
                        "csv:id=placeholder_weapon:name",
                        "csv:id=placeholder_weapon:primaryRoleStr",
                        "csv:id=placeholder_weapon:tech/manufacturer");

        Path withoutOptionalColumns = weaponFixture("""
                id,name
                placeholder_weapon,Placeholder Weapon
                """);
        List<ExtractedString> withoutOptional = weaponExtractor.extract(
                new ExtractionRequest("test_mod", temporaryDirectory, withoutOptionalColumns));

        assertThat(withoutOptional)
                .extracting(ExtractedString::key)
                .containsExactly("csv:id=placeholder_weapon:name");

        CsvExtractor shipExtractor = new CsvExtractor(
                StandardCsvSchemas.find(Path.of("data/hulls/ship_data.csv")).orElseThrow());
        Path shipWithManufacturer = shipFixture("""
                id,name,designation,tech/manufacturer
                placeholder_ship,Placeholder Ship,Destroyer,Placeholder Manufacturer
                """);
        List<ExtractedString> shipStrings = shipExtractor.extract(
                new ExtractionRequest("test_mod", temporaryDirectory, shipWithManufacturer));

        assertThat(shipStrings)
                .extracting(ExtractedString::key)
                .containsExactlyInAnyOrder(
                        "csv:id=placeholder_ship:designation",
                        "csv:id=placeholder_ship:name",
                        "csv:id=placeholder_ship:tech/manufacturer");
    }

    @Test
    void extractsShipSystemDisplayNameAndPreservesMechanicsColumns() throws Exception {
        CsvExtractor shipSystemExtractor = new CsvExtractor(
                StandardCsvSchemas.find(Path.of("data/shipsystems/ship_systems.csv")).orElseThrow());
        Path fixture = shipSystemFixture("""
                name,id,flux/second,max uses,active,down,cooldown,toggle,tags,icon
                Placeholder System,placeholder_system,,2,1.5,0.5,1,,movement,graphics/icons/hullsys/placeholder.png
                """);

        List<ExtractedString> strings = shipSystemExtractor.extract(
                new ExtractionRequest("test_mod", temporaryDirectory, fixture));

        assertThat(strings)
                .extracting(ExtractedString::key)
                .containsExactly("csv:id=placeholder_system:name");
        assertThat(strings)
                .extracting(ExtractedString::originalText)
                .containsExactly("Placeholder System");
    }

    @Test
    void extractsHullModNameAndOptionalTooltipColumnsWhenPresent() throws Exception {
        CsvExtractor hullModExtractor = new CsvExtractor(
                StandardCsvSchemas.find(Path.of("data/hullmods/hull_mods.csv")).orElseThrow());
        Path fixture = hullModFixture("""
                name,id,tier,rarity,tech/manufacturer,tags,uiTags,base value,unlocked,hidden,hiddenEverywhere,cost_frigate,cost_dest,cost_cruiser,cost_capital,script,desc,short,sModDesc,sprite
                Placeholder Coating,placeholder_hullmod,4,,Placeholder Manufacturer,"special, no_drop",,0,,TRUE,,0,0,0,0,data.hullmods.Placeholder,Placeholder tooltip text.,Placeholder short text,,graphics/hullmods/placeholder.png
                """);

        List<ExtractedString> strings = hullModExtractor.extract(
                new ExtractionRequest("test_mod", temporaryDirectory, fixture));

        assertThat(strings)
                .extracting(ExtractedString::key)
                .containsExactlyInAnyOrder(
                        "csv:id=placeholder_hullmod:name",
                        "csv:id=placeholder_hullmod:tech/manufacturer",
                        "csv:id=placeholder_hullmod:desc",
                        "csv:id=placeholder_hullmod:short");
    }

    @Test
    void extractsSkillNameDescriptionAndOptionalAuthor() throws Exception {
        CsvExtractor skillExtractor = new CsvExtractor(
                StandardCsvSchemas.find(Path.of("data/characters/skills/skill_data.csv")).orElseThrow());
        Path fixture = skillFixture("""
                id,name,order,tier,description,author,combat officer,admiral,admin,tags,icon
                placeholder_skill,Placeholder Skill,1,0,Placeholder description text,Placeholder Academy,TRUE,,,,graphics/icons/placeholder.png
                """);

        List<ExtractedString> strings = skillExtractor.extract(
                new ExtractionRequest("test_mod", temporaryDirectory, fixture));

        assertThat(strings)
                .extracting(ExtractedString::key)
                .containsExactlyInAnyOrder(
                        "csv:id=placeholder_skill:name",
                        "csv:id=placeholder_skill:description",
                        "csv:id=placeholder_skill:author");
    }

    @Test
    void extractsWingRoleDescWhenPresentButToleratesItsAbsence() throws Exception {
        CsvExtractor wingExtractor = new CsvExtractor(
                StandardCsvSchemas.find(Path.of("data/hulls/wing_data.csv")).orElseThrow());
        Path withRoleDesc = wingFixture("""
                id,variant,tags,tier,rarity,fleet pts,op cost,formation,range,attackRunRange,attackPositionOffset,num,role,role desc,refit,base value,number
                placeholder_wing,placeholder_variant,"fighter",1,,5,20,V,6000,600,,3,FIGHTER,Placeholder Role Text,16,10000,3.1
                """);

        List<ExtractedString> withRoleStrings = wingExtractor.extract(
                new ExtractionRequest("test_mod", temporaryDirectory, withRoleDesc));

        assertThat(withRoleStrings)
                .extracting(ExtractedString::key)
                .containsExactly("csv:id=placeholder_wing:role desc");

        Path withoutRoleDesc = wingFixture("""
                id,variant,tags,tier,rarity,fleet pts,op cost,formation,range,attackRunRange,attackPositionOffset,num,role,refit,base value,number
                placeholder_wing,placeholder_variant,"fighter",1,,5,20,V,6000,600,,3,FIGHTER,16,10000,3.1
                """);

        List<ExtractedString> withoutRoleStrings = wingExtractor.extract(
                new ExtractionRequest("test_mod", temporaryDirectory, withoutRoleDesc));

        assertThat(withoutRoleStrings).isEmpty();
    }

    @Test
    void skipsWingDataColumnCommentRowWithBlankIdentityButNonBlankRoleDesc() throws Exception {
        CsvExtractor wingExtractor = new CsvExtractor(
                StandardCsvSchemas.find(Path.of("data/hulls/wing_data.csv")).orElseThrow());
        // Reproduces a real mod's convention: a second header-like row restating each
        // column's meaning as a "#"-prefixed comment, with every identity cell blank.
        Path fixture = wingFixture("""
                id,variant,tags,tier,rarity,fleet pts,op cost,formation,range,attackRunRange,attackPositionOffset,num,role,role desc,refit,base value,number
                ,,,#tier,#rarity,#fleet points,#op cost,#formation,#range,#attack run range,#attack position offset,#num,#role,#role desc,#refit,#base value,
                placeholder_wing,placeholder_variant,"fighter",1,,5,20,V,6000,600,,3,FIGHTER,Placeholder Role Text,16,10000,3.1
                """);

        List<ExtractedString> strings = wingExtractor.extract(
                new ExtractionRequest("test_mod", temporaryDirectory, fixture));

        assertThat(strings)
                .extracting(ExtractedString::key)
                .containsExactly("csv:id=placeholder_wing:role desc");
    }

    @Test
    void extractsCampaignEconomyFileNamesAndOptionalDescriptions() throws Exception {
        record Case(String fileName, String header, String row, List<String> expectedKeys) {
        }
        List<Case> cases = List.of(
                new Case("commodities.csv",
                        "name,id,demand class,base price,export value,price variability,utility,"
                                + "origin,tags,stack size,cargo space,icon,sound id,sound id drop,"
                                + "order,economyTier,econUnit,iconWidthMult,plugin,desc",
                        "Placeholder Good,placeholder_good,,,,,,,,,,,,,,,,,,Placeholder desc",
                        List.of("csv:id=placeholder_good:desc", "csv:id=placeholder_good:name")),
                new Case("industries.csv",
                        "id,name,cost mult,build time,income,upkeep,downgrade,upgrade,tags,data,"
                                + "image,plugin,desc,order",
                        "placeholder_industry,Placeholder Industry,,,,,,,,,,,Placeholder desc,",
                        List.of("csv:id=placeholder_industry:desc",
                                "csv:id=placeholder_industry:name")),
                new Case("market_conditions.csv",
                        "name,id,script,desc,icon,order",
                        "Placeholder Condition,placeholder_condition,,Placeholder desc,,",
                        List.of("csv:id=placeholder_condition:desc",
                                "csv:id=placeholder_condition:name")),
                new Case("special_items.csv",
                        "name,id,tags,tech/manufacturer,rarity,base price,stack size,cargo space,"
                                + "icon,sound id,sound id drop,plugin,plugin params,desc,order",
                        "Placeholder Item,placeholder_item,,Placeholder Manufacturer,,,,,,,,,,"
                                + "Placeholder desc,",
                        List.of("csv:id=placeholder_item:desc",
                                "csv:id=placeholder_item:name",
                                "csv:id=placeholder_item:tech/manufacturer")),
                new Case("submarkets.csv",
                        "id,name,faction,desc,script,icon,order",
                        "placeholder_market,Placeholder Market,,Placeholder desc,,,",
                        List.of("csv:id=placeholder_market:desc",
                                "csv:id=placeholder_market:name")));

        for (Case testCase : cases) {
            CsvExtractor extractor = new CsvExtractor(StandardCsvSchemas.find(
                    Path.of("data/campaign/" + testCase.fileName())).orElseThrow());
            Path fixture = campaignFixture(testCase.fileName(),
                    testCase.header() + "\n" + testCase.row() + "\n");

            List<ExtractedString> strings = extractor.extract(
                    new ExtractionRequest("test_mod", temporaryDirectory, fixture));

            assertThat(strings)
                    .as("entries for %s", testCase.fileName())
                    .extracting(ExtractedString::key)
                    .containsExactlyInAnyOrderElementsOf(testCase.expectedKeys());
        }
    }

    @Test
    void rejectsMissingConfiguredHeader() {
        assertThatThrownBy(() -> extract("missing-header.csv"))
                .isInstanceOf(SsmtParseException.class)
                .hasMessageContaining("description");
    }

    @Test
    void rejectsBlankOrDuplicateIdentity() {
        assertThatThrownBy(() -> extract("duplicate-id.csv"))
                .isInstanceOf(SsmtParseException.class)
                .hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> extract("blank-id.csv"))
                .isInstanceOf(SsmtParseException.class)
                .hasMessageContaining("Blank");
    }

    @Test
    void skipsBlankIdentitySentinelRowsWhenLocalizableCellsAreEmpty() throws Exception {
        List<ExtractedString> strings = extractTemporary("""
                id,name,description
                laser,Laser,Weapon text
                ,,
                """);

        assertThat(strings).hasSize(2);
    }

    @Test
    void stillRejectsBlankIdentityRowsContainingLocalizableText() throws Exception {
        assertThatThrownBy(() -> extractTemporary("""
                id,name,description
                ,Orphaned text,
                """))
                .isInstanceOf(SsmtParseException.class)
                .hasMessageContaining("Blank");
    }

    @Test
    void toleratesAnUnnamedNonConfiguredHeader() throws Exception {
        List<ExtractedString> strings = extractTemporary("""
                id,name,description,
                laser,Laser,Weapon text,internal
                """);

        assertThat(strings).hasSize(2);
    }

    @Test
    void extractsOptionalTextColumnsOnlyWhenPresent() throws Exception {
        CsvExtractor compatibleExtractor = new CsvExtractor(
                new CsvExtractionSpec("id", List.of("name"), List.of("description")));
        Path dataDirectory = Files.createDirectories(temporaryDirectory.resolve("data"));
        Path source = dataDirectory.resolve("optional.csv");
        Files.writeString(source, "id,name\nlaser,Laser\n");

        List<ExtractedString> strings = compatibleExtractor.extract(
                new ExtractionRequest("test_mod", temporaryDirectory, source));

        assertThat(strings).extracting(ExtractedString::originalText).containsExactly("Laser");
    }

    @Test
    void fallsBackToGb18030ForLegacyChineseCsv() throws Exception {
        Path dataDirectory = Files.createDirectories(temporaryDirectory.resolve("data"));
        Path source = dataDirectory.resolve("legacy.csv");
        Files.write(source, "id,name,description\nlaser,激光,\n"
                .getBytes(Charset.forName("GB18030")));

        List<ExtractedString> strings = extractor.extract(
                new ExtractionRequest("test_mod", temporaryDirectory, source));

        assertThat(strings).extracting(ExtractedString::originalText)
                .containsExactly("激光");
    }

    @Test
    void skipsHashPrefixedCommentRowsBeforeIdentityValidation() throws Exception {
        Path dataDirectory = Files.createDirectories(temporaryDirectory.resolve("data"));
        Path source = dataDirectory.resolve("comments.csv");
        Files.writeString(source, """
                name,id,description
                # Section,duplicate,
                # Another section,duplicate,
                Laser,laser,Weapon text
                """);
        List<ExtractedString> strings = extractor.extract(
                new ExtractionRequest("test_mod", temporaryDirectory, source));

        assertThat(strings).hasSize(2);
    }

    @Test
    void reportsMalformedCsv() {
        assertThatThrownBy(() -> extract("malformed.csv"))
                .isInstanceOf(SsmtParseException.class)
                .hasMessageContaining("Malformed CSV");
    }

    @Test
    void supportsCsvCaseInsensitively() {
        assertThat(extractor.supports(Path.of("WEAPONS.CSV"))).isTrue();
        assertThat(extractor.supports(Path.of("weapon.json"))).isFalse();
    }

    @Test
    void doesNotModifySourceFile() throws Exception {
        Path source = fixture("valid.csv");
        FileTime modifiedBefore = Files.getLastModifiedTime(source);
        byte[] hashBefore = MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(source));

        extract("valid.csv");

        assertThat(Files.getLastModifiedTime(source)).isEqualTo(modifiedBefore);
        assertThat(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)))
                .isEqualTo(hashBefore);
    }

    private List<ExtractedString> extract(String fixtureName) throws Exception {
        Path source = fixture(fixtureName);
        Path dataDirectory = Objects.requireNonNull(source.getParent());
        Path modRoot = Objects.requireNonNull(dataDirectory.getParent());
        return extractor.extract(new ExtractionRequest("test_mod", modRoot, source));
    }

    private Path weaponFixture(String csv) throws Exception {
        Path dataDirectory = Files.createDirectories(temporaryDirectory.resolve("data/weapons"));
        Path source = dataDirectory.resolve("weapon_data.csv");
        Files.writeString(source, csv);
        return source;
    }

    private Path shipFixture(String csv) throws Exception {
        Path dataDirectory = Files.createDirectories(temporaryDirectory.resolve("data/hulls"));
        Path source = dataDirectory.resolve("ship_data.csv");
        Files.writeString(source, csv);
        return source;
    }

    private Path shipSystemFixture(String csv) throws Exception {
        Path dataDirectory = Files.createDirectories(temporaryDirectory.resolve("data/shipsystems"));
        Path source = dataDirectory.resolve("ship_systems.csv");
        Files.writeString(source, csv);
        return source;
    }

    private Path hullModFixture(String csv) throws Exception {
        Path dataDirectory = Files.createDirectories(temporaryDirectory.resolve("data/hullmods"));
        Path source = dataDirectory.resolve("hull_mods.csv");
        Files.writeString(source, csv);
        return source;
    }

    private Path skillFixture(String csv) throws Exception {
        Path dataDirectory = Files.createDirectories(temporaryDirectory.resolve("data/characters/skills"));
        Path source = dataDirectory.resolve("skill_data.csv");
        Files.writeString(source, csv);
        return source;
    }

    private Path wingFixture(String csv) throws Exception {
        Path dataDirectory = Files.createDirectories(temporaryDirectory.resolve("data/hulls"));
        Path source = dataDirectory.resolve("wing_data.csv");
        Files.writeString(source, csv);
        return source;
    }

    private Path campaignFixture(String fileName, String csv) throws Exception {
        Path dataDirectory = Files.createDirectories(temporaryDirectory.resolve("data/campaign"));
        Path source = dataDirectory.resolve(fileName);
        Files.writeString(source, csv);
        return source;
    }

    private List<ExtractedString> extractTemporary(String csv) throws Exception {
        Path dataDirectory = Files.createDirectories(temporaryDirectory.resolve("data"));
        Path source = dataDirectory.resolve("temporary.csv");
        Files.writeString(source, csv);
        return extractor.extract(new ExtractionRequest(
                "test_mod", temporaryDirectory, source));
    }

    private static Path fixture(String name) throws URISyntaxException {
        return Path.of(Objects.requireNonNull(
                CsvExtractorTest.class.getResource("/fixtures/mod/data/" + name)).toURI());
    }
}
