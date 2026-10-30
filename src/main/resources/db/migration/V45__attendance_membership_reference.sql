-- V9 required member_id. Rebuild only this table to permit the new, explicit
-- membership namespace. Every historical ID/value, including duplicate Member
-- records, is copied unchanged. Startup creates a consistent backup before V45.
CREATE TABLE attendance_records_membership_upgrade (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id INTEGER NOT NULL,
    member_id INTEGER,
    membership_id INTEGER,
    status TEXT DEFAULT 'PRESENT',
    check_in_time TIME,
    notes TEXT,
    FOREIGN KEY (session_id) REFERENCES attendance_sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE,
    FOREIGN KEY (membership_id) REFERENCES user_associations(id)
);
INSERT INTO attendance_records_membership_upgrade (id, session_id, member_id, status, check_in_time, notes)
    SELECT id, session_id, member_id, status, check_in_time, notes FROM attendance_records;
-- Preserve the high-water mark even when the highest historical ID was deleted.
INSERT INTO sqlite_sequence (name, seq)
    SELECT 'attendance_records_membership_upgrade', seq FROM sqlite_sequence
    WHERE name = 'attendance_records'
      AND NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = 'attendance_records_membership_upgrade');
UPDATE sqlite_sequence SET seq = MAX(seq, COALESCE(
    (SELECT seq FROM sqlite_sequence WHERE name = 'attendance_records'), 0))
    WHERE name = 'attendance_records_membership_upgrade';
DROP TABLE attendance_records;
ALTER TABLE attendance_records_membership_upgrade RENAME TO attendance_records;
CREATE UNIQUE INDEX ux_attendance_session_membership ON attendance_records(session_id, membership_id);
