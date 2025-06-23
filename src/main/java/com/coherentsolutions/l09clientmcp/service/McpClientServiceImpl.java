package com.coherentsolutions.l09clientmcp.service;

import com.coherentsolutions.l09clientmcp.mcp.McpTool;
import com.coherentsolutions.l09clientmcp.mcp.McpToolResult;
import com.coherentsolutions.l09clientmcp.mcp.MockMcpEchoServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Implementation of MCP client service providing foundation for Model Context Protocol operations.
 * 
 * Branch 3 Update: Added mock echo server integration with tool discovery and invocation.
 * 
 * Educational Focus:
 * - Configuration-driven behavior (enabled/disabled states)
 * - Tool discovery and invocation patterns
 * - Graceful degradation when MCP is unavailable
 * - Mock server for local development and testing
 */
@Slf4j
@Service
@ConditionalOnProperty(value = "spring.ai.mcp.client.type", havingValue = "STDIO", matchIfMissing = true)
public class McpClientServiceImpl implements McpClientService {
    
    private final boolean mcpEnabled;
    private final String mcpType;
    private final int requestTimeout;
    private final MockMcpEchoServer echoServer;
    
    public McpClientServiceImpl(
            @Value("${spring.ai.mcp.client.enabled:false}") boolean mcpEnabled,
            @Value("${spring.ai.mcp.client.type:STDIO}") String mcpType,
            @Value("${spring.ai.mcp.client.request-timeout:30s}") String requestTimeoutStr,
            MockMcpEchoServer echoServer) {
        this.mcpEnabled = mcpEnabled;
        this.mcpType = mcpType;
        this.requestTimeout = parseTimeout(requestTimeoutStr);
        this.echoServer = echoServer;
        
        log.info("MCP Client Service initialized - Enabled: {}, Type: {}, Timeout: {}s", 
                mcpEnabled, mcpType, this.requestTimeout);
                
        if (!mcpEnabled) {
            log.info("MCP is disabled. Application will run in baseline mode without external tool access.");
        }
    }
    
    @PostConstruct
    public void initialize() {
        if (mcpEnabled) {
            try {
                echoServer.connect();
                log.info("MCP Echo Server connection established successfully");
            } catch (Exception e) {
                log.error("Failed to connect to MCP Echo Server", e);
            }
        }
    }
    
    @PreDestroy
    public void cleanup() {
        if (mcpEnabled && echoServer.isConnected()) {
            try {
                echoServer.disconnect();
                log.info("MCP Echo Server disconnected cleanly");
            } catch (Exception e) {
                log.warn("Error during MCP Echo Server cleanup", e);
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
        
        boolean echoServerHealthy = echoServer.isConnected();
        log.debug("MCP health check: echo server connected = {}", echoServerHealthy);
        return echoServerHealthy;
    }
    
    @Override
    public String getStatus() {
        if (!mcpEnabled) {
            return "MCP disabled - running in baseline mode";
        }
        
        String echoStatus = echoServer.isConnected() ? "connected" : "disconnected";
        int toolCount = echoServer.isConnected() ? echoServer.listTools().size() : 0;
        
        return String.format("MCP enabled - Type: %s, Timeout: %ds, Echo Server: %s (%d tools)", 
                mcpType, requestTimeout, echoStatus, toolCount);
    }
    
    @Override
    public List<McpTool> listTools() {
        if (!mcpEnabled) {
            log.debug("MCP is disabled, returning empty tools list");
            return Collections.emptyList();
        }
        
        if (!echoServer.isConnected()) {
            log.warn("Echo server not connected, returning empty tools list");
            return Collections.emptyList();
        }
        
        try {
            List<McpTool> tools = echoServer.listTools();
            log.info("Retrieved {} tools from echo server", tools.size());
            return tools;
        } catch (Exception e) {
            log.error("Error listing tools from echo server", e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public McpToolResult invokeTool(String toolName, Map<String, Object> arguments) {
        if (!mcpEnabled) {
            throw new IllegalStateException("MCP is disabled - cannot invoke tools");
        }
        
        if (!echoServer.isConnected()) {
            throw new IllegalStateException("Echo server not connected - cannot invoke tools");
        }
        
        try {
            log.info("Invoking tool '{}' with arguments: {}", toolName, arguments);
            McpToolResult result = echoServer.invokeTool(toolName, arguments);
            log.info("Tool '{}' invocation {} - Result: {}", 
                    toolName, result.isSuccess() ? "succeeded" : "failed", result.getContent());
            return result;
        } catch (Exception e) {
            log.error("Error invoking tool '{}'", toolName, e);
            return McpToolResult.builder()
                .success(false)
                .content("Tool invocation failed: " + e.getMessage())
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
}