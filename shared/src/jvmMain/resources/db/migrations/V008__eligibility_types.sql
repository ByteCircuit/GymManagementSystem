-- V008: configurable eligibility types (Student, PWD, ...) for discount verification. Existing data is kept.
CREATE TABLE eligibility_types (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT    NOT NULL,
    active     INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0,1)),
    created_at TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%S','now','localtime'))
);

INSERT INTO eligibility_types(name) VALUES ('Student'), ('PWD');

ALTER TABLE student_verifications ADD COLUMN eligibility_type_id INTEGER REFERENCES eligibility_types(id);

ALTER TABLE student_verifications ADD COLUMN document BLOB;

UPDATE student_verifications SET eligibility_type_id = (SELECT id FROM eligibility_types WHERE name = 'Student');

ALTER TABLE membership_plans ADD COLUMN eligibility_type_id INTEGER REFERENCES eligibility_types(id);