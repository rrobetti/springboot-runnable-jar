package com.example.export.runner;

import com.example.export.service.ExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Entry point executed after the application context is started.
 *
 * <p>Required argument:
 * <pre>
 *   --export.param=YYYYMMDD   (or as the first positional argument)
 * </pre>
 *
 * <p>Optional arguments:
 * <pre>
 *   --export.outputDir=/path/to/output   overrides app.output.directory
 *   --export.errorDir=/path/to/error     overrides app.error.directory
 * </pre>
 *
 * <p>Examples:
 * <pre>
 *   java -jar app.jar 20240115
 *   java -jar app.jar --export.param=20240115
 *   java -jar app.jar --export.param=20240115 --export.outputDir=/data/out --export.errorDir=/data/err
 * </pre>
 */
@Profile("!test")
@Component
public class ExportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExportRunner.class);

    private static final String OPTION_PARAM      = "export.param";
    private static final String OPTION_OUTPUT_DIR = "export.outputDir";
    private static final String OPTION_ERROR_DIR  = "export.errorDir";

    private final ExportService exportService;

    public ExportRunner(ExportService exportService) {
        this.exportService = exportService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String param     = resolveParam(args);
        String outputDir = resolveOutputDir(args);
        String errorDir  = resolveErrorDir(args);
        log.info("Starting export job: param='{}', outputDir='{}', errorDir='{}'",
                param, outputDir, errorDir);
        Path outputFile = exportService.export(param, outputDir, errorDir);
        log.info("Export finished. Output file: {}", outputFile.toAbsolutePath());
    }

    /**
     * Resolves the mandatory date parameter ({@code YYYYMMDD}) from either:
     * <ol>
     *   <li>The {@code --export.param=value} named option, or</li>
     *   <li>The first non-option (positional) argument.</li>
     * </ol>
     *
     * @throws IllegalArgumentException if neither form is present
     */
    public String resolveParam(ApplicationArguments args) {
        List<String> optionValues = args.getOptionValues(OPTION_PARAM);
        if (optionValues != null && !optionValues.isEmpty()) {
            return optionValues.get(0);
        }

        List<String> nonOptionArgs = args.getNonOptionArgs();
        if (!nonOptionArgs.isEmpty()) {
            return nonOptionArgs.get(0);
        }

        throw new IllegalArgumentException(
                "A runtime parameter is required. Provide it as: --export.param=<YYYYMMDD> or as a positional argument.");
    }

    /**
     * Resolves the optional output directory from {@code --export.outputDir=value}.
     *
     * @return the supplied path, or {@code null} if the option was not provided
     *         (the service falls back to {@code app.output.directory})
     */
    public String resolveOutputDir(ApplicationArguments args) {
        List<String> values = args.getOptionValues(OPTION_OUTPUT_DIR);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    /**
     * Resolves the optional error directory from {@code --export.errorDir=value}.
     *
     * @return the supplied path, or {@code null} if the option was not provided
     *         (the service falls back to {@code app.error.directory})
     */
    public String resolveErrorDir(ApplicationArguments args) {
        List<String> values = args.getOptionValues(OPTION_ERROR_DIR);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }
}
