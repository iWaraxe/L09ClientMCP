package com.coherentsolutions.l09clientmcp.production.monitoring;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive metrics collection for MCP operations.
 * 
 * This component provides detailed monitoring and observability for all MCP-related
 * operations including tool invocations, server selections, circuit breaker events,
 * and performance metrics.
 * 
 * Educational Focus:
 * - Production monitoring with Micrometer
 * - Custom metrics creation and tagging
 * - Performance measurement and analysis
 * - Operational observability patterns
 */
@Component
@Slf4j
public class McpMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    
    // Core MCP Metrics
    private final Timer toolInvocationTimer;
    private final Counter toolInvocationCounter;
    private final Counter serverSelectionCounter;
    private final Counter circuitBreakerEventsCounter;
    private final Gauge activeConnectionsGauge;
    private final Gauge healthyServersGauge;
    
    // Performance Metrics
    private final Timer serverResponseTimer;
    private final DistributionSummary requestSizeDistribution;
    private final DistributionSummary responseSizeDistribution;
    
    // Business Metrics
    private final Counter failureCounter;
    private final Counter retryCounter;
    private final Timer healthCheckTimer;
    
    // Internal state for gauges
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong healthyServers = new AtomicLong(0);
    
    public McpMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize all metrics
        this.toolInvocationTimer = Timer.builder("mcp.tool.invocation")
                .description("Time taken for tool invocations")
                .tag("component", "mcp-client")
                .register(meterRegistry);
        
        this.toolInvocationCounter = Counter.builder("mcp.tool.invocation.total")
                .description("Total number of tool invocations")
                .tag("component", "mcp-client")
                .register(meterRegistry);
        
        this.serverSelectionCounter = Counter.builder("mcp.server.selection.total")
                .description("Total number of server selections")
                .tag("component", "load-balancer")
                .register(meterRegistry);
        
        this.circuitBreakerEventsCounter = Counter.builder("mcp.circuit.breaker.events.total")
                .description("Circuit breaker state change events")
                .tag("component", "circuit-breaker")
                .register(meterRegistry);
        
        this.activeConnectionsGauge = Gauge.builder("mcp.connections.active")
                .description("Number of active MCP server connections")
                .tag("component", "connection-pool")
                .register(meterRegistry, activeConnections, AtomicLong::get);
        
        this.healthyServersGauge = Gauge.builder("mcp.servers.healthy")
                .description("Number of healthy MCP servers")
                .tag("component", "health-monitor")
                .register(meterRegistry, healthyServers, AtomicLong::get);
        
        this.serverResponseTimer = Timer.builder("mcp.server.response")
                .description("Server response time")
                .tag("component", "server-communication")
                .register(meterRegistry);
        
        this.requestSizeDistribution = DistributionSummary.builder("mcp.request.size")
                .description("Size of MCP requests")
                .baseUnit("bytes")
                .tag("component", "communication")
                .register(meterRegistry);
        
        this.responseSizeDistribution = DistributionSummary.builder("mcp.response.size")
                .description("Size of MCP responses")
                .baseUnit("bytes")
                .tag("component", "communication")
                .register(meterRegistry);
        
        this.failureCounter = Counter.builder("mcp.failures.total")
                .description("Total number of MCP operation failures")
                .tag("component", "error-tracking")
                .register(meterRegistry);
        
        this.retryCounter = Counter.builder("mcp.retries.total")
                .description("Total number of retry attempts")
                .tag("component", "retry-mechanism")
                .register(meterRegistry);
        
        this.healthCheckTimer = Timer.builder("mcp.health.check")
                .description("Time taken for health checks")
                .tag("component", "health-monitor")
                .register(meterRegistry);
        
        log.info("MCP metrics collector initialized with {} metrics", meterRegistry.getMeters().size());
    }
    
    /**
     * Record a tool invocation with detailed metrics.
     */
    public void recordToolInvocation(String toolName, String serverId, Duration duration, 
                                   boolean success, int requestSize, int responseSize) {
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(Timer.builder("mcp.tool.invocation")
                .tag("tool", toolName)
                .tag("server", serverId)
                .tag("success", String.valueOf(success))
                .tag("component", "tool-execution")
                .register(meterRegistry));
        
        toolInvocationCounter.increment(
                Tags.of(
                        "tool", toolName,
                        "server", serverId,
                        "success", String.valueOf(success)
                )
        );
        
        requestSizeDistribution.record(requestSize, Tags.of("tool", toolName));
        responseSizeDistribution.record(responseSize, Tags.of("tool", toolName));
        
        if (!success) {
            recordFailure("tool_invocation", toolName, serverId, "execution_failed");
        }
        
        log.debug("Recorded tool invocation: tool={}, server={}, duration={}ms, success={}", 
                 toolName, serverId, duration.toMillis(), success);
    }
    
    /**
     * Record server selection events.
     */
    public void recordServerSelection(String toolName, String selectedServerId, 
                                    String strategy, int candidateCount) {
        serverSelectionCounter.increment(
                Tags.of(
                        "tool", toolName,
                        "server", selectedServerId,
                        "strategy", strategy,
                        "candidates", String.valueOf(candidateCount)
                )
        );
        
        log.debug("Recorded server selection: tool={}, server={}, strategy={}, candidates={}", 
                 toolName, selectedServerId, strategy, candidateCount);
    }
    
    /**
     * Record circuit breaker events.
     */
    public void recordCircuitBreakerEvent(String serverId, String event, String previousState, 
                                        String newState) {
        circuitBreakerEventsCounter.increment(
                Tags.of(
                        "server", serverId,
                        "event", event,
                        "previous_state", previousState,
                        "new_state", newState
                )
        );
        
        log.info("Circuit breaker event: server={}, event={}, {} -> {}", 
                serverId, event, previousState, newState);
    }
    
    /**
     * Record server response time.
     */
    public void recordServerResponse(String serverId, Duration responseTime, boolean success) {
        serverResponseTimer.record(responseTime, 
                Tags.of(
                        "server", serverId,
                        "success", String.valueOf(success)
                )
        );
    }
    
    /**
     * Record failure with categorization.
     */
    public void recordFailure(String category, String operation, String serverId, String reason) {
        failureCounter.increment(
                Tags.of(
                        "category", category,
                        "operation", operation,
                        "server", serverId,
                        "reason", reason
                )
        );
        
        log.warn("Failure recorded: category={}, operation={}, server={}, reason={}", 
                category, operation, serverId, reason);
    }
    
    /**
     * Record retry attempt.
     */
    public void recordRetry(String operation, String serverId, int attemptNumber, String reason) {
        retryCounter.increment(
                Tags.of(
                        "operation", operation,
                        "server", serverId,
                        "attempt", String.valueOf(attemptNumber),
                        "reason", reason
                )
        );
        
        log.debug("Retry recorded: operation={}, server={}, attempt={}, reason={}", 
                 operation, serverId, attemptNumber, reason);
    }
    
    /**
     * Record health check execution.
     */
    public void recordHealthCheck(String serverId, Duration duration, boolean healthy) {
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(Timer.builder("mcp.health.check")
                .tag("server", serverId)
                .tag("healthy", String.valueOf(healthy))
                .register(meterRegistry));
        
        log.debug("Health check recorded: server={}, duration={}ms, healthy={}", 
                 serverId, duration.toMillis(), healthy);
    }
    
    /**
     * Update active connections count.
     */
    public void updateActiveConnections(long count) {
        activeConnections.set(count);
        log.debug("Updated active connections count: {}", count);
    }
    
    /**
     * Update healthy servers count.
     */
    public void updateHealthyServers(long count) {
        healthyServers.set(count);
        log.debug("Updated healthy servers count: {}", count);
    }
    
    /**
     * Get current metrics summary for operational dashboards.
     */
    public MetricsSummary getCurrentMetrics() {
        return new MetricsSummary(
                toolInvocationCounter.count(),
                serverSelectionCounter.count(),
                circuitBreakerEventsCounter.count(),
                failureCounter.count(),
                retryCounter.count(),
                activeConnections.get(),
                healthyServers.get(),
                toolInvocationTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS),
                serverResponseTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS)
        );
    }
    
    /**
     * Metrics summary for operational visibility.
     */
    public record MetricsSummary(
            double totalToolInvocations,
            double totalServerSelections,
            double totalCircuitBreakerEvents,
            double totalFailures,
            double totalRetries,
            long activeConnections,
            long healthyServers,
            double avgToolInvocationTimeMs,
            double avgServerResponseTimeMs
    ) {}
}