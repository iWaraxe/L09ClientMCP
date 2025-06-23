package com.coherentsolutions.l09clientmcp.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class MockMcpEchoServer {
    
    private boolean connected = false;
    
    public void connect() {
        log.info("Connecting to Mock MCP Echo Server...");
        this.connected = true;
        log.info("✅ Mock MCP Echo Server connected successfully");
    }
    
    public void disconnect() {
        log.info("Disconnecting from Mock MCP Echo Server...");
        this.connected = false;
        log.info("✅ Mock MCP Echo Server disconnected");
    }
    
    public boolean isConnected() {
        return connected;
    }
    
    public List<McpTool> listTools() {
        if (!connected) {
            throw new IllegalStateException("Echo server not connected");
        }
        
        log.debug("Listing available tools from Mock Echo Server");
        return List.of(
            McpTool.builder()
                .name("echo")
                .description("Echoes back the provided message with optional formatting")
                .inputSchema(Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "message", Map.of(
                            "type", "string",
                            "description", "The message to echo back"
                        ),
                        "format", Map.of(
                            "type", "string",
                            "description", "Optional formatting: 'uppercase', 'lowercase', or 'reverse'",
                            "enum", List.of("uppercase", "lowercase", "reverse")
                        )
                    ),
                    "required", List.of("message")
                ))
                .build(),
            
            McpTool.builder()
                .name("ping")
                .description("Simple ping tool that returns 'pong' with timestamp")
                .inputSchema(Map.of(
                    "type", "object",
                    "properties", Map.of(),
                    "required", List.of()
                ))
                .build()
        );
    }
    
    public McpToolResult invokeTool(String toolName, Map<String, Object> arguments) {
        if (!connected) {
            throw new IllegalStateException("Echo server not connected");
        }
        
        log.debug("Invoking tool '{}' with arguments: {}", toolName, arguments);
        
        return switch (toolName) {
            case "echo" -> handleEcho(arguments);
            case "ping" -> handlePing(arguments);
            default -> McpToolResult.builder()
                .success(false)
                .content("Unknown tool: " + toolName)
                .build();
        };
    }
    
    private McpToolResult handleEcho(Map<String, Object> arguments) {
        String message = (String) arguments.get("message");
        String format = (String) arguments.getOrDefault("format", "none");
        
        if (message == null || message.trim().isEmpty()) {
            return McpToolResult.builder()
                .success(false)
                .content("Message cannot be empty")
                .build();
        }
        
        String result = switch (format) {
            case "uppercase" -> message.toUpperCase();
            case "lowercase" -> message.toLowerCase();
            case "reverse" -> new StringBuilder(message).reverse().toString();
            default -> message;
        };
        
        log.info("Echo tool executed: '{}' -> '{}'", message, result);
        
        return McpToolResult.builder()
            .success(true)
            .content(String.format("Echo: %s", result))
            .metadata(Map.of(
                "original_message", message,
                "format_applied", format,
                "timestamp", System.currentTimeMillis()
            ))
            .build();
    }
    
    private McpToolResult handlePing(Map<String, Object> arguments) {
        long timestamp = System.currentTimeMillis();
        
        log.info("Ping tool executed at timestamp: {}", timestamp);
        
        return McpToolResult.builder()
            .success(true)
            .content("pong")
            .metadata(Map.of(
                "timestamp", timestamp,
                "server_type", "mock_echo_server"
            ))
            .build();
    }
}