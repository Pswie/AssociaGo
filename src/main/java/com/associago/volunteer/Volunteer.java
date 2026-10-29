package com.associago.volunteer;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Una persona iscritta al registro dei volontari — art. 17 c.1 D.Lgs. 117/2017.
 *
 * <p>{@code startedAt} e {@code endedAt} non sono due date in più: sono ciò che
 * distingue un registro da un elenco. {@code status} dice com'è la situazione
 * adesso; il registro deve saper rispondere a «chi era volontario il 14 marzo»,
 * ed è la prima domanda che viene posta dopo un infortunio.
 */
@Entity
@Table(name = "volunteers")
public class Volunteer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private Long memberId;

    /** Da quando svolge attività di volontariato. */
    @Column(name = "started_at")
    private LocalDate startedAt;

    /** Quando ha cessato. {@code null} finché è in attività. */
    @Column(name = "ended_at")
    private LocalDate endedAt;

    private String skills;
    private String availability;
    private String status; // ACTIVE, INACTIVE
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Se risultava volontario nel giorno indicato.
     *
     * <p>Una data di inizio assente conta come «da sempre»: sono le righe
     * scritte prima che il registro avesse le date, e escluderle direbbe che
     * quelle persone non erano volontarie — che è falso e peggio di impreciso.
     */
    public boolean isAttivoIl(LocalDate giorno) {
        if (giorno == null) return false;
        if (startedAt != null && giorno.isBefore(startedAt)) return false;
        return endedAt == null || !giorno.isAfter(endedAt);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public LocalDate getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDate startedAt) { this.startedAt = startedAt; }
    public LocalDate getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDate endedAt) { this.endedAt = endedAt; }
    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }
    public String getAvailability() { return availability; }
    public void setAvailability(String availability) { this.availability = availability; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
