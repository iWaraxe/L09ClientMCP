package com.coherentsolutions.l09clientmcp.multiserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RoundRobinServerSelector.
 * Branch 9: Tests round-robin load balancing algorithm for multi-server MCP setup.
 * 
 * Educational Focus:
 * - Round-robin algorithm verification
 * - Load balancing fairness testing
 * - Thread safety validation
 * - Statistics tracking verification
 */
@ExtendWith(MockitoExtension.class)
class RoundRobinServerSelectorTest {
    
    @Mock
    private McpServerConnection server1;
    
    @Mock
    private McpServerConnection server2;
    
    @Mock
    private McpServerConnection server3;
    
    private RoundRobinServerSelector selector;
    
    @BeforeEach
    void setUp() {
        selector = new RoundRobinServerSelector();
        
        lenient().when(server1.getServerId()).thenReturn("server1");
        lenient().when(server2.getServerId()).thenReturn("server2");
        lenient().when(server3.getServerId()).thenReturn("server3");
    }
    
    @Test
    void testSingleServerSelection() {
        // Arrange
        List<McpServerConnection> candidates = List.of(server1);
        ServerSelector.SelectionContext context = ServerSelector.SelectionContext.create("test-1");
        
        // Act
        Optional<McpServerConnection> selected = selector.selectServer("echo", candidates, context);
        
        // Assert
        assertTrue(selected.isPresent());
        assertEquals("server1", selected.get().getServerId());
    }
    
    @Test
    void testEmptyServerList() {
        // Arrange
        List<McpServerConnection> candidates = List.of();
        ServerSelector.SelectionContext context = ServerSelector.SelectionContext.create("test-2");
        
        // Act
        Optional<McpServerConnection> selected = selector.selectServer("echo", candidates, context);
        
        // Assert
        assertTrue(selected.isEmpty());
    }
    
    @Test
    void testNullServerList() {
        // Arrange
        ServerSelector.SelectionContext context = ServerSelector.SelectionContext.create("test-3");
        
        // Act
        Optional<McpServerConnection> selected = selector.selectServer("echo", null, context);
        
        // Assert
        assertTrue(selected.isEmpty());
    }
    
    @Test
    void testRoundRobinDistribution() {
        // Arrange
        List<McpServerConnection> candidates = List.of(server1, server2, server3);
        ServerSelector.SelectionContext context = ServerSelector.SelectionContext.create("test-4");
        
        // Act - First round
        Optional<McpServerConnection> selection1 = selector.selectServer("echo", candidates, context);
        Optional<McpServerConnection> selection2 = selector.selectServer("echo", candidates, context);
        Optional<McpServerConnection> selection3 = selector.selectServer("echo", candidates, context);
        
        // Second round
        Optional<McpServerConnection> selection4 = selector.selectServer("echo", candidates, context);
        Optional<McpServerConnection> selection5 = selector.selectServer("echo", candidates, context);
        Optional<McpServerConnection> selection6 = selector.selectServer("echo", candidates, context);
        
        // Assert
        assertTrue(selection1.isPresent());
        assertTrue(selection2.isPresent());
        assertTrue(selection3.isPresent());
        assertTrue(selection4.isPresent());
        assertTrue(selection5.isPresent());
        assertTrue(selection6.isPresent());
        
        // Verify round-robin pattern
        assertEquals("server1", selection1.get().getServerId());
        assertEquals("server2", selection2.get().getServerId());
        assertEquals("server3", selection3.get().getServerId());
        
        // Second round should repeat the pattern
        assertEquals("server1", selection4.get().getServerId());
        assertEquals("server2", selection5.get().getServerId());
        assertEquals("server3", selection6.get().getServerId());
    }
    
    @Test
    void testPerToolCounterSeparation() {
        // Arrange
        List<McpServerConnection> candidates = List.of(server1, server2, server3);
        ServerSelector.SelectionContext context = ServerSelector.SelectionContext.create("test-5");
        
        // Act - Different tools should have separate counters
        Optional<McpServerConnection> echoSelection1 = selector.selectServer("echo", candidates, context);
        Optional<McpServerConnection> pingSelection1 = selector.selectServer("ping", candidates, context);
        Optional<McpServerConnection> echoSelection2 = selector.selectServer("echo", candidates, context);
        Optional<McpServerConnection> pingSelection2 = selector.selectServer("ping", candidates, context);
        
        // Assert - Each tool should start from the first server
        assertEquals("server1", echoSelection1.get().getServerId());
        assertEquals("server1", pingSelection1.get().getServerId());
        assertEquals("server2", echoSelection2.get().getServerId());
        assertEquals("server2", pingSelection2.get().getServerId());
    }
    
