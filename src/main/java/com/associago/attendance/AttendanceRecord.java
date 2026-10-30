package com.associago.attendance;

import jakarta.persistence.*;
import java.time.LocalTime;

@Entity
@Table(name = "attendance_records")
public class AttendanceRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "session_id")
    private Long sessionId;
    
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "membership_id")
    private Long membershipId;
    
    private String status; // PRESENT, ABSENT, LATE, EXCUSED
    
    @Column(name = "check_in_time")
    private LocalTime checkInTime;

    private String notes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public Long getMembershipId() { return membershipId; }
    public void setMembershipId(Long membershipId) { this.membershipId = membershipId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalTime getCheckInTime() { return checkInTime; }
    public void setCheckInTime(LocalTime checkInTime) { this.checkInTime = checkInTime; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
