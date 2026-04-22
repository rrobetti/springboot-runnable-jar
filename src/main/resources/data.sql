-- ─── batch_config seed data ─────────────────────────────────────────────────
-- Number of rows fetched per round-trip to the database (JDBC fetch size).
MERGE INTO batch_config (config_key, config_value)
    KEY (config_key)
    VALUES ('batch_size', '50');

-- ─── person seed data ────────────────────────────────────────────────────────
MERGE INTO person (id, name, email, status, record_date)
    KEY (id)
    VALUES (1, 'Alice Smith',   'alice@example.com',   'ACTIVE',   '2024-01-01'),
           (2, 'Bob Jones',     'bob@example.com',     'ACTIVE',   '2024-01-01'),
           (3, 'Carol White',   'carol@example.com',   'INACTIVE', '2024-01-01'),
           (4, 'David Brown',   'david@example.com',   'ACTIVE',   '2024-01-02'),
           (5, 'Eve Davis',     'eve@example.com',     'INACTIVE', '2024-01-02');
