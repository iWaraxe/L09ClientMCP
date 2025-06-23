package com.coherentsolutions.l09clientmcp.controller;

import com.coherentsolutions.l09clientmcp.model.ChatRequest;
import com.coherentsolutions.l09clientmcp.model.ChatResponse;
import com.coherentsolutions.l09clientmcp.service.ChatService;
import com.coherentsolutions.l09clientmcp.service.FunctionRegistry;
import com.coherentsolutions.l09clientmcp.service.McpClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final McpClientService mcpClientService;
    private final FunctionRegistry functionRegistry;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("Received chat request: {}", request.getMessage());
        
        String response = chatService.chat(request.getMessage());
        
        return ResponseEntity.ok(new ChatResponse(response));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        boolean mcpHealthy = mcpClientService.isHealthy();
        String mcpStatus = mcpClientService.getStatus();
        String functionStatus = functionRegistry.getFunctionStatus();
        
        String healthMessage = String.format("Chat service is running. MCP: %s (%s). Functions: %s", 
                mcpHealthy ? "healthy" : "unhealthy", mcpStatus, functionStatus);
        
        log.debug("Health check - Chat: OK, MCP: {}, Functions: {}", mcpStatus, functionStatus);
        
        return ResponseEntity.ok(healthMessage);
    }
    
    @GetMapping("/functions")
    public ResponseEntity<Map<String, Object>> getFunctions() {
        log.debug("Function registry status requested");
        
        List<String> availableFunctions = functionRegistry.getAvailableFunctionNames();
        boolean functionsAvailable = functionRegistry.areFunctionsAvailable();
        String functionStatus = functionRegistry.getFunctionStatus();
        
        Map<String, Object> response = Map.of(
            "available", functionsAvailable,
            "status", functionStatus,
            "functions", availableFunctions,
            "count", availableFunctions.size()
        );
        
        log.debug("Returning function info: {}", response);
        return ResponseEntity.ok(response);
    }
}