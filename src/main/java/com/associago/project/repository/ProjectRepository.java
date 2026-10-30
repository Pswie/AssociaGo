package com.associago.project.repository;

import com.associago.project.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByAssociationIdOrderByDeadlineAsc(Long associationId);

    List<Project> findByAssociationIdAndStatus(Long associationId, String status);

    // Portable write lock: SQLite serializes writers; MySQL locks the matched row.
    @Modifying(flushAutomatically=true)
    @Query("UPDATE Project p SET p.name=p.name WHERE p.id=:id")
    int lockForFinancialWrite(@Param("id") Long id);

    @Modifying(flushAutomatically=true,clearAutomatically=true)
    @Query("DELETE FROM Project p WHERE p.id=:id AND NOT EXISTS (SELECT t.id FROM Transaction t WHERE t.projectId=p.id) AND NOT EXISTS (SELECT l.suiteProjectId FROM SuiteProjectLink l WHERE l.projectId=p.id) AND NOT EXISTS (SELECT a.id FROM ProjectAssignment a WHERE a.projectId=p.id) AND NOT EXISTS (SELECT s.id FROM VolunteerShift s WHERE s.projectId=p.id)")
    int deleteIfUnreferenced(@Param("id") Long id);
}
