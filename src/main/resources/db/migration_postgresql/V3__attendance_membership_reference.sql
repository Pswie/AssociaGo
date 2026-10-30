-- Add a separate participant namespace; historical Member references are unchanged.
ALTER TABLE associago.attendance_records ADD COLUMN membership_id bigint;
ALTER TABLE associago.attendance_records ADD CONSTRAINT fk_attendance_membership
    FOREIGN KEY (membership_id) REFERENCES associago.user_associations(id);
CREATE UNIQUE INDEX ux_attendance_session_membership
    ON associago.attendance_records(session_id, membership_id);
