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

CREATE TYPE delivery_type AS ENUM (
    'PICKUP', 
    'COURIER'
);

CREATE TABLE orders (
    id TEXT PRIMARY KEY NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    status order_status NOT NULL,
    customer_id TEXT NOT NULL,
    reserve_id TEXT,
    payment_id TEXT,
    payment_transaction_id TEXT,
    reserve_transaction_id TEXT
);

CREATE TABLE item (
    id TEXT PRIMARY KEY NOT NULL,
    order_id TEXT NOT NULL,
    amount INT NOT NULL,
    CONSTRAINT item_orders_id_fk FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE TABLE delivery (
    order_id TEXT PRIMARY KEY NOT NULL,
    address TEXT NOT NULL,
    type delivery_type NOT NULL,
    CONSTRAINT delivery_orders_id_fk FOREIGN KEY (order_id) REFERENCES orders(id)
);