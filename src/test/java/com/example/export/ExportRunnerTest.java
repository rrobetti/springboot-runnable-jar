package com.example.export;

import com.example.export.runner.ExportRunner;
import com.example.export.service.ExportService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ExportRunner} – verifies parameter resolution logic
 * without starting a full application context.
 */
class ExportRunnerTest {

    private final ExportService exportService = Mockito.mock(ExportService.class);
    private final ExportRunner runner = new ExportRunner(exportService);

    // ─── resolveParam ─────────────────────────────────────────────────────────

    @Test
    void resolveParam_fromNamedOption() {
        DefaultApplicationArguments args = new DefaultApplicationArguments("--export.param=january");
        assertThat(runner.resolveParam(args)).isEqualTo("january");
    }

    @Test
    void resolveParam_fromPositionalArgument() {
        DefaultApplicationArguments args = new DefaultApplicationArguments("february");
        assertThat(runner.resolveParam(args)).isEqualTo("february");
    }

    @Test
    void resolveParam_namedOptionTakesPrecedenceOverPositional() {
        DefaultApplicationArguments args =
                new DefaultApplicationArguments("--export.param=named", "positional");
        assertThat(runner.resolveParam(args)).isEqualTo("named");
    }

    @Test
    void resolveParam_throwsWhenNoArgumentProvided() {
        DefaultApplicationArguments args = new DefaultApplicationArguments();
        assertThatThrownBy(() -> runner.resolveParam(args))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime parameter is required");
    }

    // ─── resolveOutputDir ─────────────────────────────────────────────────────

    @Test
    void resolveOutputDir_fromNamedOption() {
        DefaultApplicationArguments args =
                new DefaultApplicationArguments("--export.outputDir=/tmp/out");
        assertThat(runner.resolveOutputDir(args)).isEqualTo("/tmp/out");
    }

    @Test
    void resolveOutputDir_returnsNullWhenAbsent() {
        DefaultApplicationArguments args =
                new DefaultApplicationArguments("--export.param=20240115");
        assertThat(runner.resolveOutputDir(args)).isNull();
    }

    // ─── resolveErrorDir ──────────────────────────────────────────────────────

    @Test
    void resolveErrorDir_fromNamedOption() {
        DefaultApplicationArguments args =
                new DefaultApplicationArguments("--export.errorDir=/tmp/err");
        assertThat(runner.resolveErrorDir(args)).isEqualTo("/tmp/err");
    }

    @Test
    void resolveErrorDir_returnsNullWhenAbsent() {
        DefaultApplicationArguments args =
                new DefaultApplicationArguments("--export.param=20240115");
        assertThat(runner.resolveErrorDir(args)).isNull();
    }
}
