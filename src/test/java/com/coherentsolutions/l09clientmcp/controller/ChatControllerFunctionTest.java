package com.coherentsolutions.l09clientmcp.controller;

import com.coherentsolutions.l09clientmcp.service.ChatService;
import com.coherentsolutions.l09clientmcp.service.FunctionRegistry;
import com.coherentsolutions.l09clientmcp.service.McpClientService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for ChatController with function integration.
 * Branch 6: Tests new function-related endpoints.
 * 
 * Educational Focus:
 * - Function registry API endpoints
 * - Enhanced health checks with function status
 * - Function availability reporting
 * - API contract for function information
 */
@WebMvcTest(ChatController.class)
class ChatControllerFunctionTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public ChatService chatService() {
            return mock(ChatService.class);
        }
        
        @Bean
        @Primary
        public McpClientService mcpClientService() {
            McpClientService mockService = mock(McpClientService.class);
            lenient().when(mockService.isHealthy()).thenReturn(true);
            lenient().when(mockService.getStatus()).thenReturn("MCP enabled, SSE connected, 2 tools available");
            return mockService;
        }
        
        @Bean
        @Primary
        public FunctionRegistry functionRegistry() {
            FunctionRegistry mockRegistry = mock(FunctionRegistry.class);
            lenient().when(mockRegistry.getAvailableFunctionNames()).thenReturn(List.of("echo", "ping"));
            lenient().when(mockRegistry.areFunctionsAvailable()).thenReturn(true);
            lenient().when(mockRegistry.getFunctionStatus()).thenReturn("Functions available (2 registered)");
            return mockRegistry;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FunctionRegistry functionRegistry;

    @Autowired
    private McpClientService mcpClientService;
    
    @Autowired
    private ChatService chatService;

    @Test
    void testGetFunctions_WhenAvailable() throws Exception {
        // Arrange
        when(functionRegistry.getAvailableFunctionNames()).thenReturn(List.of("echo", "ping"));
        when(functionRegistry.areFunctionsAvailable()).thenReturn(true);
        when(functionRegistry.getFunctionStatus()).thenReturn("Functions available (2 registered)");

        // Act & Assert
        mockMvc.perform(get("/api/chat/functions"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.status").value("Functions available (2 registered)"))
                .andExpect(jsonPath("$.functions").isArray())
                .andExpect(jsonPath("$.functions[0]").value("echo"))
                .andExpect(jsonPath("$.functions[1]").value("ping"))
                .andExpect(jsonPath("$.count").value(2));

        verify(functionRegistry).getAvailableFunctionNames();
        verify(functionRegistry).areFunctionsAvailable();
        verify(functionRegistry).getFunctionStatus();
    }

    @Test
    void testGetFunctions_WhenUnavailable() throws Exception {
        // Arrange
        when(functionRegistry.getAvailableFunctionNames()).thenReturn(List.of());
        when(functionRegistry.areFunctionsAvailable()).thenReturn(false);
        when(functionRegistry.getFunctionStatus()).thenReturn("Functions disabled (MCP disabled)");

        // Act & Assert
        mockMvc.perform(get("/api/chat/functions"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.status").value("Functions disabled (MCP disabled)"))
                .andExpect(jsonPath("$.functions").isEmpty())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void testHealthWithFunctions() throws Exception {
        // Arrange
        when(mcpClientService.isHealthy()).thenReturn(true);
        when(mcpClientService.getStatus()).thenReturn("MCP enabled, SSE connected, 2 tools available");
        when(functionRegistry.getFunctionStatus()).thenReturn("Functions available (2 registered)");

        // Act & Assert
        mockMvc.perform(get("/api/chat/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chat service is running")))
                .andExpect(content().string(containsString("MCP: healthy")))
                .andExpect(content().string(containsString("Functions: Functions available (2 registered)")));

        verify(mcpClientService).isHealthy();
        verify(mcpClientService).getStatus();
        verify(functionRegistry).getFunctionStatus();
    }

    @Test
    void testHealthWithDisabledFunctions() throws Exception {
        // Arrange
        when(mcpClientService.isHealthy()).thenReturn(false);
        when(mcpClientService.getStatus()).thenReturn("MCP disabled - running in baseline mode");
        when(functionRegistry.getFunctionStatus()).thenReturn("Functions disabled (MCP disabled)");

        // Act & Assert
        mockMvc.perform(get("/api/chat/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chat service is running")))
                .andExpect(content().string(containsString("MCP: unhealthy")))
                .andExpect(content().string(containsString("Functions: Functions disabled (MCP disabled)")));
    }

    @Test
    void testChatEndpointStillWorks() throws Exception {
        // Arrange
        String testMessage = "Hello, can you help me test the functions?";
        String expectedResponse = "I can help you test the function integration. The echo and ping functions are available.";
        
        when(chatService.chat(testMessage)).thenReturn(expectedResponse);

        // Act & Assert
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\": \"" + testMessage + "\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.response").value(expectedResponse));

        verify(chatService).chat(testMessage);
    }
}