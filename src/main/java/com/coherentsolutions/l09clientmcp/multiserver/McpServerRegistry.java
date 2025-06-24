package com.coherentsolutions.l09clientmcp.multiserver;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for managing multiple MCP server connections.
 * 
 * This component maintains a registry of all available MCP servers and their capabilities,
 * enabling intelligent routing and load balancing across multiple server instances.
 * 
 * Educational Focus:
 * - Multi-server connection management
 * - Tool-to-server mapping and discovery
 * - Concurrent access patterns with thread safety
 * - Server health tracking and availability
 */
@Component
@Slf4j
public class McpServerRegistry {
    
    private final Map<String, McpServerConnection> servers = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> toolToServers = new ConcurrentHashMap<>();
    private final Map<String, ServerHealth> serverHealth = new ConcurrentHashMap<>();
    
    /**
     * Register a new MCP server with its connection and capabilities.
     */
    public void registerServer(String serverId, McpServerConnection connection) {
        log.info("Registering MCP server: {} with URL: {}", serverId, connection.getUrl());
        
        servers.put(serverId, connection);
        serverHealth.put(serverId, new ServerHealth(serverId, true, System.currentTimeMillis()));
        
        // Register tools provided by this server
        Set<String> tools = connection.getAvailableTools();
        for (String tool : tools) {
            toolToServers.computeIfAbsent(tool, k -> ConcurrentHashMap.newKeySet()).add(serverId);
        }
        
        log.info("Server {} registered with {} tools: {}", serverId, tools.size(), tools);
    }
    
    /**
     * Unregister an MCP server and remove all its tool mappings.
     */
    public void unregisterServer(String serverId) {
        log.info("Unregistering MCP server: {}", serverId);
        
        McpServerConnection connection = servers.remove(serverId);
        serverHealth.remove(serverId);
        
        if (connection != null) {
            // Remove server from all tool mappings
            Set<String> tools = connection.getAvailableTools();
            for (String tool : tools) {
                Set<String> serverSet = toolToServers.get(tool);
                if (serverSet != null) {
                    serverSet.remove(serverId);
                    if (serverSet.isEmpty()) {
                        toolToServers.remove(tool);
                    }
                }
            }
            
            // Disconnect the server
            try {
                connection.disconnect();
            } catch (Exception e) {
                log.warn("Error disconnecting server {}: {}", serverId, e.getMessage());
            }
        }
        
        log.info("Server {} unregistered successfully", serverId);
    }
    
    /**
     * Get all server IDs that provide a specific tool.
     */
    public Set<String> getServersForTool(String toolName) {
        return toolToServers.getOrDefault(toolName, Collections.emptySet());
    }
    
    /**
     * Get a healthy server connection by server ID.
     */
    public Optional<McpServerConnection> getHealthyServer(String serverId) {
        ServerHealth health = serverHealth.get(serverId);
        if (health != null && health.isHealthy()) {
            return Optional.ofNullable(servers.get(serverId));
        }
        return Optional.empty();
    }
    
    /**
     * Get all healthy server connections that provide a specific tool.
     */
    public List<McpServerConnection> getHealthyServersForTool(String toolName) {
        Set<String> serverIds = getServersForTool(toolName);
        return serverIds.stream()
                .map(this::getHealthyServer)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }
    
    /**
     * Get all healthy server connections.
     */
    public List<McpServerConnection> getAllHealthyServers() {
        return servers.entrySet().stream()
                .filter(entry -> {
                    ServerHealth health = serverHealth.get(entry.getKey());
                    return health != null && health.isHealthy();
                })
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }
    
    /**
     * Get all registered server IDs.
     */
    public Set<String> getAllServerIds() {
        return new HashSet<>(servers.keySet());
    }
    
    /**
     * Get server connection by ID (regardless of health status).
     */
    public Optional<McpServerConnection> getServer(String serverId) {
        return Optional.ofNullable(servers.get(serverId));
    }
    
    /**
     * Update server health status.
     */
    public void updateServerHealth(String serverId, boolean isHealthy, String reason) {
        ServerHealth health = serverHealth.get(serverId);
        if (health != null) {
            health.setHealthy(isHealthy);
            health.setLastChecked(System.currentTimeMillis());
            health.setHealthCheckReason(reason);
            
            if (!isHealthy) {
                log.warn("Server {} marked as unhealthy: {}", serverId, reason);
            } else {
                log.debug("Server {} health confirmed: {}", serverId, reason);
            }
        }
    }
    
    /**
     * Get server health information.
     */
    public Optional<ServerHealth> getServerHealth(String serverId) {
        return Optional.ofNullable(serverHealth.get(serverId));
    }
    
    /**
     * Get health status for all servers.
     */
    public Map<String, ServerHealth> getAllServerHealth() {
        return new HashMap<>(serverHealth);
    }
    
    /**
     * Get all available tools across all healthy servers.
     */
    public Set<String> getAllAvailableTools() {
        return toolToServers.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .anyMatch(serverId -> {
                            ServerHealth health = serverHealth.get(serverId);
                            return health != null && health.isHealthy();
                        }))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
    
    /**
     * Get registry statistics for monitoring.
     */
    public RegistryStats getRegistryStats() {
        int totalServers = servers.size();
        int healthyServers = (int) serverHealth.values().stream()
                .filter(ServerHealth::isHealthy)
                .count();
        int totalTools = toolToServers.size();
        int availableTools = getAllAvailableTools().size();
        
        return new RegistryStats(totalServers, healthyServers, totalTools, availableTools);
    }
    
    /**
     * Server health information.
     */
    @Data
    public static class ServerHealth {
        private final String serverId;
        private boolean healthy;
        private long lastChecked;
        private String healthCheckReason;
        private long responseTimeMs;
        private int failureCount;
        private long lastFailureTime;
        
        public ServerHealth(String serverId, boolean healthy, long lastChecked) {
            this.serverId = serverId;
            this.healthy = healthy;
            this.lastChecked = lastChecked;
            this.healthCheckReason = "Initial registration";
            this.responseTimeMs = 0;
            this.failureCount = 0;
            this.lastFailureTime = 0;
        }
        
        public void recordSuccess(long responseTime) {
            this.healthy = true;
            this.lastChecked = System.currentTimeMillis();
            this.responseTimeMs = responseTime;
            this.healthCheckReason = "Health check passed";
        }
        
        public void recordFailure(String reason) {
            this.healthy = false;
            this.lastChecked = System.currentTimeMillis();
            this.failureCount++;
            this.lastFailureTime = System.currentTimeMillis();
            this.healthCheckReason = reason;
        }
    }
    
    /**
     * Registry statistics for monitoring.
     */
    @Data
    public static class RegistryStats {
        private final int totalServers;
        private final int healthyServers;
        private final int totalTools;
        private final int availableTools;
        
        public double getHealthyServerRatio() {
            return totalServers > 0 ? (double) healthyServers / totalServers : 0.0;
        }
        
        public double getAvailableToolRatio() {
            return totalTools > 0 ? (double) availableTools / totalTools : 0.0;
        }
    }
}