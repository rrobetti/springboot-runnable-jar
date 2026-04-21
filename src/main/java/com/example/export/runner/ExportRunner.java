package com.example.export.runner;

import com.example.export.service.ExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Entry point executed after the application context is started.
 *
 * <p>Expects one non-option argument (or the {@code --export.param} option) that
 * becomes part of the output filename.  Examples:
 * <pre>
 *   java -jar app.jar myValue
 *   java -jar app.jar --export.param=myValue
 * </pre>
 */
@Component
public class ExportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExportRunner.class);

    private static final String OPTION_NAME = "export.param";

    private final ExportService exportService;

    public ExportRunner(ExportService exportService) {
        this.exportService = exportService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String param = resolveParam(args);
        log.info("Starting export job with param='{}'", param);
        Path outputFile = exportService.export(param);
        log.info("Export finished. Output file: {}", outputFile.toAbsolutePath());
    }

    /**
     * Resolves the runtime parameter from either:
     * <ol>
     *   <li>The {@code --export.param=value} named option, or</li>
     *   <li>The first non-option argument.</li>
     * </ol>
     * Throws {@link IllegalArgumentException} if neither is present.
     */
    public String resolveParam(ApplicationArguments args) {
        // Check named option first: --export.param=value
        List<String> optionValues = args.getOptionValues(OPTION_NAME);
        if (optionValues != null && !optionValues.isEmpty()) {
            return optionValues.get(0);
        }

        // Fall back to first positional (non-option) argument
        List<String> nonOptionArgs = args.getNonOptionArgs();
        if (!nonOptionArgs.isEmpty()) {
            return nonOptionArgs.get(0);
        }

        throw new IllegalArgumentException(
                "A runtime parameter is required. Provide it as: --export.param=<value> or as a positional argument.");
    }
}
