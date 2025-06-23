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
 * Unit tests for EchoFunction.
 * Branch 4: Tests Spring AI Function integration for MCP echo tool.
 * 
 * Educational Focus:
 * - Function-based tool integration patterns
 * - Type-safe parameter handling with records
 * - Error handling and response transformation
 * - MCP tool result processing
 */
@ExtendWith(MockitoExtension.class)
class EchoFunctionTest {

    @Mock
    private McpClientService mcpClientService;

    private EchoFunction echoFunction;

    @BeforeEach
    void setUp() {
        echoFunction = new EchoFunction(mcpClientService);
    }

    @Test
    void testSuccessfulEchoWithoutFormat() {
        // Arrange
        EchoFunction.Request request = new EchoFunction.Request("Hello World", null);
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(true)
            .content("Echo: Hello World")
            .metadata(Map.of("original_message", "Hello World", "format_applied", "none"))
            .build();
        
        when(mcpClientService.invokeTool(eq("echo"), eq(Map.of("message", "Hello World"))))
            .thenReturn(mcpResult);

        // Act
        EchoFunction.Response response = echoFunction.apply(request);

        // Assert
        assertTrue(response.success());
        assertEquals("Echo: Hello World", response.result());
        assertEquals("Hello World", response.originalMessage());
        assertEquals("none", response.formatApplied());
        assertNull(response.error());
    }

    @Test
    void testSuccessfulEchoWithUppercaseFormat() {
        // Arrange
        EchoFunction.Request request = new EchoFunction.Request("hello", "uppercase");
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(true)
            .content("Echo: HELLO")
            .metadata(Map.of("original_message", "hello", "format_applied", "uppercase"))
            .build();
        
        when(mcpClientService.invokeTool(eq("echo"), eq(Map.of("message", "hello", "format", "uppercase"))))
            .thenReturn(mcpResult);

        // Act
        EchoFunction.Response response = echoFunction.apply(request);

        // Assert
        assertTrue(response.success());
        assertEquals("Echo: HELLO", response.result());
        assertEquals("hello", response.originalMessage());
        assertEquals("uppercase", response.formatApplied());
        assertNull(response.error());
    }

    @Test
    void testSuccessfulEchoWithLowercaseFormat() {
        // Arrange
        EchoFunction.Request request = new EchoFunction.Request("HELLO", "lowercase");
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(true)
            .content("Echo: hello")
            .metadata(Map.of("original_message", "HELLO", "format_applied", "lowercase"))
            .build();
        
        when(mcpClientService.invokeTool(eq("echo"), eq(Map.of("message", "HELLO", "format", "lowercase"))))
            .thenReturn(mcpResult);

        // Act
        EchoFunction.Response response = echoFunction.apply(request);

        // Assert
        assertTrue(response.success());
        assertEquals("Echo: hello", response.result());
        assertEquals("HELLO", response.originalMessage());
        assertEquals("lowercase", response.formatApplied());
    }

    @Test
    void testSuccessfulEchoWithReverseFormat() {
        // Arrange
        EchoFunction.Request request = new EchoFunction.Request("hello", "reverse");
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(true)
            .content("Echo: olleh")
            .metadata(Map.of("original_message", "hello", "format_applied", "reverse"))
            .build();
        
        when(mcpClientService.invokeTool(eq("echo"), eq(Map.of("message", "hello", "format", "reverse"))))
            .thenReturn(mcpResult);

        // Act
        EchoFunction.Response response = echoFunction.apply(request);

        // Assert
        assertTrue(response.success());
        assertEquals("Echo: olleh", response.result());
        assertEquals("reverse", response.formatApplied());
    }

    @Test
    void testFailedEchoTool() {
        // Arrange
        EchoFunction.Request request = new EchoFunction.Request("", null);
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(false)
            .content("Message cannot be empty")
            .build();
        
        when(mcpClientService.invokeTool(eq("echo"), eq(Map.of("message", ""))))
            .thenReturn(mcpResult);

        // Act
        EchoFunction.Response response = echoFunction.apply(request);

        // Assert
        assertFalse(response.success());
        assertNull(response.result());
        assertEquals("", response.originalMessage());
        assertNull(response.formatApplied());
        assertEquals("Message cannot be empty", response.error());
    }

    @Test
    void testEchoToolException() {
        // Arrange
        EchoFunction.Request request = new EchoFunction.Request("test", null);
        
        when(mcpClientService.invokeTool(eq("echo"), eq(Map.of("message", "test"))))
            .thenThrow(new RuntimeException("MCP connection failed"));

        // Act
        EchoFunction.Response response = echoFunction.apply(request);

        // Assert
        assertFalse(response.success());
        assertNull(response.result());
        assertEquals("test", response.originalMessage());
        assertNull(response.formatApplied());
        assertTrue(response.error().contains("Echo function error"));
        assertTrue(response.error().contains("MCP connection failed"));
    }

    @Test
    void testFormatParameterTrimming() {
        // Arrange - test that format is trimmed and normalized
        EchoFunction.Request request = new EchoFunction.Request("test", "  UPPERCASE  ");
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(true)
            .content("Echo: TEST")
            .metadata(Map.of("format_applied", "uppercase"))
            .build();
        
        when(mcpClientService.invokeTool(eq("echo"), eq(Map.of("message", "test", "format", "uppercase"))))
            .thenReturn(mcpResult);

        // Act
        EchoFunction.Response response = echoFunction.apply(request);

        // Assert
        assertTrue(response.success());
        assertEquals("Echo: TEST", response.result());
    }

    @Test
    void testEmptyFormatIgnored() {
        // Arrange - test that empty format string is ignored
        EchoFunction.Request request = new EchoFunction.Request("test", "");
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(true)
            .content("Echo: test")
            .metadata(Map.of("format_applied", "none"))
            .build();
        
        when(mcpClientService.invokeTool(eq("echo"), eq(Map.of("message", "test"))))
            .thenReturn(mcpResult);

        // Act
        EchoFunction.Response response = echoFunction.apply(request);

        // Assert
        assertTrue(response.success());
        assertEquals("Echo: test", response.result());
    }

    @Test
    void testMetadataHandlingWhenNull() {
        // Arrange
        EchoFunction.Request request = new EchoFunction.Request("test", null);
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(true)
            .content("Echo: test")
            .metadata(null) // No metadata
            .build();
        
        when(mcpClientService.invokeTool(eq("echo"), eq(Map.of("message", "test"))))
            .thenReturn(mcpResult);

        // Act
        EchoFunction.Response response = echoFunction.apply(request);

        // Assert
        assertTrue(response.success());
        assertEquals("Echo: test", response.result());
        assertEquals("test", response.originalMessage());
        assertNull(response.formatApplied()); // Should handle null metadata gracefully
        assertNull(response.error());
    }
}