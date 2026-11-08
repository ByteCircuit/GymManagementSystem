-- V006: card taps that were refused (expired, no membership, card not assigned).
CREATE TABLE denied_scans (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    scanned_at TEXT    NOT NULL,
    card_uid   TEXT,
    member_id  INTEGER REFERENCES members(id),
    reason     TEXT    NOT NULL CHECK (reason IN ('EXPIRED','NO_MEMBERSHIP','UNKNOWN_CARD'))
);

CREATE INDEX idx_denied_time ON denied_scans(scanned_at);