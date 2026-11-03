package com.associago.access;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice(assignableTypes = SuiteAccessController.class)
public class SuiteAccessProblemHandler {
    @ExceptionHandler(SuiteAccessException.class)
    public ResponseEntity<?> handle(SuiteAccessException error) {
        return ResponseEntity.status(error.status()).body(Map.of("code", error.code(), "message", error.getMessage()));
    }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<?> conflict() {
        return ResponseEntity.status(409).body(Map.of("code", "ACCESS_VERSION_CONFLICT", "message", "Accessi modificati nel frattempo. Ricarica prima di salvare."));
    }
}
