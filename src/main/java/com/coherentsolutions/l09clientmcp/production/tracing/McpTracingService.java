package com.coherentsolutions.l09clientmcp.production.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Distributed tracing service for MCP operations.
 * 
 * This service provides comprehensive request tracing across multiple MCP servers,
 * enabling detailed performance analysis and debugging in distributed environments.
 * 
 * Educational Focus:
 * - Distributed tracing concepts and implementation
 * - Request correlation across multiple services
 * - Performance bottleneck identification
 * - Debugging complex distributed operations
 */
@Component
@ConditionalOnProperty(
    name = "spring.ai.mcp.production.enabled", 
    havingValue = "true"
)
@RequiredArgsConstructor
@Slf4j
public class McpTracingService {
    
    private final Tracer tracer;
    
    /**
     * Trace a tool invocation with comprehensive context.
     */
    public <T> T traceToolInvocation(String toolName, String serverId, 
                                   Map<String, Object> arguments, 
                                   Supplier<T> operation) {
        Span span = tracer.nextSpan()
                .name("mcp.tool.invocation")
                .tag("tool.name", toolName)
                .tag("server.id", serverId)
                .tag("component", "mcp-client")
                .tag("operation.type", "tool_execution")
                .start();
        
        // Add argument information for debugging (be careful with sensitive data)
        if (arguments != null && !arguments.isEmpty()) {
            span.tag("args.count", String.valueOf(arguments.size()));
            // Add safe argument tags
            arguments.entrySet().stream()
                    .filter(entry -> isSafeToLog(entry.getKey()))
                    .limit(5) // Limit to prevent span bloat
                    .forEach(entry -> span.tag("arg." + entry.getKey(), 
                                             String.valueOf(entry.getValue())));
        }
        
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            log.debug("Starting traced tool invocation: tool={}, server={}, traceId={}", 
                     toolName, serverId, span.context().traceId());
            
            T result = operation.get();
            
            span.tag("success", "true");
            span.event("tool.execution.completed");
            
            log.debug("Completed traced tool invocation: tool={}, server={}, traceId={}", 
                     toolName, serverId, span.context().traceId());
            
            return result;
            
        } catch (Exception e) {
            span.tag("success", "false");
            span.tag("error.type", e.getClass().getSimpleName());
            span.tag("error.message", e.getMessage());
            span.event("tool.execution.failed");
            
            log.error("Failed traced tool invocation: tool={}, server={}, traceId={}, error={}", 
                     toolName, serverId, span.context().traceId(), e.getMessage());
            
            throw e;
            
        } finally {
            span.end();
        }
    }
    
    /**
     * Trace server selection process.
     */
    public <T> T traceServerSelection(String toolName, String strategy, 
                                    int candidateCount, Supplier<T> operation) {
        Span span = tracer.nextSpan()
                .name("mcp.server.selection")
                .tag("tool.name", toolName)
                .tag("selection.strategy", strategy)
                .tag("candidate.count", String.valueOf(candidateCount))
                .tag("component", "load-balancer")
                .tag("operation.type", "server_selection")
                .start();
        
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            log.debug("Starting traced server selection: tool={}, strategy={}, candidates={}, traceId={}", 
                     toolName, strategy, candidateCount, span.context().traceId());
            
            T result = operation.get();
            
            span.tag("success", "true");
            span.event("server.selection.completed");
            
            return result;
            
        } catch (Exception e) {
            span.tag("success", "false");
            span.tag("error.type", e.getClass().getSimpleName());
            span.tag("error.message", e.getMessage());
            span.event("server.selection.failed");
            
            throw e;
            
        } finally {
            span.end();
        }
    }
    
    /**
     * Trace health check operations.
     */
    public <T> T traceHealthCheck(String serverId, Supplier<T> operation) {
        Span span = tracer.nextSpan()
                .name("mcp.health.check")
                .tag("server.id", serverId)
                .tag("component", "health-monitor")
                .tag("operation.type", "health_check")
                .start();
        
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            log.debug("Starting traced health check: server={}, traceId={}", 
                     serverId, span.context().traceId());
            
            T result = operation.get();
            
            span.tag("success", "true");
            span.event("health.check.completed");
            
            return result;
            
        } catch (Exception e) {
            span.tag("success", "false");
            span.tag("error.type", e.getClass().getSimpleName());
            span.tag("error.message", e.getMessage());
            span.event("health.check.failed");
            
            throw e;
            
        } finally {
            span.end();
        }
    }
    
    /**
     * Trace circuit breaker operations.
     */
    public <T> T traceCircuitBreakerExecution(String serverId, String currentState, 
                                            Supplier<T> operation) {
        Span span = tracer.nextSpan()
                .name("mcp.circuit.breaker.execution")
                .tag("server.id", serverId)
                .tag("circuit.state", currentState)
                .tag("component", "circuit-breaker")
                .tag("operation.type", "circuit_breaker_execution")
                .start();
        
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            log.debug("Starting traced circuit breaker execution: server={}, state={}, traceId={}", 
                     serverId, currentState, span.context().traceId());
            
            T result = operation.get();
            
            span.tag("success", "true");
            span.event("circuit.breaker.execution.completed");
            
            return result;
            
        } catch (Exception e) {
            span.tag("success", "false");
            span.tag("error.type", e.getClass().getSimpleName());
            span.tag("error.message", e.getMessage());
            span.event("circuit.breaker.execution.failed");
            
            // Add circuit breaker specific information
            if (e.getMessage() != null && e.getMessage().contains("circuit breaker")) {
                span.tag("circuit.breaker.triggered", "true");
                span.event("circuit.breaker.opened");
            }
            
            throw e;
            
        } finally {
            span.end();
        }
    }
    
    /**
     * Trace retry operations.
     */
    public <T> T traceRetryAttempt(String operation, String serverId, int attemptNumber, 
                                 String reason, Supplier<T> supplier) {
        Span span = tracer.nextSpan()
                .name("mcp.retry.attempt")
                .tag("operation", operation)
                .tag("server.id", serverId)
                .tag("attempt.number", String.valueOf(attemptNumber))
                .tag("retry.reason", reason)
                .tag("component", "retry-mechanism")
                .tag("operation.type", "retry_attempt")
                .start();
        
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            log.debug("Starting traced retry attempt: operation={}, server={}, attempt={}, reason={}, traceId={}", 
                     operation, serverId, attemptNumber, reason, span.context().traceId());
            
            T result = supplier.get();
            
            span.tag("success", "true");
            span.event("retry.attempt.succeeded");
            
            return result;
            
        } catch (Exception e) {
            span.tag("success", "false");
            span.tag("error.type", e.getClass().getSimpleName());
            span.tag("error.message", e.getMessage());
            span.event("retry.attempt.failed");
            
            throw e;
            
        } finally {
            span.end();
        }
    }
    
    /**
     * Add baggage (cross-service context) to current trace.
     */
    public void addBaggage(String key, String value) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.tag("baggage." + key, value);
            log.debug("Added baggage to current span: {}={}", key, value);
        }
    }
    
    /**
     * Get current trace ID for correlation logging.
     */
    public String getCurrentTraceId() {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            return currentSpan.context().traceId();
        }
        return "no-trace";
    }
    
    /**
     * Get current span ID for detailed correlation.
     */
    public String getCurrentSpanId() {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            return currentSpan.context().spanId();
        }
        return "no-span";
    }
    
    /**
     * Create a manual span for complex operations.
     */
    public Span createSpan(String name, String component, String operationType) {
        return tracer.nextSpan()
                .name(name)
                .tag("component", component)
                .tag("operation.type", operationType)
                .start();
    }
    
    /**
     * Determine if an argument key is safe to include in traces.
     */
    private boolean isSafeToLog(String key) {
        if (key == null) return false;
        
        String lowerKey = key.toLowerCase();
        
        // Exclude sensitive information
        return !lowerKey.contains("password") &&
               !lowerKey.contains("secret") &&
               !lowerKey.contains("token") &&
               !lowerKey.contains("key") &&
               !lowerKey.contains("auth") &&
               !lowerKey.contains("credential");
    }
    
    /**
     * Get tracing configuration and status.
     */
    public TracingStatus getTracingStatus() {
        boolean tracingEnabled = tracer != null;
        String currentTraceId = getCurrentTraceId();
        String currentSpanId = getCurrentSpanId();
        
        return new TracingStatus(
                tracingEnabled,
                currentTraceId,
                currentSpanId,
                !currentTraceId.equals("no-trace")
        );
    }
    
    /**
     * Tracing status information.
     */
    public record TracingStatus(
            boolean tracingEnabled,
            String currentTraceId,
            String currentSpanId,
            boolean hasActiveTrace
    ) {}
}