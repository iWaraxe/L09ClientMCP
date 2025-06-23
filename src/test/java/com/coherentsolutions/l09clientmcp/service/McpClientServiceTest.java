package com.coherentsolutions.l09clientmcp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for McpClientService functionality.
 * Tests the foundational MCP client behavior and configuration handling.
 */
@ExtendWith(MockitoExtension.class)
class McpClientServiceTest {

    @Test
    void testMcpDisabled() {
        McpClientService service = new McpClientServiceImpl(false, "STDIO", "30s");
        
        assertFalse(service.isEnabled());
        assertTrue(service.isHealthy()); // Should return true for graceful degradation
        assertEquals("MCP disabled - running in baseline mode", service.getStatus());
    }
    
    @Test
    void testMcpEnabledStdio() {
        McpClientService service = new McpClientServiceImpl(true, "STDIO", "30s");
        
        assertTrue(service.isEnabled());
        assertTrue(service.isHealthy()); // Should return true (no servers configured yet)
        assertTrue(service.getStatus().contains("MCP enabled"));
        assertTrue(service.getStatus().contains("Type: STDIO"));
        assertTrue(service.getStatus().contains("Timeout: 30s"));
    }
    
    @Test
    void testMcpEnabledSse() {
        McpClientService service = new McpClientServiceImpl(true, "SSE", "45s");
        
        assertTrue(service.isEnabled());
        assertTrue(service.isHealthy());
        assertTrue(service.getStatus().contains("Type: SSE"));
        assertTrue(service.getStatus().contains("Timeout: 45s"));
    }
    
    @Test
    void testTimeoutParsing() {
        // Test different timeout formats
        McpClientService service1 = new McpClientServiceImpl(true, "STDIO", "60s");
        assertTrue(service1.getStatus().contains("Timeout: 60s"));
        
        McpClientService service2 = new McpClientServiceImpl(true, "STDIO", "2m");
        assertTrue(service2.getStatus().contains("Timeout: 120s"));
        
        McpClientService service3 = new McpClientServiceImpl(true, "STDIO", "45");
        assertTrue(service3.getStatus().contains("Timeout: 45s"));
        
        // Test invalid timeout (should default to 30)
        McpClientService service4 = new McpClientServiceImpl(true, "STDIO", "invalid");
        assertTrue(service4.getStatus().contains("Timeout: 30s"));
    }
}