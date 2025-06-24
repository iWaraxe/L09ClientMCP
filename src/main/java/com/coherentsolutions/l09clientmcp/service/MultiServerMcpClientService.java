package com.coherentsolutions.l09clientmcp.service;

import com.coherentsolutions.l09clientmcp.config.MultiServerConfig;
import com.coherentsolutions.l09clientmcp.mcp.McpTool;
import com.coherentsolutions.l09clientmcp.mcp.McpToolResult;
import com.coherentsolutions.l09clientmcp.multiserver.*;
import com.coherentsolutions.l09clientmcp.multiserver.StdioMcpServerConnection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Multi-server implementation of MCP client service.
 * 
 * This service manages multiple MCP server connections, providing intelligent
 * load balancing, health monitoring, and circuit breaker functionality.
 * 
 * Educational Focus:
 * - Multi-server connection management
 * - Intelligent server selection and load balancing
 * - Circuit breaker pattern integration
 * - Health monitoring and failover
 * - Graceful degradation and error handling
 */
@Service
@ConditionalOnProperty(
    name = "spring.ai.mcp.multi-server.enabled", 
    havingValue = "true"
)
@Slf4j
public class MultiServerMcpClientService implements McpClientService {
    
    private final MultiServerConfig config;
    private final McpServerRegistry serverRegistry;
    private final RoundRobinServerSelector roundRobinSelector;
    private final WeightedServerSelector weightedSelector;
    private final McpCircuitBreaker circuitBreaker;
    private final McpHealthMonitor healthMonitor;
    
    public MultiServerMcpClientService(
            MultiServerConfig config,
            McpServerRegistry serverRegistry,
            @Qualifier("roundRobinServerSelector") RoundRobinServerSelector roundRobinSelector,
            @Qualifier("weightedServerSelector") WeightedServerSelector weightedSelector,
            McpCircuitBreaker circuitBreaker,
            McpHealthMonitor healthMonitor
    ) {
        this.config = config;
        this.serverRegistry = serverRegistry;
        this.roundRobinSelector = roundRobinSelector;
        this.weightedSelector = weightedSelector;
        this.circuitBreaker = circuitBreaker;
        this.healthMonitor = healthMonitor;
    }
    
    private ServerSelector activeSelector;
    
