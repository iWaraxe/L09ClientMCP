package com.coherentsolutions.l09clientmcp.config;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for multi-server MCP setup.
 * 
 * This configuration class manages settings for multiple MCP server connections,
 * load balancing strategies, health monitoring, and circuit breaker parameters.
 * 
 * Educational Focus:
 * - Complex configuration property binding
 * - Nested configuration structures
 * - Type-safe configuration with validation
 * - Conditional configuration loading
 */
@Configuration
@ConditionalOnProperty(
    name = "spring.ai.mcp.multi-server.enabled", 
    havingValue = "true", 
    matchIfMissing = false
)
@ConfigurationProperties(prefix = "spring.ai.mcp.multi-server")
@Data
public class MultiServerConfig {
    
    /**
     * Enable/disable multi-server functionality.
     */
    private boolean enabled = false;
    
    /**
     * Load balancing strategy for server selection.
     */
    private LoadBalancingStrategy loadBalancingStrategy = LoadBalancingStrategy.ROUND_ROBIN;
    
    /**
     * Health check interval for all servers.
     */
    private Duration healthCheckInterval = Duration.ofSeconds(30);
    
    /**
     * Circuit breaker configuration.
     */
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();
    
    /**
     * Server configurations mapped by server ID.
     */
    private Map<String, ServerConfig> servers;
    
    /**
     * Load balancing strategy enumeration.
     */
    public enum LoadBalancingStrategy {
        ROUND_ROBIN,
        WEIGHTED,
        RANDOM,
        LEAST_CONNECTIONS
    }
    
    /**
     * Circuit breaker configuration.
     */
    @Data
    public static class CircuitBreakerConfig {
        /**
         * Number of consecutive failures before opening circuit.
         */
        private int failureThreshold = 5;
        
        /**
         * Time to wait before attempting to close an open circuit.
         */
        private Duration timeout = Duration.ofMinutes(1);
        
        /**
         * Delay between retry attempts in half-open state.
         */
        private Duration halfOpenRetryDelay = Duration.ofSeconds(30);
        
        /**
         * Failure rate threshold (0.0 to 1.0) for opening circuit.
         */
        private double failureRateThreshold = 0.5;
        
        /**
         * Maximum number of calls allowed in half-open state.
         */
        private int halfOpenMaxCalls = 3;
    }
    
    /**
     * Individual server configuration.
     */
    @Data
    public static class ServerConfig {
        /**
         * Server connection URL.
         */
        private String url;
        
        /**
         * Server connection type.
         */
        private ServerType type = ServerType.SSE;
        
        /**
         * List of tools provided by this server.
         */
        private List<String> tools;
        
        /**
         * Server weight for load balancing (higher = more requests).
         */
        private int weight = 1;
        
        /**
         * Enable/disable this server.
         */
        private boolean enabled = true;
        
        /**
         * Connection timeout for this server.
         */
        private Duration connectionTimeout = Duration.ofSeconds(30);
        
        /**
         * Human-readable description of the server.
         */
        private String description;
        
        /**
         * Additional metadata for the server.
         */
        private Map<String, Object> metadata;
        
        /**
         * Server-specific circuit breaker overrides.
         */
        private CircuitBreakerConfig circuitBreaker;
    }
    
    /**
     * Server type enumeration.
     */
    public enum ServerType {
        MOCK,       // Mock server for testing
        SSE,        // Server-Sent Events transport
        STDIO,      // Standard I/O transport
        WEBSOCKET,  // WebSocket transport
        HTTP        // Direct HTTP transport
    }
    
    /**
     * Get enabled servers only.
     */
    public Map<String, ServerConfig> getEnabledServers() {
        if (servers == null) {
            return Map.of();
        }
        
        return servers.entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled())
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue
                ));
    }
    
    /**
     * Get servers that provide a specific tool.
     */
    public Map<String, ServerConfig> getServersForTool(String toolName) {
        if (servers == null) {
            return Map.of();
        }
        
        return servers.entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled())
                .filter(entry -> entry.getValue().getTools() != null && 
                               entry.getValue().getTools().contains(toolName))
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue
                ));
    }
    
    /**
     * Get circuit breaker configuration for a specific server.
     * Falls back to global configuration if server-specific config is not available.
     */
    public CircuitBreakerConfig getCircuitBreakerConfig(String serverId) {
        if (servers != null && servers.containsKey(serverId)) {
            ServerConfig serverConfig = servers.get(serverId);
            if (serverConfig.getCircuitBreaker() != null) {
                return serverConfig.getCircuitBreaker();
            }
        }
        return circuitBreaker;
    }
    
    /**
     * Validate configuration after properties are bound.
     */
    public void validateConfiguration() {
        if (!enabled) {
            return;
        }
        
        if (servers == null || servers.isEmpty()) {
            throw new IllegalStateException("Multi-server enabled but no servers configured");
        }
        
        // Validate each server configuration
        for (Map.Entry<String, ServerConfig> entry : servers.entrySet()) {
            String serverId = entry.getKey();
            ServerConfig config = entry.getValue();
            
            if (config.getUrl() == null || config.getUrl().trim().isEmpty()) {
                throw new IllegalStateException("Server " + serverId + " has no URL configured");
            }
            
            if (config.getTools() == null || config.getTools().isEmpty()) {
                throw new IllegalStateException("Server " + serverId + " has no tools configured");
            }
            
            if (config.getWeight() <= 0) {
                throw new IllegalStateException("Server " + serverId + " must have positive weight");
            }
        }
        
        // Validate circuit breaker configuration
        if (circuitBreaker.getFailureThreshold() <= 0) {
            throw new IllegalStateException("Circuit breaker failure threshold must be positive");
        }
        
        if (circuitBreaker.getFailureRateThreshold() < 0.0 || circuitBreaker.getFailureRateThreshold() > 1.0) {
            throw new IllegalStateException("Circuit breaker failure rate threshold must be between 0.0 and 1.0");
        }
    }
    
    /**
     * Get summary statistics about the configuration.
     */
    public ConfigSummary getConfigSummary() {
        if (servers == null) {
            return new ConfigSummary(0, 0, 0, loadBalancingStrategy);
        }
        
        int totalServers = servers.size();
        int enabledServers = (int) servers.values().stream().filter(ServerConfig::isEnabled).count();
        int totalTools = servers.values().stream()
                .filter(ServerConfig::isEnabled)
                .flatMap(config -> config.getTools().stream())
                .collect(java.util.stream.Collectors.toSet())
                .size();
        
        return new ConfigSummary(totalServers, enabledServers, totalTools, loadBalancingStrategy);
    }
    
    /**
     * Configuration summary record.
     */
    public record ConfigSummary(
        int totalServers,
        int enabledServers,
        int uniqueTools,
        LoadBalancingStrategy strategy
    ) {}
}