package com.coherentsolutions.l09clientmcp.function;

import com.coherentsolutions.l09clientmcp.mcp.McpToolResult;
import com.coherentsolutions.l09clientmcp.service.McpClientService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Spring AI Function for MCP Echo Tool integration.
 * 
 * This function enables the AI model to automatically use the echo tool
 * when users request text echoing, formatting, or transformation.
 * 
 * Educational Focus:
 * - Function-based tool integration with Spring AI
 * - Type-safe parameter handling with records
 * - Error handling and graceful degradation
 * - AI-driven tool selection and usage
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Description("Echo back a message with optional text formatting (uppercase, lowercase, reverse)")
public class EchoFunction implements Function<EchoFunction.Request, EchoFunction.Response> {
    
    private final McpClientService mcpClientService;
    
    /**
     * Request record for type-safe parameter handling.
     */
    public record Request(
        @JsonProperty(required = true)
        @JsonPropertyDescription("The message to echo back")
        String message,
        
        @JsonProperty(required = false)
        @JsonPropertyDescription("Optional formatting: 'uppercase', 'lowercase', 'reverse', or leave empty for no formatting")
        String format
    ) {}
    
    /**
     * Response record for structured tool results.
     */
    public record Response(
        boolean success,
        String result,
        String originalMessage,
        String formatApplied,
        String error
    ) {}
    
    @Override
    public Response apply(Request request) {
        log.info("AI requested echo function: message='{}', format='{}'", 
                request.message(), request.format());
        
        try {
            // Prepare arguments for MCP tool
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("message", request.message());
            
            if (request.format() != null && !request.format().trim().isEmpty()) {
                arguments.put("format", request.format().trim().toLowerCase());
            }
            
            // Invoke MCP echo tool
            McpToolResult result = mcpClientService.invokeTool("echo", arguments);
            
            if (result.isSuccess()) {
                String formatApplied = null;
                if (result.getMetadata() != null) {
                    Object formatObj = result.getMetadata().get("format_applied");
                    formatApplied = formatObj != null ? (String) formatObj : "none";
                }
                
                log.info("Echo tool succeeded: '{}' -> '{}'", request.message(), result.getContent());
                
                return new Response(
                    true,
                    result.getContent(),
                    request.message(),
                    formatApplied,
                    null
                );
            } else {
                log.warn("Echo tool failed: {}", result.getContent());
                
                return new Response(
                    false,
                    null,
                    request.message(),
                    null,
                    result.getContent()
                );
            }
            
        } catch (Exception e) {
            log.error("Error in echo function execution", e);
            
            return new Response(
                false,
                null,
                request.message(),
                null,
                "Echo function error: " + e.getMessage()
            );
        }
    }
}