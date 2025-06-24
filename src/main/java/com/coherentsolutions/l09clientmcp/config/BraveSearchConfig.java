package com.coherentsolutions.l09clientmcp.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

/**
 * Configuration properties for Brave Search API integration.
 * 
 * This configuration class manages Brave Search API settings and credentials,
 * demonstrating production-grade configuration patterns for external API integration.
 * 
 * Educational Focus:
 * - Configuration properties binding with validation
 * - Conditional configuration loading
 * - Secure API key handling through environment variables
 * - Type-safe configuration with proper defaults
 */
@Configuration
@ConditionalOnProperty(
    name = "spring.ai.mcp.client.enabled", 
    havingValue = "true", 
    matchIfMissing = false
)
@ConfigurationProperties(prefix = "brave.search")
@Data
@Slf4j
public class BraveSearchConfig {
    
    /**
     * Brave Search API key - should be provided via environment variable BRAVE_API_KEY.
     */
    private String apiKey = "YOUR_API_KEY_HERE";
    
    /**
     * Base URL for Brave Search API.
     */
    private String baseUrl = "https://api.search.brave.com";
    
    /**
     * Request timeout for API calls.
     */
    private Duration timeout = Duration.ofSeconds(30);
    
    /**
     * Maximum number of search results to return.
     */
    private int maxResults = 10;
    
    /**
     * Default country code for search localization.
     */
    private String defaultCountry = "US";
    
    /**
     * Default safe search level.
     */
    private String defaultSafeSearch = "moderate";
    
    /**
     * Enable request/response logging for debugging.
     */
    private boolean debugLogging = false;
    
    /**
     * MCP server connection settings for Brave Search.
     */
    private McpServerSettings mcpServer = new McpServerSettings();
    
    @Data
    public static class McpServerSettings {
        /**
         * URL for the Brave Search MCP server (typically running locally).
         */
        private String url = "http://localhost:3001/sse";
        
        /**
         * Enable/disable Brave Search MCP server connection.
         */
        private boolean enabled = true;
        
        /**
         * Health check interval for server connectivity.
         */
        private Duration healthCheckInterval = Duration.ofSeconds(30);
        
        /**
         * Maximum retry attempts for failed connections.
         */
        private int maxRetries = 3;
        
        /**
         * Delay between retry attempts.
         */
        private Duration retryDelay = Duration.ofSeconds(5);
    }
    
    /**
     * Validate configuration after properties are bound.
     */
    public void validateConfiguration() {
        if ("YOUR_API_KEY_HERE".equals(apiKey)) {
            log.warn("Brave Search API key not configured - using placeholder value");
            log.warn("Set BRAVE_API_KEY environment variable for production use");
        }
        
        if (debugLogging) {
            log.info("Brave Search debug logging is enabled");
        }
        
        log.info("Brave Search configuration loaded:");
        log.info("  - Base URL: {}", baseUrl);
        log.info("  - Max Results: {}", maxResults);
        log.info("  - Timeout: {}", timeout);
        log.info("  - Default Country: {}", defaultCountry);
        log.info("  - MCP Server URL: {}", mcpServer.getUrl());
        log.info("  - MCP Server Enabled: {}", mcpServer.isEnabled());
    }
    
    /**
     * Get the complete API endpoint for web search.
     */
    public String getWebSearchEndpoint() {
        return baseUrl + "/res/v1/web/search";
    }
    
    /**
     * Get the complete API endpoint for news search.
     */
    public String getNewsSearchEndpoint() {
        return baseUrl + "/res/v1/news/search";
    }
    
    /**
     * Get the complete API endpoint for image search.
     */
    public String getImageSearchEndpoint() {
        return baseUrl + "/res/v1/images/search";
    }
    
    /**
     * Get the complete API endpoint for video search.
     */
    public String getVideoSearchEndpoint() {
        return baseUrl + "/res/v1/videos/search";
    }
    
    /**
     * Check if API key is properly configured (not the default placeholder).
     */
    public boolean isApiKeyConfigured() {
        return apiKey != null && !"YOUR_API_KEY_HERE".equals(apiKey) && !apiKey.trim().isEmpty();
    }
}