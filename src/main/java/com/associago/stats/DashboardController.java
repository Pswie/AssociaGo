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
    @org.springframework.beans.factory.annotation.Value("${SUITE_ACCESS_ENABLED:false}")
    private boolean suiteAccessEnabled;

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
            @RequestParam(required = false) Integer anno,
            org.springframework.security.core.Authentication actor) {
        Map<String, Object> stats = new java.util.LinkedHashMap<>(statsService.getDashboardStats(anno));
        if (suiteAccessEnabled) {
            if (!com.associago.access.SuiteAccessService.permits(actor, "associago.members.read")) stats.remove("members");
            if (!com.associago.access.SuiteAccessService.permits(actor, "associago.finance.read")) stats.remove("treasury");
            if (!com.associago.access.SuiteAccessService.permits(actor, "associago.events.read")) stats.remove("events");
            // The mixed activity feed contains payment and member details: require both grants.
            if (!com.associago.access.SuiteAccessService.permits(actor, "associago.members.read") ||
                    !com.associago.access.SuiteAccessService.permits(actor, "associago.finance.read")) stats.remove("recentActivity");
        }
        return ResponseEntity.ok(stats);
    }
}
