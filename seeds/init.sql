CREATE TABLE IF NOT EXISTS products (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS orders (
    id SERIAL PRIMARY KEY,
    customer_id INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL,
    items JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

INSERT INTO products (name, category, price)
VALUES
  ('Seed Keyboard', 'Electronics', 99.99),
  ('Seed Mouse', 'Electronics', 49.99),
  ('Seed Notebook', 'Stationery', 9.99)
ON CONFLICT DO NOTHING;
