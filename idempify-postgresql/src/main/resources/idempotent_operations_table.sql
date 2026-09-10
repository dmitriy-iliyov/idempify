CREATE TABLE IF NOT EXISTS idempotent_operations(
    idempotency_key UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    is_first_attempt BOOLEAN NOT NULL,
    result TEXT,
    response TEXT,
    fingerprint VARCHAR(255),
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);