package com.coherentsolutions.l09clientmcp.multiserver;

import java.util.List;
import java.util.Optional;

/**
 * Strategy interface for selecting an MCP server from available candidates.
 * 
 * Different implementations can provide various load balancing strategies
 * such as round-robin, weighted selection, least connections, or performance-based routing.
 * 
 * Educational Focus:
 * - Strategy pattern implementation
 * - Load balancing algorithms
 * - Server selection criteria
 * - Performance optimization strategies
 */
public interface ServerSelector {
    
    /**
     * Select the best server from available candidates for executing a tool.
     * 
     * @param toolName The name of the tool to be executed
     * @param candidates List of available server connections that support the tool
     * @param context Additional context for server selection
     * @return Selected server connection, or empty if no suitable server found
     */
    Optional<McpServerConnection> selectServer(String toolName, List<McpServerConnection> candidates, SelectionContext context);
    
    /**
     * Get the name/type of this selector strategy.
     */
    String getStrategyName();
    
    /**
     * Get selection statistics for monitoring.
     */
    SelectionStats getSelectionStats();
    
    /**
     * Reset selection statistics.
     */
    void resetStats();
    
    /**
     * Context information for server selection decisions.
     */
    record SelectionContext(
        String requestId,
        String userId,
        long requestTime,
        int retryAttempt,
        java.util.Map<String, Object> metadata
    ) {
        public static SelectionContext create(String requestId) {
            return new SelectionContext(requestId, null, System.currentTimeMillis(), 0, java.util.Map.of());
        }
        
        public static SelectionContext create(String requestId, String userId) {
            return new SelectionContext(requestId, userId, System.currentTimeMillis(), 0, java.util.Map.of());
        }
        
        public SelectionContext withRetry(int retryAttempt) {
            return new SelectionContext(requestId, userId, requestTime, retryAttempt, metadata);
        }
    }
    
    /**
     * Statistics for server selection monitoring.
     */
    record SelectionStats(
        long totalSelections,
        java.util.Map<String, Long> serverSelectionCounts,
        java.util.Map<String, Long> toolSelectionCounts,
        long averageSelectionTimeMs,
        long lastSelectionTime
    ) {}
}