package com.example.export.service;

import com.example.export.config.AppProperties;
import com.example.export.repository.BatchConfigRepository;
import com.example.export.repository.PersonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Orchestrates the export job:
 * <ol>
 *   <li>Loads the batch size from the {@code batch_config} table.</li>
 *   <li>Resolves the output file path from configurable properties, replacing the
 *       {@code {param}} placeholder in the filename with the supplied runtime value.</li>
 *   <li>Streams rows from the {@code person} table (filtered by status) and writes
 *       each row as a CSV line to the output file without loading all data into memory.</li>
 * </ol>
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    /** Placeholder used in the filename template. */
    public static final String PARAM_PLACEHOLDER = "{param}";

    /** CSV header written as the first line of the output file. */
    private static final String CSV_HEADER = "id,name,email,status";

    private final AppProperties appProperties;
    private final BatchConfigRepository batchConfigRepository;
    private final PersonRepository personRepository;

    public ExportService(AppProperties appProperties,
                         BatchConfigRepository batchConfigRepository,
                         PersonRepository personRepository) {
        this.appProperties = appProperties;
        this.batchConfigRepository = batchConfigRepository;
        this.personRepository = personRepository;
    }

    /**
     * Runs the export job.
     *
     * @param param runtime parameter used to build the output filename
     * @return path of the file that was written
     * @throws IOException if the output file cannot be created or written
     */
    public Path export(String param) throws IOException {
        // 1. Load batch size from the database
        int batchSize = batchConfigRepository.loadBatchSize();

        // 2. Resolve output path
        Path outputFile = resolveOutputPath(param);
        Files.createDirectories(outputFile.getParent());
        log.info("Writing export to: {}", outputFile.toAbsolutePath());

        // 3. Stream rows and write to file
        AtomicLong rowCount = new AtomicLong(0);

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
            writer.write(CSV_HEADER);
            writer.newLine();

            personRepository.streamByStatus(
                    appProperties.getFilter().getStatus(),
                    batchSize,
                    person -> {
                        try {
                            writer.write(person.toCsvLine());
                            writer.newLine();
                            rowCount.incrementAndGet();
                        } catch (IOException e) {
                            throw new ExportWriteException("Failed to write row: " + person, e);
                        }
                    });
        }

        log.info("Export complete. {} rows written to {}", rowCount.get(), outputFile.toAbsolutePath());
        return outputFile;
    }

    /**
     * Builds the output {@link Path} by combining the configured directory with the
     * filename template (after replacing the {@code {param}} placeholder).
     */
    public Path resolveOutputPath(String param) {
        String filename = appProperties.getOutput().getFilename()
                .replace(PARAM_PLACEHOLDER, param);
        return Paths.get(appProperties.getOutput().getDirectory(), filename);
    }

    /**
     * Unchecked wrapper for {@link IOException} thrown inside the streaming lambda.
     */
    public static class ExportWriteException extends RuntimeException {
        public ExportWriteException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
