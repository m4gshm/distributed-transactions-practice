-- +goose Up
-- +goose StatementBegin
-- 1️⃣ Create table "account"
CREATE TABLE IF NOT EXISTS account (
    client_id TEXT PRIMARY KEY NOT NULL,
    amount FLOAT8 NOT NULL,
    locked FLOAT8 NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE account ADD CONSTRAINT lock_leq_amount CHECK (locked <= amount);

-- 2️⃣ Create table "payment"
CREATE TYPE payment_status AS ENUM (
    'CREATED',
    'HOLD', 
    'INSUFFICIENT',
    'PAID',
    'CANCELLED'
);

CREATE TABLE IF NOT EXISTS payment (
    id TEXT PRIMARY KEY NOT NULL,
    external_ref TEXT NOT NULL,
    client_id TEXT NOT NULL REFERENCES account (client_id) ON UPDATE NO ACTION ON DELETE NO ACTION,
    amount FLOAT8 NOT NULL,
    insufficient FLOAT8,
    status payment_status NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ
);

-- 3️⃣ Create index on payment.client_id
CREATE INDEX payment_client_id ON payment (client_id);
-- +goose StatementEnd

-- +goose Down
-- +goose StatementBegin
DROP INDEX IF EXISTS payment_client_id;
DROP TABLE IF EXISTS payment;
DROP TYPE IF EXISTS payment_status;
DROP TABLE IF EXISTS account;
-- +goose StatementEnd