package com.coherentsolutions.l09clientmcp.service;

import com.coherentsolutions.l09clientmcp.function.EchoFunction;
import com.coherentsolutions.l09clientmcp.function.PingFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FunctionRegistry.
 * Branch 6: Tests dynamic function discovery and registration.
 * 
 * Educational Focus:
 * - Function discovery patterns using Spring context
 * - Type-safe function registration
 * - Function availability checking
 * - Function metadata management
 */
@ExtendWith(MockitoExtension.class)
class FunctionRegistryTest {

    @Mock
    private ApplicationContext applicationContext;
    
    @Mock
    private McpClientService mcpClientService;
    
    @Mock
    private EchoFunction echoFunction;
    
    @Mock
    private PingFunction pingFunction;
    
    private FunctionRegistry functionRegistry;

    @BeforeEach
    void setUp() {
        functionRegistry = new FunctionRegistry(applicationContext, mcpClientService);
    }

    @Test
    void testDiscoverFunctions() {
        // Arrange
        Map<String, Function> functionBeans = Map.of(
            "echoFunction", echoFunction,
            "pingFunction", pingFunction
        );
        
        when(applicationContext.getBeansOfType(Function.class)).thenReturn(functionBeans);
        
        // Act
        Map<String, Function<?, ?>> discoveredFunctions = functionRegistry.discoverFunctions();
        
        // Assert
        assertEquals(2, discoveredFunctions.size());
        assertTrue(discoveredFunctions.containsKey("echo"));
        assertTrue(discoveredFunctions.containsKey("ping"));
        assertSame(echoFunction, discoveredFunctions.get("echo"));
        assertSame(pingFunction, discoveredFunctions.get("ping"));
    }

    @Test
    void testGetAvailableFunctionNames() {
        // Arrange
        Map<String, Function> functionBeans = Map.of(
            "echoFunction", echoFunction,
            "pingFunction", pingFunction
        );
        
        when(applicationContext.getBeansOfType(Function.class)).thenReturn(functionBeans);
        
        // Act
        List<String> functionNames = functionRegistry.getAvailableFunctionNames();
        
        // Assert
        assertEquals(2, functionNames.size());
        assertTrue(functionNames.contains("echo"));
        assertTrue(functionNames.contains("ping"));
        // Verify sorted order
        assertEquals("echo", functionNames.get(0));
        assertEquals("ping", functionNames.get(1));
    }

    @Test
    void testAreFunctionsAvailable_WhenMcpEnabledAndHealthy() {
        // Arrange
        when(mcpClientService.isEnabled()).thenReturn(true);
        when(mcpClientService.isHealthy()).thenReturn(true);
        
        // Act
        boolean available = functionRegistry.areFunctionsAvailable();
        
        // Assert
        assertTrue(available);
    }

    @Test
    void testAreFunctionsAvailable_WhenMcpDisabled() {
        // Arrange
        when(mcpClientService.isEnabled()).thenReturn(false);
        // Note: isHealthy() not called when isEnabled() returns false
        
        // Act
        boolean available = functionRegistry.areFunctionsAvailable();
        
        // Assert
        assertFalse(available);
    }

    @Test
    void testAreFunctionsAvailable_WhenMcpUnhealthy() {
        // Arrange
        when(mcpClientService.isEnabled()).thenReturn(true);
        when(mcpClientService.isHealthy()).thenReturn(false);
        
        // Act
        boolean available = functionRegistry.areFunctionsAvailable();
        
        // Assert
        assertFalse(available);
    }

    @Test
    void testGetFunctionStatus_WhenDisabled() {
        // Arrange
        when(mcpClientService.isEnabled()).thenReturn(false);
        
        // Act
        String status = functionRegistry.getFunctionStatus();
        
        // Assert
        assertEquals("Functions disabled (MCP disabled)", status);
    }

    @Test
    void testGetFunctionStatus_WhenUnhealthy() {
        // Arrange
        when(mcpClientService.isEnabled()).thenReturn(true);
        when(mcpClientService.isHealthy()).thenReturn(false);
        
        // Act
        String status = functionRegistry.getFunctionStatus();
        
        // Assert
        assertEquals("Functions unavailable (MCP unhealthy)", status);
    }

    @Test
    void testGetFunctionStatus_WhenAvailable() {
        // Arrange
        when(mcpClientService.isEnabled()).thenReturn(true);
        when(mcpClientService.isHealthy()).thenReturn(true);
        
        Map<String, Function> functionBeans = Map.of(
            "echoFunction", echoFunction,
            "pingFunction", pingFunction
        );
        when(applicationContext.getBeansOfType(Function.class)).thenReturn(functionBeans);
        
        // Act
        String status = functionRegistry.getFunctionStatus();
        
        // Assert
        assertEquals("Functions available (2 registered)", status);
    }

    @Test
    void testDiscoverFunctions_WithNoFunctions() {
        // Arrange
        when(applicationContext.getBeansOfType(Function.class)).thenReturn(Map.of());
        
        // Act
        Map<String, Function<?, ?>> discoveredFunctions = functionRegistry.discoverFunctions();
        
        // Assert
        assertTrue(discoveredFunctions.isEmpty());
    }

    @Test
    void testGetAvailableFunctionNames_WithNoFunctions() {
        // Arrange
        when(applicationContext.getBeansOfType(Function.class)).thenReturn(Map.of());
        
        // Act
        List<String> functionNames = functionRegistry.getAvailableFunctionNames();
        
        // Assert
        assertTrue(functionNames.isEmpty());
    }
}