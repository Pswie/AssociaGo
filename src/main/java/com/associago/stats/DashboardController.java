package com.associago.stats;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping({"/api/dashboard", "/api/v1/dashboard"})
public class DashboardController {

    private final StatsService statsService;

    public DashboardController(StatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * @param anno l'esercizio da riassumere; assente significa tutta la storia.
     *
     *             <p>Il parametro sta qui e non nel browser perché questo
     *             endpoint restituisce somme, non righe: un totale già
     *             calcolato non si filtra dopo.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats(
            @RequestParam(required = false) Integer anno) {
        return ResponseEntity.ok(statsService.getDashboardStats(anno));
    }
}
