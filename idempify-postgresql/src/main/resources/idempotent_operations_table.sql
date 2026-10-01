CREATE TABLE IF NOT EXISTS idempotent_operations(
    idempotency_key UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    is_first_attempt BOOLEAN NOT NULL,
    result TEXT,
    exception_class_name TEXT,
    response TEXT,
    fingerprint VARCHAR(255),
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    lock_token UUID,
    lock_until TIMESTAMPTZ
);