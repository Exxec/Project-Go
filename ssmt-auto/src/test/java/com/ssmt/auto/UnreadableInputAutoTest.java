package com.ssmt.auto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UnreadableInputAutoTest {
    @TempDir Path temporary;

    @Test void unreadableInputsStopAutoUntilAcceptedThenTheRestIsProcessed() throws Exception {
        Path source = temporary.resolve("mod");
        Files.createDirectories(source.resolve("data/config"));
        Files.writeString(source.resolve("mod_info.json"), "{\"id\":\"au\",\"name\":\"Au\",\"version\":\"1\"}");
        Files.writeString(source.resolve("data/good.csv"), "id,name\nx,中文\n");
        Files.write(source.resolve("data/config/bad.json"), new byte[] {'{', '"', (byte) 0xb5, (byte) 0xe7, (byte) 0xff, '"', '}'});
        var workflow = new AutoWorkflow(temporary.resolve("internal/catalog.db"), temporary.resolve("internal/workspaces"));
        var stopped = workflow.run(source);
        assertThat(stopped.status()).isEqualTo(AutoRunResult.Status.UNREADABLE_INPUTS_NEED_ACCEPTANCE);
        assertThat(stopped.detail()).contains("data/config/bad.json").contains("--accept-unreadable");
        var accepted = workflow.acceptUnreadable(true).run(source);
        assertThat(accepted.status()).isEqualTo(AutoRunResult.Status.MASTER_LIBRARY_NEEDED);
    }
}
