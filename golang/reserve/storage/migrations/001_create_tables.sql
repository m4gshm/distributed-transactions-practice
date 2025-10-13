-- +goose Up
-- +goose StatementBegin
-- 1️⃣ warehouse_item
CREATE TABLE IF NOT EXISTS
  warehouse_item (
    id text NOT NULL PRIMARY KEY,
    amount int4 NOT NULL,
    reserved int4 NOT NULL DEFAULT 0,
    unit_cost float8 NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
  );


-- 2️⃣ reserve
DO $$
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'reserve_status') THEN 
        CREATE TYPE reserve_status AS ENUM (
          'CREATED',
          'APPROVED',
          'RELEASED',
          'INSUFFICIENT',
          'CANCELLED'
        );
    END IF;
END$$;

CREATE TABLE IF NOT EXISTS
  reserve (
    id text NOT NULL PRIMARY KEY,
    external_ref text,
    status reserve_status NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
  );


-- 3️⃣ reserve_item
CREATE TABLE IF NOT EXISTS
  reserve_item (
    id text NOT NULL,
    reserve_id text NOT NULL,
    amount int4 NOT NULL,
    insufficient int4 NULL,
    reserved bool,
    PRIMARY KEY (reserve_id, id),
    CONSTRAINT reserve_item_reserve_id_fk FOREIGN KEY (reserve_id) REFERENCES reserve(id),
    CONSTRAINT reserve_item_id_fk FOREIGN KEY (id) REFERENCES warehouse_item(id)
  );
-- +goose StatementEnd

-- +goose Down
-- +goose StatementBegin
DROP TABLE IF EXISTS reserve_item;
DROP TABLE IF EXISTS reserve;
DROP TYPE IF EXISTS reserve_status;
DROP TABLE IF EXISTS warehouse_item;
-- +goose StatementEnd