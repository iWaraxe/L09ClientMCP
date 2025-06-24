package com.coherentsolutions.l09clientmcp.production.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Production-ready configuration for MCP client.
 * 
 * This configuration class provides comprehensive settings for all production
 * features including monitoring, tracing, retry policies, rate limiting,
 * security, and performance optimization.
 * 
 * Educational Focus:
 * - Type-safe configuration with validation
 * - Environment-specific settings
 * - Configuration organization and structure
 * - Production readiness considerations
 */
@ConfigurationProperties(prefix = "spring.ai.mcp.production")
@Component
@Data
@Validated
public class ProductionConfig {
    
    /**
     * Global production features toggle.
     */
    @NotNull
    private Boolean enabled = false;
    
    /**
     * Environment profile (dev, staging, prod).
     */
    @NotBlank
    private String environment = "dev";
    
    /**
     * Monitoring and metrics configuration.
     */
    @Valid
    private MonitoringConfig monitoring = new MonitoringConfig();
    
    /**
     * Distributed tracing configuration.
     */
    @Valid
    private TracingConfig tracing = new TracingConfig();
    
    /**
     * Retry mechanisms configuration.
     */
    @Valid
    private RetryConfig retry = new RetryConfig();
    
    /**
     * Rate limiting configuration.
     */
    @Valid
    private RateLimitConfig rateLimit = new RateLimitConfig();
    
    /**
     * Security configuration.
     */
    @Valid
    private SecurityConfig security = new SecurityConfig();
    
    /**
     * Performance optimization configuration.
     */
    @Valid
    private PerformanceConfig performance = new PerformanceConfig();
    
    /**
     * Audit logging configuration.
     */
    @Valid
    private AuditConfig audit = new AuditConfig();
    
    /**
     * Connection management configuration.
     */
    @Valid
    private ConnectionConfig connection = new ConnectionConfig();
    
    @Data
    public static class MonitoringConfig {
        
        @NotNull
        private Boolean enabled = true;
        
        /**
         * Metrics export configuration.
         */
        @Valid
        private MetricsConfig metrics = new MetricsConfig();
        
        /**
         * Health check configuration.
         */
        @Valid
        private HealthConfig health = new HealthConfig();
        
        public boolean isEnabled() {
            return enabled;
        }
        
        @Data
        public static class MetricsConfig {
            @NotNull
            private Boolean enabled = true;
            
            @NotNull
            private Boolean prometheus = true;
            
            @Min(1)
            @Max(300)
            private int exportIntervalSeconds = 30;
            
            @NotEmpty
            private List<String> includedMetrics = List.of(
                    "mcp.tool.*",
                    "mcp.server.*",
                    "mcp.circuit.*",
                    "mcp.health.*"
            );
        }
        
        @Data
        public static class HealthConfig {
            @NotNull
            private Boolean enabled = true;
            
            @NotNull
            @Min(5)
            @Max(300)
            private Duration interval = Duration.ofSeconds(30);
            
            @NotNull
            @Min(1)
            @Max(60)
            private Duration timeout = Duration.ofSeconds(5);
            
            @Min(1)
            @Max(10)
            private int retryAttempts = 3;
            
            public boolean isEnabled() {
                return enabled;
            }
        }
    }
    
    @Data
    public static class TracingConfig {
        
        @NotNull
        private Boolean enabled = true;
        
        /**
         * Sampling rate (0.0 to 1.0).
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double samplingRate = 0.1;
        
        /**
         * Trace export configuration.
         */
        @NotBlank
        private String exporterType = "zipkin";
        
        @NotBlank
        private String exporterEndpoint = "http://localhost:9411/api/v2/spans";
        
        /**
         * Baggage propagation settings.
         */
        @NotEmpty
        private List<String> baggageFields = List.of(
                "user.id",
                "session.id",
                "request.id"
        );
        
        public boolean isEnabled() {
            return enabled;
        }
    }
    
    @Data
    public static class RetryConfig {
        
        @NotNull
        private Boolean enabled = true;
        
        /**
         * Default retry policy.
         */
        @Valid
        private RetryPolicyConfig defaultPolicy = new RetryPolicyConfig();
        
        /**
         * Tool-specific retry policies.
         */
        @Valid
        private Map<String, RetryPolicyConfig> toolPolicies = new HashMap<>();
        
        public boolean isEnabled() {
            return enabled;
        }
        
        @Data
        public static class RetryPolicyConfig {
            @Min(1)
            @Max(10)
            private int maxAttempts = 3;
            
            @NotNull
            private Duration initialDelay = Duration.ofMillis(100);
            
            @NotNull
            private Duration maxDelay = Duration.ofSeconds(5);
            
            @DecimalMin("1.0")
            @DecimalMax("5.0")
            private double multiplier = 2.0;
            
            @DecimalMin("0.0")
            @DecimalMax("1.0")
            private double jitter = 0.1;
            
            @NotEmpty
            private List<String> retryableExceptions = List.of(
                    "java.net.SocketTimeoutException",
                    "org.springframework.web.client.ResourceAccessException",
                    "java.util.concurrent.TimeoutException"
            );
        }
    }
    
    @Data
    public static class RateLimitConfig {
        
        @NotNull
        private Boolean enabled = true;
        
        /**
         * Global rate limits.
         */
        @Min(1)
        @Max(10000)
        private int globalRequestsPerMinute = 1000;
        
        /**
         * Per-server rate limits.
         */
        @Min(1)
        @Max(1000)
        private int serverRequestsPerMinute = 60;
        
        /**
         * Per-tool rate limits.
         */
        @Min(1)
        @Max(500)
        private int toolRequestsPerMinute = 30;
        
