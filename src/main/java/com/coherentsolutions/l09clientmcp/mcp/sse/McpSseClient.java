package com.coherentsolutions.l09clientmcp.mcp.sse;

import com.coherentsolutions.l09clientmcp.mcp.McpTool;
import com.coherentsolutions.l09clientmcp.mcp.McpToolResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * MCP client implementation using SSE transport for real-time communication.
 * Branch 5: Provides real MCP protocol implementation over Server-Sent Events.
 * 
 * Educational Focus:
 * - Real MCP protocol implementation
 * - SSE transport layer integration
 * - Asynchronous request/response patterns
 * - Tool discovery and invocation over network
 * - Error handling in distributed systems
 */
@Slf4j
public class McpSseClient {
    
    private final String serverBaseUrl;
    private final SseConnection sseConnection;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    private final Map<String, CompletableFuture<String>> pendingRequests;
    private volatile boolean connected;
    
    public McpSseClient(String serverUrl, int timeoutSeconds, int retryAttempts, int retryDelaySeconds) {
        this.serverBaseUrl = extractBaseUrl(serverUrl);
        this.sseConnection = new SseConnection(serverUrl, timeoutSeconds, retryAttempts, retryDelaySeconds);
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.pendingRequests = new ConcurrentHashMap<>();
        this.connected = false;
        
        // Set up SSE message handling
        this.sseConnection.setMessageHandler(this::handleSseMessage);
        this.sseConnection.setErrorHandler(this::handleSseError);
    }
    
    /**
     * Connect to the MCP server via SSE.
     */
    public CompletableFuture<Void> connect() {
        log.info("Connecting to MCP server via SSE: {}", serverBaseUrl);
        return sseConnection.connect().thenRun(() -> {
            connected = sseConnection.isConnected();
            if (connected) {
                log.info("MCP SSE client connected successfully");
            }
        });
    }
    
    /**
     * Disconnect from the MCP server.
     */
    public void disconnect() {
        log.info("Disconnecting from MCP server");
        connected = false;
        sseConnection.disconnect();
        
        // Complete any pending requests with error
        pendingRequests.values().forEach(future -> 
            future.completeExceptionally(new RuntimeException("Connection closed")));
        pendingRequests.clear();
    }
    
    /**
     * Check if the client is connected to the server.
     */
    public boolean isConnected() {
        return connected && sseConnection.isConnected();
    }
    
