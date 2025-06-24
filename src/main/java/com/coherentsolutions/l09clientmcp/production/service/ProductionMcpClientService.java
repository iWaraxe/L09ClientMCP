package com.coherentsolutions.l09clientmcp.production.service;

import com.coherentsolutions.l09clientmcp.mcp.McpTool;
import com.coherentsolutions.l09clientmcp.mcp.McpToolResult;
import com.coherentsolutions.l09clientmcp.production.config.ProductionConfig;
import com.coherentsolutions.l09clientmcp.production.monitoring.McpMetricsCollector;
import com.coherentsolutions.l09clientmcp.production.ratelimit.McpRateLimitService;
import com.coherentsolutions.l09clientmcp.production.retry.McpRetryService;
import com.coherentsolutions.l09clientmcp.production.tracing.McpTracingService;
import com.coherentsolutions.l09clientmcp.service.McpClientService;
import com.coherentsolutions.l09clientmcp.service.MultiServerMcpClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Production-ready MCP client service with comprehensive enterprise features.
 * 
 * This service wraps the multi-server MCP client with production-grade capabilities
 * including monitoring, tracing, retry mechanisms, rate limiting, security,
 * and performance optimization.
 * 
 * Educational Focus:
 * - Production service patterns and enterprise integration
 * - Comprehensive error handling and resilience
 * - Monitoring and observability implementation
 * - Performance optimization techniques
 * - Security and compliance considerations
 */
@Service
@ConditionalOnProperty(
    name = "spring.ai.mcp.production.enabled", 
    havingValue = "true"
)
@RequiredArgsConstructor
@Slf4j
public class ProductionMcpClientService implements McpClientService {
    
    private final MultiServerMcpClientService multiServerClient;
    private final ProductionConfig config;
    private final McpMetricsCollector metricsCollector;
    private final McpTracingService tracingService;
    private final McpRetryService retryService;
    private final McpRateLimitService rateLimitService;
    
    // Performance tracking
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final AtomicLong rateLimitedRequests = new AtomicLong(0);
    
    @PostConstruct
    public void initialize() {
        if (!config.isEnabled()) {
            log.info("Production MCP client is disabled");
            return;
        }
        
        log.info("Initializing production MCP client for environment: {}", config.getEnvironment());
        
        // Validate configuration
        config.validateConfiguration();
        
        // Initialize metrics
        initializeCustomMetrics();
        
        // Log production features status
        logProductionFeaturesStatus();
        
        log.info("Production MCP client initialized successfully");
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down production MCP client");
        
        // Log final statistics
        logFinalStatistics();
        
        log.info("Production MCP client shutdown complete");
    }
    
    @Override
    public boolean isEnabled() {
        return config.isEnabled() && multiServerClient.isEnabled();
    }
    
    @Override
    public boolean isHealthy() {
        if (!isEnabled()) {
            return true; // Graceful degradation
        }
        
        // Enhanced health check with production metrics
        return multiServerClient.isHealthy() && 
               isRateLimitHealthy() && 
               isPerformanceHealthy();
    }
    
    @Override
    public String getStatus() {
        if (!isEnabled()) {
            return "Production MCP client is disabled";
        }
        
        return String.format(
            "Production MCP [%s]: %s, Requests: %d (Success: %d, Failed: %d, Rate Limited: %d)",
            config.getEnvironment(),
            multiServerClient.getStatus(),
            totalRequests.get(),
            successfulRequests.get(),
            failedRequests.get(),
            rateLimitedRequests.get()
        );
    }
    
    @Override
    @Cacheable(value = "mcpTools", unless = "#result.isEmpty()")
    public List<McpTool> listTools() {
        if (!isEnabled()) {
            return List.of();
        }
        
        return tracingService.traceToolInvocation("list_tools", "system", Map.of(), () -> {
            Instant start = Instant.now();
            
            try {
                List<McpTool> tools = multiServerClient.listTools();
                
                Duration duration = Duration.between(start, Instant.now());
                metricsCollector.recordToolInvocation("list_tools", "system", duration, true, 0, tools.size());
                
                log.debug("Listed {} tools in {}ms", tools.size(), duration.toMillis());
                return tools;
                
            } catch (Exception e) {
                Duration duration = Duration.between(start, Instant.now());
                metricsCollector.recordToolInvocation("list_tools", "system", duration, false, 0, 0);
                metricsCollector.recordFailure("tool_listing", "list_tools", "system", e.getClass().getSimpleName());
                
                log.error("Failed to list tools: {}", e.getMessage());
                throw e;
            }
        });
    }
    
