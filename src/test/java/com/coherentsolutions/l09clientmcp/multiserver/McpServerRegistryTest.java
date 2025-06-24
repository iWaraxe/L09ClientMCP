package com.coherentsolutions.l09clientmcp.multiserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for McpServerRegistry.
 * Branch 9: Tests multi-server registry functionality for MCP server management.
 * 
 * Educational Focus:
 * - Server registration and discovery
 * - Tool-to-server mapping
 * - Health status management
 * - Thread-safe operations
 */
@ExtendWith(MockitoExtension.class)
class McpServerRegistryTest {
    
    @Mock
    private McpServerConnection server1;
    
    @Mock
    private McpServerConnection server2;
    
    @Mock
    private McpServerConnection server3;
    
    private McpServerRegistry registry;
    
    @BeforeEach
    void setUp() {
        registry = new McpServerRegistry();
        
        // Setup mock servers
        when(server1.getServerId()).thenReturn("server1");
        when(server1.getUrl()).thenReturn("mock://server1");
        when(server1.getAvailableTools()).thenReturn(Set.of("echo", "ping"));
        
        when(server2.getServerId()).thenReturn("server2");
        when(server2.getUrl()).thenReturn("mock://server2");
        when(server2.getAvailableTools()).thenReturn(Set.of("echo", "search"));
        
        when(server3.getServerId()).thenReturn("server3");
        when(server3.getUrl()).thenReturn("mock://server3");
        when(server3.getAvailableTools()).thenReturn(Set.of("calculate", "convert"));
    }
    
    @Test
    void testServerRegistration() {
        // Act
        registry.registerServer("server1", server1);
        
        // Assert
        assertTrue(registry.getAllServerIds().contains("server1"));
        Optional<McpServerConnection> retrieved = registry.getServer("server1");
        assertTrue(retrieved.isPresent());
        assertEquals("server1", retrieved.get().getServerId());
        
        // Verify health is initialized
        Optional<McpServerRegistry.ServerHealth> health = registry.getServerHealth("server1");
        assertTrue(health.isPresent());
        assertTrue(health.get().isHealthy());
    }
    
    @Test
    void testServerUnregistration() {
        // Arrange
        registry.registerServer("server1", server1);
        assertTrue(registry.getAllServerIds().contains("server1"));
        
        // Act
        registry.unregisterServer("server1");
        
        // Assert
        assertFalse(registry.getAllServerIds().contains("server1"));
        assertTrue(registry.getServer("server1").isEmpty());
        assertTrue(registry.getServerHealth("server1").isEmpty());
        
        // Verify server connection was disconnected
        verify(server1, times(1)).disconnect();
    }
    
    @Test
    void testToolToServerMapping() {
        // Arrange
        registry.registerServer("server1", server1);
        registry.registerServer("server2", server2);
        registry.registerServer("server3", server3);
        
        // Act & Assert
        Set<String> echoServers = registry.getServersForTool("echo");
        assertEquals(Set.of("server1", "server2"), echoServers);
        
        Set<String> pingServers = registry.getServersForTool("ping");
        assertEquals(Set.of("server1"), pingServers);
        
        Set<String> searchServers = registry.getServersForTool("search");
        assertEquals(Set.of("server2"), searchServers);
        
        Set<String> calculateServers = registry.getServersForTool("calculate");
        assertEquals(Set.of("server3"), calculateServers);
        
        Set<String> unknownServers = registry.getServersForTool("unknown");
        assertTrue(unknownServers.isEmpty());
    }
    
    @Test
    void testHealthyServerRetrieval() {
        // Arrange
        registry.registerServer("server1", server1);
        registry.registerServer("server2", server2);
        
        // Initially both servers should be healthy
        assertEquals(2, registry.getAllHealthyServers().size());
        assertTrue(registry.getHealthyServer("server1").isPresent());
        assertTrue(registry.getHealthyServer("server2").isPresent());
        
        // Act - Mark server1 as unhealthy
        registry.updateServerHealth("server1", false, "Connection timeout");
        
        // Assert
        assertEquals(1, registry.getAllHealthyServers().size());
        assertTrue(registry.getHealthyServer("server1").isEmpty());
        assertTrue(registry.getHealthyServer("server2").isPresent());
    }
    
    @Test
    void testHealthyServersForTool() {
        // Arrange
        registry.registerServer("server1", server1);
        registry.registerServer("server2", server2);
        
        // Act & Assert - Initially both servers are healthy and provide echo
        List<McpServerConnection> echoServers = registry.getHealthyServersForTool("echo");
        assertEquals(2, echoServers.size());
        
        // Mark server1 as unhealthy
        registry.updateServerHealth("server1", false, "Network error");
        
        // Only server2 should be available for echo now
        echoServers = registry.getHealthyServersForTool("echo");
        assertEquals(1, echoServers.size());
        assertEquals("server2", echoServers.get(0).getServerId());
    }
    
