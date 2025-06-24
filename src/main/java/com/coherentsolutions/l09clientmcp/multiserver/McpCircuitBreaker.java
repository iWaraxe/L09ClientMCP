package com.coherentsolutions.l09clientmcp.multiserver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Circuit breaker implementation for MCP server connections.
 * 
 * This component implements the circuit breaker pattern to prevent cascading failures
 * when MCP servers become unresponsive. It tracks failure rates and automatically
 * opens circuits to failing servers, with automatic recovery attempts.
 * 
 * Educational Focus:
 * - Circuit breaker pattern implementation
 * - Failure detection and recovery
 * - State management (CLOSED, OPEN, HALF_OPEN)
 * - Automatic failover and fallback mechanisms
 * - Performance protection and stability
 */
@Component
@Slf4j
public class McpCircuitBreaker {
    
    // Circuit breaker configuration
    private static final int DEFAULT_FAILURE_THRESHOLD = 5;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration DEFAULT_HALF_OPEN_RETRY_DELAY = Duration.ofSeconds(30);
    private static final int DEFAULT_HALF_OPEN_MAX_CALLS = 3;
    
    private final Map<String, CircuitBreakerState> serverStates = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreakerConfig> serverConfigs = new ConcurrentHashMap<>();
    
    /**
     * Execute an operation with circuit breaker protection.
     */
    public <T> T executeWithCircuitBreaker(String serverId, Supplier<T> operation) throws CircuitBreakerOpenException {
        CircuitBreakerState state = getOrCreateState(serverId);
        CircuitBreakerConfig config = getOrCreateConfig(serverId);
        
        // Check circuit state before execution
        if (shouldRejectCall(state, config)) {
            log.debug("Circuit breaker OPEN for server {}, rejecting call", serverId);
            throw new CircuitBreakerOpenException("Circuit breaker is open for server: " + serverId);
        }
        
        // Record call attempt
        state.recordCallAttempt();
        
        try {
            long startTime = System.currentTimeMillis();
            T result = operation.get();
            long responseTime = System.currentTimeMillis() - startTime;
            
            recordSuccess(serverId, responseTime);
            return result;
            
        } catch (Exception e) {
            recordFailure(serverId, e);
            throw e;
        }
    }
    
    /**
     * Record a successful operation.
     */
    public void recordSuccess(String serverId, long responseTimeMs) {
        CircuitBreakerState state = getOrCreateState(serverId);
        state.recordSuccess(responseTimeMs);
        
        // Transition to CLOSED if we were in HALF_OPEN
        if (state.getCurrentState() == CircuitState.HALF_OPEN) {
            transitionToClosed(serverId, state);
        }
        
        log.debug("Recorded success for server {} (response time: {}ms)", serverId, responseTimeMs);
    }
    
    /**
     * Record a failed operation.
     */
    public void recordFailure(String serverId, Exception error) {
        CircuitBreakerState state = getOrCreateState(serverId);
        CircuitBreakerConfig config = getOrCreateConfig(serverId);
        
        state.recordFailure(error);
        
        // Check if we should trip the circuit breaker
        if (shouldTripCircuit(state, config)) {
            transitionToOpen(serverId, state, "Failure threshold exceeded");
        }
        
        log.debug("Recorded failure for server {} (consecutive failures: {}): {}", 
                  serverId, state.getConsecutiveFailures(), error.getMessage());
    }
    
    /**
     * Get current circuit breaker state for a server.
     */
    public CircuitState getCircuitState(String serverId) {
        CircuitBreakerState state = serverStates.get(serverId);
        return state != null ? state.getCurrentState() : CircuitState.CLOSED;
    }
    
    /**
     * Force circuit breaker to open for a server.
     */
    public void forceOpen(String serverId, String reason) {
        CircuitBreakerState state = getOrCreateState(serverId);
        transitionToOpen(serverId, state, "Force opened: " + reason);
    }
    
    /**
     * Force circuit breaker to close for a server.
     */
    public void forceClose(String serverId) {
        CircuitBreakerState state = getOrCreateState(serverId);
        transitionToClosed(serverId, state);
    }
    
