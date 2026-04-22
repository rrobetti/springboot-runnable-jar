package com.example.export.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.time.LocalDate;

/**
 * Data-access operations on the {@code person} table.
 *
 * <p>Table DDL:
 * <pre>
 *   CREATE TABLE person (
 *       id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
 *       name        VARCHAR(255) NOT NULL,
 *       email       VARCHAR(255),
 *       status      VARCHAR(50)  NOT NULL,
 *       record_date DATE         NOT NULL
 *   );
 * </pre>
 */
@Repository
public class PersonRepository {

    private static final Logger log = LoggerFactory.getLogger(PersonRepository.class);

    private static final String UPDATE_STATUS_BY_DATE =
            "UPDATE person SET status = ? WHERE record_date = ? AND status = ?";

    private static final String SELECT_BY_DATE_AND_STATUS =
            "SELECT id, name, email, status FROM person WHERE record_date = ? AND status = ?";

    private final JdbcTemplate jdbcTemplate;

    public PersonRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Updates all rows whose {@code record_date} matches {@code date} and whose
     * current status is {@code fromStatus}, setting their status to {@code toStatus}.
     *
     * @param date       the record date to match
     * @param fromStatus the current status value to filter on
     * @param toStatus   the new status value to set
     * @return number of rows updated
     */
    public int updateStatusByDate(LocalDate date, String fromStatus, String toStatus) {
        log.info("Updating status '{}' -> '{}' for record_date={}", fromStatus, toStatus, date);
        int rows = jdbcTemplate.update(UPDATE_STATUS_BY_DATE, toStatus, date, fromStatus);
        log.info("Updated {} row(s)", rows);
        return rows;
    }

    /**
     * Streams all rows whose {@code record_date} matches {@code date} and whose
     * {@code status} matches {@code status}, fetching {@code fetchSize} rows at a
     * time from the database to keep memory usage low.
     *
     * <p>The supplied {@link RowCallbackHandler} is invoked once per row with the
     * live {@link ResultSet} — no intermediate object is created.
     *
     * @param date       the record date to filter on
     * @param status     the status value to filter on
     * @param fetchSize  JDBC fetch size controlling rows per network round-trip
     * @param rowHandler callback invoked for every matching row
     */
    public void streamByDateAndStatus(LocalDate date, String status,
                                      int fetchSize, RowCallbackHandler rowHandler) {
        log.info("Streaming persons with record_date={}, status='{}', fetchSize={}",
                date, status, fetchSize);

        // PreparedStatementCreator sets TYPE_FORWARD_ONLY + CONCUR_READ_ONLY so the
        // driver can use a server-side cursor, and sets the fetch size to control
        // how many rows are transferred per round-trip.
        jdbcTemplate.query(
                connection -> {
                    var ps = connection.prepareStatement(
                            SELECT_BY_DATE_AND_STATUS,
                            ResultSet.TYPE_FORWARD_ONLY,
                            ResultSet.CONCUR_READ_ONLY);
                    ps.setFetchSize(fetchSize);
                    ps.setObject(1, date);
                    ps.setString(2, status);
                    return ps;
                },
                rowHandler
        );
    }
}