    @Test
    void testAllAvailableTools() {
        // Arrange
        registry.registerServer("server1", server1);
        registry.registerServer("server2", server2);
        registry.registerServer("server3", server3);
        
        // Act
        Set<String> allTools = registry.getAllAvailableTools();
        
        // Assert
        Set<String> expectedTools = Set.of("echo", "ping", "search", "calculate", "convert");
        assertEquals(expectedTools, allTools);
    }
    
    @Test
    void testAllAvailableToolsWithUnhealthyServers() {
        // Arrange
        registry.registerServer("server1", server1);
        registry.registerServer("server2", server2);
        registry.registerServer("server3", server3);
        
        // Mark server3 as unhealthy
        registry.updateServerHealth("server3", false, "Server down");
        
        // Act
        Set<String> allTools = registry.getAllAvailableTools();
        
        // Assert - Should only include tools from healthy servers
        Set<String> expectedTools = Set.of("echo", "ping", "search");
        assertEquals(expectedTools, allTools);
    }
    
    @Test
    void testRegistryStats() {
        // Arrange
        registry.registerServer("server1", server1);
        registry.registerServer("server2", server2);
        registry.registerServer("server3", server3);
        
        // Mark one server as unhealthy
        registry.updateServerHealth("server2", false, "Maintenance");
        
        // Act
        McpServerRegistry.RegistryStats stats = registry.getRegistryStats();
        
        // Assert
        assertEquals(3, stats.getTotalServers());
        assertEquals(2, stats.getHealthyServers());
        assertEquals(5, stats.getTotalTools()); // Total unique tools across all servers
        assertEquals(4, stats.getAvailableTools()); // Tools from healthy servers only
        
        assertEquals(2.0/3.0, stats.getHealthyServerRatio(), 0.001);
        assertEquals(4.0/5.0, stats.getAvailableToolRatio(), 0.001);
    }
    
    @Test
    void testServerHealthUpdates() {
        // Arrange
        registry.registerServer("server1", server1);
        Optional<McpServerRegistry.ServerHealth> health = registry.getServerHealth("server1");
        assertTrue(health.isPresent());
        assertTrue(health.get().isHealthy());
        
        // Act - Update health status
        registry.updateServerHealth("server1", false, "Connection failed");
        
        // Assert
        health = registry.getServerHealth("server1");
        assertTrue(health.isPresent());
        assertFalse(health.get().isHealthy());
        assertEquals("Connection failed", health.get().getHealthCheckReason());
        assertTrue(health.get().getLastChecked() > 0);
    }
    
    @Test
    void testServerHealthRecording() {
        // Arrange
        registry.registerServer("server1", server1);
        Optional<McpServerRegistry.ServerHealth> health = registry.getServerHealth("server1");
        assertTrue(health.isPresent());
        
        McpServerRegistry.ServerHealth serverHealth = health.get();
        
        // Act - Record success
        serverHealth.recordSuccess(150);
        
        // Assert
        assertTrue(serverHealth.isHealthy());
        assertEquals(150, serverHealth.getResponseTimeMs());
        assertEquals("Health check passed", serverHealth.getHealthCheckReason());
        
        // Act - Record failure
        serverHealth.recordFailure("Network timeout");
        
        // Assert
        assertFalse(serverHealth.isHealthy());
        assertEquals(1, serverHealth.getFailureCount());
        assertEquals("Network timeout", serverHealth.getHealthCheckReason());
        assertTrue(serverHealth.getLastFailureTime() > 0);
    }
    
    @Test
    void testConcurrentRegistration() throws InterruptedException {
        // Arrange
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        
        // Act - Multiple threads registering servers concurrently
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                McpServerConnection mockServer = mock(McpServerConnection.class);
                when(mockServer.getServerId()).thenReturn("server" + index);
                when(mockServer.getUrl()).thenReturn("mock://server" + index);
                when(mockServer.getAvailableTools()).thenReturn(Set.of("tool" + index));
                
                registry.registerServer("server" + index, mockServer);
            });
            threads[i].start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Assert
        assertEquals(threadCount, registry.getAllServerIds().size());
        assertEquals(threadCount, registry.getAllHealthyServers().size());
        assertEquals(threadCount, registry.getAllAvailableTools().size());
    }
    
    @Test
    void testUnregisterNonExistentServer() {
        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> registry.unregisterServer("non-existent"));
    }
    
    @Test
    void testGetHealthOfNonExistentServer() {
        // Act & Assert
        assertTrue(registry.getServerHealth("non-existent").isEmpty());
    }
    
    @Test
    void testUpdateHealthOfNonExistentServer() {
        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> registry.updateServerHealth("non-existent", false, "Not found"));
    }
}