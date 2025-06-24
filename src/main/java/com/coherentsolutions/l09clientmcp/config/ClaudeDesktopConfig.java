package com.coherentsolutions.l09clientmcp.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Configuration class for parsing Claude Desktop MCP servers JSON format.
 * 
 * This class supports the standard Claude Desktop configuration format,
 * allowing easy migration of existing MCP server configurations.
 * 
 * Educational Focus:
 * - JSON configuration parsing patterns
 * - External configuration format compatibility
 * - Configuration validation and defaults
 */
@Data
public class ClaudeDesktopConfig {
    
    /**
     * Map of MCP server configurations.
     * Key is the server name, value is the server configuration.
     */
    @JsonProperty("mcpServers")
    private Map<String, McpServerConfig> mcpServers;
    
    /**
     * Individual MCP server configuration.
     */
    @Data
    public static class McpServerConfig {
        /**
         * Command to execute (e.g., "npx", "node", "python").
         */
        private String command;
        
        /**
         * Command arguments.
         */
        private List<String> args;
        
        /**
         * Environment variables for the process.
         */
        private Map<String, String> env;
        
        /**
         * Additional metadata (optional).
         */
        private Map<String, Object> metadata;
        
        /**
         * Check if this is a valid configuration.
         */
        public boolean isValid() {
            return command != null && !command.trim().isEmpty();
        }
        
        /**
         * Get a human-readable description of this server.
         */
        public String getDescription() {
            if (args != null && !args.isEmpty()) {
                // Try to extract meaningful info from args
                for (String arg : args) {
                    if (arg.startsWith("@") && arg.contains("/")) {
                        // NPM package format
                        return "MCP Server: " + arg;
                    }
                }
            }
            return "MCP Server: " + command;
        }
        
        /**
         * Get the full command line as a string.
         */
        public String getFullCommand() {
            StringBuilder sb = new StringBuilder(command);
            if (args != null) {
                for (String arg : args) {
                    sb.append(" ").append(arg);
                }
            }
            return sb.toString();
        }
    }
    
    /**
     * Validate the configuration.
     */
    public void validate() {
        if (mcpServers == null || mcpServers.isEmpty()) {
            throw new IllegalStateException("No MCP servers configured");
        }
        
        for (Map.Entry<String, McpServerConfig> entry : mcpServers.entrySet()) {
            String serverName = entry.getKey();
            McpServerConfig config = entry.getValue();
            
            if (config == null) {
                throw new IllegalStateException("Server " + serverName + " has null configuration");
            }
            
            if (!config.isValid()) {
                throw new IllegalStateException("Server " + serverName + " has invalid configuration: missing command");
            }
        }
    }
    
    /**
     * Get the number of configured servers.
     */
    public int getServerCount() {
        return mcpServers != null ? mcpServers.size() : 0;
    }
    
    /**
     * Check if a specific server is configured.
     */
    public boolean hasServer(String serverName) {
        return mcpServers != null && mcpServers.containsKey(serverName);
    }
    
    /**
     * Get configuration for a specific server.
     */
    public McpServerConfig getServerConfig(String serverName) {
        return mcpServers != null ? mcpServers.get(serverName) : null;
    }
}