package com.coherentsolutions.l09clientmcp.multiserver;

import com.coherentsolutions.l09clientmcp.mcp.McpToolResult;
import com.coherentsolutions.l09clientmcp.mcp.MockMcpEchoServer;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mock MCP server connection implementation for testing and development.
 * 
 * This implementation wraps our existing MockMcpEchoServer to provide
 * multi-server capabilities for testing scenarios.
 * 
 * Educational Focus:
 * - Adapting existing components to new interfaces
 * - Statistics collection and monitoring
 * - Health checking implementation
 * - Connection lifecycle management
 */
@Slf4j
public class MockMcpServerConnection implements McpServerConnection {
    
    private final String serverId;
    private final MockMcpEchoServer mockServer;
    private final Instant createdAt;
    
    // Statistics tracking
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    private volatile long lastRequestTime = 0;
    
    public MockMcpServerConnection(String serverId) {
        this.serverId = serverId;
        this.mockServer = new MockMcpEchoServer();
        this.createdAt = Instant.now();
        log.info("Created mock MCP server connection: {}", serverId);
    }
    
    @Override
    public String getServerId() {
        return serverId;
    }
    
    @Override
    public String getUrl() {
        return "mock://" + serverId;
    }
    
    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.STDIO;
    }
    
    @Override
    public boolean isConnected() {
        return mockServer.isConnected();
    }
    
    @Override
    public void connect() throws McpConnectionException {
        try {
            mockServer.connect();
            log.info("Mock server {} connected successfully", serverId);
        } catch (Exception e) {
            throw new McpConnectionException("Failed to connect to mock server: " + serverId, e);
        }
    }
    
    @Override
    public void disconnect() {
        try {
            mockServer.disconnect();
            log.info("Mock server {} disconnected", serverId);
        } catch (Exception e) {
            log.warn("Error disconnecting mock server {}: {}", serverId, e.getMessage());
        }
    }
    
    @Override
    public Set<String> getAvailableTools() {
        return Set.of("echo", "ping");
    }
    
    @Override
    public McpToolResult invokeTool(String toolName, Map<String, Object> arguments) throws McpConnectionException {
        long startTime = System.currentTimeMillis();
        totalRequests.incrementAndGet();
        lastRequestTime = startTime;
        
        try {
            McpToolResult result;
            
            switch (toolName) {
                case "echo" -> {
                    result = mockServer.invokeTool("echo", arguments);
                }
                case "ping" -> {
                    result = mockServer.invokeTool("ping", arguments);
                }
                default -> throw new McpConnectionException("Unknown tool: " + toolName);
            }
            
            long responseTime = System.currentTimeMillis() - startTime;
            totalResponseTime.addAndGet(responseTime);
            successfulRequests.incrementAndGet();
            
            log.debug("Tool {} executed successfully on server {} in {}ms", toolName, serverId, responseTime);
            return result;
            
        } catch (Exception e) {
            failedRequests.incrementAndGet();
            long responseTime = System.currentTimeMillis() - startTime;
            totalResponseTime.addAndGet(responseTime);
            
            log.error("Tool {} failed on server {}: {}", toolName, serverId, e.getMessage());
            throw new McpConnectionException("Tool execution failed: " + toolName, e);
        }
    }
    
    @Override
    public HealthCheckResult performHealthCheck() {
        long startTime = System.currentTimeMillis();
        
        try {
            if (!isConnected()) {
                return new HealthCheckResult(false, 0, "Server not connected", startTime);
            }
            
            // Perform a simple ping to check health
            McpToolResult pingResult = mockServer.invokeTool("ping", Map.of());
            long responseTime = System.currentTimeMillis() - startTime;
            
            boolean healthy = pingResult.isSuccess();
            String message = healthy ? "Health check passed" : "Ping failed: " + pingResult.getContent();
            
            return new HealthCheckResult(healthy, responseTime, message, startTime);
            
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return new HealthCheckResult(false, responseTime, "Health check error: " + e.getMessage(), startTime);
        }
    }
    
    @Override
    public ServerInfo getServerInfo() {
        return new ServerInfo(
            serverId,
            "1.0.0-mock",
            "Mock MCP server for testing and development",
            Set.of("echo", "ping", "text-processing"),
            Map.of(
                "type", "mock",
                "created_at", createdAt.toString(),
                "supports_concurrency", true,
                "max_concurrent_requests", 100
            )
        );
    }
    
    @Override
    public ConnectionStats getConnectionStats() {
        long total = totalRequests.get();
        long successful = successfulRequests.get();
        long failed = failedRequests.get();
        long avgResponseTime = total > 0 ? totalResponseTime.get() / total : 0;
        Duration uptime = Duration.between(createdAt, Instant.now());
        
        return new ConnectionStats(
            total,
            successful,
            failed,
            avgResponseTime,
            lastRequestTime,
            uptime
        );
    }
}