package com.coherentsolutions.l09clientmcp.controller;

import com.coherentsolutions.l09clientmcp.mcp.McpTool;
import com.coherentsolutions.l09clientmcp.mcp.McpToolResult;
import com.coherentsolutions.l09clientmcp.service.McpClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
@Slf4j
public class McpController {
    
    private final McpClientService mcpClientService;
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        log.info("MCP status request received");
        
        boolean enabled = mcpClientService.isEnabled();
        boolean healthy = mcpClientService.isHealthy();
        String status = mcpClientService.getStatus();
        
        Map<String, Object> response = Map.of(
            "enabled", enabled,
            "healthy", healthy,
            "status", status,
            "message", enabled ? "MCP is operational" : "MCP is disabled"
        );
        
        log.info("MCP status response: enabled={}, healthy={}", enabled, healthy);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/tools")
    public ResponseEntity<Map<String, Object>> listTools() {
        log.info("MCP tools list request received");
        
        if (!mcpClientService.isEnabled()) {
            return ResponseEntity.ok(Map.of(
                "tools", List.of(),
                "message", "MCP is disabled - no tools available"
            ));
        }
        
        try {
            List<McpTool> tools = mcpClientService.listTools();
            log.info("Retrieved {} MCP tools", tools.size());
            
            return ResponseEntity.ok(Map.of(
                "tools", tools,
                "count", tools.size(),
                "message", String.format("Found %d available tools", tools.size())
            ));
        } catch (Exception e) {
            log.error("Error retrieving MCP tools", e);
            return ResponseEntity.ok(Map.of(
                "tools", List.of(),
                "error", e.getMessage(),
                "message", "Error retrieving tools"
            ));
        }
    }
    
    @PostMapping("/tools/{toolName}/invoke")
    public ResponseEntity<Map<String, Object>> invokeTool(
            @PathVariable String toolName,
            @RequestBody(required = false) Map<String, Object> arguments) {
        
        log.info("Tool invocation request: tool='{}', arguments={}", toolName, arguments);
        
        if (!mcpClientService.isEnabled()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "MCP is disabled",
                "message", "Cannot invoke tools when MCP is disabled"
            ));
        }
        
        try {
            Map<String, Object> safeArguments = arguments != null ? arguments : Map.of();
            McpToolResult result = mcpClientService.invokeTool(toolName, safeArguments);
            
            log.info("Tool '{}' invocation completed: success={}", toolName, result.isSuccess());
            
            return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "content", result.getContent(),
                "metadata", result.getMetadata() != null ? result.getMetadata() : Map.of(),
                "tool", toolName,
                "arguments", safeArguments
            ));
            
        } catch (IllegalStateException e) {
            log.error("Tool invocation failed - illegal state: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage(),
                "tool", toolName
            ));
        } catch (Exception e) {
            log.error("Tool invocation failed with unexpected error", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", "Internal server error: " + e.getMessage(),
                "tool", toolName
            ));
        }
    }
    
    @PostMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        log.info("MCP ping request received");
        
        if (!mcpClientService.isEnabled()) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "MCP is disabled - ping not available"
            ));
        }
        
        try {
            McpToolResult result = mcpClientService.invokeTool("ping", Map.of());
            
            return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "message", result.getContent(),
                "metadata", result.getMetadata() != null ? result.getMetadata() : Map.of()
            ));
            
        } catch (Exception e) {
            log.error("MCP ping failed", e);
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "Ping failed: " + e.getMessage()
            ));
        }
    }
}