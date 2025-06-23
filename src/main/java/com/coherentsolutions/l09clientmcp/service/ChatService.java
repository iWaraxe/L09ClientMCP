package com.coherentsolutions.l09clientmcp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatClient chatClient;
    private final McpClientService mcpClientService;
    private final FunctionRegistry functionRegistry;

    public String chat(String userMessage) {
        log.debug("Processing chat message: {}", userMessage);
        
        try {
            // Log function availability for educational purposes
            if (functionRegistry.areFunctionsAvailable()) {
                List<String> functionNames = functionRegistry.getAvailableFunctionNames();
                log.info("Functions available for AI: {}", functionNames);
            } else {
                log.debug("Functions not available - using basic AI mode");
            }
            
            // Build system message with function awareness
            String systemMessageContent = buildSystemMessage();
            
            // Use Spring AI's chat processing
            // Note: In Branch 6, we're demonstrating function awareness in system messages
            // Real function calling would require Spring AI 1.1+ with native function support
            String response = chatClient
                .prompt()
                .system(systemMessageContent)
                .user(userMessage)
                .call()
                .content();
            
            log.debug("Generated AI response with function awareness: {}", response);
            return response;
            
        } catch (Exception e) {
            log.error("Error generating chat response", e);
            return "I'm sorry, I encountered an error processing your request.";
        }
    }
    
    /**
     * Build system message with function awareness.
     * Branch 6 Update: Enhanced for Spring AI function integration.
     */
    private String buildSystemMessage() {
        StringBuilder systemMessage = new StringBuilder();
        
        systemMessage.append("You are a helpful AI assistant. Be concise and informative. ");
        systemMessage.append("Note: Your knowledge has a cutoff date and may not include the most recent information about ");
        systemMessage.append("software versions, recent developments, or current events. When discussing specific versions ");
        systemMessage.append("or recent changes, acknowledge this limitation.");
        
        // Add function capabilities to system message
        if (functionRegistry.areFunctionsAvailable()) {
            List<String> functionNames = functionRegistry.getAvailableFunctionNames();
            log.debug("Functions are available: {}", functionNames);
            
            systemMessage.append("\n\nIMPORTANT: You have access to the following functions that can help answer user questions:");
            
            if (functionNames.contains("echo")) {
                systemMessage.append("\n- echo: Echo back text with optional formatting (uppercase, lowercase, reverse)");
            }
            if (functionNames.contains("ping")) {
                systemMessage.append("\n- ping: Test MCP server connectivity and get server timestamps");
            }
            
            systemMessage.append("\n\nWhen users ask for text echoing, formatting, connectivity testing, or other tasks that match your available functions, ");
            systemMessage.append("automatically use the appropriate functions to provide actual results. ");
            systemMessage.append("Always explain what function you used and what it accomplished. ");
            systemMessage.append("The functions are automatically available to you - just call them when appropriate.");
        } else if (mcpClientService.isEnabled()) {
            log.debug("MCP is enabled but functions not available");
            systemMessage.append("\n\nNote: External functions are configured but currently unavailable. ");
            systemMessage.append("Provide explanations and guidance instead of direct function usage.");
        }
        
        return systemMessage.toString();
    }
}