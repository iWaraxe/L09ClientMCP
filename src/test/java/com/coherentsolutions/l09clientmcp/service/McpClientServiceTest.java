package com.coherentsolutions.l09clientmcp.service;

import com.coherentsolutions.l09clientmcp.mcp.MockMcpEchoServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for McpClientService functionality.
 * Tests the foundational MCP client behavior and configuration handling.
 * 
 * Branch 3 Update: Updated tests to work with MockMcpEchoServer integration.
 */
@ExtendWith(MockitoExtension.class)
class McpClientServiceTest {

    @Test
    void testMcpDisabled() {
        MockMcpEchoServer mockEchoServer = new MockMcpEchoServer();
        McpClientService service = new McpClientServiceImpl(false, "STDIO", "30s", mockEchoServer);
        
        assertFalse(service.isEnabled());
        assertTrue(service.isHealthy()); // Should return true for graceful degradation
        assertEquals("MCP disabled - running in baseline mode", service.getStatus());
    }
    
    @Test
    void testMcpEnabledStdio() {
        MockMcpEchoServer mockEchoServer = new MockMcpEchoServer();
        McpClientService service = new McpClientServiceImpl(true, "STDIO", "30s", mockEchoServer);
        
        assertTrue(service.isEnabled());
        assertFalse(service.isHealthy()); // Should return false since echo server not connected
        assertTrue(service.getStatus().contains("MCP enabled"));
        assertTrue(service.getStatus().contains("Type: STDIO"));
        assertTrue(service.getStatus().contains("Timeout: 30s"));
        assertTrue(service.getStatus().contains("disconnected"));
    }
    
    @Test
    void testMcpEnabledSse() {
        MockMcpEchoServer mockEchoServer = new MockMcpEchoServer();
        McpClientService service = new McpClientServiceImpl(true, "SSE", "45s", mockEchoServer);
        
        assertTrue(service.isEnabled());
        assertFalse(service.isHealthy()); // Should return false since echo server not connected
        assertTrue(service.getStatus().contains("Type: SSE"));
        assertTrue(service.getStatus().contains("Timeout: 45s"));
        assertTrue(service.getStatus().contains("disconnected"));
    }
    
    @Test
    void testTimeoutParsing() {
        // Test different timeout formats
        MockMcpEchoServer mockServer = new MockMcpEchoServer();
        
        McpClientService service1 = new McpClientServiceImpl(true, "STDIO", "60s", mockServer);
        assertTrue(service1.getStatus().contains("Timeout: 60s"));
        
        McpClientService service2 = new McpClientServiceImpl(true, "STDIO", "2m", mockServer);
        assertTrue(service2.getStatus().contains("Timeout: 120s"));
        
        McpClientService service3 = new McpClientServiceImpl(true, "STDIO", "45", mockServer);
        assertTrue(service3.getStatus().contains("Timeout: 45s"));
        
        // Test invalid timeout (should default to 30)
        McpClientService service4 = new McpClientServiceImpl(true, "STDIO", "invalid", mockServer);
        assertTrue(service4.getStatus().contains("Timeout: 30s"));
    }
}