    /**
     * List available tools from the MCP server.
     */
    public List<McpTool> listTools() {
        if (!isConnected()) {
            log.warn("Not connected to MCP server, returning empty tools list");
            return Collections.emptyList();
        }
        
        try {
            String url = serverBaseUrl + "/tools";
            log.debug("Requesting tools list from: {}", url);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode responseNode = objectMapper.readTree(response.getBody());
                if (responseNode.has("tools")) {
                    List<McpTool> tools = objectMapper.convertValue(
                        responseNode.get("tools"), 
                        new TypeReference<List<McpTool>>() {}
                    );
                    log.info("Retrieved {} tools from MCP server", tools.size());
                    return tools;
                }
            }
            
            log.warn("Invalid response from tools endpoint: {}", response.getStatusCode());
            return Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Error listing tools from MCP server", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Invoke a tool on the MCP server.
     */
    public McpToolResult invokeTool(String toolName, Map<String, Object> arguments) {
        if (!isConnected()) {
            return McpToolResult.builder()
                .success(false)
                .content("Not connected to MCP server")
                .build();
        }
        
        try {
            String url = serverBaseUrl + "/tools/" + toolName;
            log.debug("Invoking tool '{}' at: {} with arguments: {}", toolName, url, arguments);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(arguments, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode responseNode = objectMapper.readTree(response.getBody());
                return parseToolResult(responseNode);
            } else {
                log.warn("Tool invocation failed with status: {}", response.getStatusCode());
                return McpToolResult.builder()
                    .success(false)
                    .content("Tool invocation failed: HTTP " + response.getStatusCode())
                    .build();
            }
            
        } catch (Exception e) {
            log.error("Error invoking tool '{}' on MCP server", toolName, e);
            return McpToolResult.builder()
                .success(false)
                .content("Tool invocation error: " + e.getMessage())
                .build();
        }
    }
    
    private void handleSseMessage(String message) {
        try {
            log.debug("Received SSE message: {}", message);
            JsonNode messageNode = objectMapper.readTree(message);
            
            String type = messageNode.path("type").asText();
            
            switch (type) {
                case "connection":
                    handleConnectionMessage(messageNode);
                    break;
                case "response":
                    handleResponseMessage(messageNode);
                    break;
                case "error":
                    handleErrorMessage(messageNode);
                    break;
                default:
                    log.debug("Unknown SSE message type: {}", type);
            }
            
        } catch (JsonProcessingException e) {
            log.error("Error parsing SSE message", e);
        }
    }
    
    private void handleConnectionMessage(JsonNode messageNode) {
        JsonNode data = messageNode.path("data");
        String server = data.path("server").asText("unknown");
        String version = data.path("version").asText("unknown");
        
        log.info("Connected to MCP server: {} version {}", server, version);
        
        JsonNode capabilities = data.path("capabilities");
        if (capabilities.isObject()) {
            log.info("Server capabilities: {}", capabilities);
        }
    }
    
    private void handleResponseMessage(JsonNode messageNode) {
        String requestId = messageNode.path("requestId").asText();
        CompletableFuture<String> pendingRequest = pendingRequests.remove(requestId);
        
        if (pendingRequest != null) {
            String responseData = messageNode.path("data").toString();
            pendingRequest.complete(responseData);
        } else {
            log.warn("Received response for unknown request ID: {}", requestId);
        }
    }
    
    private void handleErrorMessage(JsonNode messageNode) {
        String requestId = messageNode.path("requestId").asText();
        String error = messageNode.path("error").asText();
        
        CompletableFuture<String> pendingRequest = pendingRequests.remove(requestId);
        if (pendingRequest != null) {
            pendingRequest.completeExceptionally(new RuntimeException(error));
        } else {
            log.error("SSE error message: {}", error);
        }
    }
    
    private void handleSseError(Exception error) {
        log.error("SSE connection error", error);
        connected = false;
        
        // Complete pending requests with error
        pendingRequests.values().forEach(future -> 
            future.completeExceptionally(error));
        pendingRequests.clear();
    }
    
    private McpToolResult parseToolResult(JsonNode responseNode) {
        boolean success = responseNode.path("success").asBoolean(false);
        String content = responseNode.path("content").asText("");
        String error = responseNode.path("error").asText(null);
        
        McpToolResult.McpToolResultBuilder builder = McpToolResult.builder()
            .success(success)
            .content(content);
        
        if (error != null) {
            builder.content(error);
        }
        
        // Parse metadata if present
        JsonNode metadataNode = responseNode.path("metadata");
        if (metadataNode.isObject()) {
            try {
                Map<String, Object> metadata = objectMapper.convertValue(
                    metadataNode, 
                    new TypeReference<Map<String, Object>>() {}
                );
                builder.metadata(metadata);
            } catch (Exception e) {
                log.warn("Error parsing metadata from tool result", e);
            }
        }
        
        return builder.build();
    }
    
    private String extractBaseUrl(String sseUrl) {
        // Convert SSE URL to base HTTP URL
        // e.g., "http://localhost:3000/mcp" -> "http://localhost:3000"
        try {
            if (sseUrl.endsWith("/mcp")) {
                return sseUrl.substring(0, sseUrl.length() - 4);
            }
            return sseUrl;
        } catch (Exception e) {
            log.warn("Error extracting base URL from: {}", sseUrl);
            return sseUrl;
        }
    }
}