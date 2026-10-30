package com.associago.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "project_person_assignments", uniqueConstraints = @UniqueConstraint(
        columnNames = {"project_id", "person_type", "person_id"}))
public class ProjectAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false)
    private Long projectId;
    @Column(name = "person_type", nullable = false, length = 16)
    private String personType;
    @Column(name = "person_id", nullable = false)
    private Long personId;

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId = value; }
    public String getPersonType() { return personType; }
    public void setPersonType(String value) { personType = value; }
    public Long getPersonId() { return personId; }
    public void setPersonId(Long value) { personId = value; }
}
