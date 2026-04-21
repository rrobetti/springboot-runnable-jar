-- ─── batch_config seed data ─────────────────────────────────────────────────
-- Number of rows fetched per round-trip to the database (JDBC fetch size).
MERGE INTO batch_config (config_key, config_value)
    KEY (config_key)
    VALUES ('batch_size', '50');

-- ─── person seed data ────────────────────────────────────────────────────────
MERGE INTO person (id, name, email, status)
    KEY (id)
    VALUES (1, 'Alice Smith',   'alice@example.com',   'ACTIVE'),
           (2, 'Bob Jones',     'bob@example.com',     'ACTIVE'),
           (3, 'Carol White',   'carol@example.com',   'INACTIVE'),
           (4, 'David Brown',   'david@example.com',   'ACTIVE'),
           (5, 'Eve Davis',     'eve@example.com',     'INACTIVE');
