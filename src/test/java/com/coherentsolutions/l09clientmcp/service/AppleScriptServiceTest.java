package com.coherentsolutions.l09clientmcp.service;

import com.coherentsolutions.l09clientmcp.mcp.McpTool;
import com.coherentsolutions.l09clientmcp.mcp.McpToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AppleScriptService.
 * 
 * These tests mock the MCP client service to test the AppleScript service logic
 * without requiring actual AppleScript execution.
 */
@ExtendWith(MockitoExtension.class)
class AppleScriptServiceTest {
    
    @Mock
    private McpClientService mcpClientService;
    
    private AppleScriptService appleScriptService;
    
    @BeforeEach
    void setUp() {
        appleScriptService = new AppleScriptService(mcpClientService);
    }
    
    @Test
    void testIsAppleScriptAvailable_WhenEnabled() {
        // Given
        when(mcpClientService.isEnabled()).thenReturn(true);
        when(mcpClientService.listTools()).thenReturn(List.of(
            McpTool.builder().name("run_applescript").build(),
            McpTool.builder().name("other_tool").build()
        ));
        
        // When
        boolean available = appleScriptService.isAppleScriptAvailable();
        
        // Then
        assertTrue(available);
    }
    
    @Test
    void testIsAppleScriptAvailable_WhenNotEnabled() {
        // Given
        when(mcpClientService.isEnabled()).thenReturn(false);
        
        // When
        boolean available = appleScriptService.isAppleScriptAvailable();
        
        // Then
        assertFalse(available);
    }
    
    @Test
    void testIsAppleScriptAvailable_WhenNoAppleScriptTool() {
        // Given
        when(mcpClientService.isEnabled()).thenReturn(true);
        when(mcpClientService.listTools()).thenReturn(List.of(
            McpTool.builder().name("other_tool").build()
        ));
        
        // When
        boolean available = appleScriptService.isAppleScriptAvailable();
        
        // Then
        assertFalse(available);
    }
    
    @Test
    void testGetBatteryStatus_Success() {
        // Given
        McpToolResult successResult = McpToolResult.builder()
            .success(true)
            .content("InternalBattery: 85%; charged; (id=12345)")
            .build();
        
        when(mcpClientService.invokeTool(eq("run_applescript"), any(Map.class)))
            .thenReturn(successResult);
        
        // When
        String result = appleScriptService.getBatteryStatus();
        
        // Then
        assertTrue(result.contains("Battery Status"));
        verify(mcpClientService).invokeTool(eq("run_applescript"), any(Map.class));
    }
    
    @Test
    void testGetBatteryStatus_Failure() {
        // Given
        McpToolResult failureResult = McpToolResult.builder()
            .success(false)
            .content("AppleScript error")
            .build();
        
        when(mcpClientService.invokeTool(eq("run_applescript"), any(Map.class)))
            .thenReturn(failureResult);
        
        // When
        String result = appleScriptService.getBatteryStatus();
        
        // Then
        assertTrue(result.contains("Failed to get battery status"));
    }
    
    @Test
    void testShowNotification_Success() {
        // Given
        McpToolResult successResult = McpToolResult.builder()
            .success(true)
            .content("Notification displayed")
            .build();
        
        when(mcpClientService.invokeTool(eq("run_applescript"), any(Map.class)))
            .thenReturn(successResult);
        
        // When
        String result = appleScriptService.showNotification("Test Title", "Test Message");
        
        // Then
        assertEquals("Notification displayed successfully", result);
        verify(mcpClientService).invokeTool(eq("run_applescript"), any(Map.class));
    }
    
    @Test
    void testSetVolume_Success() {
        // Given
        McpToolResult successResult = McpToolResult.builder()
            .success(true)
            .content("Volume set to 75")
            .build();
        
        when(mcpClientService.invokeTool(eq("run_applescript"), any(Map.class)))
            .thenReturn(successResult);
        
        // When
        String result = appleScriptService.setVolume(75);
        
        // Then
        assertEquals("Volume set to 75", result);
    }
    
    @Test
    void testSetVolume_ClampsBounds() {
        // Given
        McpToolResult successResult = McpToolResult.builder()
            .success(true)
            .content("Volume set to 100")
            .build();
        
        when(mcpClientService.invokeTool(eq("run_applescript"), any(Map.class)))
            .thenReturn(successResult);
        
        // When - test upper bound
        appleScriptService.setVolume(150);
        
        // Then
        verify(mcpClientService).invokeTool(eq("run_applescript"), argThat(args -> {
            String script = (String) args.get("script");
            return script.contains("100"); // Should be clamped to 100
        }));
    }
    
    @Test
    void testOpenApplication_Success() {
        // Given
        McpToolResult successResult = McpToolResult.builder()
            .success(true)
            .content("Opened Calculator")
            .build();
        
        when(mcpClientService.invokeTool(eq("run_applescript"), any(Map.class)))
            .thenReturn(successResult);
        
        // When
        String result = appleScriptService.openApplication("Calculator");
        
        // Then
        assertEquals("Opened Calculator", result);
    }
    
    @Test
    void testGetSystemInfo_Success() {
        // Given
        McpToolResult successResult = McpToolResult.builder()
            .success(true)
            .content("Computer Name: MacBook Pro\nSystem Version: 14.0")
            .build();
        
        when(mcpClientService.invokeTool(eq("run_applescript"), any(Map.class)))
            .thenReturn(successResult);
        
        // When
        String result = appleScriptService.getSystemInfo();
        
        // Then
        assertTrue(result.contains("MacBook Pro"));
        assertTrue(result.contains("14.0"));
    }
    
    @Test
    void testMcpException_HandledGracefully() {
        // Given
        when(mcpClientService.invokeTool(eq("run_applescript"), any(Map.class)))
            .thenThrow(new RuntimeException("MCP connection failed"));
        
        // When
        String result = appleScriptService.getBatteryStatus();
        
        // Then
        assertTrue(result.contains("Error getting battery status"));
    }
}