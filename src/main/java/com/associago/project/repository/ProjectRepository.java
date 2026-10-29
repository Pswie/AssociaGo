package com.associago.project.repository;

import com.associago.project.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByAssociationIdOrderByDeadlineAsc(Long associationId);

    List<Project> findByAssociationIdAndStatus(Long associationId, String status);
}
