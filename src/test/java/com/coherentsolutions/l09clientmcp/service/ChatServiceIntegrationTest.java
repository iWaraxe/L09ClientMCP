package com.coherentsolutions.l09clientmcp.service;

import com.coherentsolutions.l09clientmcp.function.EchoFunction;
import com.coherentsolutions.l09clientmcp.function.PingFunction;
import com.coherentsolutions.l09clientmcp.mcp.MockMcpEchoServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Integration tests for ChatService with MCP tool functionality.
 * Branch 5: Originally tested manual tool detection patterns.
 * Branch 6: These tests are disabled as manual tool detection was replaced with Spring AI function integration.
 * 
 * Educational Focus:
 * - Legacy manual tool detection patterns (replaced in Branch 6)
 * - Evolution from manual to automatic function calling
 * - Historical perspective on chat-tool integration approaches
 */
@ExtendWith(MockitoExtension.class)
@Disabled("Branch 6: Manual tool detection replaced with Spring AI function integration - see ChatServiceBranch6Test for current functionality")
class ChatServiceIntegrationTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callSpec;

    private MockMcpEchoServer echoServer;
    private McpClientService mcpClientService;
    private EchoFunction echoFunction;
    private PingFunction pingFunction;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        echoServer = new MockMcpEchoServer();
        
        // Create MCP service with enabled state
        mcpClientService = new McpClientServiceImpl(true, "STDIO", "30s", echoServer);
        
        // Initialize MCP connection
        echoServer.connect();
        
        // Create function instances
        echoFunction = new EchoFunction(mcpClientService);
        pingFunction = new PingFunction(mcpClientService);
        
        // Create function registry
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBeansOfType(Function.class)).thenReturn(Map.of(
            "echoFunction", echoFunction,
            "pingFunction", pingFunction
        ));
        FunctionRegistry functionRegistry = new FunctionRegistry(applicationContext, mcpClientService);
        
        // Create chat service
        chatService = new ChatService(chatClient, mcpClientService, functionRegistry);
        
        // Note: These tests demonstrate legacy behavior from Branch 5
        // In Branch 6, manual tool detection was replaced with Spring AI function integration
        // For now, these tests are disabled to focus on new functionality
    }

    @Test
    @Disabled("Branch 6: Manual tool detection replaced with Spring AI function awareness")
    void testEchoRequestDetectionAndExecution() {
        // Branch 6 Update: Manual tool detection replaced with Spring AI function awareness
        String userMessage = "echo Hello World";
        
        String response = chatService.chat(userMessage);
        
        assertNotNull(response);
        // In Branch 6, this now generates standard AI response with function awareness
        assertEquals("Standard AI response", response);
        
        // Verify AI was called with function-aware system message
        verify(chatClient, times(1)).prompt();
    }

    @Test
    void testEchoRequestWithUppercaseFormatting() {
        String userMessage = "echo \"Hello World\" in uppercase";
        
        String response = chatService.chat(userMessage);
        
        assertNotNull(response);
        assertTrue(response.contains("I used the echo tool"));
        assertTrue(response.contains("HELLO WORLD"));
        assertTrue(response.contains("**Format Applied:** uppercase"));
    }

    @Test
    void testEchoRequestWithLowercaseFormatting() {
        String userMessage = "please echo back \"TESTING\" in lowercase";
        
        String response = chatService.chat(userMessage);
        
        assertNotNull(response);
        assertTrue(response.contains("I used the echo tool"));
        assertTrue(response.contains("testing"));
        assertTrue(response.contains("**Format Applied:** lowercase"));
    }

    @Test
    void testEchoRequestWithReverseFormatting() {
        String userMessage = "echo \"hello\" reverse";
        
        String response = chatService.chat(userMessage);
        
        assertNotNull(response);
        assertTrue(response.contains("I used the echo tool"));
        assertTrue(response.contains("olleh"));
        assertTrue(response.contains("**Format Applied:** reverse"));
    }

    @Test
    void testPingRequestDetectionAndExecution() {
        String userMessage = "ping the server";
        
        String response = chatService.chat(userMessage);
        
        assertNotNull(response);
        assertTrue(response.contains("I used the ping tool"));
        assertTrue(response.contains("pong"));
        assertTrue(response.contains("The MCP connection is working properly"));
        assertTrue(response.contains("mock_echo_server"));
        
        // Verify AI wasn't called since tool handled the request
        verify(chatClient, never()).prompt(any(Prompt.class));
    }

    @Test
    void testConnectionTestRequestDetection() {
        String userMessage = "test connection to MCP";
        
        String response = chatService.chat(userMessage);
        
        assertNotNull(response);
        assertTrue(response.contains("I used the ping tool"));
        assertTrue(response.contains("connectivity"));
    }

    @Test
    void testNonToolRequestUsesStandardAI() {
        String userMessage = "What is the weather today?";
        
        String response = chatService.chat(userMessage);
        
        assertEquals("Standard AI response", response);
        
        // Verify AI was called for non-tool requests
        verify(chatClient, times(1)).prompt(any(Prompt.class));
    }

    @Test
    void testMcpDisabledFallsBackToAI() {
        // Create service with MCP disabled
        McpClientService disabledMcpService = new McpClientServiceImpl(false, "STDIO", "30s", echoServer);
        
        // Create disabled function registry
        ApplicationContext disabledContext = mock(ApplicationContext.class);
        when(disabledContext.getBeansOfType(Function.class)).thenReturn(Map.of());
        FunctionRegistry disabledFunctionRegistry = new FunctionRegistry(disabledContext, disabledMcpService);
        
        ChatService disabledChatService = new ChatService(chatClient, disabledMcpService, disabledFunctionRegistry);
        
        String userMessage = "echo Hello World";
        
        String response = disabledChatService.chat(userMessage);
        
        assertEquals("Standard AI response", response);
        
        // Verify AI was called since MCP disabled
        verify(chatClient, times(1)).prompt(any(Prompt.class));
    }

    @Test
    void testMcpUnhealthyFallsBackToAI() {
        // Disconnect echo server to make MCP unhealthy
        echoServer.disconnect();
        
        String userMessage = "echo Hello World";
        
        String response = chatService.chat(userMessage);
        
        assertEquals("Standard AI response", response);
        
        // Verify AI was called since MCP unhealthy
        verify(chatClient, times(1)).prompt(any(Prompt.class));
    }

    @Test
    void testEchoToolErrorHandling() {
        String userMessage = "echo \"\""; // Empty message should cause error
        
        String response = chatService.chat(userMessage);
        
        assertNotNull(response);
        assertTrue(response.contains("I tried to use the echo tool"));
        assertTrue(response.contains("error"));
    }

    @Test
    void testSystemMessageContainsMcpInformation() {
        // This test verifies that when MCP is enabled, the chat service uses it appropriately
        String userMessage = "Hello, how can you help me?";
        
        String response = chatService.chat(userMessage);
        
        // Verify standard AI response for non-tool messages
        assertEquals("Standard AI response", response);
        
        // Verify the prompt was called for non-tool requests
        verify(chatClient, times(1)).prompt(any(Prompt.class));
    }

    @Test
    void testMessageExtractionFromQuotedStrings() {
        String userMessage = "echo \"Special message with symbols!@#\"";
        
        String response = chatService.chat(userMessage);
        
        assertNotNull(response);
        assertTrue(response.contains("Special message with symbols!@#"));
    }

    @Test
    void testCaseInsensitiveToolDetection() {
        String userMessage = "ECHO hello world IN UPPERCASE";
        
        String response = chatService.chat(userMessage);
        
        assertNotNull(response);
        assertTrue(response.contains("I used the echo tool"));
        assertTrue(response.contains("HELLO WORLD"));
    }
}