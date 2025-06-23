package com.coherentsolutions.l09clientmcp.controller;

import com.coherentsolutions.l09clientmcp.model.ChatRequest;
import com.coherentsolutions.l09clientmcp.model.ChatResponse;
import com.coherentsolutions.l09clientmcp.service.ChatService;
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

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("Received chat request: {}", request.getMessage());
        
        String response = chatService.chat(request.getMessage());
        
        return ResponseEntity.ok(new ChatResponse(response));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Chat service is running");
    }
}