        /**
         * Per-user rate limits.
         */
        @Min(1)
        @Max(1000)
        private int userRequestsPerMinute = 100;
        
        /**
         * Adaptive rate limiting based on performance.
         */
        @NotNull
        private Boolean adaptive = true;
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public boolean isAdaptive() {
            return adaptive;
        }
    }
    
    @Data
    public static class SecurityConfig {
        
        @NotNull
        private Boolean enabled = true;
        
        /**
         * Authentication configuration.
         */
        @Valid
        private AuthConfig auth = new AuthConfig();
        
        /**
         * Authorization configuration.
         */
        @Valid
        private AuthzConfig authz = new AuthzConfig();
        
        public boolean isEnabled() {
            return enabled;
        }
        
        @Data
        public static class AuthConfig {
            @NotNull
            private Boolean enabled = true;
            
            @NotBlank
            private String type = "jwt";
            
            @NotBlank
            private String issuer = "mcp-client";
            
            @NotNull
            private Duration tokenExpiration = Duration.ofHours(1);
            
            @NotNull
            private Boolean requireHttps = true;
        }
        
        @Data
        public static class AuthzConfig {
            @NotNull
            private Boolean enabled = true;
            
            @NotBlank
            private String type = "rbac";
            
            @NotEmpty
            private Map<String, List<String>> rolePermissions = Map.of(
                    "admin", List.of("*"),
                    "user", List.of("tool:invoke", "tool:list"),
                    "readonly", List.of("tool:list", "health:check")
            );
        }
    }
    
    @Data
    public static class PerformanceConfig {
        
        /**
         * Caching configuration.
         */
        @Valid
        private CacheConfig cache = new CacheConfig();
        
        /**
         * Connection pooling configuration.
         */
        @Valid
        private PoolConfig pool = new PoolConfig();
        
        @Data
        public static class CacheConfig {
            @NotNull
            private Boolean enabled = true;
            
            @NotBlank
            private String type = "caffeine";
            
            @Min(10)
            @Max(10000)
            private int maxSize = 1000;
            
            @NotNull
            private Duration expireAfterWrite = Duration.ofMinutes(5);
            
            @NotNull
            private Duration expireAfterAccess = Duration.ofMinutes(2);
            
            public boolean isEnabled() {
                return enabled;
            }
        }
        
        @Data
        public static class PoolConfig {
            @Min(1)
            @Max(100)
            private int corePoolSize = 10;
            
            @Min(1)
            @Max(500)
            private int maxPoolSize = 50;
            
            @NotNull
            private Duration keepAliveTime = Duration.ofMinutes(2);
            
            @Min(10)
            @Max(10000)
            private int queueCapacity = 1000;
        }
    }
    
    @Data
    public static class AuditConfig {
        
        @NotNull
        private Boolean enabled = true;
        
        /**
         * Events to audit.
         */
        @NotEmpty
        private List<String> auditEvents = List.of(
                "tool.invocation",
                "server.selection",
                "circuit.breaker.event",
                "security.authentication",
                "security.authorization"
        );
        
        /**
         * Audit log destination.
         */
        @NotBlank
        private String destination = "file";
        
        /**
         * Log retention period.
         */
        @NotNull
        private Duration retentionPeriod = Duration.ofDays(90);
        
        public boolean isEnabled() {
            return enabled;
        }
    }
    
    @Data
    public static class ConnectionConfig {
        
        /**
         * Connection timeouts.
         */
        @NotNull
        private Duration connectTimeout = Duration.ofSeconds(10);
        
        @NotNull
        private Duration readTimeout = Duration.ofSeconds(30);
        
        @NotNull
        private Duration writeTimeout = Duration.ofSeconds(30);
        
        /**
         * Keep-alive settings.
         */
        @NotNull
        private Boolean keepAlive = true;
        
        @NotNull
        private Duration keepAliveTimeout = Duration.ofMinutes(5);
        
        /**
         * SSL/TLS configuration.
         */
        @Valid
        private TlsConfig tls = new TlsConfig();
        
        @Data
        public static class TlsConfig {
            @NotNull
            private Boolean enabled = true;
            
            @NotNull
            private Boolean verifyHostname = true;
            
            @NotEmpty
            private List<String> protocols = List.of("TLSv1.2", "TLSv1.3");
            
            private String trustStore;
            private String trustStorePassword;
            private String keyStore;
            private String keyStorePassword;
        }
    }
    
    /**
     * Validate configuration consistency.
     */
    public void validateConfiguration() {
        if (enabled) {
            if (monitoring.enabled && monitoring.health.interval.compareTo(Duration.ofSeconds(5)) < 0) {
                throw new IllegalStateException("Health check interval must be at least 5 seconds");
            }
            
            if (retry.enabled && retry.defaultPolicy.maxAttempts < 1) {
                throw new IllegalStateException("Retry max attempts must be at least 1");
            }
            
            if (rateLimit.enabled && rateLimit.globalRequestsPerMinute < 1) {
                throw new IllegalStateException("Global rate limit must be at least 1 request per minute");
            }
            
            if (security.enabled && security.auth.enabled && security.auth.tokenExpiration.isNegative()) {
                throw new IllegalStateException("Token expiration must be positive");
            }
        }
    }
    
    /**
     * Get environment-specific configuration.
     */
    public boolean isProduction() {
        return "prod".equalsIgnoreCase(environment) || "production".equalsIgnoreCase(environment);
    }
    
    public boolean isDevelopment() {
        return "dev".equalsIgnoreCase(environment) || "development".equalsIgnoreCase(environment);
    }
    
    public boolean isStaging() {
        return "staging".equalsIgnoreCase(environment) || "stage".equalsIgnoreCase(environment);
    }
    
    /**
     * Check if production features are enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }
}