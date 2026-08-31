package com.ordevia.aidev.api;

import com.ordevia.aidev.telemetry.application.DeliveryAnalyticsService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/analytics")
public class DeliveryAnalyticsController {
    private final DeliveryAnalyticsService analytics;
    public DeliveryAnalyticsController(DeliveryAnalyticsService analytics){this.analytics=analytics;}

    @GetMapping("/projects/{projectId}/delivery")
    public DeliveryAnalyticsService.ProjectDeliverySummary project(@PathVariable UUID projectId){return analytics.project(projectId);}
}
