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
 * <p>Exits with code {@code 0} on success and code {@code 1} on any failure
 * (standard runnable-job behaviour via Spring Boot's {@link ApplicationRunner}).
 *
 * <p>Required argument:
 * <pre>
 *   --export.param=YYYYMMDD   (or as the first positional argument)
 * </pre>
 *
 * <p>Optional arguments:
 * <pre>
 *   --export.code=CODE               prefix used for the log file name (e.g. myapp → myapp-export.log)
 *   --export.outputFile=/path/to/output/file.dat   full path of the file to create
 * </pre>
 *
 * <p>Examples:
 * <pre>
 *   java -jar app.jar 20240115
 *   java -jar app.jar --export.param=20240115
 *   java -jar app.jar --export.param=20240115 --export.code=myapp
 *   java -jar app.jar --export.param=20240115 --export.outputFile=/data/out/export_20240115.dat
 * </pre>
 */
@Profile("!test")
@Component
public class ExportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExportRunner.class);

    private static final String OPTION_PARAM       = "export.param";
    private static final String OPTION_CODE        = "export.code";
    private static final String OPTION_OUTPUT_FILE = "export.outputFile";

    private final ExportService exportService;

    public ExportRunner(ExportService exportService) {
        this.exportService = exportService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String param      = resolveParam(args);
        String code       = resolveCode(args);
        String outputFile = resolveOutputFile(args);
        log.info("Starting export job: param='{}', code='{}', outputFile='{}'", param, code, outputFile);
        Path result = exportService.export(param, outputFile);
        log.info("Export finished. Output file: {}", result.toAbsolutePath());
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
     * Resolves the optional code prefix from {@code --export.code=value}.
     * This value is used by Logback as the prefix of the log file name.
     *
     * @return the supplied code, or {@code null} if the option was not provided
     */
    public String resolveCode(ApplicationArguments args) {
        List<String> values = args.getOptionValues(OPTION_CODE);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    /**
     * Resolves the optional full output file path from {@code --export.outputFile=value}.
     *
     * @return the supplied path, or {@code null} if the option was not provided
     *         (the service falls back to {@code app.output.directory} and
     *         {@code app.output.filename})
     */
    public String resolveOutputFile(ApplicationArguments args) {
        List<String> values = args.getOptionValues(OPTION_OUTPUT_FILE);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }
}
