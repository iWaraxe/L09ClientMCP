package com.coherentsolutions.l09clientmcp.service;

import com.coherentsolutions.l09clientmcp.mcp.McpTool;
import com.coherentsolutions.l09clientmcp.mcp.McpToolResult;
import com.coherentsolutions.l09clientmcp.mcp.sse.McpSseClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for McpSseClientService.
 * Branch 5: Tests SSE-based MCP client service implementation.
 * 
 * Educational Focus:
 * - Service layer testing with SSE transport
 * - Configuration-driven behavior testing
 * - Graceful degradation scenarios
 * - Connection lifecycle management
 */
@ExtendWith(MockitoExtension.class)
class McpSseClientServiceTest {

    @Mock
    private McpSseClient mockSseClient;

    private McpSseClientService mcpSseClientService;
    private McpSseClientService mcpDisabledService;

    @BeforeEach
    void setUp() {
        // Create enabled service
        mcpSseClientService = new McpSseClientService(
            true,                          // mcpEnabled
            "SSE",                         // mcpType
            "30s",                         // requestTimeoutStr
            "http://localhost:3000/mcp",   // serverUrl
            3,                             // retryAttempts
            "5s"                           // retryDelayStr
        );
        
        // Inject mock SSE client using reflection
        try {
            var field = McpSseClientService.class.getDeclaredField("sseClient");
            field.setAccessible(true);
            field.set(mcpSseClientService, mockSseClient);
            
            var connectedField = McpSseClientService.class.getDeclaredField("connectionInitialized");
            connectedField.setAccessible(true);
            connectedField.set(mcpSseClientService, true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mock SSE client", e);
        }
        
        // Create disabled service
        mcpDisabledService = new McpSseClientService(
            false,                         // mcpEnabled
            "SSE",                         // mcpType
            "30s",                         // requestTimeoutStr
            "http://localhost:3000/mcp",   // serverUrl
            3,                             // retryAttempts
            "5s"                           // retryDelayStr
        );
    }

    @Test
    void testIsEnabledTrue() {
        assertTrue(mcpSseClientService.isEnabled());
    }

    @Test
    void testIsEnabledFalse() {
        assertFalse(mcpDisabledService.isEnabled());
    }

    @Test
    void testIsHealthyWhenEnabled() {
        when(mockSseClient.isConnected()).thenReturn(true);
        
        assertTrue(mcpSseClientService.isHealthy());
        verify(mockSseClient).isConnected();
    }

    @Test
    void testIsHealthyWhenDisabled() {
        // Disabled service should return true for graceful degradation
        assertTrue(mcpDisabledService.isHealthy());
    }

    @Test
    void testIsHealthyWhenNotConnected() {
        when(mockSseClient.isConnected()).thenReturn(false);
        
        assertFalse(mcpSseClientService.isHealthy());
        verify(mockSseClient).isConnected();
    }

    @Test
    void testGetStatusWhenEnabled() {
        when(mockSseClient.isConnected()).thenReturn(true);
        lenient().when(mockSseClient.listTools()).thenReturn(List.of(
            createMockTool("echo"),
            createMockTool("ping")
        ));
        
        String status = mcpSseClientService.getStatus();
        
        assertTrue(status.contains("MCP enabled"));
        assertTrue(status.contains("Type: SSE"));
        assertTrue(status.contains("connected"));
        assertTrue(status.contains("2 tools"));
    }

    @Test
    void testGetStatusWhenDisabled() {
        String status = mcpDisabledService.getStatus();
        
        assertEquals("MCP disabled - running in baseline mode", status);
    }

    @Test
    void testGetStatusWhenNotConnected() {
        when(mockSseClient.isConnected()).thenReturn(false);
        
        String status = mcpSseClientService.getStatus();
        
        assertTrue(status.contains("disconnected"));
        assertTrue(status.contains("0 tools"));
    }

    @Test
    void testListToolsWhenEnabled() {
        List<McpTool> expectedTools = List.of(
            createMockTool("echo"),
            createMockTool("ping")
        );
        
        when(mockSseClient.isConnected()).thenReturn(true);
        when(mockSseClient.listTools()).thenReturn(expectedTools);
        
        List<McpTool> tools = mcpSseClientService.listTools();
        
        assertEquals(2, tools.size());
        assertEquals("echo", tools.get(0).getName());
        assertEquals("ping", tools.get(1).getName());
        verify(mockSseClient).listTools();
    }

    @Test
    void testListToolsWhenDisabled() {
        List<McpTool> tools = mcpDisabledService.listTools();
        
        assertTrue(tools.isEmpty());
    }

    @Test
    void testListToolsWhenNotConnected() {
        when(mockSseClient.isConnected()).thenReturn(false);
        
        List<McpTool> tools = mcpSseClientService.listTools();
        
        assertTrue(tools.isEmpty());
        verify(mockSseClient, never()).listTools();
    }

    @Test
    void testInvokeToolSuccessful() {
        Map<String, Object> arguments = Map.of("message", "Hello World");
        McpToolResult expectedResult = McpToolResult.builder()
            .success(true)
            .content("Echo: Hello World")
            .metadata(Map.of("original_message", "Hello World"))
            .build();
        
        when(mockSseClient.isConnected()).thenReturn(true);
        when(mockSseClient.invokeTool("echo", arguments)).thenReturn(expectedResult);
        
        McpToolResult result = mcpSseClientService.invokeTool("echo", arguments);
        
        assertTrue(result.isSuccess());
        assertEquals("Echo: Hello World", result.getContent());
        verify(mockSseClient).invokeTool("echo", arguments);
    }

    @Test
    void testInvokeToolWhenDisabled() {
        Map<String, Object> arguments = Map.of("message", "test");
        
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> mcpDisabledService.invokeTool("echo", arguments)
        );
        
        assertEquals("MCP is disabled - cannot invoke tools", exception.getMessage());
    }

