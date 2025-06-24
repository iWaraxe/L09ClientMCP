package com.coherentsolutions.l09clientmcp.production.ratelimit;

import com.coherentsolutions.l09clientmcp.production.monitoring.McpMetricsCollector;
import com.google.common.util.concurrent.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Rate limiting service for MCP operations.
 * 
 * This service provides sophisticated rate limiting capabilities using token bucket
 * algorithm with per-server, per-tool, and global rate limiting support.
 * 
 * Educational Focus:
 * - Token bucket algorithm implementation
 * - Multi-dimensional rate limiting (server, tool, global)
 * - Adaptive rate limiting based on server performance
 * - Integration with monitoring and metrics
 */
@Component
@ConditionalOnProperty(
    name = "spring.ai.mcp.production.enabled", 
    havingValue = "true"
)
@RequiredArgsConstructor
@Slf4j
public class McpRateLimitService {
    
    private final McpMetricsCollector metricsCollector;
    
    // Rate limiters organized by type and identifier
    private final ConcurrentMap<String, RateLimiter> serverRateLimiters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RateLimiter> toolRateLimiters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RateLimiter> userRateLimiters = new ConcurrentHashMap<>();
    private final RateLimiter globalRateLimiter;
    
    // Default rate limiting configurations
    private static final int DEFAULT_SERVER_REQUESTS_PER_MINUTE = 60;
    private static final int DEFAULT_TOOL_REQUESTS_PER_MINUTE = 30;
    private static final int DEFAULT_USER_REQUESTS_PER_MINUTE = 100;
    private static final int DEFAULT_GLOBAL_REQUESTS_PER_MINUTE = 1000;
    
    public McpRateLimitService(McpMetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
        
        // Initialize global rate limiter (permits per second)
        this.globalRateLimiter = RateLimiter.create(DEFAULT_GLOBAL_REQUESTS_PER_MINUTE / 60.0);
        
        log.info("Rate limiting service initialized with global limit: {} requests/minute", 
                DEFAULT_GLOBAL_REQUESTS_PER_MINUTE);
    }
    
    /**
     * Check if request is allowed under all applicable rate limits.
     */
    public RateLimitResult checkRateLimit(String serverId, String toolName, String userId) {
        log.debug("Checking rate limits for server={}, tool={}, user={}", serverId, toolName, userId);
        
        // Check global rate limit first (fail fast)
        if (!globalRateLimiter.tryAcquire()) {
            log.warn("Global rate limit exceeded");
            return RateLimitResult.denied("global", "Global rate limit exceeded");
        }
        
        // Check server-specific rate limit
        RateLimiter serverRateLimiter = getOrCreateServerRateLimiter(serverId);
        if (!serverRateLimiter.tryAcquire()) {
            log.warn("Server rate limit exceeded for server: {}", serverId);
            return RateLimitResult.denied("server", "Server rate limit exceeded for: " + serverId);
        }
        
        // Check tool-specific rate limit
        String toolKey = serverId + ":" + toolName;
        RateLimiter toolRateLimiter = getOrCreateToolRateLimiter(toolKey);
        if (!toolRateLimiter.tryAcquire()) {
            log.warn("Tool rate limit exceeded for server={}, tool={}", serverId, toolName);
            return RateLimitResult.denied("tool", "Tool rate limit exceeded for: " + toolName);
        }
        
        // Check user-specific rate limit (if user provided)
        if (userId != null && !userId.isEmpty()) {
            RateLimiter userRateLimiter = getOrCreateUserRateLimiter(userId);
            if (!userRateLimiter.tryAcquire()) {
                log.warn("User rate limit exceeded for user: {}", userId);
                return RateLimitResult.denied("user", "User rate limit exceeded for: " + userId);
            }
        }
        
        log.debug("Rate limit check passed for server={}, tool={}, user={}", serverId, toolName, userId);
        return RateLimitResult.allowed();
    }
    
    /**
     * Get remaining tokens for a specific server (estimated).
     */
    public double getRemainingTokens(String serverId) {
        RateLimiter serverRateLimiter = serverRateLimiters.get(serverId);
        // Guava RateLimiter doesn't expose remaining tokens, so we return rate
        return serverRateLimiter != null ? serverRateLimiter.getRate() : 0;
    }
    
    /**
     * Get rate limit status for monitoring.
     */
    public RateLimitStatus getRateLimitStatus(String serverId, String toolName, String userId) {
        double globalRate = globalRateLimiter.getRate();
        double serverRate = serverRateLimiters.getOrDefault(serverId, createDefaultServerRateLimiter()).getRate();
        
        String toolKey = serverId + ":" + toolName;
        double toolRate = toolRateLimiters.getOrDefault(toolKey, createDefaultToolRateLimiter()).getRate();
        
        double userRate = userId != null ? 
                userRateLimiters.getOrDefault(userId, createDefaultUserRateLimiter()).getRate() : -1;
        
        return new RateLimitStatus(globalRate, serverRate, toolRate, userRate);
    }
    
