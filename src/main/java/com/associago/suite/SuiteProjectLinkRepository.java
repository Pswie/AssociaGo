package com.associago.suite;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SuiteProjectLinkRepository extends JpaRepository<SuiteProjectLink, String> {
    Optional<SuiteProjectLink> findByProjectId(Long projectId);
}
