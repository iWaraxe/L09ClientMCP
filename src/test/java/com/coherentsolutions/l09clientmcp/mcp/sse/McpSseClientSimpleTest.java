package com.coherentsolutions.l09clientmcp.mcp.sse;

import com.coherentsolutions.l09clientmcp.mcp.McpTool;
import com.coherentsolutions.l09clientmcp.mcp.McpToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple unit tests for McpSseClient.
 * Branch 5: Basic tests for SSE-based MCP client implementation.
 * 
 * Educational Focus:
 * - Basic client behavior testing
 * - Initial state verification
 * - Connection state management
 * - Error handling without external dependencies
 */
class McpSseClientSimpleTest {

    private McpSseClient mcpSseClient;

    private static final String SERVER_URL = "http://localhost:3000/mcp";

    @BeforeEach
    void setUp() {
        mcpSseClient = new McpSseClient(SERVER_URL, 30, 3, 5);
    }

    @Test
    void testInitialState() {
        assertFalse(mcpSseClient.isConnected());
    }

    @Test
    void testListToolsWhenNotConnected() {
        List<McpTool> tools = mcpSseClient.listTools();
        
        assertTrue(tools.isEmpty());
    }

    @Test
    void testInvokeToolWhenNotConnected() {
        Map<String, Object> arguments = Map.of("message", "test");
        McpToolResult result = mcpSseClient.invokeTool("echo", arguments);
        
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("Not connected to MCP server", result.getContent());
    }

    @Test
    void testDisconnectWhenNotConnected() {
        // Should not throw exception
        assertDoesNotThrow(() -> mcpSseClient.disconnect());
        assertFalse(mcpSseClient.isConnected());
    }

    @Test
    void testClientConfiguration() {
        // Test that client was created with expected configuration
        assertNotNull(mcpSseClient);
        
        // Initial state should be disconnected
        assertFalse(mcpSseClient.isConnected());
    }
}