    /**
     * Get circuit breaker statistics for a server.
     */
    public CircuitBreakerStats getStats(String serverId) {
        CircuitBreakerState state = serverStates.get(serverId);
        if (state == null) {
            return new CircuitBreakerStats(serverId, CircuitState.CLOSED, 0, 0, 0, 0, null, null);
        }
        
        return new CircuitBreakerStats(
            serverId,
            state.getCurrentState(),
            state.getTotalCalls(),
            state.getSuccessfulCalls(),
            state.getFailedCalls(),
            state.getConsecutiveFailures(),
            state.getLastStateChange(),
            state.getLastFailure()
        );
    }
    
    /**
     * Get statistics for all servers.
     */
    public Map<String, CircuitBreakerStats> getAllStats() {
        Map<String, CircuitBreakerStats> allStats = new ConcurrentHashMap<>();
        
        for (String serverId : serverStates.keySet()) {
            allStats.put(serverId, getStats(serverId));
        }
        
        return allStats;
    }
    
    /**
     * Reset circuit breaker state for a server.
     */
    public void reset(String serverId) {
        CircuitBreakerState state = getOrCreateState(serverId);
        state.reset();
        transitionToClosed(serverId, state);
        log.info("Circuit breaker reset for server: {}", serverId);
    }
    
    /**
     * Configure circuit breaker parameters for a server.
     */
    public void configureServer(String serverId, CircuitBreakerConfig config) {
        serverConfigs.put(serverId, config);
        log.info("Circuit breaker configured for server {}: {}", serverId, config);
    }
    
    // Private helper methods
    
    private CircuitBreakerState getOrCreateState(String serverId) {
        return serverStates.computeIfAbsent(serverId, k -> new CircuitBreakerState());
    }
    
    private CircuitBreakerConfig getOrCreateConfig(String serverId) {
        return serverConfigs.computeIfAbsent(serverId, k -> new CircuitBreakerConfig());
    }
    
    private boolean shouldRejectCall(CircuitBreakerState state, CircuitBreakerConfig config) {
        CircuitState currentState = state.getCurrentState();
        
        if (currentState == CircuitState.CLOSED) {
            return false;
        }
        
        if (currentState == CircuitState.OPEN) {
            // Check if timeout has passed to try half-open
            if (state.getLastStateChange().plus(config.getTimeout()).isBefore(Instant.now())) {
                transitionToHalfOpen(state);
                return false; // Allow the call in half-open state
            }
            return true; // Still in timeout period
        }
        
        if (currentState == CircuitState.HALF_OPEN) {
            // Allow limited calls in half-open state
            return state.getHalfOpenCalls() >= config.getHalfOpenMaxCalls();
        }
        
        return false;
    }
    
    private boolean shouldTripCircuit(CircuitBreakerState state, CircuitBreakerConfig config) {
        return state.getConsecutiveFailures() >= config.getFailureThreshold() ||
               (state.getTotalCalls() >= config.getFailureThreshold() && 
                state.getFailureRate() >= config.getFailureRateThreshold());
    }
    
    private void transitionToOpen(String serverId, CircuitBreakerState state, String reason) {
        state.transitionToOpen();
        log.warn("Circuit breaker OPENED for server {}: {}", serverId, reason);
    }
    
    private void transitionToClosed(String serverId, CircuitBreakerState state) {
        state.transitionToClosed();
        log.info("Circuit breaker CLOSED for server {}", serverId);
    }
    
    private void transitionToHalfOpen(CircuitBreakerState state) {
        state.transitionToHalfOpen();
        log.info("Circuit breaker transitioned to HALF_OPEN, allowing limited calls");
    }
    
    // Inner classes and enums
    
    public enum CircuitState {
        CLOSED,    // Normal operation
        OPEN,      // Circuit is open, rejecting calls
        HALF_OPEN  // Testing if service has recovered
    }
    
    public static class CircuitBreakerState {
        private volatile CircuitState currentState = CircuitState.CLOSED;
        private volatile Instant lastStateChange = Instant.now();
        private final AtomicLong totalCalls = new AtomicLong(0);
        private final AtomicLong successfulCalls = new AtomicLong(0);
        private final AtomicLong failedCalls = new AtomicLong(0);
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private final AtomicInteger halfOpenCalls = new AtomicInteger(0);
        private volatile Exception lastFailure;
        
