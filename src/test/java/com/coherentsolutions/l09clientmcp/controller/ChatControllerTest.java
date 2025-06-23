package com.coherentsolutions.l09clientmcp.controller;

import com.coherentsolutions.l09clientmcp.model.ChatRequest;
import com.coherentsolutions.l09clientmcp.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatService chatService;

    /**
     * Test configuration that provides a mock ChatService bean.
     * This replaces the deprecated @MockBean approach with a cleaner
     * TestConfiguration pattern that creates a Mockito mock as a Spring bean.
     */
    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public ChatService chatService() {
            return mock(ChatService.class);
        }
    }

    @Test
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/chat/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Chat service is running"));
    }

    @Test
    void testChatEndpoint() throws Exception {
        String testMessage = "Hello, AI!";
        String expectedResponse = "Hello! How can I help you today?";
        
        when(chatService.chat(anyString())).thenReturn(expectedResponse);

        ChatRequest request = new ChatRequest(testMessage);

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value(expectedResponse));
    }
}