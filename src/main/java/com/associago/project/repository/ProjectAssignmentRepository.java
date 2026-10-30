package com.associago.project.repository;

import com.associago.project.ProjectAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProjectAssignmentRepository extends JpaRepository<ProjectAssignment, Long> {
    List<ProjectAssignment> findByProjectId(Long projectId);
    Optional<ProjectAssignment> findByProjectIdAndPersonTypeAndPersonId(Long projectId, String personType, Long personId);
    long countByProjectId(Long projectId);
}
