package com.example.export.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;

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
     * Streams all rows matching the given {@code status}, fetching
     * {@code fetchSize} rows at a time from the database to limit memory usage.
     *
     * <p>The supplied {@link RowCallbackHandler} is invoked once per row with the
     * live {@link ResultSet} — no intermediate object is created.
     *
     * @param status     value to filter on the {@code status} column
     * @param fetchSize  JDBC fetch size (batch size loaded from {@code batch_config})
     * @param rowHandler callback invoked for every matching row
     */
    public void streamByStatus(String status, int fetchSize, RowCallbackHandler rowHandler) {
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
                rowHandler
        );
    }
}
