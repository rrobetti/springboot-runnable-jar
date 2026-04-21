package com.example.export.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads job configuration from the {@code batch_config} table.
 *
 * <p>The table stores key/value pairs:
 * <pre>
 *   CREATE TABLE batch_config (
 *       config_key   VARCHAR(100) PRIMARY KEY,
 *       config_value VARCHAR(255) NOT NULL
 *   );
 * </pre>
 */
@Repository
public class BatchConfigRepository {

    private static final Logger log = LoggerFactory.getLogger(BatchConfigRepository.class);

    private static final String SELECT_VALUE =
            "SELECT config_value FROM batch_config WHERE config_key = ?";

    private static final String DEFAULT_BATCH_SIZE = "100";
    private static final String KEY_BATCH_SIZE = "batch_size";

    private final JdbcTemplate jdbcTemplate;

    public BatchConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Returns the configured JDBC fetch size (batch size) from the database.
     * Falls back to {@code 100} if the key is missing.
     */
    public int loadBatchSize() {
        try {
            String value = jdbcTemplate.queryForObject(SELECT_VALUE, String.class, KEY_BATCH_SIZE);
            int batchSize = Integer.parseInt(value != null ? value : DEFAULT_BATCH_SIZE);
            log.info("Loaded batch_size from database: {}", batchSize);
            return batchSize;
        } catch (Exception e) {
            log.warn("Could not load batch_size from batch_config table, using default {}. Reason: {}",
                    DEFAULT_BATCH_SIZE, e.getMessage());
            return Integer.parseInt(DEFAULT_BATCH_SIZE);
        }
    }
}
