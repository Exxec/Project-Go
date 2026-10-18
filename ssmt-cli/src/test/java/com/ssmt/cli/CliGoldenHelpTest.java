package com.ssmt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;

/**
 * Pins the command-line contract: a hash of every command's --help text. A deliberate change is accepted with
 * {@code gradlew :ssmt-cli:writeGoldenHelp} and the diff of {@code golden-help.tsv} is reviewed.
 */
class CliGoldenHelpTest {
    private static final Path GOLDEN = Path.of("src/test/resources/golden-help.tsv");
    private static final int WIDTH = 100;

    @Test void everyCommandHelpMatchesTheGoldenHash() throws Exception {
        Map<String, String> actual = hashes();
        if (Boolean.getBoolean("ssmt.goldenHelp.write")) {
            StringBuilder text = new StringBuilder("# command<TAB>sha256 of --help text; rewrite with gradlew :ssmt-cli:writeGoldenHelp\n");
            actual.forEach((name, hash) -> text.append(name).append('\t').append(hash).append('\n'));
            Files.createDirectories(java.util.Objects.requireNonNull(GOLDEN.getParent()));
            Files.writeString(GOLDEN, text.toString(), StandardCharsets.UTF_8);
        }
        Map<String, String> expected = new TreeMap<>();
        for (String line : Files.readAllLines(GOLDEN, StandardCharsets.UTF_8)) {
            if (!line.isBlank() && !line.startsWith("#")) {
                String[] columns = line.split("\t");
                expected.put(columns[0], columns[1]);
            }
        }
        assertThat(actual).as("command set and --help hashes (see class comment to accept a deliberate change)")
                .isEqualTo(expected);
    }

    private static Map<String, String> hashes() throws IOException, NoSuchAlgorithmException {
        Map<String, String> result = new TreeMap<>();
        collect(new CommandLine(new Main()), "ssmt", result);
        return result;
    }

    private static void collect(CommandLine command, String name, Map<String, String> into)
            throws NoSuchAlgorithmException {
        command.setUsageHelpWidth(WIDTH);
        String usage = command.getUsageMessage(Ansi.OFF).replace("\r\n", "\n");
        into.put(name, java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(usage.getBytes(StandardCharsets.UTF_8))));
        for (var child : new TreeMap<>(command.getSubcommands()).entrySet()) {
            collect(child.getValue(), name + " " + child.getKey(), into);
        }
    }
}
