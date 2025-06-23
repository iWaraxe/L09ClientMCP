package com.coherentsolutions.l09clientmcp.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MockMcpEchoServerTest {

    private MockMcpEchoServer echoServer;

    @BeforeEach
    void setUp() {
        echoServer = new MockMcpEchoServer();
    }

    @Test
    void testConnectionManagement() {
        // Initially not connected
        assertFalse(echoServer.isConnected());

        // Connect
        echoServer.connect();
        assertTrue(echoServer.isConnected());

        // Disconnect
        echoServer.disconnect();
        assertFalse(echoServer.isConnected());
    }

    @Test
    void testListToolsWhenConnected() {
        echoServer.connect();
        
        List<McpTool> tools = echoServer.listTools();
        
        assertNotNull(tools);
        assertEquals(2, tools.size());
        
        // Check echo tool
        McpTool echoTool = tools.stream()
            .filter(tool -> "echo".equals(tool.getName()))
            .findFirst()
            .orElse(null);
        assertNotNull(echoTool);
        assertEquals("echo", echoTool.getName());
        assertTrue(echoTool.getDescription().toLowerCase().contains("echo"));
        assertNotNull(echoTool.getInputSchema());
        
        // Check ping tool
        McpTool pingTool = tools.stream()
            .filter(tool -> "ping".equals(tool.getName()))
            .findFirst()
            .orElse(null);
        assertNotNull(pingTool);
        assertEquals("ping", pingTool.getName());
        assertTrue(pingTool.getDescription().contains("ping"));
    }

    @Test
    void testListToolsWhenNotConnected() {
        assertFalse(echoServer.isConnected());
        
        assertThrows(IllegalStateException.class, () -> echoServer.listTools());
    }

    @Test
    void testEchoToolBasic() {
        echoServer.connect();
        
        Map<String, Object> arguments = Map.of("message", "Hello World");
        McpToolResult result = echoServer.invokeTool("echo", arguments);
        
        assertTrue(result.isSuccess());
        assertEquals("Echo: Hello World", result.getContent());
        assertNotNull(result.getMetadata());
        assertEquals("Hello World", result.getMetadata().get("original_message"));
        assertEquals("none", result.getMetadata().get("format_applied"));
    }

    @Test
    void testEchoToolWithUppercase() {
        echoServer.connect();
        
        Map<String, Object> arguments = Map.of("message", "hello", "format", "uppercase");
        McpToolResult result = echoServer.invokeTool("echo", arguments);
        
        assertTrue(result.isSuccess());
        assertEquals("Echo: HELLO", result.getContent());
        assertEquals("uppercase", result.getMetadata().get("format_applied"));
    }

    @Test
    void testEchoToolWithLowercase() {
        echoServer.connect();
        
        Map<String, Object> arguments = Map.of("message", "HELLO", "format", "lowercase");
        McpToolResult result = echoServer.invokeTool("echo", arguments);
        
        assertTrue(result.isSuccess());
        assertEquals("Echo: hello", result.getContent());
        assertEquals("lowercase", result.getMetadata().get("format_applied"));
    }

    @Test
    void testEchoToolWithReverse() {
        echoServer.connect();
        
        Map<String, Object> arguments = Map.of("message", "hello", "format", "reverse");
        McpToolResult result = echoServer.invokeTool("echo", arguments);
        
        assertTrue(result.isSuccess());
        assertEquals("Echo: olleh", result.getContent());
        assertEquals("reverse", result.getMetadata().get("format_applied"));
    }

    @Test
    void testEchoToolWithEmptyMessage() {
        echoServer.connect();
        
        Map<String, Object> arguments = Map.of("message", "");
        McpToolResult result = echoServer.invokeTool("echo", arguments);
        
        assertFalse(result.isSuccess());
        assertEquals("Message cannot be empty", result.getContent());
    }

    @Test
    void testEchoToolWithNullMessage() {
        echoServer.connect();
        
        Map<String, Object> arguments = Map.of();
        McpToolResult result = echoServer.invokeTool("echo", arguments);
        
        assertFalse(result.isSuccess());
        assertEquals("Message cannot be empty", result.getContent());
    }

    @Test
    void testPingTool() {
        echoServer.connect();
        
        Map<String, Object> arguments = Map.of();
        McpToolResult result = echoServer.invokeTool("ping", arguments);
        
        assertTrue(result.isSuccess());
        assertEquals("pong", result.getContent());
        assertNotNull(result.getMetadata());
        assertTrue(result.getMetadata().containsKey("timestamp"));
        assertEquals("mock_echo_server", result.getMetadata().get("server_type"));
    }

    @Test
    void testUnknownTool() {
        echoServer.connect();
        
        Map<String, Object> arguments = Map.of();
        McpToolResult result = echoServer.invokeTool("unknown", arguments);
        
        assertFalse(result.isSuccess());
        assertEquals("Unknown tool: unknown", result.getContent());
    }

    @Test
    void testInvokeToolWhenNotConnected() {
        assertFalse(echoServer.isConnected());
        
        Map<String, Object> arguments = Map.of("message", "test");
        assertThrows(IllegalStateException.class, 
            () -> echoServer.invokeTool("echo", arguments));
    }
}