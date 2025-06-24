package com.coherentsolutions.l09clientmcp.multiserver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Weighted server selector implementation.
 * 
 * This selector chooses servers based on their performance characteristics,
 * giving preference to servers with better response times and higher success rates.
 * It uses a weighted random selection algorithm that adapts to server performance.
 * 
 * Educational Focus:
 * - Weighted selection algorithms
 * - Performance-based load balancing
 * - Adaptive weight calculation
 * - Statistical performance tracking
 */
@Component
@Slf4j
public class WeightedServerSelector implements ServerSelector {
    
    private final Map<String, AtomicLong> serverSelectionCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> toolSelectionCounts = new ConcurrentHashMap<>();
    private final AtomicLong totalSelections = new AtomicLong(0);
    private final AtomicLong totalSelectionTime = new AtomicLong(0);
    private volatile long lastSelectionTime = 0;
    
    // Configuration for weight calculation
    private static final double RESPONSE_TIME_WEIGHT = 0.4;
    private static final double SUCCESS_RATE_WEIGHT = 0.4;
    private static final double HEALTH_WEIGHT = 0.2;
    private static final long DEFAULT_RESPONSE_TIME = 1000; // Default 1 second
    
    @Override
    public Optional<McpServerConnection> selectServer(String toolName, List<McpServerConnection> candidates, SelectionContext context) {
        long startTime = System.currentTimeMillis();
        
        if (candidates == null || candidates.isEmpty()) {
            log.debug("No server candidates available for tool: {}", toolName);
            return Optional.empty();
        }
        
        if (candidates.size() == 1) {
            McpServerConnection selected = candidates.get(0);
            recordSelection(toolName, selected, startTime);
            return Optional.of(selected);
        }
        
        // Calculate weights for each server
        double[] weights = calculateWeights(candidates);
        double totalWeight = 0;
        for (double weight : weights) {
            totalWeight += weight;
        }
        
        if (totalWeight <= 0) {
            // Fallback to first server if no weights available
            McpServerConnection selected = candidates.get(0);
            recordSelection(toolName, selected, startTime);
            return Optional.of(selected);
        }
        
        // Weighted random selection
        double random = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulativeWeight = 0;
        
        for (int i = 0; i < candidates.size(); i++) {
            cumulativeWeight += weights[i];
            if (random <= cumulativeWeight) {
                McpServerConnection selected = candidates.get(i);
                recordSelection(toolName, selected, startTime);
                
                log.debug("Weighted selection chose server {} for tool {} (weight: {:.3f}/{:.3f})", 
                          selected.getServerId(), toolName, weights[i], totalWeight);
                
                return Optional.of(selected);
            }
        }
        
        // Fallback to last server (should not happen)
        McpServerConnection selected = candidates.get(candidates.size() - 1);
        recordSelection(toolName, selected, startTime);
        return Optional.of(selected);
    }
    
    @Override
    public String getStrategyName() {
        return "WEIGHTED";
    }
    
    @Override
    public SelectionStats getSelectionStats() {
        long total = totalSelections.get();
        long avgTime = total > 0 ? totalSelectionTime.get() / total : 0;
        
        Map<String, Long> serverCounts = new ConcurrentHashMap<>();
        serverSelectionCounts.forEach((key, value) -> serverCounts.put(key, value.get()));
        
        Map<String, Long> toolCounts = new ConcurrentHashMap<>();
        toolSelectionCounts.forEach((key, value) -> toolCounts.put(key, value.get()));
        
        return new SelectionStats(
            total,
            serverCounts,
            toolCounts,
            avgTime,
            lastSelectionTime
        );
    }
    
    @Override
    public void resetStats() {
        totalSelections.set(0);
        totalSelectionTime.set(0);
        lastSelectionTime = 0;
        serverSelectionCounts.clear();
        toolSelectionCounts.clear();
        
        log.info("Weighted selector statistics reset");
    }
    
    /**
     * Calculate weights for each server based on performance metrics.
     */
    private double[] calculateWeights(List<McpServerConnection> candidates) {
        double[] weights = new double[candidates.size()];
        
        for (int i = 0; i < candidates.size(); i++) {
            McpServerConnection server = candidates.get(i);
            weights[i] = calculateServerWeight(server);
        }
        
        return weights;
    }
    
    /**
     * Calculate weight for a single server based on its performance characteristics.
     */
    private double calculateServerWeight(McpServerConnection server) {
        try {
            McpServerConnection.ConnectionStats stats = server.getConnectionStats();
            
            // Response time component (lower is better)
            long responseTime = stats.averageResponseTimeMs();
            double responseTimeScore = responseTime > 0 ? 
                Math.max(0.1, 1.0 / (1.0 + responseTime / (double) DEFAULT_RESPONSE_TIME)) : 1.0;
            
            // Success rate component (higher is better)
            double successRate = stats.getSuccessRate();
            double successRateScore = Math.max(0.1, successRate);
            
            // Health component (connected servers get bonus)
            double healthScore = server.isConnected() ? 1.0 : 0.1;
            
            // Combined weight calculation
            double weight = (RESPONSE_TIME_WEIGHT * responseTimeScore) +
                           (SUCCESS_RATE_WEIGHT * successRateScore) +
                           (HEALTH_WEIGHT * healthScore);
            
            log.debug("Server {} weight calculation: responseTime={}ms (score={:.3f}), " +
                     "successRate={:.3f} (score={:.3f}), health={} (score={:.3f}), " +
                     "totalWeight={:.3f}",
                     server.getServerId(), responseTime, responseTimeScore,
                     successRate, successRateScore, server.isConnected(), healthScore, weight);
            
            return Math.max(0.01, weight); // Minimum weight to ensure all servers can be selected
            
        } catch (Exception e) {
            log.warn("Error calculating weight for server {}: {}", server.getServerId(), e.getMessage());
            return 0.1; // Default minimal weight
        }
    }
    
    /**
     * Record a server selection for statistics tracking.
     */
    private void recordSelection(String toolName, McpServerConnection server, long startTime) {
        long selectionTime = System.currentTimeMillis() - startTime;
        
        totalSelections.incrementAndGet();
        totalSelectionTime.addAndGet(selectionTime);
        lastSelectionTime = System.currentTimeMillis();
        
        serverSelectionCounts.computeIfAbsent(server.getServerId(), k -> new AtomicLong(0)).incrementAndGet();
        toolSelectionCounts.computeIfAbsent(toolName, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    /**
     * Get detailed weight information for all servers (for testing/monitoring).
     */
    public Map<String, Double> getServerWeights(List<McpServerConnection> servers) {
        Map<String, Double> weights = new ConcurrentHashMap<>();
        
        for (McpServerConnection server : servers) {
            double weight = calculateServerWeight(server);
            weights.put(server.getServerId(), weight);
        }
        
        return weights;
    }
}