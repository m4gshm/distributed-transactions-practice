-- Create Orders tables
CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    customer_id VARCHAR(255) NOT NULL,
    payment_id VARCHAR(255),
    reserve_id VARCHAR(255),
    status INTEGER NOT NULL DEFAULT 0,
    payment_status INTEGER,
    delivery_type INTEGER NOT NULL DEFAULT 0,
    delivery_date TIMESTAMP,
    delivery_address TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS order_items (
    id VARCHAR(255) PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    item_id VARCHAR(255) NOT NULL,
    amount INTEGER NOT NULL,
    reserved BOOLEAN NOT NULL DEFAULT FALSE
);

-- Create Payments tables
CREATE TABLE IF NOT EXISTS payments (
    id VARCHAR(255) PRIMARY KEY,
    external_ref VARCHAR(255) NOT NULL,
    client_id VARCHAR(255) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    insufficient DECIMAL(15,2),
    status INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS accounts (
    client_id VARCHAR(255) PRIMARY KEY,
    amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    locked DECIMAL(15,2) NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Create Reserve tables
CREATE TABLE IF NOT EXISTS reserves (
    id VARCHAR(255) PRIMARY KEY,
    external_ref VARCHAR(255) NOT NULL,
    status INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS reserve_items (
    id VARCHAR(255) PRIMARY KEY,
    reserve_id VARCHAR(255) NOT NULL REFERENCES reserves(id) ON DELETE CASCADE,
    item_id VARCHAR(255) NOT NULL,
    amount INTEGER NOT NULL,
    insufficient INTEGER,
    reserved BOOLEAN NOT NULL DEFAULT FALSE
);

-- Create Warehouse tables
CREATE TABLE IF NOT EXISTS warehouse_items (
    id VARCHAR(255) PRIMARY KEY,
    amount INTEGER NOT NULL DEFAULT 0,
    reserved INTEGER NOT NULL DEFAULT 0,
    unit_cost DECIMAL(15,2) NOT NULL DEFAULT 10.0,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_orders_customer_id ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_item_id ON order_items(item_id);

CREATE INDEX IF NOT EXISTS idx_payments_client_id ON payments(client_id);
CREATE INDEX IF NOT EXISTS idx_payments_external_ref ON payments(external_ref);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);

CREATE INDEX IF NOT EXISTS idx_reserves_external_ref ON reserves(external_ref);
CREATE INDEX IF NOT EXISTS idx_reserves_status ON reserves(status);
CREATE INDEX IF NOT EXISTS idx_reserve_items_reserve_id ON reserve_items(reserve_id);
CREATE INDEX IF NOT EXISTS idx_reserve_items_item_id ON reserve_items(item_id);

-- Insert some sample data
INSERT INTO accounts (client_id, amount) VALUES 
    ('customer-1', 1000.00),
    ('customer-2', 2000.00),
    ('customer-3', 500.00)
ON CONFLICT (client_id) DO NOTHING;

INSERT INTO warehouse_items (id, amount, unit_cost) VALUES 
    ('item-1', 100, 10.50),
    ('item-2', 200, 25.00),
    ('item-3', 50, 15.75),
    ('item-4', 300, 8.25)
ON CONFLICT (id) DO NOTHING;