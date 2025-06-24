package com.coherentsolutions.l09clientmcp.production.controller;

import com.coherentsolutions.l09clientmcp.production.config.ProductionConfig;
import com.coherentsolutions.l09clientmcp.production.monitoring.McpMetricsCollector;
import com.coherentsolutions.l09clientmcp.production.ratelimit.McpRateLimitService;
import com.coherentsolutions.l09clientmcp.production.retry.McpRetryService;
import com.coherentsolutions.l09clientmcp.production.service.ProductionMcpClientService;
import com.coherentsolutions.l09clientmcp.production.tracing.McpTracingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * Production monitoring and management endpoints.
 * 
 * This controller provides specialized endpoints for production monitoring,
 * debugging, and operational management of the MCP system beyond what's
 * available through standard Actuator endpoints.
 * 
 * Educational Focus:
 * - Production monitoring endpoint design
 * - Operational management APIs
 * - Performance debugging endpoints
 * - Production troubleshooting tools
 */
@RestController
@RequestMapping("/api/production")
@ConditionalOnProperty(
    name = "spring.ai.mcp.production.enabled", 
    havingValue = "true"
)
@RequiredArgsConstructor
@Slf4j
public class ProductionMonitoringController {
    
    private final ProductionMcpClientService productionClient;
    private final ProductionConfig config;
    private final McpMetricsCollector metricsCollector;
    private final McpTracingService tracingService;
    private final McpRetryService retryService;
    private final McpRateLimitService rateLimitService;
    
