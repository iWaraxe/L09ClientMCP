package com.coherentsolutions.l09clientmcp.multiserver;

import com.coherentsolutions.l09clientmcp.mcp.McpToolResult;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Interface representing a connection to an MCP server.
 * 
 * This abstraction allows different types of MCP server connections
 * (STDIO, SSE, WebSocket) to be managed uniformly in the multi-server environment.
 * 
 * Educational Focus:
 * - Abstraction patterns for different connection types
 * - Connection lifecycle management
 * - Tool capability discovery
 * - Health checking and monitoring
 */
public interface McpServerConnection {
    
    /**
     * Get the unique identifier for this server.
     */
    String getServerId();
    
    /**
     * Get the connection URL or identifier.
     */
    String getUrl();
    
    /**
     * Get the connection type (STDIO, SSE, WebSocket).
     */
    ConnectionType getConnectionType();
    
    /**
     * Check if the connection is currently active.
     */
    boolean isConnected();
    
    /**
     * Establish connection to the MCP server.
     */
    void connect() throws McpConnectionException;
    
    /**
     * Disconnect from the MCP server.
     */
    void disconnect();
    
    /**
     * Get the set of tools available on this server.
     */
    Set<String> getAvailableTools();
    
    /**
     * Invoke a tool on this server.
     */
    McpToolResult invokeTool(String toolName, Map<String, Object> arguments) throws McpConnectionException;
    
    /**
     * Perform a health check on this server.
     */
    HealthCheckResult performHealthCheck();
    
    /**
     * Get server configuration and metadata.
     */
    ServerInfo getServerInfo();
    
    /**
     * Get connection statistics.
     */
    ConnectionStats getConnectionStats();
    
    /**
     * Connection type enumeration.
     */
    enum ConnectionType {
        STDIO, SSE, WEBSOCKET, HTTP
    }
    
    /**
     * Health check result.
     */
    record HealthCheckResult(
        boolean healthy,
        long responseTimeMs,
        String message,
        long timestamp
    ) {}
    
    /**
     * Server information.
     */
    record ServerInfo(
        String serverId,
        String version,
        String description,
        Set<String> capabilities,
        Map<String, Object> metadata
    ) {}
    
    /**
     * Connection statistics.
     */
    record ConnectionStats(
        long totalRequests,
        long successfulRequests,
        long failedRequests,
        long averageResponseTimeMs,
        long lastRequestTime,
        Duration uptime
    ) {
        public double getSuccessRate() {
            return totalRequests > 0 ? (double) successfulRequests / totalRequests : 0.0;
        }
        
        public double getFailureRate() {
            return totalRequests > 0 ? (double) failedRequests / totalRequests : 0.0;
        }
    }
    
    /**
     * Exception for MCP connection-related errors.
     */
    class McpConnectionException extends Exception {
        public McpConnectionException(String message) {
            super(message);
        }
        
        public McpConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}