package com.coherentsolutions.l09clientmcp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatClient chatClient;
    private final McpClientService mcpClientService;

    public String chat(String userMessage) {
        log.debug("Processing chat message: {}", userMessage);
        
        // Build system message with MCP awareness
        String systemMessageContent = "You are a helpful AI assistant. Be concise and informative. " +
            "Note: Your knowledge has a cutoff date and may not include the most recent information about " +
            "software versions, recent developments, or current events. When discussing specific versions " +
            "or recent changes, acknowledge this limitation.";
        
        // Future enhancement: In upcoming branches, we'll add MCP tool capabilities here
        if (mcpClientService.isEnabled()) {
            log.debug("MCP is enabled - future branches will add tool capabilities");
            // TODO: In future branches, this will include MCP tool discovery and usage
        }
        
        var systemMessage = new SystemMessage(systemMessageContent);
        var userMsg = new UserMessage(userMessage);
        
        var prompt = new Prompt(List.of(systemMessage, userMsg));
        
        try {
            var response = chatClient.prompt(prompt).call().content();
            log.debug("Generated response: {}", response);
            return response;
        } catch (Exception e) {
            log.error("Error generating chat response", e);
            return "I'm sorry, I encountered an error processing your request.";
        }
    }
}