package com.coherentsolutions.l09clientmcp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Configuration class for Model Context Protocol (MCP) client setup.
 * 
 * This configuration is conditionally loaded based on MCP enable property.
 * Branch 8 adds Brave Search configuration and validation.
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
@EnableConfigurationProperties(BraveSearchConfig.class)
@RequiredArgsConstructor
@Slf4j
public class McpConfig {
    
    private final BraveSearchConfig braveSearchConfig;
    
    @PostConstruct
    public void initializeConfiguration() {
        log.info("MCP Configuration loaded - MCP client features are enabled");
        
        // Validate Brave Search configuration
        braveSearchConfig.validateConfiguration();
        
        if (!braveSearchConfig.isApiKeyConfigured()) {
            log.warn("Brave Search API key not properly configured - search functionality may be limited");
        }
        
        log.info("MCP server connections configured:");
        log.info("  - Echo Server: enabled");
        log.info("  - Brave Search: {}", braveSearchConfig.getMcpServer().isEnabled() ? "enabled" : "disabled");
    }
}