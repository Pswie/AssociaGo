-- Multiple historical NULL membership references (including legacy duplicates) remain valid.
ALTER TABLE attendance_records ADD COLUMN membership_id bigint NULL;
ALTER TABLE attendance_records ADD CONSTRAINT fk_attendance_membership
    FOREIGN KEY (membership_id) REFERENCES user_associations(id);
CREATE UNIQUE INDEX ux_attendance_session_membership
    ON attendance_records(session_id, membership_id);
