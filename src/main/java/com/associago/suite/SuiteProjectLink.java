package com.associago.suite;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "suite_project_links")
public class SuiteProjectLink {
    @Id
    @Column(name = "suite_project_id", length = 36)
    private String suiteProjectId;
    @Column(name = "organization_id", nullable = false, length = 36)
    private String organizationId;
    @Column(name = "project_id", nullable = false, unique = true)
    private Long projectId;
    @Column(name = "source_version", nullable = false)
    private Long sourceVersion = 0L;
    @Column(name = "source_fingerprint", length = 64)
    private String sourceFingerprint;
    @Column(name = "source_description", columnDefinition = "TEXT")
    private String sourceDescription;
    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    public String getSuiteProjectId() { return suiteProjectId; }
    public void setSuiteProjectId(String value) { suiteProjectId = value; }
    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String value) { organizationId = value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId = value; }
    public Long getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(Long value) { sourceVersion = value; }
    public String getSourceFingerprint() { return sourceFingerprint; }
    public void setSourceFingerprint(String value) { sourceFingerprint = value; }
    public String getSourceDescription() { return sourceDescription; }
    public void setSourceDescription(String value) { sourceDescription = value; }
}
