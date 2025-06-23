package com.coherentsolutions.l09clientmcp.function;

import com.coherentsolutions.l09clientmcp.mcp.McpToolResult;
import com.coherentsolutions.l09clientmcp.service.McpClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PingFunction.
 * Branch 4: Tests Spring AI Function integration for MCP ping tool.
 * 
 * Educational Focus:
 * - No-parameter function implementation
 * - Connectivity testing patterns
 * - Metadata extraction and handling
 * - Error response transformation
 */
@ExtendWith(MockitoExtension.class)
class PingFunctionTest {

    @Mock
    private McpClientService mcpClientService;

    private PingFunction pingFunction;

    @BeforeEach
    void setUp() {
        pingFunction = new PingFunction(mcpClientService);
    }

    @Test
    void testSuccessfulPing() {
        // Arrange
        PingFunction.Request request = new PingFunction.Request();
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(true)
            .content("pong")
            .metadata(Map.of(
                "timestamp", "1750704859249",
                "server_type", "mock_echo_server"
            ))
            .build();
        
        when(mcpClientService.invokeTool(eq("ping"), eq(Map.of())))
            .thenReturn(mcpResult);

        // Act
        PingFunction.Response response = pingFunction.apply(request);

        // Assert
        assertTrue(response.success());
        assertEquals("pong", response.message());
        assertEquals("1750704859249", response.timestamp());
        assertEquals("mock_echo_server", response.serverType());
        assertNull(response.error());
    }

    @Test
    void testSuccessfulPingWithContext() {
        // Arrange
        PingFunction.Request request = new PingFunction.Request("Testing MCP connectivity");
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(true)
            .content("pong")
            .metadata(Map.of(
                "timestamp", "1750704859250",
                "server_type", "mock_echo_server"
            ))
            .build();
        
        when(mcpClientService.invokeTool(eq("ping"), eq(Map.of())))
            .thenReturn(mcpResult);

        // Act
        PingFunction.Response response = pingFunction.apply(request);

        // Assert
        assertTrue(response.success());
        assertEquals("pong", response.message());
        assertEquals("1750704859250", response.timestamp());
        assertEquals("mock_echo_server", response.serverType());
        assertNull(response.error());
    }

    @Test
    void testFailedPing() {
        // Arrange
        PingFunction.Request request = new PingFunction.Request();
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(false)
            .content("Server not responding")
            .build();
        
        when(mcpClientService.invokeTool(eq("ping"), eq(Map.of())))
            .thenReturn(mcpResult);

        // Act
        PingFunction.Response response = pingFunction.apply(request);

        // Assert
        assertFalse(response.success());
        assertNull(response.message());
        assertNull(response.timestamp());
        assertNull(response.serverType());
        assertEquals("Server not responding", response.error());
    }

    @Test
    void testPingToolException() {
        // Arrange
        PingFunction.Request request = new PingFunction.Request();
        
        when(mcpClientService.invokeTool(eq("ping"), eq(Map.of())))
            .thenThrow(new RuntimeException("MCP connection lost"));

        // Act
        PingFunction.Response response = pingFunction.apply(request);

        // Assert
        assertFalse(response.success());
        assertNull(response.message());
        assertNull(response.timestamp());
        assertNull(response.serverType());
        assertTrue(response.error().contains("Ping function error"));
        assertTrue(response.error().contains("MCP connection lost"));
    }

    @Test
    void testPingWithPartialMetadata() {
        // Arrange
        PingFunction.Request request = new PingFunction.Request();
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(true)
            .content("pong")
            .metadata(Map.of("timestamp", "1750704859251")) // Missing server_type
            .build();
        
        when(mcpClientService.invokeTool(eq("ping"), eq(Map.of())))
            .thenReturn(mcpResult);

        // Act
        PingFunction.Response response = pingFunction.apply(request);

        // Assert
        assertTrue(response.success());
        assertEquals("pong", response.message());
        assertEquals("1750704859251", response.timestamp());
        assertEquals("unknown", response.serverType()); // Default value
        assertNull(response.error());
    }

    @Test
    void testPingWithNullMetadata() {
        // Arrange
        PingFunction.Request request = new PingFunction.Request();
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(true)
            .content("pong")
            .metadata(null) // No metadata at all
            .build();
        
        when(mcpClientService.invokeTool(eq("ping"), eq(Map.of())))
            .thenReturn(mcpResult);

        // Act
        PingFunction.Response response = pingFunction.apply(request);

        // Assert
        assertTrue(response.success());
        assertEquals("pong", response.message());
        assertNull(response.timestamp());
        assertEquals("unknown", response.serverType());
        assertNull(response.error());
    }

    @Test
    void testDefaultRequestConstructor() {
        // Arrange & Act
        PingFunction.Request request = new PingFunction.Request();

        // Assert
        assertNull(request.context());
    }

    @Test
    void testRequestWithContext() {
        // Arrange & Act
        PingFunction.Request request = new PingFunction.Request("Connection test context");

        // Assert
        assertEquals("Connection test context", request.context());
    }

    @Test
    void testPingWithEmptyMetadata() {
        // Arrange
        PingFunction.Request request = new PingFunction.Request();
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(true)
            .content("pong")
            .metadata(Map.of()) // Empty metadata
            .build();
        
        when(mcpClientService.invokeTool(eq("ping"), eq(Map.of())))
            .thenReturn(mcpResult);

        // Act
        PingFunction.Response response = pingFunction.apply(request);

        // Assert
        assertTrue(response.success());
        assertEquals("pong", response.message());
        assertNull(response.timestamp());
        assertEquals("unknown", response.serverType());
        assertNull(response.error());
    }
}