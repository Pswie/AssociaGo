package com.associago.suite;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class SuiteExceptionHandler {
    @ExceptionHandler(SuiteException.class)
    public ResponseEntity<Map<String, String>> handle(SuiteException error) {
        return ResponseEntity.status(error.getStatus()).body(Map.of("code", error.getCode(), "message", error.getMessage()));
    }

    @ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class,
            org.springframework.dao.CannotAcquireLockException.class})
    public ResponseEntity<Map<String, String>> concurrentChange(Exception error) {
        return ResponseEntity.status(409).body(Map.of("code", "CONCURRENT_CHANGE", "message",
                "Un'altra richiesta sta aggiornando questi dati. Ricaricare e riprovare."));
    }
}
