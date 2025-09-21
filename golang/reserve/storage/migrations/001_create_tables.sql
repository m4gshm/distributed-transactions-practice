-- 1️⃣ warehouse_item
CREATE TABLE
  warehouse_item (
    id text NOT NULL PRIMARY KEY,
    amount int4 NOT NULL,
    reserved int4 NOT NULL DEFAULT 0,
    unit_cost float8 NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
  );


-- 2️⃣ reserve
CREATE TYPE reserve_status AS ENUM (
  'CREATED',
  'APPROVED',
  'RELEASED',
  'INSUFFICIENT',
  'CANCELLED'
);


CREATE TABLE
  reserve (
    id text NOT NULL PRIMARY KEY,
    external_ref text,
    status reserve_status NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
  );


-- 3️⃣ reserve_item
CREATE TABLE
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