    @Override
    public McpToolResult invokeTool(String toolName, Map<String, Object> arguments) {
        if (!isEnabled()) {
            throw new IllegalStateException("Production MCP client is disabled");
        }
        
        totalRequests.incrementAndGet();
        String requestId = UUID.randomUUID().toString();
        String userId = getCurrentUserId();
        
        // Add request context to trace
        tracingService.addBaggage("request.id", requestId);
        tracingService.addBaggage("user.id", userId);
        tracingService.addBaggage("tool.name", toolName);
        
        log.info("Processing tool invocation: tool={}, requestId={}, userId={}", toolName, requestId, userId);
        
        return tracingService.traceToolInvocation(toolName, "auto-selected", arguments, () -> {
            
            // Check rate limits
            McpRateLimitService.RateLimitResult rateLimitResult = 
                    rateLimitService.checkRateLimit("auto-selected", toolName, userId);
            
            if (!rateLimitResult.isAllowed()) {
                rateLimitedRequests.incrementAndGet();
                metricsCollector.recordFailure("rate_limiting", toolName, "auto-selected", rateLimitResult.getReason());
                
                log.warn("Rate limit exceeded: requestId={}, reason={}", requestId, rateLimitResult.getReason());
                
                return McpToolResult.builder()
                        .success(false)
                        .content("Rate limit exceeded: " + rateLimitResult.getReason())
                        .metadata(Map.of(
                                "request_id", requestId,
                                "error_type", "rate_limit_exceeded",
                                "limit_type", rateLimitResult.getLimitType()
                        ))
                        .build();
            }
            
            // Execute with retry policy
            McpRetryService.RetryPolicy retryPolicy = getRetryPolicyForTool(toolName);
            
            try {
                McpToolResult result = retryService.executeWithRetry(
                        "tool_invocation", 
                        "auto-selected", 
                        () -> multiServerClient.invokeTool(toolName, arguments),
                        retryPolicy
                );
                
                if (result.isSuccess()) {
                    successfulRequests.incrementAndGet();
                } else {
                    failedRequests.incrementAndGet();
                }
                
                // Add production metadata
                Map<String, Object> enhancedMetadata = Map.of(
                        "request_id", requestId,
                        "user_id", userId,
                        "trace_id", tracingService.getCurrentTraceId(),
                        "environment", config.getEnvironment(),
                        "timestamp", Instant.now().toString()
                );
                
                return McpToolResult.builder()
                        .success(result.isSuccess())
                        .content(result.getContent())
                        .metadata(enhancedMetadata)
                        .build();
                
            } catch (Exception e) {
                failedRequests.incrementAndGet();
                metricsCollector.recordFailure("tool_execution", toolName, "auto-selected", e.getClass().getSimpleName());
                
                log.error("Tool invocation failed: tool={}, requestId={}, error={}", toolName, requestId, e.getMessage());
                
                return McpToolResult.builder()
                        .success(false)
                        .content("Tool execution failed: " + e.getMessage())
                        .metadata(Map.of(
                                "request_id", requestId,
                                "error_type", e.getClass().getSimpleName(),
                                "error_message", e.getMessage(),
                                "trace_id", tracingService.getCurrentTraceId()
                        ))
                        .build();
            }
        });
    }
    
    /**
     * Get production-specific statistics.
     */
    public ProductionStatistics getProductionStatistics() {
        return new ProductionStatistics(
                totalRequests.get(),
                successfulRequests.get(),
                failedRequests.get(),
                rateLimitedRequests.get(),
                calculateSuccessRate(),
                config.getEnvironment(),
                isHealthy(),
                tracingService.getTracingStatus(),
                rateLimitService.getStatistics(),
                metricsCollector.getCurrentMetrics()
        );
    }
    
    /**
     * Perform production health check.
     */
    public ProductionHealthCheck performHealthCheck() {
        Instant start = Instant.now();
        
        boolean multiServerHealthy = multiServerClient.isHealthy();
        boolean rateLimitHealthy = isRateLimitHealthy();
        boolean performanceHealthy = isPerformanceHealthy();
        boolean tracingHealthy = tracingService.getTracingStatus().tracingEnabled();
        
        boolean overallHealthy = multiServerHealthy && rateLimitHealthy && performanceHealthy;
        
        Duration checkDuration = Duration.between(start, Instant.now());
        
        return new ProductionHealthCheck(
                overallHealthy,
                multiServerHealthy,
                rateLimitHealthy,
                performanceHealthy,
                tracingHealthy,
                checkDuration,
                Instant.now()
        );
    }
    
