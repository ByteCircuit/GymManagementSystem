-- V007: product catalogue and stock movements for the inventory and point of sale screens.
-- The V001 products and inventory_movements tables were never used by the app, so they are replaced here.
DROP TABLE IF EXISTS inventory_movements;
DROP TABLE IF EXISTS products;

CREATE TABLE products (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            TEXT    NOT NULL,
    sku             TEXT    NOT NULL UNIQUE,
    category        TEXT    NOT NULL,
    price_cents     INTEGER NOT NULL CHECK (price_cents > 0),
    stock           INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0),
    low_stock_level INTEGER NOT NULL CHECK (low_stock_level >= 0),
    active          INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    created_at      TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%S','now','localtime'))
);

CREATE INDEX idx_products_category ON products(category);

CREATE TABLE inventory_movements (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id     INTEGER NOT NULL REFERENCES products(id),
    kind           TEXT    NOT NULL CHECK (kind IN ('INITIAL','RESTOCK','SALE','ADJUSTMENT')),
    change_qty     INTEGER NOT NULL CHECK (change_qty <> 0),
    stock_after    INTEGER NOT NULL CHECK (stock_after >= 0),
    note           TEXT,
    transaction_id INTEGER REFERENCES transactions(id),
    created_at     TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%S','now','localtime'))
);

CREATE INDEX idx_movements_product ON inventory_movements(product_id, id);