package com.example.export.repository;

import com.example.export.model.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.util.function.Consumer;

/**
 * Streams rows from the {@code person} table using a server-side cursor
 * (controlled by JDBC fetch size) to keep memory usage low.
 *
 * <p>Table DDL:
 * <pre>
 *   CREATE TABLE person (
 *       id     BIGINT       PRIMARY KEY AUTO_INCREMENT,
 *       name   VARCHAR(255) NOT NULL,
 *       email  VARCHAR(255),
 *       status VARCHAR(50)  NOT NULL
 *   );
 * </pre>
 */
@Repository
public class PersonRepository {

    private static final Logger log = LoggerFactory.getLogger(PersonRepository.class);

    private static final String SELECT_BY_STATUS =
            "SELECT id, name, email, status FROM person WHERE status = ?";

    private final JdbcTemplate jdbcTemplate;

    public PersonRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Streams all {@link Person} rows matching the given {@code status}, fetching
     * {@code fetchSize} rows at a time from the database to limit memory usage.
     *
     * <p>Each row is passed to {@code rowConsumer} as soon as it is fetched —
     * no intermediate list is built.
     *
     * @param status      value to filter on the {@code status} column
     * @param fetchSize   JDBC fetch size (batch size loaded from {@code batch_config})
     * @param rowConsumer callback invoked for every matching row
     */
    public void streamByStatus(String status, int fetchSize, Consumer<Person> rowConsumer) {
        log.info("Streaming persons with status='{}' and fetchSize={}", status, fetchSize);

        // Use PreparedStatementCreator to set the JDBC fetch size (controls how many rows
        // are transferred from the DB server per network round-trip, keeping heap usage low).
        // The RowCallbackHandler is invoked once per row by JdbcTemplate; we must NOT call
        // rs.next() ourselves inside the callback.
        jdbcTemplate.query(
                connection -> {
                    var ps = connection.prepareStatement(
                            SELECT_BY_STATUS,
                            ResultSet.TYPE_FORWARD_ONLY,
                            ResultSet.CONCUR_READ_ONLY);
                    ps.setFetchSize(fetchSize);
                    ps.setString(1, status);
                    return ps;
                },
                (ResultSet rs) -> {
                    Person person = new Person(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("status"));
                    rowConsumer.accept(person);
                }
        );
    }
}
