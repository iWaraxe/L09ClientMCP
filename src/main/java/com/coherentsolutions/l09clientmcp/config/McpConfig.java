package com.coherentsolutions.l09clientmcp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for Model Context Protocol (MCP) client setup.
 * 
 * This configuration is conditionally loaded based on MCP enable property.
 * Future branches will expand this with actual MCP client beans and server connections.
 * 
 * Educational Note: This demonstrates how to structure conditional configuration
 * for optional features that may not be available in all environments.
 */
@Configuration
@ConditionalOnProperty(
    name = "spring.ai.mcp.client.enabled", 
    havingValue = "true", 
    matchIfMissing = false
)
@Slf4j
public class McpConfig {
    
    public McpConfig() {
        log.info("MCP Configuration loaded - MCP client features are enabled");
        log.info("Future branches will add MCP client beans and server connections here");
    }
    
    // TODO: In future branches, add MCP client beans here:
    // - MCP client factory
    // - MCP server connection configurations
    // - MCP tool registry
    // - MCP session management
}