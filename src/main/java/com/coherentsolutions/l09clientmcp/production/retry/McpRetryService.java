package com.coherentsolutions.l09clientmcp.production.retry;

import com.coherentsolutions.l09clientmcp.production.monitoring.McpMetricsCollector;
import com.coherentsolutions.l09clientmcp.production.tracing.McpTracingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Advanced retry mechanism with exponential backoff and jitter.
 * 
 * This service provides sophisticated retry logic for MCP operations with
 * configurable policies, comprehensive monitoring, and intelligent backoff strategies.
 * 
 * Educational Focus:
 * - Exponential backoff with jitter implementation
 * - Retry policy configuration and customization
 * - Integration with monitoring and tracing
 * - Resilience patterns in distributed systems
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpRetryService {
    
    private final McpMetricsCollector metricsCollector;
    private final McpTracingService tracingService;
    private final Random random = new Random();
    
    // Default retry configuration
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final Duration DEFAULT_INITIAL_DELAY = Duration.ofMillis(100);
    private static final Duration DEFAULT_MAX_DELAY = Duration.ofSeconds(5);
    private static final double DEFAULT_MULTIPLIER = 2.0;
    private static final double DEFAULT_JITTER = 0.1;
    
    /**
     * Execute operation with default retry policy.
     */
    public <T> T executeWithRetry(String operation, String serverId, Supplier<T> supplier) {
        return executeWithRetry(operation, serverId, supplier, createDefaultRetryPolicy());
    }
    
    /**
     * Execute operation with custom retry policy.
     */
    public <T> T executeWithRetry(String operation, String serverId, 
                                Supplier<T> supplier, RetryPolicy retryPolicy) {
        RetryTemplate retryTemplate = createRetryTemplate(retryPolicy);
        
        return retryTemplate.execute(context -> {
            int attemptNumber = context.getRetryCount() + 1;
            String reason = context.getLastThrowable() != null ? 
                    context.getLastThrowable().getClass().getSimpleName() : "initial_attempt";
            
            log.debug("Executing retry attempt {}/{} for operation={}, server={}, reason={}", 
                     attemptNumber, retryPolicy.maxAttempts(), operation, serverId, reason);
            
            // Record retry attempt (skip for first attempt)
            if (attemptNumber > 1) {
                metricsCollector.recordRetry(operation, serverId, attemptNumber, reason);
            }
            
            // Execute with tracing
            return tracingService.traceRetryAttempt(operation, serverId, attemptNumber, reason, supplier);
        });
    }
    
    /**
     * Execute with circuit breaker integration.
     */
    public <T> T executeWithRetryAndCircuitBreaker(String operation, String serverId,
                                                 Supplier<T> supplier, RetryPolicy retryPolicy) {
        return executeWithRetry(operation, serverId, () -> {
            // This would integrate with circuit breaker
            // For now, we'll just execute the operation
            return supplier.get();
        }, retryPolicy);
    }
    
    /**
     * Create retry template with custom policy.
     */
    private RetryTemplate createRetryTemplate(RetryPolicy policy) {
        return RetryTemplate.builder()
                .maxAttempts(policy.maxAttempts())
                .exponentialBackoff(
                        policy.initialDelay().toMillis(),
                        policy.multiplier(),
                        policy.maxDelay().toMillis()
                )
                .retryOn(policy.retryableExceptions())
                .withListener(new McpRetryListener())
                .build();
    }
    
    /**
     * Create default retry policy.
     */
    public RetryPolicy createDefaultRetryPolicy() {
        return new RetryPolicy(
                DEFAULT_MAX_ATTEMPTS,
                DEFAULT_INITIAL_DELAY,
                DEFAULT_MAX_DELAY,
                DEFAULT_MULTIPLIER,
                DEFAULT_JITTER,
                List.of(
                        RuntimeException.class,
                        TimeoutException.class,
                        java.net.SocketTimeoutException.class,
                        org.springframework.web.client.ResourceAccessException.class
                )
        );
    }
    
    /**
     * Create retry policy for health checks (more aggressive).
     */
    public RetryPolicy createHealthCheckRetryPolicy() {
        return new RetryPolicy(
                2, // Fewer attempts for health checks
                Duration.ofMillis(50),
                Duration.ofSeconds(2),
                1.5, // Less aggressive backoff
                0.05, // Less jitter
                List.of(
                        TimeoutException.class,
                        java.net.SocketTimeoutException.class
                )
        );
    }
    
    /**
     * Create retry policy for critical operations (more patient).
     */
    public RetryPolicy createCriticalOperationRetryPolicy() {
        return new RetryPolicy(
                5, // More attempts for critical operations
                Duration.ofMillis(200),
                Duration.ofSeconds(10),
                2.5, // More aggressive backoff
                0.2, // More jitter to spread load
                List.of(
                        RuntimeException.class,
                        TimeoutException.class,
                        java.net.SocketTimeoutException.class,
                        org.springframework.web.client.ResourceAccessException.class,
                        java.net.ConnectException.class
                )
        );
    }
    
    /**
     * Calculate delay with jitter to prevent thundering herd.
     */
    public Duration calculateDelayWithJitter(Duration baseDelay, double jitterFactor) {
        if (jitterFactor <= 0) {
            return baseDelay;
        }
        
        long baseMillis = baseDelay.toMillis();
        long jitterRange = (long) (baseMillis * jitterFactor);
        long jitter = (long) (random.nextGaussian() * jitterRange);
        
        // Ensure delay is not negative
        long finalDelay = Math.max(baseMillis + jitter, baseMillis / 2);
        
        return Duration.ofMillis(finalDelay);
    }
    
    /**
     * Determine if exception is retryable.
     */
    public boolean isRetryableException(Throwable throwable, List<Class<? extends Throwable>> retryableExceptions) {
        if (throwable == null || retryableExceptions == null) {
            return false;
        }
        
        return retryableExceptions.stream()
                .anyMatch(retryableClass -> retryableClass.isInstance(throwable));
    }
    
    /**
     * Get retry statistics for monitoring.
     */
    public RetryStatistics getRetryStatistics() {
        // This would collect real statistics in a production system
        return new RetryStatistics(
                0L, // Total retry attempts would be tracked
                0L, // Successful retries would be tracked
                0L, // Failed retries would be tracked
                0.0, // Average retry count would be calculated
                Duration.ZERO // Average retry duration would be calculated
        );
    }
    
    /**
     * Retry policy configuration.
     */
    public record RetryPolicy(
            int maxAttempts,
            Duration initialDelay,
            Duration maxDelay,
            double multiplier,
            double jitter,
            List<Class<? extends Throwable>> retryableExceptions
    ) {
        public RetryPolicy {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("Max attempts must be at least 1");
            }
            if (initialDelay.isNegative() || initialDelay.isZero()) {
                throw new IllegalArgumentException("Initial delay must be positive");
            }
            if (maxDelay.compareTo(initialDelay) < 0) {
                throw new IllegalArgumentException("Max delay must be >= initial delay");
            }
            if (multiplier < 1.0) {
                throw new IllegalArgumentException("Multiplier must be >= 1.0");
            }
            if (jitter < 0.0 || jitter > 1.0) {
                throw new IllegalArgumentException("Jitter must be between 0.0 and 1.0");
            }
        }
    }
    
    /**
     * Retry statistics for monitoring.
     */
    public record RetryStatistics(
            long totalRetryAttempts,
            long successfulRetries,
            long failedRetries,
            double averageRetryCount,
            Duration averageRetryDuration
    ) {}
    
    /**
     * Custom retry listener for monitoring and logging.
     */
    private class McpRetryListener implements RetryListener {
        
        @Override
        public <T, E extends Throwable> void onError(RetryContext context, 
                                                    RetryCallback<T, E> callback, 
                                                    Throwable throwable) {
            int attemptNumber = context.getRetryCount() + 1;
            
            log.warn("Retry attempt {} failed: {}", attemptNumber, throwable.getMessage());
            
            // Add additional context to trace
            if (tracingService.getCurrentTraceId() != null) {
                tracingService.addBaggage("retry.attempt", String.valueOf(attemptNumber));
                tracingService.addBaggage("retry.error", throwable.getClass().getSimpleName());
            }
        }
        
        @Override
        public <T, E extends Throwable> void close(RetryContext context, 
                                                  RetryCallback<T, E> callback, 
                                                  Throwable throwable) {
            int totalAttempts = context.getRetryCount() + 1;
            boolean succeeded = throwable == null;
            
            if (succeeded) {
                log.debug("Operation succeeded after {} attempt(s)", totalAttempts);
            } else {
                log.error("Operation failed after {} attempt(s): {}", totalAttempts, throwable.getMessage());
            }
            
            // Final trace baggage
            tracingService.addBaggage("retry.total_attempts", String.valueOf(totalAttempts));
            tracingService.addBaggage("retry.final_result", succeeded ? "success" : "failure");
        }
    }
}