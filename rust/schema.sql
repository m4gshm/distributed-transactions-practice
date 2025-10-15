-- Database schema for Rust services (matching Java implementation)

-- Order status enum
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

-- Payment status enum  
CREATE TYPE payment_status AS ENUM (
    'CREATED',
    'HOLD',
    'INSUFFICIENT', 
    'PAID',
    'CANCELLED'
);

-- Reserve status enum
CREATE TYPE reserve_status AS ENUM (
    'CREATED',
    'INSUFFICIENT',
    'APPROVED',
    'RELEASED',
    'CANCELLED'
);

-- Delivery type enum
CREATE TYPE delivery_type AS ENUM (
    'PICKUP',
    'COURIER'
);

-- Orders table
CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ,
    customer_id VARCHAR(36) NOT NULL,
    payment_id VARCHAR(36),
    reserve_id VARCHAR(36),
    status order_status NOT NULL DEFAULT 'CREATING',
    payment_status payment_status,
    delivery_address TEXT NOT NULL,
    delivery_type delivery_type NOT NULL,
    delivery_date_time TIMESTAMPTZ,
    payment_transaction_id VARCHAR(255),
    reserve_transaction_id VARCHAR(255)
);

-- Order items table
CREATE TABLE IF NOT EXISTS order_items (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    item_id VARCHAR(36) NOT NULL,
    amount INTEGER NOT NULL CHECK (amount > 0),
    insufficient INTEGER,
    reserved BOOLEAN NOT NULL DEFAULT FALSE
);

-- Payments table
CREATE TABLE IF NOT EXISTS payments (
    id VARCHAR(36) PRIMARY KEY,
    external_ref VARCHAR(36) NOT NULL,
    client_id VARCHAR(36) NOT NULL,
    amount DECIMAL(10,2) NOT NULL CHECK (amount > 0),
    insufficient DECIMAL(10,2),
    status payment_status NOT NULL DEFAULT 'CREATED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ
);

-- Accounts table
CREATE TABLE IF NOT EXISTS accounts (
    client_id VARCHAR(36) PRIMARY KEY,
    amount DECIMAL(10,2) NOT NULL DEFAULT 0 CHECK (amount >= 0),
    locked DECIMAL(10,2) NOT NULL DEFAULT 0 CHECK (locked >= 0),
    updated_at TIMESTAMPTZ
);

-- Reserves table
CREATE TABLE IF NOT EXISTS reserves (
    id VARCHAR(36) PRIMARY KEY,
    external_ref VARCHAR(36) NOT NULL,
    status reserve_status NOT NULL DEFAULT 'CREATED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ
);

-- Reserve items table
CREATE TABLE IF NOT EXISTS reserve_items (
    id VARCHAR(36) PRIMARY KEY,
    reserve_id VARCHAR(36) NOT NULL REFERENCES reserves(id) ON DELETE CASCADE,
    item_id VARCHAR(36) NOT NULL,
    amount INTEGER NOT NULL CHECK (amount > 0),
    insufficient INTEGER,
    reserved BOOLEAN NOT NULL DEFAULT FALSE
);

-- Warehouse items table
CREATE TABLE IF NOT EXISTS warehouse_items (
    id VARCHAR(36) PRIMARY KEY,
    amount INTEGER NOT NULL DEFAULT 0 CHECK (amount >= 0),
    reserved INTEGER NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    updated_at TIMESTAMPTZ,
    CONSTRAINT check_reserved_not_exceeds_amount CHECK (reserved <= amount)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_orders_customer_id ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at);

CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_item_id ON order_items(item_id);

CREATE INDEX IF NOT EXISTS idx_payments_client_id ON payments(client_id);
CREATE INDEX IF NOT EXISTS idx_payments_external_ref ON payments(external_ref);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);

CREATE INDEX IF NOT EXISTS idx_reserve_items_reserve_id ON reserve_items(reserve_id);
CREATE INDEX IF NOT EXISTS idx_reserve_items_item_id ON reserve_items(item_id);

CREATE INDEX IF NOT EXISTS idx_reserves_external_ref ON reserves(external_ref);
CREATE INDEX IF NOT EXISTS idx_reserves_status ON reserves(status);

-- Sample data for testing
INSERT INTO accounts (client_id, amount, locked) VALUES
    ('11111111-1111-1111-1111-111111111111', 1000.00, 0.00),
    ('22222222-2222-2222-2222-222222222222', 2000.00, 0.00),
    ('33333333-3333-3333-3333-333333333333', 500.00, 0.00)
ON CONFLICT (client_id) DO NOTHING;

INSERT INTO warehouse_items (id, amount, reserved) VALUES
    ('item-1', 100, 0),
    ('item-2', 200, 0),
    ('item-3', 50, 0),
    ('item-4', 300, 0),
    ('item-5', 75, 0)
ON CONFLICT (id) DO NOTHING;