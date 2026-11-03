package com.associago.access;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "suite_access_users")
public class SuiteAccessUser {
    @Id @Column(length = 128) private String subject;
    @Column(name = "display_name", nullable = false, length = 200) private String displayName;
    @Column(name = "first_name", length = 100) private String firstName;
    @Column(name = "last_name", length = 100) private String lastName;
    @Column(length = 254) private String email;
    @Column(nullable = false, length = 16) private String status = "pending";
    @Column(name = "applications_json", nullable = false, columnDefinition = "TEXT") private String applicationsJson = "{}";
    @Version @Column(nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    protected SuiteAccessUser() { }
    SuiteAccessUser(String subject) {
        this.subject = subject;
        this.displayName = subject;
        this.createdAt = this.updatedAt = LocalDateTime.now(java.time.Clock.systemUTC());
    }
    public String getSubject() { return subject; }
    public String getDisplayName() { return displayName; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getStatus() { return status; }
    public String getApplicationsJson() { return applicationsJson; }
    public long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    void profile(String display, String first, String last, String email) {
        this.displayName = display; this.firstName = first; this.lastName = last; this.email = email;
        this.updatedAt = LocalDateTime.now(java.time.Clock.systemUTC());
    }
    void grant(String status, String applications) {
        this.status = status; this.applicationsJson = applications;
        this.updatedAt = LocalDateTime.now(java.time.Clock.systemUTC());
    }
}
