-- V005: NFC cards, attendance (time-in only) and walk-in visits.
-- These three tables were created empty in V001 and nothing has used them yet, so they are recreated with the final layout.
DROP TABLE IF EXISTS walk_in_visits;

DROP TABLE IF EXISTS attendance;

DROP TABLE IF EXISTS nfc_cards;

CREATE TABLE nfc_cards (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    member_id      INTEGER NOT NULL REFERENCES members(id),
    uid            TEXT    NOT NULL,
    active         INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0,1)),
    assigned_at    TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%S','now','localtime')),
    deactivated_at TEXT
);

CREATE UNIQUE INDEX idx_nfc_active_uid ON nfc_cards(uid) WHERE active = 1;

CREATE INDEX idx_nfc_member ON nfc_cards(member_id);

CREATE TABLE attendance (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    member_id INTEGER NOT NULL REFERENCES members(id),
    time_in   TEXT    NOT NULL,
    source    TEXT    NOT NULL CHECK (source IN ('NFC','MANUAL')),
    card_uid  TEXT
);

CREATE INDEX idx_attendance_member ON attendance(member_id, time_in);

CREATE INDEX idx_attendance_time ON attendance(time_in);

CREATE TABLE walk_in_visits (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    transaction_id INTEGER NOT NULL REFERENCES transactions(id),
    visitor_name   TEXT,
    visited_at     TEXT    NOT NULL
);

CREATE INDEX idx_walkin_time ON walk_in_visits(visited_at);