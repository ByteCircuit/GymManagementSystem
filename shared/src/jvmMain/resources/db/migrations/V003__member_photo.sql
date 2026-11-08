-- V003: optional member profile photo, stored in the database so backups include it.
ALTER TABLE members ADD COLUMN photo BLOB;