package com.associago.access;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "suite_access_audit")
public class SuiteAccessAudit {
    @Id @Column(length = 36) private String id;
    @Column(name = "actor_subject", nullable = false, length = 128) private String actorSubject;
    @Column(name = "target_subject", nullable = false, length = 128) private String targetSubject;
    @Column(name = "before_json", nullable = false, columnDefinition = "TEXT") private String beforeJson;
    @Column(name = "after_json", nullable = false, columnDefinition = "TEXT") private String afterJson;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    protected SuiteAccessAudit() { }
    SuiteAccessAudit(String actor, String target, String before, String after) {
        id = UUID.randomUUID().toString(); actorSubject = actor; targetSubject = target;
        beforeJson = before; afterJson = after; createdAt = LocalDateTime.now(java.time.Clock.systemUTC());
    }
}
