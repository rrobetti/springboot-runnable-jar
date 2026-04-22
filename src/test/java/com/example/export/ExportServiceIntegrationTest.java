package com.example.export;

import com.example.export.repository.BatchConfigRepository;
import com.example.export.repository.PersonRepository;
import com.example.export.service.ExportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static com.example.export.service.ExportService.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;

/**
 * Integration tests for {@link ExportService} using an H2 in-memory database.
 *
 * <p>The {@code test} profile activates {@code application-test.properties} which
 * configures H2 and points the output directory to the system temp folder.
 */
@SpringBootTest
@ActiveProfiles("test")
class ExportServiceIntegrationTest {

    /** Date used as the export parameter in all tests that exercise the full flow. */
    private static final String TEST_DATE       = "20240115";
    private static final LocalDate TEST_LOCAL_DATE = LocalDate.of(2024, 1, 15);

    @Autowired
    private ExportService exportService;

    @Autowired
    private BatchConfigRepository batchConfigRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @SpyBean
    private PersonRepository personRepository;

    private Path outputFile;

    /** Expected record length: sum of all field widths. */
    private static final int RECORD_LENGTH_TEST = WIDTH_ID + WIDTH_NAME + WIDTH_EMAIL + WIDTH_STATUS;

    @BeforeEach
    void cleanPersonTable() {
        jdbcTemplate.execute("DELETE FROM person");
    }

    @AfterEach
    void cleanup() throws IOException {
        Mockito.reset(personRepository);
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
        Path path = exportService.resolveOutputPath("jan2024", null);
        assertThat(path.getFileName().toString()).isEqualTo("export_jan2024.dat");
    }

    @Test
    void resolveOutputPath_withEmptyParam_producesValidPath() {
        Path path = exportService.resolveOutputPath("", null);
        assertThat(path.getFileName().toString()).isEqualTo("export_.dat");
    }

    @Test
    void resolveOutputPath_usesOverrideDirectoryWhenSupplied(@TempDir Path tempDir) {
        Path path = exportService.resolveOutputPath(TEST_DATE, tempDir.toString());
        assertThat(path.getParent()).isEqualTo(tempDir);
        assertThat(path.getFileName().toString()).isEqualTo("export_" + TEST_DATE + ".dat");
    }

    // ─── ExportService – header record ───────────────────────────────────────

    @Test
    void toHeaderRecord_hasCorrectLength() {
        String header = ExportService.toHeaderRecord(TEST_DATE, "ACTIVE");
        assertThat(header).hasSize(RECORD_LENGTH);
    }

    @Test
    void toHeaderRecord_startsWithH_thenDateThenStatus() {
        String header = ExportService.toHeaderRecord(TEST_DATE, "ACTIVE");
        assertThat(header.charAt(0)).isEqualTo('H');
        assertThat(header.substring(1, 9)).isEqualTo(TEST_DATE);
        assertThat(header.substring(9, 19).stripTrailing()).isEqualTo("ACTIVE");
    }

    // ─── ExportService – full export ──────────────────────────────────────────