        public void recordCallAttempt() {
            totalCalls.incrementAndGet();
            if (currentState == CircuitState.HALF_OPEN) {
                halfOpenCalls.incrementAndGet();
            }
        }
        
        public void recordSuccess(long responseTimeMs) {
            successfulCalls.incrementAndGet();
            consecutiveFailures.set(0);
        }
        
        public void recordFailure(Exception error) {
            failedCalls.incrementAndGet();
            consecutiveFailures.incrementAndGet();
            lastFailure = error;
        }
        
        public void transitionToOpen() {
            currentState = CircuitState.OPEN;
            lastStateChange = Instant.now();
        }
        
        public void transitionToClosed() {
            currentState = CircuitState.CLOSED;
            lastStateChange = Instant.now();
            consecutiveFailures.set(0);
            halfOpenCalls.set(0);
        }
        
        public void transitionToHalfOpen() {
            currentState = CircuitState.HALF_OPEN;
            lastStateChange = Instant.now();
            halfOpenCalls.set(0);
        }
        
        public void reset() {
            totalCalls.set(0);
            successfulCalls.set(0);
            failedCalls.set(0);
            consecutiveFailures.set(0);
            halfOpenCalls.set(0);
            lastFailure = null;
            lastStateChange = Instant.now();
        }
        
        public double getFailureRate() {
            long total = totalCalls.get();
            return total > 0 ? (double) failedCalls.get() / total : 0.0;
        }
        
        // Getters
        public CircuitState getCurrentState() { return currentState; }
        public Instant getLastStateChange() { return lastStateChange; }
        public long getTotalCalls() { return totalCalls.get(); }
        public long getSuccessfulCalls() { return successfulCalls.get(); }
        public long getFailedCalls() { return failedCalls.get(); }
        public int getConsecutiveFailures() { return consecutiveFailures.get(); }
        public int getHalfOpenCalls() { return halfOpenCalls.get(); }
        public Exception getLastFailure() { return lastFailure; }
    }
    
    public static class CircuitBreakerConfig {
        private final int failureThreshold;
        private final Duration timeout;
        private final Duration halfOpenRetryDelay;
        private final int halfOpenMaxCalls;
        private final double failureRateThreshold;
        
        public CircuitBreakerConfig() {
            this(DEFAULT_FAILURE_THRESHOLD, DEFAULT_TIMEOUT, DEFAULT_HALF_OPEN_RETRY_DELAY, 
                 DEFAULT_HALF_OPEN_MAX_CALLS, 0.5);
        }
        
        public CircuitBreakerConfig(int failureThreshold, Duration timeout, Duration halfOpenRetryDelay,
                                   int halfOpenMaxCalls, double failureRateThreshold) {
            this.failureThreshold = failureThreshold;
            this.timeout = timeout;
            this.halfOpenRetryDelay = halfOpenRetryDelay;
            this.halfOpenMaxCalls = halfOpenMaxCalls;
            this.failureRateThreshold = failureRateThreshold;
        }
        
        // Getters
        public int getFailureThreshold() { return failureThreshold; }
        public Duration getTimeout() { return timeout; }
        public Duration getHalfOpenRetryDelay() { return halfOpenRetryDelay; }
        public int getHalfOpenMaxCalls() { return halfOpenMaxCalls; }
        public double getFailureRateThreshold() { return failureRateThreshold; }
        
        @Override
        public String toString() {
            return String.format("CircuitBreakerConfig{failureThreshold=%d, timeout=%s, failureRateThreshold=%.2f}",
                    failureThreshold, timeout, failureRateThreshold);
        }
    }
    
    public record CircuitBreakerStats(
        String serverId,
        CircuitState state,
        long totalCalls,
        long successfulCalls,
        long failedCalls,
        int consecutiveFailures,
        Instant lastStateChange,
        Exception lastFailure
    ) {
        public double getSuccessRate() {
            return totalCalls > 0 ? (double) successfulCalls / totalCalls : 0.0;
        }
        
        public double getFailureRate() {
            return totalCalls > 0 ? (double) failedCalls / totalCalls : 0.0;
        }
    }
    
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}