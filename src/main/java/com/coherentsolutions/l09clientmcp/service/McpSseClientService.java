package com.coherentsolutions.l09clientmcp.service;

import com.coherentsolutions.l09clientmcp.mcp.McpTool;
import com.coherentsolutions.l09clientmcp.mcp.McpToolResult;
import com.coherentsolutions.l09clientmcp.mcp.sse.McpSseClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * SSE-based implementation of MCP client service for real-time communication with external MCP servers.
 * Branch 5: Provides production-ready MCP client using Server-Sent Events transport.
 * 
 * Educational Focus:
 * - Real MCP protocol implementation over network
 * - SSE transport layer for real-time communication
 * - Connection lifecycle management with retry logic
 * - Graceful degradation and error handling
 * - Configuration-driven server connections
 */
@Slf4j
@Service
@ConditionalOnProperty(value = "spring.ai.mcp.client.type", havingValue = "SYNC")
public class McpSseClientService implements McpClientService {
    
    private final boolean mcpEnabled;
    private final String mcpType;
    private final int requestTimeout;
    private final String serverUrl;
    private final int retryAttempts;
    private final int retryDelaySeconds;
    
    private McpSseClient sseClient;
    private volatile boolean connectionInitialized;
    
    public McpSseClientService(
            @Value("${spring.ai.mcp.client.enabled:false}") boolean mcpEnabled,
            @Value("${spring.ai.mcp.client.type:SSE}") String mcpType,
            @Value("${spring.ai.mcp.client.request-timeout:30s}") String requestTimeoutStr,
            @Value("${spring.ai.mcp.client.sse.connections.echo-server.url:http://localhost:3000/sse}") String serverUrl,
            @Value("${spring.ai.mcp.client.sse.connections.echo-server.max-retries:3}") int retryAttempts,
            @Value("${spring.ai.mcp.client.sse.connections.echo-server.retry-delay:5s}") String retryDelayStr) {
        
        this.mcpEnabled = mcpEnabled;
        this.mcpType = mcpType;
        this.requestTimeout = parseTimeout(requestTimeoutStr);
        this.serverUrl = serverUrl;
        this.retryAttempts = retryAttempts;
        this.retryDelaySeconds = parseTimeout(retryDelayStr);
        this.connectionInitialized = false;
        
        log.info("MCP SSE Client Service initialized - Enabled: {}, Type: {}, Server: {}, Timeout: {}s", 
                mcpEnabled, mcpType, serverUrl, this.requestTimeout);
                
        if (!mcpEnabled) {
            log.info("MCP is disabled. Application will run in baseline mode without external tool access.");
        }
    }
    
    @PostConstruct
    public void initialize() {
        if (mcpEnabled) {
            try {
                log.info("Initializing SSE MCP client connection to: {}", serverUrl);
                sseClient = new McpSseClient(serverUrl, requestTimeout, retryAttempts, retryDelaySeconds);
                
                // Start connection asynchronously
                CompletableFuture<Void> connectionFuture = sseClient.connect();
                connectionFuture.whenComplete((result, error) -> {
                    if (error == null) {
                        connectionInitialized = true;
                        log.info("MCP SSE client connection established successfully");
                    } else {
                        log.error("Failed to establish MCP SSE connection", error);
                    }
                });
                
                // Wait a short time for connection to establish
                try {
                    connectionFuture.get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("Initial connection attempt did not complete immediately: {}", e.getMessage());
                }
                
            } catch (Exception e) {
                log.error("Failed to initialize MCP SSE client", e);
            }
        }
    }
    
    @PreDestroy
    public void cleanup() {
        if (mcpEnabled && sseClient != null) {
            try {
                sseClient.disconnect();
                log.info("MCP SSE client disconnected cleanly");
            } catch (Exception e) {
                log.warn("Error during MCP SSE client cleanup", e);
            }
        }
    }
    
    @Override
    public boolean isEnabled() {
        return mcpEnabled;
    }
    
    @Override
    public boolean isHealthy() {
        if (!mcpEnabled) {
            log.debug("MCP health check: disabled (returning healthy for graceful degradation)");
            return true;
        }
        
        if (sseClient == null) {
            log.debug("MCP health check: SSE client not initialized");
            return false;
        }
        
        boolean connected = sseClient.isConnected();
        log.debug("MCP health check: SSE client connected = {}", connected);
        return connected;
    }
    
    @Override
    public String getStatus() {
        if (!mcpEnabled) {
            return "MCP disabled - running in baseline mode";
        }
        
        if (sseClient == null) {
            return "MCP SSE client not initialized";
        }
        
        String connectionStatus = sseClient.isConnected() ? "connected" : "disconnected";
        int toolCount = 0;
        
        try {
            if (sseClient.isConnected()) {
                toolCount = listTools().size();
            }
        } catch (Exception e) {
            log.debug("Error getting tool count for status", e);
        }
        
        return String.format("MCP enabled - Type: %s, Server: %s, Status: %s (%d tools)", 
                mcpType, serverUrl, connectionStatus, toolCount);
    }
    
    @Override
    public List<McpTool> listTools() {
        if (!mcpEnabled) {
            log.debug("MCP is disabled, returning empty tools list");
            return Collections.emptyList();
        }
        
        if (sseClient == null) {
            log.warn("SSE client not initialized, returning empty tools list");
            return Collections.emptyList();
        }
        
        if (!sseClient.isConnected()) {
            log.warn("SSE client not connected, returning empty tools list");
            return Collections.emptyList();
        }
        
        try {
            List<McpTool> tools = sseClient.listTools();
            log.info("Retrieved {} tools from SSE MCP server", tools.size());
            return tools;
        } catch (Exception e) {
            log.error("Error listing tools from SSE MCP server", e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public McpToolResult invokeTool(String toolName, Map<String, Object> arguments) {
        if (!mcpEnabled) {
            throw new IllegalStateException("MCP is disabled - cannot invoke tools");
        }
        
        if (sseClient == null) {
            throw new IllegalStateException("SSE client not initialized - cannot invoke tools");
        }
        
        if (!sseClient.isConnected()) {
            throw new IllegalStateException("SSE client not connected - cannot invoke tools");
        }
        
        try {
            log.info("Invoking tool '{}' via SSE with arguments: {}", toolName, arguments);
            McpToolResult result = sseClient.invokeTool(toolName, arguments);
            log.info("Tool '{}' invocation via SSE {} - Result: {}", 
                    toolName, result.isSuccess() ? "succeeded" : "failed", result.getContent());
            return result;
        } catch (Exception e) {
            log.error("Error invoking tool '{}' via SSE", toolName, e);
            return McpToolResult.builder()
                .success(false)
                .content("SSE tool invocation failed: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * Parse timeout string (like "30s") to integer seconds.
     * Supports formats: "30s", "30", "1m"
     */
    private int parseTimeout(String timeoutStr) {
        if (timeoutStr == null || timeoutStr.trim().isEmpty()) {
            return 30;
        }
        
        timeoutStr = timeoutStr.trim().toLowerCase();
        
        try {
            if (timeoutStr.endsWith("s")) {
                return Integer.parseInt(timeoutStr.substring(0, timeoutStr.length() - 1));
            } else if (timeoutStr.endsWith("m")) {
                return Integer.parseInt(timeoutStr.substring(0, timeoutStr.length() - 1)) * 60;
            } else {
                return Integer.parseInt(timeoutStr);
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid timeout format '{}', using default 30s", timeoutStr);
            return 30;
        }
    }
    
    /**
     * Get the SSE client for advanced operations (testing/debugging).
     */
    public McpSseClient getSseClient() {
        return sseClient;
    }
}