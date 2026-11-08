-- V001: initial schema. Money is INTEGER centavos. Dates are ISO-8601 TEXT in gym-local time.
-- Statements end with a semicolon at end of line. Do not use triggers in this file.

CREATE TABLE settings (
    key        TEXT PRIMARY KEY,
    value      TEXT NOT NULL,
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%S','now','localtime'))
);

-- Single superadmin. The CHECK keeps this to exactly one row.
CREATE TABLE admin_account (
    id             INTEGER PRIMARY KEY CHECK (id = 1),
    username       TEXT    NOT NULL CHECK (length(trim(username)) > 0),
    password_hash  TEXT    NOT NULL,
    salt           TEXT    NOT NULL,
    iterations     INTEGER NOT NULL CHECK (iterations > 0),
    created_at     TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%S','now','localtime'))
);

CREATE TABLE members (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    member_number      TEXT    NOT NULL UNIQUE,
    full_name          TEXT    NOT NULL CHECK (length(trim(full_name)) > 0),
    contact_number     TEXT    NOT NULL CHECK (length(trim(contact_number)) > 0),
    email              TEXT,
    date_of_birth      TEXT,
    address            TEXT,
    emergency_contact  TEXT,
    photo_path         TEXT,
    notes              TEXT,
    registered_at      TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%S','now','localtime'))
);

CREATE INDEX idx_members_name    ON members(full_name COLLATE NOCASE);
CREATE INDEX idx_members_contact ON members(contact_number);

CREATE TABLE student_verifications (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    member_id      INTEGER NOT NULL REFERENCES members(id),
    document_type  TEXT    NOT NULL CHECK (document_type IN ('STUDENT_ID','COR')),
    school_name    TEXT,
    document_path  TEXT,
    verified       INTEGER NOT NULL DEFAULT 0 CHECK (verified IN (0,1)),
    valid_until    TEXT,
    created_at     TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%S','now','localtime'))
);

CREATE INDEX idx_student_ver_member ON student_verifications(member_id);

CREATE TABLE transactions (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    reference        TEXT    NOT NULL UNIQUE,
    idempotency_key  TEXT    UNIQUE,
    type             TEXT    NOT NULL CHECK (type IN
                       ('MONTHLY_MEMBERSHIP','DAILY_MEMBERSHIP','MEMBERSHIP_RENEWAL','WALK_IN','PRODUCT_SALE')),
    member_id        INTEGER REFERENCES members(id),
    subtotal_cents   INTEGER NOT NULL CHECK (subtotal_cents >= 0),
    discount_cents   INTEGER NOT NULL DEFAULT 0 CHECK (discount_cents >= 0),
    total_cents      INTEGER NOT NULL CHECK (total_cents >= 0),
    status           TEXT    NOT NULL DEFAULT 'COMPLETED' CHECK (status IN ('COMPLETED','PENDING','CANCELLED')),
    created_at       TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%S','now','localtime')),
    CHECK (total_cents = subtotal_cents - discount_cents)
);

CREATE INDEX idx_tx_created ON transactions(created_at);
CREATE INDEX idx_tx_member  ON transactions(member_id);
CREATE INDEX idx_tx_type    ON transactions(type, status);

CREATE TABLE payments (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    transaction_id  INTEGER NOT NULL REFERENCES transactions(id),
    method          TEXT    NOT NULL,
    amount_cents    INTEGER NOT NULL CHECK (amount_cents > 0),
    reference_note  TEXT,
    paid_at         TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%S','now','localtime'))
);

CREATE INDEX idx_payments_tx ON payments(transaction_id);

CREATE TABLE membership_records (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    member_id       INTEGER NOT NULL REFERENCES members(id),
    type            TEXT    NOT NULL CHECK (type IN ('MONTHLY','DAILY')),
    start_date      TEXT    NOT NULL,
    end_date        TEXT    NOT NULL,
    transaction_id  INTEGER REFERENCES transactions(id),
    renewal_of_id   INTEGER REFERENCES membership_records(id),
    CHECK (end_date >= start_date)
);

CREATE INDEX idx_membership_member ON membership_records(member_id, end_date);

