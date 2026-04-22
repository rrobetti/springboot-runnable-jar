package com.example.export.service;

import com.example.export.config.AppProperties;
import com.example.export.model.Person;
import com.example.export.repository.BatchConfigRepository;
import com.example.export.repository.PersonRepository;
import net.sf.JRecord.Details.AbstractLine;
import net.sf.JRecord.IO.AbstractLineWriter;
import net.sf.JRecord.JRecordInterface1;
import net.sf.JRecord.Types.Type;
import net.sf.JRecord.def.IO.builders.IFixedWidthIOBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.Closeable;
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
 *       each row as a fixed-width positional line to the output file without loading
 *       all data into memory.</li>
 * </ol>
 *
 * <p>The positional file layout is:
 * <pre>
 *   Field   Position  Length  Type
 *   ------  --------  ------  --------------------
 *   id          1       10    Numeric right-justified
 *   name       11       50    Character (space-padded)
 *   email      61       60    Character (space-padded)
 *   status    121       10    Character (space-padded)
 * </pre>
 * The first line of the file is a header with the column names at their respective positions.
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    /** Placeholder used in the filename template. */
    public static final String PARAM_PLACEHOLDER = "{param}";

    /** Fixed-width field definitions: name, type, length. */
    public static final int ID_LEN     = 10;
    public static final int NAME_LEN   = 50;
    public static final int EMAIL_LEN  = 60;
    public static final int STATUS_LEN = 10;

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
     * Builds the JRecord {@link IFixedWidthIOBuilder} for the positional layout.
     */
    IFixedWidthIOBuilder createIOBuilder() {
        return JRecordInterface1.FIXED_WIDTH.newIOBuilder()
                .defineFieldsByLength()
                    .addFieldByLength("id",     Type.ftCharRightJust,     ID_LEN,     0)
                    .addFieldByLength("name",   Type.ftChar,              NAME_LEN,   0)
                    .addFieldByLength("email",  Type.ftChar,              EMAIL_LEN,  0)
                    .addFieldByLength("status", Type.ftChar,              STATUS_LEN, 0)
                .endOfRecord();
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

        // 3. Build JRecord IO builder for the fixed-width layout
        IFixedWidthIOBuilder ioBuilder = createIOBuilder();

        // 4. Stream rows and write to positional file
        AtomicLong rowCount = new AtomicLong(0);

        // AbstractLineWriter does not implement AutoCloseable; wrap it to enable try-with-resources.
        AbstractLineWriter writer = ioBuilder.newWriter(outputFile.toString());
        try (Closeable writerCloseable = writer::close) {
            // Write header line
            AbstractLine headerLine = ioBuilder.newLine();
            headerLine.getFieldValue("id").set("id");
            headerLine.getFieldValue("name").set("name");
            headerLine.getFieldValue("email").set("email");
            headerLine.getFieldValue("status").set("status");
            writer.write(headerLine);

            // Reuse a single AbstractLine for every data row to minimise allocations.
            AbstractLine dataLine = ioBuilder.newLine();

            // Stream and write data lines
            personRepository.streamByStatus(
                    appProperties.getFilter().getStatus(),
                    batchSize,
                    person -> {
                        try {
                            fillPositionalLine(dataLine, person);
                            writer.write(dataLine);
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
     * Fills the reusable {@link AbstractLine} with data from a {@link Person}.
     */
    private void fillPositionalLine(AbstractLine line, Person person) throws IOException {
        line.getFieldValue("id").set(String.valueOf(person.getId()));
        line.getFieldValue("name").set(person.getName() != null ? person.getName() : "");
        line.getFieldValue("email").set(person.getEmail() != null ? person.getEmail() : "");
        line.getFieldValue("status").set(person.getStatus() != null ? person.getStatus() : "");
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
