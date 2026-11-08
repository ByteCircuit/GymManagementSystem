-- V002: students are verified by staff. A certificate of registration is not required and the
-- student ID is optional, so document_type is dropped and an optional id_number is added.
CREATE TABLE student_verifications_new (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    member_id   INTEGER NOT NULL REFERENCES members(id),
    school_name TEXT,
    id_number   TEXT,
    verified    INTEGER NOT NULL DEFAULT 1 CHECK (verified IN (0,1)),
    created_at  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%S','now','localtime'))
);

INSERT INTO student_verifications_new(id, member_id, school_name, verified, created_at)
SELECT id, member_id, school_name, verified, created_at FROM student_verifications;

DROP TABLE student_verifications;

ALTER TABLE student_verifications_new RENAME TO student_verifications;

CREATE INDEX idx_student_ver_member ON student_verifications(member_id);