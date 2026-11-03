package com.associago.stats;

import com.associago.stats.dto.ActivityStatsDTO;
import com.associago.stats.dto.AssemblyStatsDTO;
import com.associago.stats.dto.FinancialStatsDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import com.associago.finance.FinancialAccess;
import com.associago.access.SuiteAssociationScope;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/stats", "/api/v1/stats"})
public class StatsController {

    private final StatsService statsService;
    private final DashboardController dashboard;
    private final FinancialAccess financial;
    private final Environment environment;

    public StatsController(StatsService statsService) {
        this(statsService, null, null, null);
    }

    @Autowired
    public StatsController(StatsService statsService, DashboardController dashboard,
                           FinancialAccess financial, Environment environment) {
        this.statsService = statsService;
        this.dashboard = dashboard;
        this.financial = financial;
        this.environment = environment;
    }

    @GetMapping("/general")
    public ResponseEntity<Map<String, Object>> getGeneralStats(Authentication actor) {
        if (central()) return dashboard.getDashboardStats(null, actor);
        return ResponseEntity.ok(statsService.getGeneralStats());
    }

    @GetMapping("/activities/{id}")
    public ResponseEntity<ActivityStatsDTO> getActivityStats(@PathVariable Long id) {
        return ResponseEntity.ok(statsService.getActivityStats(id));
    }

    @GetMapping("/assemblies/{id}")
    public ResponseEntity<AssemblyStatsDTO> getAssemblyStats(@PathVariable Long id) {
        return ResponseEntity.ok(statsService.getAssemblyStats(id));
    }

    @GetMapping("/financial")
    public ResponseEntity<FinancialStatsDTO> getFinancialStats(@RequestParam Long associationId, @RequestParam int year,
                                                             Authentication actor) {
        if (central()) financial.readAssociation(actor, null, associationId);
        return ResponseEntity.ok(statsService.getFinancialStats(associationId, year));
    }

    private boolean central() { return environment != null && SuiteAssociationScope.enabled(environment); }
}
