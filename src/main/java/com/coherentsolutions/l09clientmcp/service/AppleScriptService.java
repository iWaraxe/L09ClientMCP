package com.coherentsolutions.l09clientmcp.service;

import com.coherentsolutions.l09clientmcp.mcp.McpToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for AppleScript-related operations via MCP.
 * 
 * This service provides convenient methods for common AppleScript operations
 * that demonstrate desktop system integration capabilities.
 * 
 * Educational Focus:
 * - System integration patterns
 * - Service layer abstraction over MCP tools
 * - Error handling for system operations
 * - Practical demonstration scenarios
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppleScriptService {
    
    private final McpClientService mcpClientService;
    
    /**
     * Get the current battery status using AppleScript.
     */
    public String getBatteryStatus() {
        log.info("Getting battery status via AppleScript MCP");
        
        String script = """
            tell application "System Events"
                set batteryInfo to (do shell script "pmset -g batt")
                return batteryInfo
            end tell
            """;
        
        try {
            McpToolResult result = mcpClientService.invokeTool("run_applescript", 
                Map.of("script", script));
            
            if (result.isSuccess()) {
                return formatBatteryInfo(result.getContent());
            } else {
                return "Failed to get battery status: " + result.getContent();
            }
        } catch (Exception e) {
            log.error("Error getting battery status", e);
            return "Error getting battery status: " + e.getMessage();
        }
    }
    
    /**
     * Show a system notification using AppleScript.
     */
    public String showNotification(String title, String message) {
        log.info("Showing notification: {} - {}", title, message);
        
        String script = String.format("""
            display notification "%s" with title "%s"
            """, escapeForAppleScript(message), escapeForAppleScript(title));
        
        try {
            McpToolResult result = mcpClientService.invokeTool("run_applescript", 
                Map.of("script", script));
            
            if (result.isSuccess()) {
                return "Notification displayed successfully";
            } else {
                return "Failed to show notification: " + result.getContent();
            }
        } catch (Exception e) {
            log.error("Error showing notification", e);
            return "Error showing notification: " + e.getMessage();
        }
    }
    
    /**
     * Get system information using AppleScript.
     */
    public String getSystemInfo() {
        log.info("Getting system information via AppleScript MCP");
        
        String script = """
            set systemInfo to ""
            set systemInfo to systemInfo & "Computer Name: " & (computer name of (system info)) & return
            set systemInfo to systemInfo & "System Version: " & (system version of (system info)) & return
            set systemInfo to systemInfo & "Uptime: " & (do shell script "uptime") & return
            set systemInfo to systemInfo & "Memory: " & (do shell script "memory_pressure") & return
            return systemInfo
            """;
        
        try {
            McpToolResult result = mcpClientService.invokeTool("run_applescript", 
                Map.of("script", script));
            
            if (result.isSuccess()) {
                return result.getContent();
            } else {
                return "Failed to get system info: " + result.getContent();
            }
        } catch (Exception e) {
            log.error("Error getting system info", e);
            return "Error getting system info: " + e.getMessage();
        }
    }
    
    /**
     * Control system volume using AppleScript.
     */
    public String setVolume(int volume) {
        log.info("Setting system volume to: {}", volume);
        
        // Clamp volume between 0 and 100
        volume = Math.max(0, Math.min(100, volume));
        
        String script = String.format("""
            set volume output volume %d
            return "Volume set to %d"
            """, volume, volume);
        
        try {
            McpToolResult result = mcpClientService.invokeTool("run_applescript", 
                Map.of("script", script));
            
            if (result.isSuccess()) {
                return result.getContent();
            } else {
                return "Failed to set volume: " + result.getContent();
            }
        } catch (Exception e) {
            log.error("Error setting volume", e);
            return "Error setting volume: " + e.getMessage();
        }
    }
    
    /**
     * Get current volume level using AppleScript.
     */
    public String getVolume() {
        log.info("Getting current system volume");
        
        String script = """
            set currentVolume to output volume of (get volume settings)
            return "Current volume: " & currentVolume
            """;
        
        try {
            McpToolResult result = mcpClientService.invokeTool("run_applescript", 
                Map.of("script", script));
            
            if (result.isSuccess()) {
                return result.getContent();
            } else {
                return "Failed to get volume: " + result.getContent();
            }
        } catch (Exception e) {
            log.error("Error getting volume", e);
            return "Error getting volume: " + e.getMessage();
        }
    }
    
    /**
     * Open an application using AppleScript.
     */
    public String openApplication(String applicationName) {
        log.info("Opening application: {}", applicationName);
        
        String script = String.format("""
            tell application "%s"
                activate
            end tell
            return "Opened %s"
            """, escapeForAppleScript(applicationName), escapeForAppleScript(applicationName));
        
        try {
            McpToolResult result = mcpClientService.invokeTool("run_applescript", 
                Map.of("script", script));
            
            if (result.isSuccess()) {
                return result.getContent();
            } else {
                return "Failed to open application: " + result.getContent();
            }
        } catch (Exception e) {
            log.error("Error opening application", e);
            return "Error opening application: " + e.getMessage();
        }
    }
    
    /**
     * Check if AppleScript MCP is available.
     */
    public boolean isAppleScriptAvailable() {
        try {
            return mcpClientService.isEnabled() && 
                   mcpClientService.listTools().stream()
                       .anyMatch(tool -> "run_applescript".equals(tool.getName()));
        } catch (Exception e) {
            log.error("Error checking AppleScript availability", e);
            return false;
        }
    }
    
    private String formatBatteryInfo(String rawBatteryInfo) {
        // Simple formatting for battery information
        if (rawBatteryInfo.contains("InternalBattery")) {
            // Extract percentage if available
            String[] lines = rawBatteryInfo.split("\n");
            for (String line : lines) {
                if (line.contains("%")) {
                    return "Battery Status: " + line.trim();
                }
            }
        }
        return "Battery Status: " + rawBatteryInfo;
    }
    
    private String escapeForAppleScript(String text) {
        if (text == null) return "";
        return text.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}