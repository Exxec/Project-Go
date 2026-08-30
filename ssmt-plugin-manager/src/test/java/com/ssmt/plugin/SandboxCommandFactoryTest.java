package com.ssmt.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SandboxCommandFactoryTest {

    @Test
    void wrapsLinuxWorkerWithNetworklessBubblewrap() throws Exception {
        Path executable = Path.of("tools", "bwrap").toAbsolutePath();
        SandboxCommandFactory factory = new SandboxCommandFactory(
                "Linux",
                name -> name.equals("bwrap")
                        ? java.util.Optional.of(executable)
                        : java.util.Optional.empty());
        Path working = Path.of("work").toAbsolutePath();

        List<String> command = factory.wrap(
                List.of("/jdk/bin/java", "worker"),
                working,
                PluginSandboxProfile.REQUIRED);

        assertThat(command)
                .startsWith(executable.toString(), "--die-with-parent", "--unshare-all")
                .containsSubsequence("--bind", working.toString(), working.toString())
                .endsWith("/jdk/bin/java", "worker");
    }

    @Test
    void requiredProfileFailsClosedWhenUnavailable() {
        SandboxCommandFactory factory =
                new SandboxCommandFactory("Windows 11", name -> java.util.Optional.empty());

        assertThatThrownBy(() -> factory.wrap(
                        List.of("java.exe", "worker"),
                        Path.of("work").toAbsolutePath(),
                        PluginSandboxProfile.REQUIRED))
                .isInstanceOf(PluginActivationException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    void autoProfileRetainsWorkerWhenUnavailable() throws Exception {
        SandboxCommandFactory factory =
                new SandboxCommandFactory("Windows 11", name -> java.util.Optional.empty());
        List<String> worker = List.of("java.exe", "worker");

        assertThat(factory.wrap(
                        worker,
                        Path.of("work").toAbsolutePath(),
                        PluginSandboxProfile.AUTO))
                .isEqualTo(worker);
    }
}