    // Private helper methods
    
    private void initializeCustomMetrics() {
        // Initialize production-specific metrics
        metricsCollector.updateActiveConnections(0);
        metricsCollector.updateHealthyServers(0);
        
        log.debug("Custom production metrics initialized");
    }
    
    private void logProductionFeaturesStatus() {
        log.info("Production features status:");
        log.info("  - Monitoring: {}", config.getMonitoring().isEnabled());
        log.info("  - Tracing: {}", config.getTracing().isEnabled());
        log.info("  - Retry: {}", config.getRetry().isEnabled());
        log.info("  - Rate Limiting: {}", config.getRateLimit().isEnabled());
        log.info("  - Security: {}", config.getSecurity().isEnabled());
        log.info("  - Caching: {}", config.getPerformance().getCache().isEnabled());
        log.info("  - Audit: {}", config.getAudit().isEnabled());
    }
    
    private void logFinalStatistics() {
        ProductionStatistics stats = getProductionStatistics();
        
        log.info("Final production statistics:");
        log.info("  - Total requests: {}", stats.totalRequests());
        log.info("  - Successful requests: {}", stats.successfulRequests());
        log.info("  - Failed requests: {}", stats.failedRequests());
        log.info("  - Rate limited requests: {}", stats.rateLimitedRequests());
        log.info("  - Success rate: {:.2f}%", stats.successRate() * 100);
    }
    
    private boolean isRateLimitHealthy() {
        // Consider rate limiting healthy if global tokens are above 10% of capacity
        McpRateLimitService.RateLimitStatistics stats = rateLimitService.getStatistics();
        return stats.globalAvailableTokens() > (config.getRateLimit().getGlobalRequestsPerMinute() * 0.1);
    }
    
    private boolean isPerformanceHealthy() {
        // Consider performance healthy if success rate is above 90%
        return calculateSuccessRate() > 0.9;
    }
    
    private double calculateSuccessRate() {
        long total = totalRequests.get();
        if (total == 0) {
            return 1.0; // No requests yet, consider healthy
        }
        return (double) successfulRequests.get() / total;
    }
    
    private String getCurrentUserId() {
        // In a real implementation, this would extract user ID from security context
        return "default-user";
    }
    
    private McpRetryService.RetryPolicy getRetryPolicyForTool(String toolName) {
        // Check if tool has specific retry policy
        Map<String, ProductionConfig.RetryConfig.RetryPolicyConfig> toolPolicies = 
                config.getRetry().getToolPolicies();
        
        if (toolPolicies.containsKey(toolName)) {
            ProductionConfig.RetryConfig.RetryPolicyConfig policyConfig = toolPolicies.get(toolName);
            return new McpRetryService.RetryPolicy(
                    policyConfig.getMaxAttempts(),
                    policyConfig.getInitialDelay(),
                    policyConfig.getMaxDelay(),
                    policyConfig.getMultiplier(),
                    policyConfig.getJitter(),
                    policyConfig.getRetryableExceptions().stream()
                            .map(className -> {
                                try {
                                    return (Class<? extends Throwable>) Class.forName(className);
                                } catch (ClassNotFoundException e) {
                                    log.warn("Unknown exception class in retry policy: {}", className);
                                    return RuntimeException.class;
                                }
                            })
                            .toList()
            );
        }
        
        // Use default retry policy
        return retryService.createDefaultRetryPolicy();
    }
    
    /**
     * Production statistics record.
     */
    public record ProductionStatistics(
            long totalRequests,
            long successfulRequests,
            long failedRequests,
            long rateLimitedRequests,
            double successRate,
            String environment,
            boolean healthy,
            McpTracingService.TracingStatus tracingStatus,
            McpRateLimitService.RateLimitStatistics rateLimitStats,
            McpMetricsCollector.MetricsSummary metricsStatus
    ) {}
    
    /**
     * Production health check result.
     */
    public record ProductionHealthCheck(
            boolean overallHealthy,
            boolean multiServerHealthy,
            boolean rateLimitHealthy,
            boolean performanceHealthy,
            boolean tracingHealthy,
            Duration checkDuration,
            Instant timestamp
    ) {}
}