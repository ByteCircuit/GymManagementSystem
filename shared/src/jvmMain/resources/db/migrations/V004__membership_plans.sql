-- V004: configurable membership plans. Each plan has a name, a duration rule and a price.
CREATE TABLE membership_plans (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL,
    duration    TEXT    NOT NULL CHECK (duration IN ('MONTHLY','DAILY')),
    price_cents INTEGER NOT NULL CHECK (price_cents > 0),
    active      INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0,1)),
    created_at  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%S','now','localtime'))
);

ALTER TABLE membership_records ADD COLUMN plan_id INTEGER REFERENCES membership_plans(id);

ALTER TABLE membership_records ADD COLUMN plan_name TEXT;