CREATE TABLE nfc_cards (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    uid             TEXT    NOT NULL,
    member_id       INTEGER NOT NULL REFERENCES members(id),
    status          TEXT    NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','REPLACED','DEACTIVATED')),
    assigned_at     TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%S','now','localtime')),
    deactivated_at  TEXT,
    replaced_by_id  INTEGER REFERENCES nfc_cards(id)
);

CREATE UNIQUE INDEX uq_nfc_active_uid    ON nfc_cards(uid) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uq_nfc_active_member ON nfc_cards(member_id) WHERE status = 'ACTIVE';

-- Time-in only. No check-out column by design.
CREATE TABLE attendance (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    member_id  INTEGER NOT NULL REFERENCES members(id),
    card_id    INTEGER REFERENCES nfc_cards(id),
    time_in    TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%S','now','localtime')),
    source     TEXT    NOT NULL DEFAULT 'NFC' CHECK (source IN ('NFC','MANUAL'))
);

CREATE INDEX idx_attendance_time   ON attendance(time_in);
CREATE INDEX idx_attendance_member ON attendance(member_id, time_in);

CREATE TABLE walk_in_visits (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    transaction_id  INTEGER NOT NULL UNIQUE REFERENCES transactions(id),
    visitor_name    TEXT,
    entered_at      TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%S','now','localtime'))
);

CREATE TABLE product_categories (
    id    INTEGER PRIMARY KEY AUTOINCREMENT,
    name  TEXT NOT NULL UNIQUE COLLATE NOCASE
);

CREATE TABLE products (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    sku               TEXT    NOT NULL UNIQUE,
    name              TEXT    NOT NULL CHECK (length(trim(name)) > 0),
    category_id       INTEGER REFERENCES product_categories(id),
    price_cents       INTEGER NOT NULL CHECK (price_cents >= 0),
    cost_cents        INTEGER CHECK (cost_cents IS NULL OR cost_cents >= 0),
    stock_qty         INTEGER NOT NULL DEFAULT 0 CHECK (stock_qty >= 0),
    low_stock_level   INTEGER,
    is_active         INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0,1)),
    created_at        TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%S','now','localtime'))
);

CREATE INDEX idx_products_name ON products(name COLLATE NOCASE);

CREATE TABLE transaction_items (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    transaction_id    INTEGER NOT NULL REFERENCES transactions(id),
    product_id        INTEGER REFERENCES products(id),
    description       TEXT    NOT NULL,
    quantity          INTEGER NOT NULL CHECK (quantity > 0),
    unit_price_cents  INTEGER NOT NULL CHECK (unit_price_cents >= 0),
    line_total_cents  INTEGER NOT NULL CHECK (line_total_cents = quantity * unit_price_cents)
);

CREATE INDEX idx_items_tx      ON transaction_items(transaction_id);
CREATE INDEX idx_items_product ON transaction_items(product_id);

CREATE TABLE inventory_movements (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id      INTEGER NOT NULL REFERENCES products(id),
    type            TEXT    NOT NULL CHECK (type IN ('STOCK_IN','SALE','ADJUSTMENT_OUT','INITIAL')),
    quantity_delta  INTEGER NOT NULL CHECK (quantity_delta <> 0),
    transaction_id  INTEGER REFERENCES transactions(id),
    note            TEXT,
    created_at      TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%S','now','localtime'))
);

CREATE INDEX idx_movements_product ON inventory_movements(product_id, created_at);

-- Defaults. Prices and the student discount are intentionally NOT seeded.
INSERT INTO settings(key, value) VALUES
    ('gym.name', 'TRIPLE 3 FITNESS AND MARTIAL ARTS GYM'),
    ('gym.contact', ''),
    ('gym.address', ''),
    ('locale.currency', 'PHP'),
    ('locale.timezone', 'Asia/Manila'),
    ('locale.date_format', 'MMM dd, yyyy'),
    ('locale.time_format', 'hh:mm a'),
    ('payment.methods', 'Cash'),
    ('inventory.low_stock_default', '5'),
    ('member.number_format', '%04dA'),
    ('receipt.footer', '');