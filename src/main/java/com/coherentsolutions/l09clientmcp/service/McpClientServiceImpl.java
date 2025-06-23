package com.coherentsolutions.l09clientmcp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Implementation of MCP client service providing foundation for Model Context Protocol operations.
 * 
 * This implementation starts with basic configuration awareness and health monitoring.
 * Future branches will add actual MCP client integration and tool management.
 * 
 * Educational Focus:
 * - Configuration-driven behavior (enabled/disabled states)
 * - Graceful degradation when MCP is unavailable
 * - Foundation for future MCP tool integration
 */
@Slf4j
@Service
public class McpClientServiceImpl implements McpClientService {
    
    private final boolean mcpEnabled;
    private final String mcpType;
    private final int requestTimeout;
    
    public McpClientServiceImpl(
            @Value("${spring.ai.mcp.client.enabled:false}") boolean mcpEnabled,
            @Value("${spring.ai.mcp.client.type:STDIO}") String mcpType,
            @Value("${spring.ai.mcp.client.request-timeout:30s}") String requestTimeoutStr) {
        this.mcpEnabled = mcpEnabled;
        this.mcpType = mcpType;
        this.requestTimeout = parseTimeout(requestTimeoutStr);
        
        log.info("MCP Client Service initialized - Enabled: {}, Type: {}, Timeout: {}s", 
                mcpEnabled, mcpType, this.requestTimeout);
                
        if (!mcpEnabled) {
            log.info("MCP is disabled. Application will run in baseline mode without external tool access.");
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
        
        // TODO: In future branches, this will check actual MCP server connections
        log.debug("MCP health check: enabled but no servers configured yet");
        return true;
    }
    
    @Override
    public String getStatus() {
        if (!mcpEnabled) {
            return "MCP disabled - running in baseline mode";
        }
        
        return String.format("MCP enabled - Type: %s, Timeout: %ds, Servers: none configured", 
                mcpType, requestTimeout);
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