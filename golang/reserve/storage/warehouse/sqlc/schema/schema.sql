CREATE TABLE IF NOT EXISTS
  warehouse_item (
    id text NOT NULL PRIMARY KEY,
    amount int4 NOT NULL,
    reserved int4 NOT NULL DEFAULT 0,
    unit_cost float8 NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
  );
