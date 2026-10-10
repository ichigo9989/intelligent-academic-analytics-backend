package academic_analytics_backend.controller;

import academic_analytics_backend.dto.AnalyticsSummary;
import academic_analytics_backend.service.AnalyticsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@PreAuthorize("hasAnyRole('ADMIN', 'FACULTY')")
public class AnalyticsController {
    private final AnalyticsService analytics;
    public AnalyticsController(AnalyticsService analytics) { this.analytics = analytics; }
    @GetMapping("/summary") public AnalyticsSummary summary() { return analytics.summary(); }
}
