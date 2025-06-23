package com.coherentsolutions.l09clientmcp.service;

/**
 * Service interface for Model Context Protocol (MCP) client operations.
 * 
 * This interface provides abstraction for MCP functionality, allowing
 * the application to gracefully handle both MCP-enabled and MCP-disabled scenarios.
 * 
 * Educational Note: Starting with basic health checking and status monitoring.
 * Future branches will extend this interface with tool discovery and invocation.
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
}