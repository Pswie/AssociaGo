package com.associago.access;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuiteAccessUserRepository extends JpaRepository<SuiteAccessUser, String> {
    Page<SuiteAccessUser> findByDisplayNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrSubjectContainingIgnoreCase(
            String displayName, String email, String subject, Pageable pageable);
}
