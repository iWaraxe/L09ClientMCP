package com.coherentsolutions.l09clientmcp.production.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Authentication Entry Point for handling unauthorized requests.
 * 
 * This component handles authentication failures and provides consistent
 * error responses for unauthorized API access attempts.
 * 
 * Educational Focus:
 * - Spring Security authentication entry point
 * - Consistent error response formatting
 * - Security event logging
 * - HTTP status code handling
 */
@Component
@Slf4j
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public void commence(HttpServletRequest request, 
                        HttpServletResponse response, 
                        AuthenticationException authException) throws IOException {
        
        String requestPath = request.getRequestURI();
        String method = request.getMethod();
        String clientIp = getClientIpAddress(request);
        
        log.warn("Unauthorized access attempt: {} {} from IP {} - {}", 
                method, requestPath, clientIp, authException.getMessage());
        
        // Set response status and content type
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        
        // Create error response
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", Instant.now().toString());
        errorResponse.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        errorResponse.put("error", "Unauthorized");
        errorResponse.put("message", determineErrorMessage(request, authException));
        errorResponse.put("path", requestPath);
        
        // Add additional context for development
        if (isDevelopmentEnvironment(request)) {
            errorResponse.put("details", authException.getMessage());
            errorResponse.put("type", authException.getClass().getSimpleName());
        }
        
        // Write response
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
    
    /**
     * Determine appropriate error message based on request and exception.
     */
    private String determineErrorMessage(HttpServletRequest request, AuthenticationException authException) {
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || authHeader.trim().isEmpty()) {
            return "Authentication required. Please provide a valid JWT token in the Authorization header.";
        }
        
        if (!authHeader.startsWith("Bearer ")) {
            return "Invalid authentication format. Please use 'Bearer <token>' format.";
        }
        
        // Token was provided but invalid
        if (authException.getMessage() != null && authException.getMessage().contains("expired")) {
            return "Authentication token has expired. Please obtain a new token.";
        }
        
        return "Invalid authentication token. Please provide a valid JWT token.";
    }
    
    /**
     * Extract client IP address from request.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Check if running in development environment.
     */
    private boolean isDevelopmentEnvironment(HttpServletRequest request) {
        // This is a simple check - in real implementation, you'd inject the config
        String serverName = request.getServerName();
        return serverName.equals("localhost") || serverName.equals("127.0.0.1");
    }
}