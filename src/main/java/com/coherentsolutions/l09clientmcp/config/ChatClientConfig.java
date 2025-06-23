package com.coherentsolutions.l09clientmcp.config;

import com.coherentsolutions.l09clientmcp.function.EchoFunction;
import com.coherentsolutions.l09clientmcp.function.PingFunction;
import com.coherentsolutions.l09clientmcp.service.McpClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Spring AI ChatClient with MCP function integration.
 * 
 * Branch 4 Update: Added MCP function registration for tool-enhanced conversations.
 * 
 * Educational Focus:
 * - Function registration with ChatClient
 * - Conditional function enabling based on MCP availability
 * - Integration of external tools with AI conversations
 * - Configuration-driven function management
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ChatClientConfig {

    private final McpClientService mcpClientService;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        log.info("Creating ChatClient - MCP function integration will be handled at call time");
        return builder.build();
    }
}