package com.coherentsolutions.l09clientmcp.multiserver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Round-robin server selector implementation.
 * 
 * This selector distributes requests evenly across all available servers
 * using a simple round-robin algorithm. It maintains separate counters
 * for each tool to ensure fair distribution.
 * 
 * Educational Focus:
 * - Round-robin load balancing algorithm
 * - Thread-safe counter management
 * - Per-tool server selection tracking
 * - Performance monitoring and statistics
 */
@Component
@Slf4j
public class RoundRobinServerSelector implements ServerSelector {
    
    private final Map<String, AtomicInteger> toolCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> serverSelectionCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> toolSelectionCounts = new ConcurrentHashMap<>();
    private final AtomicLong totalSelections = new AtomicLong(0);
    private final AtomicLong totalSelectionTime = new AtomicLong(0);
    private volatile long lastSelectionTime = 0;
    
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
        
        // Get the current counter for this tool and increment it
        AtomicInteger counter = toolCounters.computeIfAbsent(toolName, k -> new AtomicInteger(0));
        int index = Math.abs(counter.getAndIncrement()) % candidates.size();
        
        McpServerConnection selected = candidates.get(index);
        recordSelection(toolName, selected, startTime);
        
        log.debug("Round-robin selected server {} for tool {} (index {} of {} candidates)", 
                  selected.getServerId(), toolName, index, candidates.size());
        
        return Optional.of(selected);
    }
    
    @Override
    public String getStrategyName() {
        return "ROUND_ROBIN";
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
        toolCounters.clear();
        
        log.info("Round-robin selector statistics reset");
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
     * Get the current counter value for a tool (for testing).
     */
    public int getCurrentCounter(String toolName) {
        AtomicInteger counter = toolCounters.get(toolName);
        return counter != null ? counter.get() : 0;
    }
    
    /**
     * Reset counter for a specific tool (for testing).
     */
    public void resetToolCounter(String toolName) {
        toolCounters.remove(toolName);
    }
}