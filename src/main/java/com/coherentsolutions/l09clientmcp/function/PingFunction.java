package com.coherentsolutions.l09clientmcp.function;

import com.coherentsolutions.l09clientmcp.mcp.McpToolResult;
import com.coherentsolutions.l09clientmcp.service.McpClientService;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.function.Function;

/**
 * Spring AI Function for MCP Ping Tool integration.
 * 
 * This function enables the AI model to test MCP connectivity
 * and demonstrate basic tool functionality when requested by users.
 * 
 * Educational Focus:
 * - Simple function implementation pattern
 * - No-parameter function design
 * - Connectivity testing through AI interaction
 * - Timestamp and metadata handling
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Description("Test MCP server connectivity and get server timestamp")
public class PingFunction implements Function<PingFunction.Request, PingFunction.Response> {
    
    private final McpClientService mcpClientService;
    
    /**
     * Request record - empty for ping (no parameters needed).
     */
    public record Request(
        @JsonPropertyDescription("Optional message for ping context")
        String context
    ) {
        // Default constructor for no-parameter calls
        public Request() {
            this(null);
        }
    }
    
    /**
     * Response record for ping results.
     */
    public record Response(
        boolean success,
        String message,
        String timestamp,
        String serverType,
        String error
    ) {}
    
    @Override
    public Response apply(Request request) {
        log.info("AI requested ping function with context: '{}'", request.context());
        
        try {
            // Invoke MCP ping tool
            McpToolResult result = mcpClientService.invokeTool("ping", new HashMap<>());
            
            if (result.isSuccess()) {
                String timestamp = result.getMetadata() != null ? 
                    (String) result.getMetadata().get("timestamp") : null;
                String serverType = result.getMetadata() != null ? 
                    (String) result.getMetadata().get("server_type") : "unknown";
                
                log.info("Ping successful: {}", result.getContent());
                
                return new Response(
                    true,
                    result.getContent(),
                    timestamp,
                    serverType,
                    null
                );
            } else {
                log.warn("Ping failed: {}", result.getContent());
                
                return new Response(
                    false,
                    null,
                    null,
                    null,
                    result.getContent()
                );
            }
            
        } catch (Exception e) {
            log.error("Error in ping function execution", e);
            
            return new Response(
                false,
                null,
                null,
                null,
                "Ping function error: " + e.getMessage()
            );
        }
    }
}