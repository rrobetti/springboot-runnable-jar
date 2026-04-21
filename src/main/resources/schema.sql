-- ─── batch_config ──────────────────────────────────────────────────────────
-- Stores key/value configuration entries used by the export job.
-- The 'batch_size' entry controls how many rows are fetched from the database
-- at a time (JDBC fetch size), keeping heap usage bounded.
CREATE TABLE IF NOT EXISTS batch_config (
    config_key   VARCHAR(100) PRIMARY KEY,
    config_value VARCHAR(255) NOT NULL
);

-- ─── person ─────────────────────────────────────────────────────────────────
-- Source table read by the export job.
CREATE TABLE IF NOT EXISTS person (
    id     BIGINT       PRIMARY KEY AUTO_INCREMENT,
    name   VARCHAR(255) NOT NULL,
    email  VARCHAR(255),
    status VARCHAR(50)  NOT NULL
);