    @Test
    void export_createsFileWithHeaderOnly_whenNoMatchingPersons() throws IOException {
        // No persons in the table (cleaned in @BeforeEach)
        outputFile = exportService.export(TEST_DATE, null, null);

        assertThat(Files.exists(outputFile)).isTrue();
        List<String> lines = Files.readAllLines(outputFile);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).charAt(0)).isEqualTo('H');
    }

    @Test
    void export_writesHeaderThenActivePersons() throws IOException {
        insertPerson("Alice", "alice@test.com", "ACTIVE");
        insertPerson("Bob",   "bob@test.com",   "ACTIVE");
        insertPerson("Carol", "carol@test.com", "INACTIVE");

        outputFile = exportService.export(TEST_DATE, null, null);

        List<String> lines = Files.readAllLines(outputFile);
        // header + 2 ACTIVE rows (Carol is INACTIVE and must be excluded)
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0).charAt(0)).isEqualTo('H');
        assertThat(lines).anyMatch(l -> l.contains("Alice"));
        assertThat(lines).anyMatch(l -> l.contains("Bob"));
        assertThat(lines).noneMatch(l -> l.contains("Carol"));
    }

    @Test
    void export_onlyProcessesRecordsMatchingDate() throws IOException {
        insertPerson("Alice", "alice@test.com", "ACTIVE");  // record_date = TEST_LOCAL_DATE
        // Insert a person with a different date
        jdbcTemplate.update(
                "INSERT INTO person(name,email,status,record_date) VALUES(?,?,?,?)",
                "Other", "other@test.com", "ACTIVE", LocalDate.of(2024, 1, 16));

        outputFile = exportService.export(TEST_DATE, null, null);

        List<String> lines = Files.readAllLines(outputFile);
        // header + 1 row (Alice only; Other is on a different date)
        assertThat(lines).hasSize(2);
        assertThat(lines).anyMatch(l -> l.contains("Alice"));
        assertThat(lines).noneMatch(l -> l.contains("Other"));
    }

    @Test
    void export_headerAndDetailRecordsHaveFixedWidth() throws IOException {
        insertPerson("Alice", "alice@test.com", "ACTIVE");

        outputFile = exportService.export(TEST_DATE, null, null);

        List<String> lines = Files.readAllLines(outputFile);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).hasSize(RECORD_LENGTH_TEST); // header
        assertThat(lines.get(1)).hasSize(RECORD_LENGTH_TEST); // detail
    }

    @Test
    void export_headerContainsDateAndStatus() throws IOException {
        outputFile = exportService.export(TEST_DATE, null, null);

        String header = Files.readAllLines(outputFile).get(0);
        assertThat(header).startsWith("H");
        assertThat(header).contains(TEST_DATE);
        assertThat(header.substring(9, 19).stripTrailing()).isEqualTo("ACTIVE");
    }

    @Test
    void export_idIsRightJustified() throws IOException {
        insertPerson("Alice", "alice@test.com", "ACTIVE");

        outputFile = exportService.export(TEST_DATE, null, null);

        // Line 0 is the header; line 1 is the first detail record
        String detail = Files.readAllLines(outputFile).get(1);
        String idField = detail.substring(0, WIDTH_ID);
        assertThat(idField.stripLeading()).matches("\\d+");
    }

    @Test
    void export_stringFieldsArePaddedWithSpaces() throws IOException {
        insertPerson("Bob", "b@t.com", "ACTIVE");

        outputFile = exportService.export(TEST_DATE, null, null);

        String detail = Files.readAllLines(outputFile).get(1);
        // name field starts at column WIDTH_ID and is WIDTH_NAME chars wide
        String nameField = detail.substring(WIDTH_ID, WIDTH_ID + WIDTH_NAME);
        assertThat(nameField).startsWith("Bob");
        assertThat(nameField).endsWith(" ");
    }

    @Test
    void export_filenameContainsSuppliedParam() throws IOException {
        outputFile = exportService.export(TEST_DATE, null, null);

        assertThat(outputFile.getFileName().toString()).isEqualTo("export_" + TEST_DATE + ".dat");
        assertThat(Files.exists(outputFile)).isTrue();
    }

    @Test
    void export_longFieldValuesAreTruncated() throws IOException {
        String longName = "A".repeat(100); // exceeds WIDTH_NAME (50)
        jdbcTemplate.update(
                "INSERT INTO person(name,email,status,record_date) VALUES(?,?,?,?)",
                longName, "a@test.com", "ACTIVE", TEST_LOCAL_DATE);

        outputFile = exportService.export(TEST_DATE, null, null);

        List<String> lines = Files.readAllLines(outputFile);
        assertThat(lines).hasSize(2); // header + 1 detail
        // Both header and detail must be exactly RECORD_LENGTH chars
        assertThat(lines.get(0)).hasSize(RECORD_LENGTH_TEST);
        assertThat(lines.get(1)).hasSize(RECORD_LENGTH_TEST);
    }

    @Test
    void export_writesAllMatchingPersons_withLargeBatchSize() throws IOException {
        for (int i = 1; i <= 10; i++) {
            insertPerson("Person" + i, "p" + i + "@test.com", "ACTIVE");
        }
        insertPerson("Ignored", "ignored@test.com", "INACTIVE");

        outputFile = exportService.export(TEST_DATE, null, null);

        List<String> lines = Files.readAllLines(outputFile);
        // header + 10 ACTIVE rows
        assertThat(lines).hasSize(11);
    }

    // ─── ExportService – transaction rollback ─────────────────────────────────

    @Test
    void export_rollsBackStatusUpdateOnFailure(@TempDir Path tempOut, @TempDir Path tempErr)
            throws IOException {
        insertPerson("Alice", "alice@test.com", "ACTIVE");

        doThrow(new RuntimeException("Simulated streaming failure"))
                .when(personRepository)
                .streamByDateAndStatus(any(), any(), anyInt(), any());

        assertThatThrownBy(
                () -> exportService.export(TEST_DATE, tempOut.toString(), tempErr.toString()))
                .isInstanceOf(RuntimeException.class);

        // Status must have been rolled back to ACTIVE
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM person WHERE name = 'Alice'", String.class);
        assertThat(status).isEqualTo("ACTIVE");
    }

    // ─── ExportService – error directory ─────────────────────────────────────

    @Test
    void export_movesPartialFileToErrorDirOnFailure(@TempDir Path tempOut, @TempDir Path tempErr)
            throws IOException {
        insertPerson("Alice", "alice@test.com", "ACTIVE");

        doThrow(new RuntimeException("Simulated streaming failure"))
                .when(personRepository)
                .streamByDateAndStatus(any(), any(), anyInt(), any());

        assertThatThrownBy(
                () -> exportService.export(TEST_DATE, tempOut.toString(), tempErr.toString()))
                .isInstanceOf(RuntimeException.class);

        // The partial file (containing at minimum the header) must be in the error dir
        Path movedFile = tempErr.resolve("export_" + TEST_DATE + ".dat");
        assertThat(movedFile).exists();
        List<String> lines = Files.readAllLines(movedFile);
        assertThat(lines.get(0)).startsWith("H");
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void insertPerson(String name, String email, String status) {
        jdbcTemplate.update(
                "INSERT INTO person(name,email,status,record_date) VALUES(?,?,?,?)",
                name, email, status, TEST_LOCAL_DATE);
    }
}
