package com.coherentsolutions.l09clientmcp.service;

import com.coherentsolutions.l09clientmcp.mcp.McpTool;
import com.coherentsolutions.l09clientmcp.mcp.McpToolResult;

import java.util.List;
import java.util.Map;

/**
 * Service interface for Model Context Protocol (MCP) client operations.
 * 
 * This interface provides abstraction for MCP functionality, allowing
 * the application to gracefully handle both MCP-enabled and MCP-disabled scenarios.
 * 
 * Branch 3 Update: Added tool discovery and invocation capabilities.
 */
public interface McpClientService {
    
    /**
     * Checks if MCP client is enabled and configured.
     * 
     * @return true if MCP is enabled and ready for use
     */
    boolean isEnabled();
    
    /**
     * Checks the health of MCP connections.
     * 
     * @return true if all MCP connections are healthy, false otherwise
     *         Returns true when MCP is disabled (graceful degradation)
     */
    boolean isHealthy();
    
    /**
     * Gets a human-readable status of the MCP client.
     * Useful for health endpoints and debugging.
     * 
     * @return status string describing MCP state
     */
    String getStatus();
    
    /**
     * Lists all available tools from connected MCP servers.
     * 
     * @return list of available tools, empty if MCP disabled
     */
    List<McpTool> listTools();
    
    /**
     * Invokes a specific MCP tool with given arguments.
     * 
     * @param toolName name of the tool to invoke
     * @param arguments tool arguments as key-value pairs
     * @return result of tool invocation
     * @throws IllegalStateException if MCP is disabled or tool not found
     */
    McpToolResult invokeTool(String toolName, Map<String, Object> arguments);
}