    @Test
    void testInvokeToolWhenNotConnected() {
        when(mockSseClient.isConnected()).thenReturn(false);
        
        Map<String, Object> arguments = Map.of("message", "test");
        
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> mcpSseClientService.invokeTool("echo", arguments)
        );
        
        assertEquals("SSE client not connected - cannot invoke tools", exception.getMessage());
    }

    @Test
    void testInvokeToolWithError() {
        Map<String, Object> arguments = Map.of("message", "");
        McpToolResult expectedResult = McpToolResult.builder()
            .success(false)
            .content("Message cannot be empty")
            .build();
        
        when(mockSseClient.isConnected()).thenReturn(true);
        when(mockSseClient.invokeTool("echo", arguments)).thenReturn(expectedResult);
        
        McpToolResult result = mcpSseClientService.invokeTool("echo", arguments);
        
        assertFalse(result.isSuccess());
        assertEquals("Message cannot be empty", result.getContent());
    }

    @Test
    void testInvokeToolWithException() {
        Map<String, Object> arguments = Map.of("message", "test");
        
        when(mockSseClient.isConnected()).thenReturn(true);
        when(mockSseClient.invokeTool("echo", arguments))
            .thenThrow(new RuntimeException("Connection lost"));
        
        McpToolResult result = mcpSseClientService.invokeTool("echo", arguments);
        
        assertFalse(result.isSuccess());
        assertTrue(result.getContent().contains("SSE tool invocation failed"));
        assertTrue(result.getContent().contains("Connection lost"));
    }

    @Test
    void testGetSseClient() {
        McpSseClient client = mcpSseClientService.getSseClient();
        
        assertSame(mockSseClient, client);
    }

    private McpTool createMockTool(String name) {
        return McpTool.builder()
            .name(name)
            .description("Test tool: " + name)
            .inputSchema(Map.of("type", "object"))
            .build();
    }
}