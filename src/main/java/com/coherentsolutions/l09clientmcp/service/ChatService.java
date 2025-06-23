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

    public String chat(String userMessage) {
        log.debug("Processing chat message: {}", userMessage);
        
        var systemMessage = new SystemMessage("You are a helpful AI assistant. Be concise and informative.");
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