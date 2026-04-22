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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Orchestrates the export job:
 * <ol>
 *   <li>Loads the batch size from the {@code batch_config} table.</li>
 *   <li>Resolves the output file path from configurable properties, replacing the
 *       {@code {param}} placeholder in the filename with the supplied runtime value.</li>
 *   <li>Streams rows from the {@code person} table (filtered by status) and writes
 *       each row as a fixed-width positional record to the output file without
 *       loading all data into memory.</li>
 * </ol>
 *
 * <p>Positional record layout (total 180 characters per record):
 * <pre>
 *   Columns  1-20  : id     (right-justified, space-padded)
 *   Columns 21-70  : name   (left-justified,  space-padded, truncated to 50 chars)
 *   Columns 71-170 : email  (left-justified,  space-padded, truncated to 100 chars)
 *   Columns 171-180: status (left-justified,  space-padded, truncated to 10 chars)
 * </pre>
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    /** Placeholder used in the filename template. */
    public static final String PARAM_PLACEHOLDER = "{param}";

    // Fixed field widths for the mainframe positional record layout
    public static final int WIDTH_ID     = 20;
    public static final int WIDTH_NAME   = 50;
    public static final int WIDTH_EMAIL  = 100;
    public static final int WIDTH_STATUS = 10;

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

        // 3. Stream rows and write positional records directly from ResultSet
        AtomicLong rowCount = new AtomicLong(0);

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
            personRepository.streamByStatus(
                    appProperties.getFilter().getStatus(),
                    batchSize,
                    rs -> {
                        try {
                            writer.write(toPositionalRecord(rs));
                            writer.newLine();
                            rowCount.incrementAndGet();
                        } catch (IOException | SQLException e) {
                            throw new ExportWriteException("Failed to write row", e);
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
     * Formats a single database row as a fixed-width positional record.
     * String values are left-justified and space-padded; the numeric id is
     * right-justified and space-padded. Values exceeding their field width are
     * truncated to prevent record-length corruption.
     */
    static String toPositionalRecord(ResultSet rs) throws SQLException {
        long   id     = rs.getLong("id");
        String name   = truncate(Objects.toString(rs.getString("name"),   ""), WIDTH_NAME);
        String email  = truncate(Objects.toString(rs.getString("email"),  ""), WIDTH_EMAIL);
        String status = truncate(Objects.toString(rs.getString("status"), ""), WIDTH_STATUS);

        return String.format(
                "%" + WIDTH_ID + "d" +
                "%-" + WIDTH_NAME   + "s" +
                "%-" + WIDTH_EMAIL  + "s" +
                "%-" + WIDTH_STATUS + "s",
                id, name, email, status);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * Unchecked wrapper for {@link IOException} or {@link SQLException} thrown
     * inside the streaming callback.
     */
    public static class ExportWriteException extends RuntimeException {
        public ExportWriteException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
