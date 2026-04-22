package com.example.export;

import com.example.export.repository.BatchConfigRepository;
import com.example.export.service.ExportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.example.export.service.ExportService.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ExportService} using an H2 in-memory database.
 *
 * <p>The {@code test} profile activates {@code application-test.properties} which
 * configures H2 and points the output directory to the system temp folder.
 */
@SpringBootTest
@ActiveProfiles("test")
class ExportServiceIntegrationTest {

    @Autowired
    private ExportService exportService;

    @Autowired
    private BatchConfigRepository batchConfigRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Path outputFile;

    /** Expected record length: sum of all field widths. */
    private static final int RECORD_LENGTH = WIDTH_ID + WIDTH_NAME + WIDTH_EMAIL + WIDTH_STATUS;

    @BeforeEach
    void cleanPersonTable() {
        jdbcTemplate.execute("DELETE FROM person");
    }

    @AfterEach
    void deleteOutputFile() throws IOException {
        if (outputFile != null && Files.exists(outputFile)) {
            Files.delete(outputFile);
        }
    }

    // ─── BatchConfigRepository ────────────────────────────────────────────────

    @Test
    void loadBatchSize_returnsDatabaseValue() {
        int batchSize = batchConfigRepository.loadBatchSize();
        assertThat(batchSize).isEqualTo(50);
    }

    @Test
    void loadBatchSize_returnsDefaultWhenKeyMissing() {
        jdbcTemplate.execute("DELETE FROM batch_config WHERE config_key = 'batch_size'");
        int batchSize = batchConfigRepository.loadBatchSize();
        assertThat(batchSize).isEqualTo(100);
        // restore for other tests
        jdbcTemplate.update("INSERT INTO batch_config(config_key, config_value) VALUES('batch_size','50')");
    }

    // ─── ExportService – file path resolution ─────────────────────────────────

    @Test
    void resolveOutputPath_replacesParamPlaceholder() {
        Path path = exportService.resolveOutputPath("jan2024");
        assertThat(path.getFileName().toString()).isEqualTo("export_jan2024.dat");
    }

    @Test
    void resolveOutputPath_withEmptyParam_producesValidPath() {
        Path path = exportService.resolveOutputPath("");
        assertThat(path.getFileName().toString()).isEqualTo("export_.dat");
    }

    // ─── ExportService – full export ──────────────────────────────────────────

    @Test
    void export_createsEmptyFile_whenNoActivePersons() throws IOException {
        // No persons in the table (cleaned in @BeforeEach)
        outputFile = exportService.export("empty");

        assertThat(Files.exists(outputFile)).isTrue();
        List<String> lines = Files.readAllLines(outputFile);
        assertThat(lines).isEmpty();
    }

    @Test
    void export_writesActivePersonsAsPositionalRecords() throws IOException {
        jdbcTemplate.update("INSERT INTO person(name,email,status) VALUES(?,?,?)",
                "Alice", "alice@test.com", "ACTIVE");
        jdbcTemplate.update("INSERT INTO person(name,email,status) VALUES(?,?,?)",
                "Bob", "bob@test.com", "ACTIVE");
        jdbcTemplate.update("INSERT INTO person(name,email,status) VALUES(?,?,?)",
                "Carol", "carol@test.com", "INACTIVE");

        outputFile = exportService.export("testrun");

        List<String> lines = Files.readAllLines(outputFile);
        // 2 active rows only (Carol is INACTIVE and must be excluded)
        assertThat(lines).hasSize(2);
        assertThat(lines).anyMatch(l -> l.contains("Alice"));
        assertThat(lines).anyMatch(l -> l.contains("Bob"));
        assertThat(lines).noneMatch(l -> l.contains("Carol"));
    }

    @Test
    void export_recordsHaveFixedWidth() throws IOException {
        jdbcTemplate.update("INSERT INTO person(name,email,status) VALUES(?,?,?)",
                "Alice", "alice@test.com", "ACTIVE");

        outputFile = exportService.export("widthcheck");

        List<String> lines = Files.readAllLines(outputFile);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).hasSize(RECORD_LENGTH);
    }

    @Test
    void export_idIsRightJustified() throws IOException {
        jdbcTemplate.update("INSERT INTO person(name,email,status) VALUES(?,?,?)",
                "Alice", "alice@test.com", "ACTIVE");

        outputFile = exportService.export("idjust");

        String record = Files.readAllLines(outputFile).get(0);
        // The id field occupies the first WIDTH_ID characters and must end with a digit
        String idField = record.substring(0, WIDTH_ID);
        assertThat(idField.stripLeading()).matches("\\d+");
    }

    @Test
    void export_stringFieldsArePaddedWithSpaces() throws IOException {
        jdbcTemplate.update("INSERT INTO person(name,email,status) VALUES(?,?,?)",
                "Bob", "b@t.com", "ACTIVE");

        outputFile = exportService.export("padcheck");

        String record = Files.readAllLines(outputFile).get(0);
        // name field starts at column WIDTH_ID and is WIDTH_NAME chars wide
        String nameField = record.substring(WIDTH_ID, WIDTH_ID + WIDTH_NAME);
        assertThat(nameField).startsWith("Bob");
        assertThat(nameField).endsWith(" ");
    }

    @Test
    void export_filenameContainsSuppliedParam() throws IOException {
        outputFile = exportService.export("Q1-2024");

        assertThat(outputFile.getFileName().toString()).isEqualTo("export_Q1-2024.dat");
        assertThat(Files.exists(outputFile)).isTrue();
    }

    @Test
    void export_longFieldValuesAreTruncated() throws IOException {
        String longName = "A".repeat(100); // exceeds WIDTH_NAME (50)
        jdbcTemplate.update("INSERT INTO person(name,email,status) VALUES(?,?,?)",
                longName, "a@test.com", "ACTIVE");

        outputFile = exportService.export("trunctest");

        List<String> lines = Files.readAllLines(outputFile);
        assertThat(lines).hasSize(1);
        // Record must still be exactly RECORD_LENGTH chars
        assertThat(lines.get(0)).hasSize(RECORD_LENGTH);
    }

    @Test
    void export_writesAllActivePersons_withLargeBatchSize() throws IOException {
        // Insert 10 active persons
        for (int i = 1; i <= 10; i++) {
            jdbcTemplate.update("INSERT INTO person(name,email,status) VALUES(?,?,?)",
                    "Person" + i, "p" + i + "@test.com", "ACTIVE");
        }
        // Also insert some inactive ones to confirm filtering
        jdbcTemplate.update("INSERT INTO person(name,email,status) VALUES(?,?,?)",
                "Ignored", "ignored@test.com", "INACTIVE");

        outputFile = exportService.export("bulk");

        List<String> lines = Files.readAllLines(outputFile);
        // 10 active rows only (no header)
        assertThat(lines).hasSize(10);
    }
}
