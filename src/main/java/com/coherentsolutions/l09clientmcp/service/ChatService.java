package com.coherentsolutions.l09clientmcp.service;

import com.coherentsolutions.l09clientmcp.function.EchoFunction;
import com.coherentsolutions.l09clientmcp.function.PingFunction;
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
    private final EchoFunction echoFunction;
    private final PingFunction pingFunction;

    public String chat(String userMessage) {
        log.debug("Processing chat message: {}", userMessage);
        
        // Build system message with MCP awareness
        String systemMessageContent = buildSystemMessage();
        
        var systemMessage = new SystemMessage(systemMessageContent);
        var userMsg = new UserMessage(userMessage);
        
        var prompt = new Prompt(List.of(systemMessage, userMsg));
        
        try {
            // Check if user message suggests tool usage before calling AI
            String toolEnhancedResponse = checkAndExecuteTools(userMessage);
            if (toolEnhancedResponse != null) {
                log.debug("Returning tool-enhanced response");
                return toolEnhancedResponse;
            }
            
            // Standard AI response without tool usage
            var response = chatClient.prompt(prompt).call().content();
            log.debug("Generated standard response: {}", response);
            return response;
        } catch (Exception e) {
            log.error("Error generating chat response", e);
            return "I'm sorry, I encountered an error processing your request.";
        }
    }
    
    /**
     * Build system message with MCP tool awareness.
     * Branch 4 Update: Enhanced to inform AI about available MCP tools.
     */
    private String buildSystemMessage() {
        StringBuilder systemMessage = new StringBuilder();
        
        systemMessage.append("You are a helpful AI assistant. Be concise and informative. ");
        systemMessage.append("Note: Your knowledge has a cutoff date and may not include the most recent information about ");
        systemMessage.append("software versions, recent developments, or current events. When discussing specific versions ");
        systemMessage.append("or recent changes, acknowledge this limitation.");
        
        // Add MCP tool capabilities to system message
        if (mcpClientService.isEnabled() && mcpClientService.isHealthy()) {
            int toolCount = mcpClientService.listTools().size();
            log.debug("MCP is enabled and healthy with {} tools available", toolCount);
            
            systemMessage.append("\n\nIMPORTANT: You have access to external tools that can help answer user questions:");
            systemMessage.append("\n- Echo Tool: Can echo back text with optional formatting (uppercase, lowercase, reverse)");
            systemMessage.append("\n- Ping Tool: Can test connectivity and get server timestamps");
            systemMessage.append("\n\nWhen users ask for text echoing, formatting, or connectivity testing, ");
            systemMessage.append("use the appropriate tools to provide actual results rather than just explanations. ");
            systemMessage.append("Always explain what tool you used and what it accomplished.");
        } else if (mcpClientService.isEnabled()) {
            log.debug("MCP is enabled but not healthy - tools unavailable");
            systemMessage.append("\n\nNote: External tools are configured but currently unavailable. ");
            systemMessage.append("Provide explanations and guidance instead of direct tool usage.");
        }
        
        return systemMessage.toString();
    }
    
    /**
     * Check if user message suggests MCP tool usage and execute if appropriate.
     * Branch 4 Implementation: Manual tool detection and invocation integration.
     * 
     * @param userMessage The user's input message
     * @return Tool-enhanced response if tool was used, null if no tool needed
     */
    private String checkAndExecuteTools(String userMessage) {
        if (!mcpClientService.isEnabled() || !mcpClientService.isHealthy()) {
            return null;
        }
        
        String lowerMessage = userMessage.toLowerCase();
        
        // Detect echo tool usage
        if (lowerMessage.contains("echo") && (lowerMessage.contains("uppercase") || 
            lowerMessage.contains("lowercase") || lowerMessage.contains("reverse"))) {
            return handleEchoRequest(userMessage);
        }
        
        // Detect basic echo requests
        if (lowerMessage.startsWith("echo ") || lowerMessage.contains("echo back")) {
            return handleEchoRequest(userMessage);
        }
        
        // Detect ping requests
        if (lowerMessage.contains("ping") || lowerMessage.contains("test connection")) {
            return handlePingRequest(userMessage);
        }
        
        return null; // No tool usage detected
    }
    
    /**
     * Handle echo tool requests with message extraction and formatting.
     */
    private String handleEchoRequest(String userMessage) {
        try {
            log.info("Detected echo request in user message: {}", userMessage);
            
            // Extract message and format from user input
            String messageToEcho = extractEchoMessage(userMessage);
            String format = extractEchoFormat(userMessage);
            
            // Create echo function request
            EchoFunction.Request request = new EchoFunction.Request(messageToEcho, format);
            EchoFunction.Response response = echoFunction.apply(request);
            
            if (response.success()) {
                return String.format("I used the echo tool to process your message:\n\n" +
                    "**Result:** %s\n" +
                    "**Original:** %s\n" +
                    "**Format Applied:** %s", 
                    response.result(), response.originalMessage(), response.formatApplied());
            } else {
                return "I tried to use the echo tool, but it encountered an error: " + response.error();
            }
        } catch (Exception e) {
            log.error("Error handling echo request", e);
            return "I attempted to use the echo tool but encountered an error: " + e.getMessage();
        }
    }
    
    /**
     * Handle ping tool requests.
     */
    private String handlePingRequest(String userMessage) {
        try {
            log.info("Detected ping request in user message: {}", userMessage);
            
            PingFunction.Request request = new PingFunction.Request();
            PingFunction.Response response = pingFunction.apply(request);
            
            if (response.success()) {
                return String.format("I used the ping tool to test MCP connectivity:\n\n" +
                    "**Status:** %s\n" +
                    "**Server Type:** %s\n" +
                    "**Timestamp:** %s\n\n" +
                    "The MCP connection is working properly!",
                    response.message(), response.serverType(), response.timestamp());
            } else {
                return "I tried to ping the MCP server, but it's not responding: " + response.error();
            }
        } catch (Exception e) {
            log.error("Error handling ping request", e);
            return "I attempted to ping the MCP server but encountered an error: " + e.getMessage();
        }
    }
    
    /**
     * Extract the message to echo from user input.
     */
    private String extractEchoMessage(String userMessage) {
        String lowerMessage = userMessage.toLowerCase();
        
        // Look for quoted strings first
        if (userMessage.contains("\"")) {
            int start = userMessage.indexOf("\"");
            int end = userMessage.lastIndexOf("\"");
            if (start != end && start >= 0) {
                return userMessage.substring(start + 1, end);
            }
        }
        
        // Look for "echo " pattern
        if (lowerMessage.startsWith("echo ")) {
            String remainder = userMessage.substring(5).trim();
            // Remove format instructions
            remainder = remainder.replaceAll("(?i)\\s+(in\\s+)?(uppercase|lowercase|reverse)", "").trim();
            return remainder;
        }
        
        // Look for "echo back" pattern
        if (lowerMessage.contains("echo back")) {
            int echoIndex = lowerMessage.indexOf("echo back");
            String remainder = userMessage.substring(echoIndex + 9).trim();
            remainder = remainder.replaceAll("(?i)\\s+(in\\s+)?(uppercase|lowercase|reverse)", "").trim();
            return remainder;
        }
        
        // Default fallback
        return "Hello World";
    }
    
    /**
     * Extract formatting instruction from user input.
     */
    private String extractEchoFormat(String userMessage) {
        String lowerMessage = userMessage.toLowerCase();
        
        if (lowerMessage.contains("uppercase")) {
            return "uppercase";
        } else if (lowerMessage.contains("lowercase")) {
            return "lowercase";
        } else if (lowerMessage.contains("reverse")) {
            return "reverse";
        }
        
        return null; // No format specified
    }
}