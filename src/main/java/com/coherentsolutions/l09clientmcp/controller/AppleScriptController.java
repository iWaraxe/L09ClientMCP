package com.coherentsolutions.l09clientmcp.controller;

import com.coherentsolutions.l09clientmcp.service.AppleScriptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for AppleScript operations via MCP.
 * 
 * This controller provides endpoints for demonstrating desktop system
 * integration capabilities through the AppleScript MCP server.
 * 
 * Educational Focus:
 * - System integration API design
 * - Practical MCP usage demonstrations
 * - Error handling for system operations
 * - RESTful endpoint patterns for system control
 */
@RestController
@RequestMapping("/api/applescript")
@RequiredArgsConstructor
@Slf4j
public class AppleScriptController {
    
    private final AppleScriptService appleScriptService;
    
    /**
     * Check if AppleScript MCP is available.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        log.info("AppleScript status request received");
        
        boolean available = appleScriptService.isAppleScriptAvailable();
        
        return ResponseEntity.ok(Map.of(
            "available", available,
            "message", available ? "AppleScript MCP is available" : "AppleScript MCP is not available",
            "platform", System.getProperty("os.name")
        ));
    }
    
    /**
     * Get current battery status.
     */
    @GetMapping("/battery")
    public ResponseEntity<Map<String, Object>> getBatteryStatus() {
        log.info("Battery status request received");
        
        if (!appleScriptService.isAppleScriptAvailable()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "AppleScript MCP is not available"
            ));
        }
        
        try {
            String batteryInfo = appleScriptService.getBatteryStatus();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "battery_info", batteryInfo,
                "message", "Battery status retrieved successfully"
            ));
            
        } catch (Exception e) {
            log.error("Error getting battery status", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error getting battery status: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Show a system notification.
     */
    @PostMapping("/notification")
    public ResponseEntity<Map<String, Object>> showNotification(@RequestBody NotificationRequest request) {
        log.info("Notification request received: {} - {}", request.title(), request.message());
        
        if (!appleScriptService.isAppleScriptAvailable()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "AppleScript MCP is not available"
            ));
        }
        
        try {
            String result = appleScriptService.showNotification(request.title(), request.message());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "result", result,
                "message", "Notification displayed successfully"
            ));
            
        } catch (Exception e) {
            log.error("Error showing notification", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error showing notification: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get system information.
     */
    @GetMapping("/system-info")
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        log.info("System info request received");
        
        if (!appleScriptService.isAppleScriptAvailable()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "AppleScript MCP is not available"
            ));
        }
        
        try {
            String systemInfo = appleScriptService.getSystemInfo();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "system_info", systemInfo,
                "message", "System information retrieved successfully"
            ));
            
        } catch (Exception e) {
            log.error("Error getting system info", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error getting system info: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Control system volume.
     */
    @PostMapping("/volume")
    public ResponseEntity<Map<String, Object>> setVolume(@RequestBody VolumeRequest request) {
        log.info("Volume control request received: {}", request.volume());
        
        if (!appleScriptService.isAppleScriptAvailable()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "AppleScript MCP is not available"
            ));
        }
        
        try {
            String result = appleScriptService.setVolume(request.volume());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "result", result,
                "message", "Volume set successfully"
            ));
            
        } catch (Exception e) {
            log.error("Error setting volume", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error setting volume: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get current volume level.
     */
    @GetMapping("/volume")
    public ResponseEntity<Map<String, Object>> getVolume() {
        log.info("Volume level request received");
        
        if (!appleScriptService.isAppleScriptAvailable()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "AppleScript MCP is not available"
            ));
        }
        
        try {
            String volumeInfo = appleScriptService.getVolume();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "volume_info", volumeInfo,
                "message", "Volume level retrieved successfully"
            ));
            
        } catch (Exception e) {
            log.error("Error getting volume", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error getting volume: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Open an application.
     */
    @PostMapping("/open-app")
    public ResponseEntity<Map<String, Object>> openApplication(@RequestBody AppRequest request) {
        log.info("Open application request received: {}", request.appName());
        
        if (!appleScriptService.isAppleScriptAvailable()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "AppleScript MCP is not available"
            ));
        }
        
        try {
            String result = appleScriptService.openApplication(request.appName());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "result", result,
                "message", "Application opened successfully"
            ));
            
        } catch (Exception e) {
            log.error("Error opening application", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error opening application: " + e.getMessage()
            ));
        }
    }
    
    // Request DTOs
    public record NotificationRequest(String title, String message) {}
    public record VolumeRequest(int volume) {}
    public record AppRequest(String appName) {}
}