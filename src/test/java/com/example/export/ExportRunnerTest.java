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
}
