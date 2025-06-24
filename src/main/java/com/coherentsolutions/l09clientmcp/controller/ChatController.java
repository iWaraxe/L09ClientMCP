package com.coherentsolutions.l09clientmcp.controller;

import com.coherentsolutions.l09clientmcp.model.ChatRequest;
import com.coherentsolutions.l09clientmcp.model.ChatResponse;
import com.coherentsolutions.l09clientmcp.production.audit.Auditable;
import com.coherentsolutions.l09clientmcp.production.audit.AuditEventType;
import com.coherentsolutions.l09clientmcp.service.ChatService;
import com.coherentsolutions.l09clientmcp.service.McpClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final McpClientService mcpClientService;

    @PostMapping
    @Auditable(eventType = AuditEventType.CHAT_REQUEST, resource = "chat", action = "process_message")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("Received chat request: {}", request.getMessage());
        
        String response = chatService.chat(request.getMessage());
        
        return ResponseEntity.ok(new ChatResponse(response));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        boolean mcpHealthy = mcpClientService.isHealthy();
        String mcpStatus = mcpClientService.getStatus();
        
        String healthMessage = String.format("Chat service is running. MCP: %s (%s)", 
                mcpHealthy ? "healthy" : "unhealthy", mcpStatus);
        
        log.debug("Health check - Chat: OK, MCP: {}", mcpStatus);
        
        return ResponseEntity.ok(healthMessage);
    }
}