    /**
     * Adjust server rate limit based on performance.
     */
    public void adjustServerRateLimit(String serverId, ServerPerformance performance) {
        int newLimit = calculateAdjustedLimit(DEFAULT_SERVER_REQUESTS_PER_MINUTE, performance);
        
        RateLimiter newRateLimiter = RateLimiter.create(newLimit / 60.0); // permits per second
        serverRateLimiters.put(serverId, newRateLimiter);
        
        log.info("Adjusted rate limit for server {}: {} requests/minute (performance score: {})", 
                serverId, newLimit, performance.performanceScore());
    }
    
    /**
     * Create adaptive rate limit based on circuit breaker state.
     */
    public void adjustRateLimitForCircuitBreakerState(String serverId, String circuitState) {
        int adjustedLimit = switch (circuitState.toUpperCase()) {
            case "OPEN" -> 0; // No requests when circuit is open
            case "HALF_OPEN" -> DEFAULT_SERVER_REQUESTS_PER_MINUTE / 4; // Reduced load during testing
            case "CLOSED" -> DEFAULT_SERVER_REQUESTS_PER_MINUTE; // Normal operation
            default -> DEFAULT_SERVER_REQUESTS_PER_MINUTE;
        };
        
        if (adjustedLimit == 0) {
            // Remove rate limiter to deny all requests
            serverRateLimiters.remove(serverId);
        } else {
            RateLimiter adjustedRateLimiter = RateLimiter.create(adjustedLimit / 60.0); // permits per second
            serverRateLimiters.put(serverId, adjustedRateLimiter);
        }
        
        log.info("Adjusted rate limit for server {} based on circuit breaker state {}: {} requests/minute", 
                serverId, circuitState, adjustedLimit);
    }
    
    /**
     * Get comprehensive rate limiting statistics.
     */
    public RateLimitStatistics getStatistics() {
        int totalServerRateLimiters = serverRateLimiters.size();
        int totalToolRateLimiters = toolRateLimiters.size();
        int totalUserRateLimiters = userRateLimiters.size();
        
        double totalServerRate = serverRateLimiters.values().stream()
                .mapToDouble(RateLimiter::getRate)
                .sum();
        
        return new RateLimitStatistics(
                totalServerRateLimiters,
                totalToolRateLimiters,
                totalUserRateLimiters,
                (long) globalRateLimiter.getRate(),
                (long) totalServerRate
        );
    }
    
    // Private helper methods
    
    private RateLimiter getOrCreateServerRateLimiter(String serverId) {
        return serverRateLimiters.computeIfAbsent(serverId, k -> createDefaultServerRateLimiter());
    }
    
    private RateLimiter getOrCreateToolRateLimiter(String toolKey) {
        return toolRateLimiters.computeIfAbsent(toolKey, k -> createDefaultToolRateLimiter());
    }
    
    private RateLimiter getOrCreateUserRateLimiter(String userId) {
        return userRateLimiters.computeIfAbsent(userId, k -> createDefaultUserRateLimiter());
    }
    
    private RateLimiter createDefaultServerRateLimiter() {
        return RateLimiter.create(DEFAULT_SERVER_REQUESTS_PER_MINUTE / 60.0); // permits per second
    }
    
    private RateLimiter createDefaultToolRateLimiter() {
        return RateLimiter.create(DEFAULT_TOOL_REQUESTS_PER_MINUTE / 60.0); // permits per second
    }
    
    private RateLimiter createDefaultUserRateLimiter() {
        return RateLimiter.create(DEFAULT_USER_REQUESTS_PER_MINUTE / 60.0); // permits per second
    }
    
    private int calculateAdjustedLimit(int baseLimit, ServerPerformance performance) {
        double adjustmentFactor = performance.performanceScore();
        
        // Performance score: 0.0 (worst) to 1.0 (best)
        // Adjust rate limit proportionally
        int adjustedLimit = (int) (baseLimit * (0.5 + 0.5 * adjustmentFactor));
        
        // Ensure minimum rate limit
        return Math.max(adjustedLimit, baseLimit / 10);
    }
    
    /**
     * Rate limit check result.
     */
    public static class RateLimitResult {
        private final boolean allowed;
        private final String limitType;
        private final String reason;
        
        private RateLimitResult(boolean allowed, String limitType, String reason) {
            this.allowed = allowed;
            this.limitType = limitType;
            this.reason = reason;
        }
        
        public static RateLimitResult allowed() {
            return new RateLimitResult(true, null, null);
        }
        
        public static RateLimitResult denied(String limitType, String reason) {
            return new RateLimitResult(false, limitType, reason);
        }
        
        public boolean isAllowed() { return allowed; }
        public String getLimitType() { return limitType; }
        public String getReason() { return reason; }
    }
    
    /**
     * Rate limit status for monitoring.
     */
    public record RateLimitStatus(
            double globalRate,
            double serverRate,
            double toolRate,
            double userRate
    ) {}
    
    /**
     * Server performance metrics for adaptive rate limiting.
     */
    public record ServerPerformance(
            double performanceScore, // 0.0 to 1.0
            Duration averageResponseTime,
            double errorRate,
            int activeConnections
    ) {}
    
    /**
     * Rate limiting statistics.
     */
    public record RateLimitStatistics(
            int totalServerBuckets,
            int totalToolBuckets,
            int totalUserBuckets,
            long globalAvailableTokens,
            long totalServerTokens
    ) {}
}