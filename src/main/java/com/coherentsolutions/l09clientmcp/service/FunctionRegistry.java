package com.coherentsolutions.l09clientmcp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry service for discovering and managing Spring AI Functions.
 * Branch 6: Enables dynamic function discovery for MCP tool integration.
 * 
 * Educational Focus:
 * - Function discovery pattern using Spring context
 * - Type-safe function registration with Spring AI
 * - Dynamic tool availability checking
 * - Function metadata management
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FunctionRegistry {
    
    private final ApplicationContext applicationContext;
    private final McpClientService mcpClientService;
    
    /**
     * Discovers all available Spring AI Functions from the application context.
     * 
     * @return Map of function name to function instance
     */
    public Map<String, Function<?, ?>> discoverFunctions() {
        log.info("Discovering available Spring AI Functions...");
        
        // Get all Function beans from Spring context
        Map<String, Function> functionBeans = applicationContext.getBeansOfType(Function.class);
        
        // Filter to only MCP-related functions (those with @Description annotation)
        Map<String, Function<?, ?>> mcpFunctions = functionBeans.entrySet().stream()
            .filter(entry -> isMcpFunction(entry.getValue()))
            .collect(Collectors.toMap(
                entry -> getFunctionName(entry.getKey()),
                Map.Entry::getValue
            ));
        
        log.info("Discovered {} MCP functions: {}", mcpFunctions.size(), mcpFunctions.keySet());
        return mcpFunctions;
    }
    
    /**
     * Gets available function names for API responses.
     * 
     * @return List of function names
     */
    public List<String> getAvailableFunctionNames() {
        return discoverFunctions().keySet().stream()
            .sorted()
            .toList();
    }
    
    /**
     * Checks if functions are available (MCP service is enabled and healthy).
     * 
     * @return true if functions can be used
     */
    public boolean areFunctionsAvailable() {
        return mcpClientService.isEnabled() && mcpClientService.isHealthy();
    }
    
    /**
     * Gets function availability status for health checks.
     * 
     * @return status string
     */
    public String getFunctionStatus() {
        if (!mcpClientService.isEnabled()) {
            return "Functions disabled (MCP disabled)";
        }
        
        if (!mcpClientService.isHealthy()) {
            return "Functions unavailable (MCP unhealthy)";
        }
        
        int functionCount = getAvailableFunctionNames().size();
        return String.format("Functions available (%d registered)", functionCount);
    }
    
    /**
     * Checks if a bean is an MCP function by looking for Spring AI @Description annotation.
     */
    private boolean isMcpFunction(Function<?, ?> function) {
        return function.getClass().isAnnotationPresent(
            org.springframework.context.annotation.Description.class
        );
    }
    
    /**
     * Converts Spring bean name to function name (removes 'Function' suffix).
     */
    private String getFunctionName(String beanName) {
        // Convert EchoFunction -> echo, PingFunction -> ping
        if (beanName.endsWith("Function")) {
            return beanName.substring(0, beanName.length() - 8).toLowerCase();
        }
        return beanName.toLowerCase();
    }
}