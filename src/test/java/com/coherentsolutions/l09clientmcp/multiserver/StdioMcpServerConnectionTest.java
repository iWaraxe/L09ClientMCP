package com.coherentsolutions.l09clientmcp.multiserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StdioMcpServerConnection.
 * 
 * These tests focus on configuration validation and basic functionality
 * without requiring actual external processes.
 */
class StdioMcpServerConnectionTest {
    
    private StdioMcpServerConnection connection;
    
    @BeforeEach
    void setUp() {
        connection = new StdioMcpServerConnection(
            "test-server",
            "echo",
            List.of("hello", "world"),
            Map.of("TEST_ENV", "value")
        );
    }
    
    @Test
    void testServerConfiguration() {
        assertEquals("test-server", connection.getServerId());
        assertEquals("stdio://echo hello world", connection.getUrl());
        assertEquals(McpServerConnection.ConnectionType.STDIO, connection.getConnectionType());
    }
    
    @Test
    void testInitialState() {
        assertFalse(connection.isConnected());
        assertTrue(connection.getAvailableTools().isEmpty());
    }
    
    @Test
    void testServerInfo() {
        McpServerConnection.ServerInfo info = connection.getServerInfo();
        assertEquals("test-server", info.serverId());
        assertEquals("1.0.0", info.version());
        assertTrue(info.description().contains("echo"));
        assertTrue(info.capabilities().contains("tools"));
        assertTrue(info.capabilities().contains("stdio"));
    }
    
    @Test
    void testConnectionStats() {
        McpServerConnection.ConnectionStats stats = connection.getConnectionStats();
        assertEquals(0, stats.totalRequests());
        assertEquals(0, stats.successfulRequests());
        assertEquals(0, stats.failedRequests());
        assertEquals(0.0, stats.getSuccessRate());
        assertEquals(0.0, stats.getFailureRate());
    }
    
    @Test
    void testHealthCheckWhenNotConnected() {
        McpServerConnection.HealthCheckResult result = connection.performHealthCheck();
        assertFalse(result.healthy());
        assertEquals("Not connected", result.message());
    }
    
    @Test
    void testToolInvocationWhenNotConnected() {
        assertThrows(McpServerConnection.McpConnectionException.class, () -> {
            connection.invokeTool("test-tool", Map.of());
        });
    }
    
    @Test
    void testToolInvocationWithUnavailableTool() {
        // This test would require mocking the connection state
        // For now, we test the exception path
        assertThrows(McpServerConnection.McpConnectionException.class, () -> {
            connection.invokeTool("non-existent-tool", Map.of());
        });
    }
    
    @Test
    void testDisconnectWhenNotConnected() {
        // Should not throw exception
        assertDoesNotThrow(() -> connection.disconnect());
    }
}