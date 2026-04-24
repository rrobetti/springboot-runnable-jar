package com.example.export.service;

import com.example.export.config.AppProperties;
import com.example.export.repository.BatchConfigRepository;
import com.example.export.repository.PersonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Orchestrates the export job inside a single database transaction:
 * <ol>
 *   <li>Loads the batch size from the {@code batch_config} table.</li>
 *   <li>Parses the {@code param} argument as a date in {@code YYYYMMDD} format.</li>
 *   <li>Updates all {@code person} rows for that date from the configured filter
 *       status to {@value #PROCESSING_STATUS} ({@code "I"}).</li>
 *   <li>Resolves the output file path (runtime override or configured default).</li>
 *   <li>Writes a fixed-width header record (record type, date, status) followed by
 *       one detail record per matching row, flushing to disk every
 *       {@code batchSize} records to bound memory usage.</li>
 *   <li>Updates all processed rows to {@value #COMPLETED_STATUS} ({@code "C"}).</li>
 *   <li>On any failure the database transaction is rolled back automatically.</li>
 * </ol>
 *
 * <p><b>Header record layout (total {@value #RECORD_LENGTH} characters):</b>
 * <pre>
 *   Column   1    : Record type "H"
 *   Columns  2-9  : Date in YYYYMMDD format          (8 chars)
 *   Columns 10-19 : Status, left-justified, padded   (10 chars)
 *   Columns 20-180: Spaces                           (161 chars)
 * </pre>
 *
 * <p><b>Detail record layout (total {@value #RECORD_LENGTH} characters):</b>
 * <pre>
 *   Columns  1-20 : id     (right-justified, space-padded)
 *   Columns 21-70 : name   (left-justified,  space-padded, truncated to 50 chars)
 *   Columns 71-170: email  (left-justified,  space-padded, truncated to 100 chars)
 *   Columns 171-180: status (left-justified, space-padded, truncated to 10 chars)
 * </pre>
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    /** Date format expected from the command line ({@code YYYYMMDD}). */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    /** Placeholder used in the filename template. */
    public static final String PARAM_PLACEHOLDER = "{param}";

    /** Intermediate status assigned to rows selected for processing. */
    public static final String PROCESSING_STATUS = "I";

    /** Final status assigned to rows after successful export. */
    public static final String COMPLETED_STATUS = "C";

    // ── Detail record field widths ────────────────────────────────────────────
    public static final int WIDTH_ID     = 20;
    public static final int WIDTH_NAME   = 50;
    public static final int WIDTH_EMAIL  = 100;
    public static final int WIDTH_STATUS = 10;

    /** Total fixed width of every record (header and detail). */
    public static final int RECORD_LENGTH = WIDTH_ID + WIDTH_NAME + WIDTH_EMAIL + WIDTH_STATUS;

    // ── Header record field widths ────────────────────────────────────────────
    public static final int WIDTH_HDR_TYPE   = 1;
    public static final int WIDTH_HDR_DATE   = 8;
    public static final int WIDTH_HDR_STATUS = 10;

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
     * Runs the full export job within a single database transaction.
     *
     * <p>On success all processed rows are updated to {@value #COMPLETED_STATUS}
     * and the transaction is committed.  On any failure the transaction is rolled
     * back automatically; the caller will receive exit code {@code 1}.
     *
     * @param param          date string in {@code YYYYMMDD} format; also used in the
     *                       output filename when falling back to configuration
     * @param outputFilePath full path of the file to create, or {@code null} to
     *                       derive the path from {@code app.output.directory} and
     *                       {@code app.output.filename}
     * @return path of the successfully written output file
     * @throws IOException            if the output file cannot be created or written
     * @throws java.time.format.DateTimeParseException if {@code param} is not a
     *                                valid {@code YYYYMMDD} date
     */
    @Transactional(rollbackFor = Exception.class)
    public Path export(String param, String outputFilePath) throws IOException {
        int batchSize = batchConfigRepository.loadBatchSize();

        LocalDate date = LocalDate.parse(param, DATE_FORMAT);
        String fromStatus = appProperties.getFilter().getStatus();

        Path outputFile = resolveOutputPath(param, outputFilePath);

        Files.createDirectories(outputFile.getParent());
        log.info("Writing export to: {}", outputFile.toAbsolutePath());

        // 1. Mark all matching rows as PROCESSING (part of the transaction).
        personRepository.updateStatusByDate(date, fromStatus, PROCESSING_STATUS);

        // 2. Stream the updated rows and write the file.
        AtomicLong rowCount = new AtomicLong(0);

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
            // Header record
            writer.write(toHeaderRecord(param, fromStatus));
            writer.newLine();

            // Detail records – flush every batchSize rows to bound buffer memory
            personRepository.streamByDateAndStatus(date, PROCESSING_STATUS, batchSize,
                    rs -> {
                        try {
                            writer.write(toDetailRecord(rs));
                            writer.newLine();
                            long count = rowCount.incrementAndGet();
                            if (count % batchSize == 0) {
                                writer.flush();
                                log.debug("Flushed {} records to file", count);
                            }
                        } catch (IOException | SQLException e) {
                            throw new ExportWriteException("Failed to write row", e);
                        }
                    });
        }

        // 3. Update all processed rows to COMPLETED.
        personRepository.updateStatusByDate(date, PROCESSING_STATUS, COMPLETED_STATUS);

        log.info("Export complete. {} detail rows written to {}",
                rowCount.get(), outputFile.toAbsolutePath());
        return outputFile;
    }

    /**
     * Builds the output {@link Path} from the supplied full path or, when
     * {@code outputFilePath} is {@code null}, by combining the configured
     * directory with the filename template (replacing the {@code {param}}
     * placeholder).
     *
     * @param param          value substituted for {@value #PARAM_PLACEHOLDER}
     *                       when falling back to the configured filename template
     * @param outputFilePath full output file path override, or {@code null} to
     *                       use {@code app.output.directory} and
     *                       {@code app.output.filename}
     */
    public Path resolveOutputPath(String param, String outputFilePath) {
        if (outputFilePath != null) {
            return Paths.get(outputFilePath);
        }
        String filename = appProperties.getOutput().getFilename()
                .replace(PARAM_PLACEHOLDER, param);
        return Paths.get(appProperties.getOutput().getDirectory(), filename);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    // ── Record formatters ─────────────────────────────────────────────────────

    /**
     * Formats the header record for the output file.
     *
     * <p>Layout ({@value #RECORD_LENGTH} characters total):
     * <ul>
     *   <li>Column 1: record type {@code "H"}</li>
     *   <li>Columns 2–9: {@code date} in {@code YYYYMMDD} format</li>
     *   <li>Columns 10–19: {@code status}, left-justified, space-padded</li>
     *   <li>Columns 20–{@value #RECORD_LENGTH}: spaces</li>
     * </ul>
     *
     * @param date   date string in {@code YYYYMMDD} format
     * @param status filter status written to the header
     */
    public static String toHeaderRecord(String date, String status) {
        int fillerWidth = RECORD_LENGTH - WIDTH_HDR_TYPE - WIDTH_HDR_DATE - WIDTH_HDR_STATUS;
        return String.format(
                "%-" + WIDTH_HDR_TYPE   + "s" +
                "%-" + WIDTH_HDR_DATE   + "s" +
                "%-" + WIDTH_HDR_STATUS + "s" +
                "%-" + fillerWidth      + "s",
                "H",
                truncate(date,   WIDTH_HDR_DATE),
                truncate(status, WIDTH_HDR_STATUS),
                "");
    }

    /**
     * Formats a single database row as a fixed-width detail record.
     * String values are left-justified and space-padded; the numeric id is
     * right-justified and space-padded. Values exceeding their field width are
     * truncated to prevent record-length corruption.
     */
    public static String toDetailRecord(ResultSet rs) throws SQLException {
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
