package com.coherentsolutions.l09clientmcp.multiserver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Health monitoring service for MCP servers.
 * 
 * This component continuously monitors the health of all registered MCP servers,
 * performing periodic health checks and updating server availability status.
 * It uses asynchronous processing to avoid blocking during health checks.
 * 
 * Educational Focus:
 * - Scheduled health checking patterns
 * - Asynchronous health monitoring
 * - Server availability tracking
 * - Performance metrics collection
 * - Circuit breaker integration
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class McpHealthMonitor {
    
    private final McpServerRegistry serverRegistry;
    private final ExecutorService healthCheckExecutor = Executors.newFixedThreadPool(5);
    
    // Health check configuration
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(10);
    private static final int CONSECUTIVE_FAILURES_THRESHOLD = 3;
    private static final Duration UNHEALTHY_SERVER_CHECK_INTERVAL = Duration.ofMinutes(1);
    
    // Health check tracking
    private final Map<String, HealthCheckHistory> healthHistory = new ConcurrentHashMap<>();
    private volatile boolean monitoringEnabled = true;
    private volatile Instant lastFullHealthCheck = Instant.now();
    
    /**
     * Perform scheduled health checks for all registered servers.
     * Runs every 30 seconds as configured in application properties.
     */
    @Scheduled(fixedDelay = 30000) // 30 seconds
    public void performScheduledHealthChecks() {
        if (!monitoringEnabled) {
            log.debug("Health monitoring is disabled, skipping scheduled check");
            return;
        }
        
        log.debug("Starting scheduled health check for all servers");
        long startTime = System.currentTimeMillis();
        
        var serverIds = serverRegistry.getAllServerIds();
        if (serverIds.isEmpty()) {
            log.debug("No servers registered for health checking");
            return;
        }
        
        // Perform health checks asynchronously for all servers
        var healthCheckFutures = serverIds.stream()
                .map(serverId -> CompletableFuture.runAsync(() -> 
                    checkServerHealth(serverId), healthCheckExecutor))
                .toArray(CompletableFuture[]::new);
        
        // Wait for all health checks to complete
        CompletableFuture.allOf(healthCheckFutures)
                .thenRun(() -> {
                    long duration = System.currentTimeMillis() - startTime;
                    lastFullHealthCheck = Instant.now();
                    log.debug("Completed health checks for {} servers in {}ms", serverIds.size(), duration);
                })
                .exceptionally(throwable -> {
                    log.error("Error during scheduled health checks: {}", throwable.getMessage());
                    return null;
                });
    }
    
    /**
     * Perform immediate health check for a specific server.
     */
    public void checkServerHealth(String serverId) {
        var serverOpt = serverRegistry.getServer(serverId);
        if (serverOpt.isEmpty()) {
            log.warn("Cannot check health for unknown server: {}", serverId);
            return;
        }
        
        var server = serverOpt.get();
        var history = healthHistory.computeIfAbsent(serverId, k -> new HealthCheckHistory(serverId));
        
        try {
            log.debug("Performing health check for server: {}", serverId);
            
            var healthResult = server.performHealthCheck();
            history.recordHealthCheck(healthResult);
            
            boolean shouldBeHealthy = determineHealthStatus(history);
            String reason = healthResult.healthy() ? 
                "Health check passed" : 
                "Health check failed: " + healthResult.message();
            
            // Update server health in registry
            serverRegistry.updateServerHealth(serverId, shouldBeHealthy, reason);
            
            if (shouldBeHealthy != healthResult.healthy()) {
                log.info("Server {} health status changed based on history: {} -> {} (reason: {})",
                         serverId, healthResult.healthy(), shouldBeHealthy, reason);
            }
            
        } catch (Exception e) {
            log.error("Health check failed for server {}: {}", serverId, e.getMessage());
            
            var failedResult = new McpServerConnection.HealthCheckResult(
                false, 0, "Health check exception: " + e.getMessage(), System.currentTimeMillis()
            );
            
            history.recordHealthCheck(failedResult);
            serverRegistry.updateServerHealth(serverId, false, "Health check exception: " + e.getMessage());
        }
    }
    
    /**
     * Get health check history for a server.
     */
    public HealthCheckHistory getHealthHistory(String serverId) {
        return healthHistory.get(serverId);
    }
    
    /**
     * Get health monitoring statistics.
     */
    public HealthMonitorStats getMonitoringStats() {
        var serverHealth = serverRegistry.getAllServerHealth();
        
        int totalServers = serverHealth.size();
        int healthyServers = (int) serverHealth.values().stream()
                .filter(McpServerRegistry.ServerHealth::isHealthy)
                .count();
        
        long totalHealthChecks = healthHistory.values().stream()
                .mapToLong(h -> h.getTotalChecks())
                .sum();
        
        double averageResponseTime = healthHistory.values().stream()
                .mapToDouble(h -> h.getAverageResponseTime())
                .filter(time -> time > 0)
                .average()
                .orElse(0.0);
        
        return new HealthMonitorStats(
            totalServers,
            healthyServers,
            totalHealthChecks,
            averageResponseTime,
            lastFullHealthCheck,
            monitoringEnabled
        );
    }
    
    /**
     * Enable or disable health monitoring.
     */
    public void setMonitoringEnabled(boolean enabled) {
        this.monitoringEnabled = enabled;
        log.info("Health monitoring {}", enabled ? "enabled" : "disabled");
    }
    
    /**
     * Force health check for all servers immediately.
     */
    public void forceHealthCheckAll() {
        log.info("Forcing immediate health check for all servers");
        var serverIds = serverRegistry.getAllServerIds();
        
        var futures = serverIds.stream()
                .map(serverId -> CompletableFuture.runAsync(() -> 
                    checkServerHealth(serverId), healthCheckExecutor))
                .toArray(CompletableFuture[]::new);
        
        CompletableFuture.allOf(futures)
                .thenRun(() -> log.info("Forced health check completed for {} servers", serverIds.size()))
                .join(); // Wait for completion
    }
    
    /**
     * Determine overall health status based on recent health check history.
     */
    private boolean determineHealthStatus(HealthCheckHistory history) {
        // If we have recent consecutive failures, mark as unhealthy
        if (history.getConsecutiveFailures() >= CONSECUTIVE_FAILURES_THRESHOLD) {
            return false;
        }
        
        // If last check was successful, consider healthy
        if (history.getLastHealthCheck() != null && history.getLastHealthCheck().healthy()) {
            return true;
        }
        
        // If we have a mix of recent results, use success rate
        double recentSuccessRate = history.getRecentSuccessRate(Duration.ofMinutes(5));
        return recentSuccessRate >= 0.7; // 70% success rate threshold
    }
    
    /**
     * Health check history for a single server.
     */
    public static class HealthCheckHistory {
        private final String serverId;
        private final java.util.List<McpServerConnection.HealthCheckResult> recentChecks = 
            new java.util.concurrent.CopyOnWriteArrayList<>();
        private volatile int consecutiveFailures = 0;
        private volatile long totalChecks = 0;
        private volatile long totalResponseTime = 0;
        
        private static final int MAX_HISTORY_SIZE = 100;
        
        public HealthCheckHistory(String serverId) {
            this.serverId = serverId;
        }
        
        public void recordHealthCheck(McpServerConnection.HealthCheckResult result) {
            recentChecks.add(result);
            totalChecks++;
            totalResponseTime += result.responseTimeMs();
            
            // Update consecutive failures counter
            if (result.healthy()) {
                consecutiveFailures = 0;
            } else {
                consecutiveFailures++;
            }
            
            // Limit history size
            if (recentChecks.size() > MAX_HISTORY_SIZE) {
                recentChecks.remove(0);
            }
        }
        
        public McpServerConnection.HealthCheckResult getLastHealthCheck() {
            return recentChecks.isEmpty() ? null : recentChecks.get(recentChecks.size() - 1);
        }
        
        public int getConsecutiveFailures() {
            return consecutiveFailures;
        }
        
        public long getTotalChecks() {
            return totalChecks;
        }
        
        public double getAverageResponseTime() {
            return totalChecks > 0 ? (double) totalResponseTime / totalChecks : 0;
        }
        
        public double getRecentSuccessRate(Duration window) {
            long windowStart = System.currentTimeMillis() - window.toMillis();
            
            var recentResults = recentChecks.stream()
                    .filter(check -> check.timestamp() >= windowStart)
                    .toList();
            
            if (recentResults.isEmpty()) {
                return 1.0; // No recent data, assume healthy
            }
            
            long successful = recentResults.stream()
                    .mapToLong(check -> check.healthy() ? 1 : 0)
                    .sum();
            
            return (double) successful / recentResults.size();
        }
        
        public String getServerId() {
            return serverId;
        }
        
        public java.util.List<McpServerConnection.HealthCheckResult> getRecentChecks() {
            return new java.util.ArrayList<>(recentChecks);
        }
    }
    
    /**
     * Health monitoring statistics.
     */
    public record HealthMonitorStats(
        int totalServers,
        int healthyServers,
        long totalHealthChecks,
        double averageResponseTime,
        Instant lastFullHealthCheck,
        boolean monitoringEnabled
    ) {
        public double getHealthyServerRatio() {
            return totalServers > 0 ? (double) healthyServers / totalServers : 0.0;
        }
        
        public int getUnhealthyServers() {
            return totalServers - healthyServers;
        }
    }
}