    @PostConstruct
    public void initialize() {
        if (!config.isEnabled()) {
            log.info("Multi-server MCP client is disabled");
            return;
        }
        
        log.info("Initializing multi-server MCP client with {} servers", 
                 config.getServers() != null ? config.getServers().size() : 0);
        
        // Validate configuration
        config.validateConfiguration();
        
        // Select load balancing strategy
        activeSelector = selectLoadBalancingStrategy();
        
        // Initialize servers
        initializeServers();
        
        // Configure circuit breakers
        configureCircuitBreakers();
        
        log.info("Multi-server MCP client initialized successfully with {} strategy", 
                 config.getLoadBalancingStrategy());
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down multi-server MCP client");
        
        if (serverRegistry != null) {
            for (String serverId : serverRegistry.getAllServerIds()) {
                serverRegistry.unregisterServer(serverId);
            }
        }
        
        log.info("Multi-server MCP client shutdown complete");
    }
    
    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }
    
    @Override
    public boolean isHealthy() {
        if (!isEnabled()) {
            return true; // Graceful degradation
        }
        
        McpHealthMonitor.HealthMonitorStats stats = healthMonitor.getMonitoringStats();
        
        // Consider healthy if at least 50% of servers are healthy
        return stats.getHealthyServerRatio() >= 0.5;
    }
    
    @Override
    public String getStatus() {
        if (!isEnabled()) {
            return "Multi-server MCP client is disabled";
        }
        
        McpServerRegistry.RegistryStats registryStats = serverRegistry.getRegistryStats();
        McpHealthMonitor.HealthMonitorStats healthStats = healthMonitor.getMonitoringStats();
        
        return String.format(
            "Multi-server MCP: %d/%d servers healthy, %d/%d tools available, strategy: %s",
            registryStats.getHealthyServers(),
            registryStats.getTotalServers(),
            registryStats.getAvailableTools(),
            registryStats.getTotalTools(),
            activeSelector.getStrategyName()
        );
    }
    
    @Override
    public List<McpTool> listTools() {
        if (!isEnabled()) {
            return Collections.emptyList();
        }
        
        Set<String> availableTools = serverRegistry.getAllAvailableTools();
        
        return availableTools.stream()
                .map(toolName -> {
                    Set<String> serverIds = serverRegistry.getServersForTool(toolName);
                    String description = String.format("Available on %d server(s): %s", 
                                                      serverIds.size(), 
                                                      String.join(", ", serverIds));
                    
                    return McpTool.builder()
                            .name(toolName)
                            .description(description)
                            .inputSchema(createToolSchema(toolName))
                            .build();
                })
                .collect(Collectors.toList());
    }
    
    @Override
    public McpToolResult invokeTool(String toolName, Map<String, Object> arguments) {
        if (!isEnabled()) {
            throw new IllegalStateException("Multi-server MCP client is disabled");
        }
        
        log.debug("Invoking tool {} with arguments: {}", toolName, arguments);
        
        // Get available servers for this tool
        List<McpServerConnection> candidates = serverRegistry.getHealthyServersForTool(toolName);
        
        if (candidates.isEmpty()) {
            String errorMsg = String.format("No healthy servers available for tool: %s", toolName);
            log.warn(errorMsg);
            return McpToolResult.builder()
                    .success(false)
                    .content(errorMsg)
                    .build();
        }
        
        // Select server using configured strategy
        ServerSelector.SelectionContext context = ServerSelector.SelectionContext.create(
            UUID.randomUUID().toString()
        );
        
        Optional<McpServerConnection> selectedServer = activeSelector.selectServer(toolName, candidates, context);
        
        if (selectedServer.isEmpty()) {
            String errorMsg = String.format("Server selection failed for tool: %s", toolName);
            log.error(errorMsg);
            return McpToolResult.builder()
                    .success(false)
                    .content(errorMsg)
                    .build();
        }
        
        McpServerConnection server = selectedServer.get();
        String serverId = server.getServerId();
        
        try {
            // Execute with circuit breaker protection
            McpToolResult result = circuitBreaker.executeWithCircuitBreaker(serverId, () -> {
                try {
                    return server.invokeTool(toolName, arguments);
                } catch (Exception e) {
                    throw new RuntimeException("Tool invocation failed", e);
                }
            });
            
            log.debug("Tool {} executed successfully on server {} in {}ms", 
                     toolName, serverId, result.getMetadata().get("execution_time_ms"));
            
            return result;
            
        } catch (McpCircuitBreaker.CircuitBreakerOpenException e) {
            log.warn("Circuit breaker open for server {}, trying fallback", serverId);
            return tryFallbackExecution(toolName, arguments, serverId, candidates);
            
        } catch (Exception e) {
            log.error("Tool execution failed on server {}: {}", serverId, e.getMessage());
            return McpToolResult.builder()
                    .success(false)
                    .content("Tool execution failed: " + e.getMessage())
                    .metadata(Map.of("server_id", serverId, "error_type", e.getClass().getSimpleName()))
                    .build();
        }
    }
    
    /**
     * Get multi-server specific statistics.
     */
    public MultiServerStats getMultiServerStats() {
        if (!isEnabled()) {
            return new MultiServerStats(false, 0, 0, 0, Map.of(), Map.of());
        }
        
        McpServerRegistry.RegistryStats registryStats = serverRegistry.getRegistryStats();
        ServerSelector.SelectionStats selectorStats = activeSelector.getSelectionStats();
        Map<String, McpCircuitBreaker.CircuitBreakerStats> circuitStats = circuitBreaker.getAllStats();
        
        return new MultiServerStats(
            true,
            registryStats.getTotalServers(),
            registryStats.getHealthyServers(),
            registryStats.getAvailableTools(),
            selectorStats.serverSelectionCounts(),
            circuitStats
        );
    }
    
    /**
     * Force health check for all servers.
     */
    public void forceHealthCheck() {
        if (isEnabled()) {
            healthMonitor.forceHealthCheckAll();
        }
    }
    
    /**
     * Switch load balancing strategy at runtime.
     */
    public void switchLoadBalancingStrategy(MultiServerConfig.LoadBalancingStrategy strategy) {
        switch (strategy) {
            case ROUND_ROBIN -> activeSelector = roundRobinSelector;
            case WEIGHTED -> activeSelector = weightedSelector;
            default -> {
                log.warn("Unsupported load balancing strategy: {}, keeping current", strategy);
                return;
            }
        }
        
        log.info("Switched load balancing strategy to: {}", strategy);
    }
    
    // Private helper methods
    
    private ServerSelector selectLoadBalancingStrategy() {
        return switch (config.getLoadBalancingStrategy()) {
            case ROUND_ROBIN -> roundRobinSelector;
            case WEIGHTED -> weightedSelector;
            default -> {
                log.warn("Unsupported strategy {}, defaulting to ROUND_ROBIN", 
                         config.getLoadBalancingStrategy());
                yield roundRobinSelector;
            }
        };
    }
    
    private void initializeServers() {
        Map<String, MultiServerConfig.ServerConfig> enabledServers = config.getEnabledServers();
        
        for (Map.Entry<String, MultiServerConfig.ServerConfig> entry : enabledServers.entrySet()) {
            String serverId = entry.getKey();
            MultiServerConfig.ServerConfig serverConfig = entry.getValue();
            
            try {
                McpServerConnection connection = createServerConnection(serverId, serverConfig);
                connection.connect();
                serverRegistry.registerServer(serverId, connection);
                
                log.info("Successfully initialized server: {} ({})", serverId, serverConfig.getUrl());
                
            } catch (Exception e) {
                log.error("Failed to initialize server {}: {}", serverId, e.getMessage());
                // Continue with other servers rather than failing completely
            }
        }
    }
    
    private McpServerConnection createServerConnection(String serverId, MultiServerConfig.ServerConfig config) {
        return switch (config.getType()) {
            case MOCK -> new MockMcpServerConnection(serverId);
            case SSE -> throw new UnsupportedOperationException("SSE connections not yet implemented in multi-server");
            case STDIO -> createStdioConnection(serverId, config);
            default -> throw new IllegalArgumentException("Unsupported server type: " + config.getType());
        };
    }
    
    private McpServerConnection createStdioConnection(String serverId, MultiServerConfig.ServerConfig config) {
        // Extract STDIO-specific configuration from metadata
        Map<String, Object> metadata = config.getMetadata();
        if (metadata == null) {
            throw new IllegalArgumentException("STDIO server " + serverId + " missing metadata configuration");
        }
        
        String command = (String) metadata.get("command");
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("STDIO server " + serverId + " missing command");
        }
        
        @SuppressWarnings("unchecked")
        List<String> args = (List<String>) metadata.get("args");
        
        @SuppressWarnings("unchecked")
        Map<String, String> env = (Map<String, String>) metadata.get("env");
        
        log.info("Creating STDIO connection for server {} with command: {}", serverId, command);
        
        return new StdioMcpServerConnection(serverId, command, args, env);
    }
    
    private void configureCircuitBreakers() {
        for (String serverId : serverRegistry.getAllServerIds()) {
            MultiServerConfig.CircuitBreakerConfig cbConfig = config.getCircuitBreakerConfig(serverId);
            
            McpCircuitBreaker.CircuitBreakerConfig circuitConfig = 
                new McpCircuitBreaker.CircuitBreakerConfig(
                    cbConfig.getFailureThreshold(),
                    cbConfig.getTimeout(),
                    cbConfig.getHalfOpenRetryDelay(),
                    cbConfig.getHalfOpenMaxCalls(),
                    cbConfig.getFailureRateThreshold()
                );
            
            circuitBreaker.configureServer(serverId, circuitConfig);
        }
    }
    
    private McpToolResult tryFallbackExecution(String toolName, Map<String, Object> arguments, 
                                             String failedServerId, List<McpServerConnection> allCandidates) {
        // Try other servers excluding the failed one
        List<McpServerConnection> fallbackCandidates = allCandidates.stream()
                .filter(server -> !server.getServerId().equals(failedServerId))
                .collect(Collectors.toList());
        
        if (fallbackCandidates.isEmpty()) {
            return McpToolResult.builder()
                    .success(false)
                    .content("No fallback servers available for tool: " + toolName)
                    .build();
        }
        
        // Use round-robin for fallback (simpler strategy)
        McpServerConnection fallbackServer = fallbackCandidates.get(0);
        
        try {
            McpToolResult result = fallbackServer.invokeTool(toolName, arguments);
            log.info("Fallback execution successful on server {} after {} failed", 
                     fallbackServer.getServerId(), failedServerId);
            return result;
            
        } catch (Exception e) {
            log.error("Fallback execution also failed: {}", e.getMessage());
            return McpToolResult.builder()
                    .success(false)
                    .content("Tool execution failed on primary and fallback servers")
                    .metadata(Map.of(
                        "primary_server", failedServerId,
                        "fallback_server", fallbackServer.getServerId(),
                        "fallback_error", e.getMessage()
                    ))
                    .build();
        }
    }
    
    private Map<String, Object> createToolSchema(String toolName) {
        // Simple schema creation for multi-server tools
        return Map.of(
            "type", "object",
            "description", "Tool available through multi-server MCP setup",
            "properties", Map.of(
                "message", Map.of("type", "string", "description", "Input message")
            ),
            "required", List.of("message")
        );
    }
    
    /**
     * Multi-server statistics record.
     */
    public record MultiServerStats(
        boolean enabled,
        int totalServers,
        int healthyServers,
        int availableTools,
        Map<String, Long> serverSelectionCounts,
        Map<String, McpCircuitBreaker.CircuitBreakerStats> circuitBreakerStats
    ) {}
}