    /**
     * Get comprehensive production status.
     */
    @GetMapping("/status")
    public ResponseEntity<ProductionStatusResponse> getStatus() {
        log.debug("Production status requested");
        
        ProductionMcpClientService.ProductionStatistics stats = productionClient.getProductionStatistics();
        ProductionMcpClientService.ProductionHealthCheck health = productionClient.performHealthCheck();
        
        ProductionStatusResponse response = new ProductionStatusResponse(
                stats.environment(),
                health.overallHealthy(),
                config.isEnabled(),
                stats,
                health,
                Instant.now()
        );
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get detailed metrics information.
     */
    @GetMapping("/metrics")
    public ResponseEntity<McpMetricsCollector.MetricsSummary> getMetrics() {
        log.debug("Production metrics requested");
        return ResponseEntity.ok(metricsCollector.getCurrentMetrics());
    }
    
    /**
     * Get tracing status and configuration.
     */
    @GetMapping("/tracing")
    public ResponseEntity<TracingInfoResponse> getTracingInfo() {
        log.debug("Tracing information requested");
        
        McpTracingService.TracingStatus status = tracingService.getTracingStatus();
        
        TracingInfoResponse response = new TracingInfoResponse(
                status,
                config.getTracing().getSamplingRate(),
                config.getTracing().getExporterType(),
                config.getTracing().getExporterEndpoint(),
                config.getTracing().getBaggageFields()
        );
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get rate limiting status.
     */
    @GetMapping("/rate-limits")
    public ResponseEntity<RateLimitInfoResponse> getRateLimitInfo() {
        log.debug("Rate limit information requested");
        
        McpRateLimitService.RateLimitStatistics stats = rateLimitService.getStatistics();
        
        RateLimitInfoResponse response = new RateLimitInfoResponse(
                config.getRateLimit().isEnabled(),
                config.getRateLimit().getGlobalRequestsPerMinute(),
                config.getRateLimit().getServerRequestsPerMinute(),
                config.getRateLimit().getToolRequestsPerMinute(),
                config.getRateLimit().getUserRequestsPerMinute(),
                config.getRateLimit().isAdaptive(),
                stats
        );
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get retry configuration and statistics.
     */
    @GetMapping("/retry")
    public ResponseEntity<RetryInfoResponse> getRetryInfo() {
        log.debug("Retry information requested");
        
        McpRetryService.RetryStatistics stats = retryService.getRetryStatistics();
        ProductionConfig.RetryConfig retryConfig = config.getRetry();
        
        RetryInfoResponse response = new RetryInfoResponse(
                retryConfig.isEnabled(),
                retryConfig.getDefaultPolicy(),
                retryConfig.getToolPolicies(),
                stats
        );
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get configuration summary.
     */
    @GetMapping("/config")
    public ResponseEntity<ConfigurationSummary> getConfiguration() {
        log.debug("Configuration summary requested");
        
        ConfigurationSummary summary = new ConfigurationSummary(
                config.isEnabled(),
                config.getEnvironment(),
                config.isDevelopment(),
                config.isStaging(),
                config.isProduction(),
                Map.of(
                        "monitoring", config.getMonitoring().isEnabled(),
                        "tracing", config.getTracing().isEnabled(),
                        "retry", config.getRetry().isEnabled(),
                        "rateLimit", config.getRateLimit().isEnabled(),
                        "security", config.getSecurity().isEnabled(),
                        "cache", config.getPerformance().getCache().isEnabled(),
                        "audit", config.getAudit().isEnabled()
                )
        );
        
        return ResponseEntity.ok(summary);
    }
    
    /**
     * Force health check on all subsystems.
     */
    @PostMapping("/health-check")
    public ResponseEntity<ProductionMcpClientService.ProductionHealthCheck> forceHealthCheck() {
        log.info("Manual health check requested");
        
        ProductionMcpClientService.ProductionHealthCheck result = productionClient.performHealthCheck();
        
        if (result.overallHealthy()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(503).body(result); // Service Unavailable
        }
    }
    
    /**
     * Get rate limit status for specific identifiers.
     */
    @GetMapping("/rate-limits/{serverId}/{toolName}")
    public ResponseEntity<McpRateLimitService.RateLimitStatus> getRateLimitStatus(
            @PathVariable String serverId,
            @PathVariable String toolName,
            @RequestParam(required = false) String userId) {
        
        log.debug("Rate limit status requested for server={}, tool={}, user={}", serverId, toolName, userId);
        
        McpRateLimitService.RateLimitStatus status = 
                rateLimitService.getRateLimitStatus(serverId, toolName, userId);
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * Test endpoint for debugging (development only).
     */
    @PostMapping("/test/circuit-breaker")
    public ResponseEntity<String> testCircuitBreaker(@RequestParam String serverId) {
        if (config.isProduction()) {
            return ResponseEntity.status(403).body("Test endpoints disabled in production");
        }
        
        log.info("Circuit breaker test requested for server: {}", serverId);
        
        // This would trigger circuit breaker testing in a real implementation
        return ResponseEntity.ok("Circuit breaker test initiated for server: " + serverId);
    }
    
    /**
     * Reset metrics (development only).
     */
    @PostMapping("/reset/metrics")
    public ResponseEntity<String> resetMetrics() {
        if (config.isProduction()) {
            return ResponseEntity.status(403).body("Reset operations disabled in production");
        }
        
        log.info("Metrics reset requested");
        
        // In a real implementation, this would reset metrics counters
        return ResponseEntity.ok("Metrics reset completed");
    }
    
    // Response DTOs
    
    public record ProductionStatusResponse(
            String environment,
            boolean healthy,
            boolean enabled,
            ProductionMcpClientService.ProductionStatistics statistics,
            ProductionMcpClientService.ProductionHealthCheck healthCheck,
            Instant timestamp
    ) {}
    
    public record TracingInfoResponse(
            McpTracingService.TracingStatus status,
            double samplingRate,
            String exporterType,
            String exporterEndpoint,
            java.util.List<String> baggageFields
    ) {}
    
    public record RateLimitInfoResponse(
            boolean enabled,
            int globalRequestsPerMinute,
            int serverRequestsPerMinute,
            int toolRequestsPerMinute,
            int userRequestsPerMinute,
            boolean adaptive,
            McpRateLimitService.RateLimitStatistics statistics
    ) {}
    
    public record RetryInfoResponse(
            boolean enabled,
            ProductionConfig.RetryConfig.RetryPolicyConfig defaultPolicy,
            Map<String, ProductionConfig.RetryConfig.RetryPolicyConfig> toolPolicies,
            McpRetryService.RetryStatistics statistics
    ) {}
    
    public record ConfigurationSummary(
            boolean enabled,
            String environment,
            boolean isDevelopment,
            boolean isStaging,
            boolean isProduction,
            Map<String, Boolean> features
    ) {}
}