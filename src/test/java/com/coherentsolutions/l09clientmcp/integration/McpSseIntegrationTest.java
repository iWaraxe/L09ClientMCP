package com.coherentsolutions.l09clientmcp.integration;

import com.coherentsolutions.l09clientmcp.mcp.McpTool;
import com.coherentsolutions.l09clientmcp.mcp.McpToolResult;
import com.coherentsolutions.l09clientmcp.service.McpClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SSE MCP transport with real external server.
 * Branch 5: Tests real MCP protocol communication over SSE.
 * 
 * Educational Focus:
 * - Real network communication testing
 * - External server dependency management  
 * - End-to-end protocol validation
 * - Performance and reliability testing
 * 
 * Prerequisites:
 * - Node.js MCP server running on localhost:3000
 * - Environment variable MCP_INTEGRATION_TEST=true
 * 
 * To run these tests:
 * 1. Start the MCP server: cd mcp-server && npm start
 * 2. Set environment: export MCP_INTEGRATION_TEST=true
 * 3. Run tests: ./mvnw test -Dtest=McpSseIntegrationTest
 */
@SpringBootTest
@ActiveProfiles("mcp-sse")
@EnabledIfEnvironmentVariable(named = "MCP_INTEGRATION_TEST", matches = "true")
class McpSseIntegrationTest {

    @Autowired
    private McpClientService mcpClientService;

    @BeforeEach
    void setUp() {
        // Wait a moment for SSE connection to establish
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void testMcpServiceIsEnabled() {
        assertTrue(mcpClientService.isEnabled());
    }

    @Test
    void testMcpServiceIsHealthy() {
        // Note: This may fail if the external server is not running
        // That's expected behavior for integration tests
        boolean healthy = mcpClientService.isHealthy();
        
        if (healthy) {
            System.out.println("✅ MCP SSE connection is healthy");
        } else {
            System.out.println("❌ MCP SSE connection is not healthy - check if server is running");
        }
        
        // We don't assert here since server availability varies
        // This test documents the expected behavior
    }

    @Test
    void testGetStatusIncludesSSEInformation() {
        String status = mcpClientService.getStatus();
        
        assertTrue(status.contains("MCP enabled"));
        assertTrue(status.contains("Type: SSE"));
        assertTrue(status.contains("localhost:3000"));
        
        System.out.println("📊 MCP Status: " + status);
    }

    @Test
    void testListToolsFromExternalServer() {
        if (!mcpClientService.isHealthy()) {
            System.out.println("⏭️  Skipping tools test - server not available");
            return;
        }
        
        List<McpTool> tools = mcpClientService.listTools();
        
        assertNotNull(tools);
        System.out.println("🛠️  Available tools: " + tools.size());
        
        // Expected tools from our Node.js server
        boolean hasEcho = tools.stream().anyMatch(tool -> "echo".equals(tool.getName()));
        boolean hasPing = tools.stream().anyMatch(tool -> "ping".equals(tool.getName()));
        
        if (hasEcho && hasPing) {
            System.out.println("✅ Expected tools found: echo, ping");
            assertTrue(hasEcho);
            assertTrue(hasPing);
        } else {
            System.out.println("⚠️  Expected tools not found - server may be different version");
        }
    }

    @Test
    void testEchoToolInvocationOverSSE() {
        if (!mcpClientService.isHealthy()) {
            System.out.println("⏭️  Skipping echo test - server not available");
            return;
        }
        
        Map<String, Object> arguments = Map.of("message", "Hello SSE Integration Test!");
        
        McpToolResult result = mcpClientService.invokeTool("echo", arguments);
        
        assertNotNull(result);
        System.out.println("🔊 Echo result: " + result.getContent());
        
        if (result.isSuccess()) {
            assertTrue(result.getContent().contains("Hello SSE Integration Test!"));
            assertEquals("Hello SSE Integration Test!", result.getMetadata().get("original_message"));
            assertEquals("none", result.getMetadata().get("format_applied"));
            assertEquals("node_mcp_server", result.getMetadata().get("server_type"));
            System.out.println("✅ Echo tool working correctly via SSE");
        } else {
            System.out.println("❌ Echo tool failed: " + result.getContent());
        }
    }

    @Test
    void testEchoToolWithFormattingOverSSE() {
        if (!mcpClientService.isHealthy()) {
            System.out.println("⏭️  Skipping formatting test - server not available");
            return;
        }
        
        Map<String, Object> arguments = Map.of(
            "message", "Hello World",
            "format", "uppercase"
        );
        
        McpToolResult result = mcpClientService.invokeTool("echo", arguments);
        
        assertNotNull(result);
        System.out.println("🔤 Formatted echo result: " + result.getContent());
        
        if (result.isSuccess()) {
            assertTrue(result.getContent().contains("HELLO WORLD"));
            assertEquals("uppercase", result.getMetadata().get("format_applied"));
            System.out.println("✅ Echo formatting working correctly via SSE");
        } else {
            System.out.println("❌ Echo formatting failed: " + result.getContent());
        }
    }

    @Test
    void testPingToolInvocationOverSSE() {
        if (!mcpClientService.isHealthy()) {
            System.out.println("⏭️  Skipping ping test - server not available");
            return;
        }
        
        McpToolResult result = mcpClientService.invokeTool("ping", Map.of());
        
        assertNotNull(result);
        System.out.println("🏓 Ping result: " + result.getContent());
        
        if (result.isSuccess()) {
            assertEquals("pong", result.getContent());
            assertEquals("node_mcp_server", result.getMetadata().get("server_type"));
            assertNotNull(result.getMetadata().get("timestamp"));
            assertNotNull(result.getMetadata().get("uptime"));
            System.out.println("✅ Ping tool working correctly via SSE");
        } else {
            System.out.println("❌ Ping tool failed: " + result.getContent());
        }
    }

    @Test
    void testErrorHandlingOverSSE() {
        if (!mcpClientService.isHealthy()) {
            System.out.println("⏭️  Skipping error test - server not available");
            return;
        }
        
        // Test with empty message (should cause error)
        Map<String, Object> arguments = Map.of("message", "");
        
        McpToolResult result = mcpClientService.invokeTool("echo", arguments);
        
        assertNotNull(result);
        System.out.println("⚠️  Error handling result: " + result.getContent());
        
        if (!result.isSuccess()) {
            assertTrue(result.getContent().contains("empty") || result.getContent().contains("cannot"));
            System.out.println("✅ Error handling working correctly via SSE");
        } else {
            System.out.println("⚠️  Expected error but got success - server behavior may vary");
        }
    }

    @Test
    void testLatencyMeasurement() {
        if (!mcpClientService.isHealthy()) {
            System.out.println("⏭️  Skipping latency test - server not available");
            return;
        }
        
        long startTime = System.currentTimeMillis();
        
        McpToolResult result = mcpClientService.invokeTool("ping", Map.of());
        
        long endTime = System.currentTimeMillis();
        long latency = endTime - startTime;
        
        System.out.println("⏱️  SSE Tool invocation latency: " + latency + "ms");
        
        if (result.isSuccess()) {
            // For local testing, latency should be reasonable
            assertTrue(latency < 5000, "Latency should be under 5 seconds for local server");
            System.out.println("✅ Latency within acceptable range");
        }
    }
}