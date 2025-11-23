CREATE TYPE order_status AS ENUM (
  'CREATING',
  'CREATED',
  'APPROVING',
  'APPROVED',
  'RELEASING',
  'RELEASED',
  'INSUFFICIENT',
  'CANCELLING',
  'CANCELLED'
);


CREATE TYPE delivery_type AS ENUM ('PICKUP', 'COURIER');


CREATE TABLE
  IF NOT EXISTS orders (
    id TEXT PRIMARY KEY NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ,
    status order_status NOT NULL,
    customer_id TEXT NOT NULL,
    reserve_id TEXT,
    payment_id TEXT,
    payment_transaction_id TEXT,
    reserve_transaction_id TEXT
  );


CREATE TABLE
  IF NOT EXISTS item (
    id TEXT NOT NULL,
    order_id TEXT NOT NULL,
    amount INT NOT NULL,
    PRIMARY KEY (id, order_id),
    CONSTRAINT item_orders_id_fk FOREIGN KEY (order_id) REFERENCES orders(id)
  );


CREATE TABLE
  IF NOT EXISTS delivery (
    order_id TEXT PRIMARY KEY NOT NULL,
    address TEXT NOT NULL,
    type delivery_type NOT NULL,
    CONSTRAINT delivery_orders_id_fk FOREIGN KEY (order_id) REFERENCES orders(id)
  );