    @Test
    void testStrategyName() {
        // Act & Assert
        assertEquals("ROUND_ROBIN", selector.getStrategyName());
    }
    
    @Test
    void testStatisticsTracking() {
        // Arrange
        List<McpServerConnection> candidates = List.of(server1, server2);
        ServerSelector.SelectionContext context = ServerSelector.SelectionContext.create("test-6");
        
        // Act
        selector.selectServer("echo", candidates, context);
        selector.selectServer("echo", candidates, context);
        selector.selectServer("ping", candidates, context);
        
        // Assert
        ServerSelector.SelectionStats stats = selector.getSelectionStats();
        assertEquals(3, stats.totalSelections());
        assertTrue(stats.serverSelectionCounts().containsKey("server1"));
        assertTrue(stats.serverSelectionCounts().containsKey("server2"));
        assertTrue(stats.toolSelectionCounts().containsKey("echo"));
        assertTrue(stats.toolSelectionCounts().containsKey("ping"));
        
        // Verify counts
        assertEquals(2, stats.serverSelectionCounts().get("server1")); // echo[0], ping[0]
        assertEquals(1, stats.serverSelectionCounts().get("server2")); // echo[1]
        assertEquals(2, stats.toolSelectionCounts().get("echo"));
        assertEquals(1, stats.toolSelectionCounts().get("ping"));
    }
    
    @Test
    void testStatisticsReset() {
        // Arrange
        List<McpServerConnection> candidates = List.of(server1, server2);
        ServerSelector.SelectionContext context = ServerSelector.SelectionContext.create("test-7");
        
        // Act
        selector.selectServer("echo", candidates, context);
        selector.selectServer("echo", candidates, context);
        
        ServerSelector.SelectionStats statsBefore = selector.getSelectionStats();
        assertEquals(2, statsBefore.totalSelections());
        
        selector.resetStats();
        ServerSelector.SelectionStats statsAfter = selector.getSelectionStats();
        
        // Assert
        assertEquals(0, statsAfter.totalSelections());
        assertTrue(statsAfter.serverSelectionCounts().isEmpty());
        assertTrue(statsAfter.toolSelectionCounts().isEmpty());
    }
    
    @Test
    void testCounterOverflow() {
        // Arrange
        List<McpServerConnection> candidates = List.of(server1, server2);
        ServerSelector.SelectionContext context = ServerSelector.SelectionContext.create("test-8");
        
        // Act - Simulate many selections to test counter overflow handling
        for (int i = 0; i < 1000; i++) {
            Optional<McpServerConnection> selected = selector.selectServer("echo", candidates, context);
            assertTrue(selected.isPresent());
            
            // Verify alternating pattern is maintained
            String expectedServer = (i % 2 == 0) ? "server1" : "server2";
            assertEquals(expectedServer, selected.get().getServerId());
        }
        
        // Assert statistics are correct
        ServerSelector.SelectionStats stats = selector.getSelectionStats();
        assertEquals(1000, stats.totalSelections());
        assertEquals(500, stats.serverSelectionCounts().get("server1"));
        assertEquals(500, stats.serverSelectionCounts().get("server2"));
    }
    
    @Test
    void testConcurrentAccess() throws InterruptedException {
        // Arrange
        List<McpServerConnection> candidates = List.of(server1, server2, server3);
        ServerSelector.SelectionContext context = ServerSelector.SelectionContext.create("test-9");
        int threadsCount = 10;
        int selectionsPerThread = 100;
        Thread[] threads = new Thread[threadsCount];
        
        // Act - Multiple threads selecting servers concurrently
        for (int i = 0; i < threadsCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < selectionsPerThread; j++) {
                    Optional<McpServerConnection> selected = selector.selectServer("echo", candidates, context);
                    assertTrue(selected.isPresent());
                }
            });
            threads[i].start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Assert
        ServerSelector.SelectionStats stats = selector.getSelectionStats();
        assertEquals(threadsCount * selectionsPerThread, stats.totalSelections());
        
        // Verify all servers were selected (distribution may not be perfectly even due to concurrency)
        assertTrue(stats.serverSelectionCounts().containsKey("server1"));
        assertTrue(stats.serverSelectionCounts().containsKey("server2"));
        assertTrue(stats.serverSelectionCounts().containsKey("server3"));
        
        // Verify total selections distributed across all servers
        long totalServerSelections = stats.serverSelectionCounts().values().stream()
                .mapToLong(Long::longValue)
                .sum();
        assertEquals(threadsCount * selectionsPerThread, totalServerSelections);
    }
}