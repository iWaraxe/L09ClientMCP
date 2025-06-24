package com.coherentsolutions.l09clientmcp.production.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT Authentication Filter for request processing.
 * 
 * This filter intercepts HTTP requests, extracts and validates JWT tokens,
 * and establishes the security context for authenticated users.
 * 
 * Educational Focus:
 * - Spring Security filter chain integration
 * - JWT token extraction and validation
 * - Security context establishment
 * - Request authentication flow
 */
@Component
@ConditionalOnProperty(
    name = "spring.ai.mcp.production.enabled", 
    havingValue = "true"
)
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtAuthenticationService jwtService;
    
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        
        String requestPath = request.getRequestURI();
        String method = request.getMethod();
        
        log.debug("Processing authentication for {} {}", method, requestPath);
        
        try {
            // Skip authentication for public endpoints
            if (isPublicEndpoint(requestPath)) {
                log.debug("Skipping authentication for public endpoint: {}", requestPath);
                filterChain.doFilter(request, response);
                return;
            }
            
            // Extract JWT token from request
            String token = extractTokenFromRequest(request);
            
            if (token == null) {
                log.debug("No JWT token found in request to {}", requestPath);
                filterChain.doFilter(request, response);
                return;
            }
            
            // Validate token and establish security context
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                authenticateWithJwt(request, token);
            }
            
        } catch (Exception e) {
            log.error("Error processing JWT authentication for request to {}: {}", 
                     requestPath, e.getMessage());
            
            // Don't block the request, let Spring Security handle the lack of authentication
            // The security configuration will return appropriate error responses
        }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * Extract JWT token from Authorization header.
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);
        
        if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PREFIX)) {
            return authorizationHeader.substring(BEARER_PREFIX.length());
        }
        
        return null;
    }
    
    /**
     * Authenticate user with JWT token and establish security context.
     */
    private void authenticateWithJwt(HttpServletRequest request, String token) {
        JwtAuthenticationService.JwtValidationResult validation = jwtService.validateToken(token);
        
        if (!validation.isValid()) {
            log.warn("Invalid JWT token in request to {}: {}", 
                    request.getRequestURI(), validation.getErrorMessage());
            return;
        }
        
        JwtAuthenticationService.UserDetails userDetails = validation.getUserDetails();
        
        // Create authorities from user roles
        List<SimpleGrantedAuthority> authorities = userDetails.authorities().stream()
                .map(authority -> new SimpleGrantedAuthority("ROLE_" + authority))
                .toList();
        
        // Create authentication token
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                userDetails.username(),
                null, // No credentials needed for JWT
                authorities
        );
        
        // Set user details for additional context
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        
        // Store user details in authentication for later use
        authToken.setDetails(userDetails);
        
        // Establish security context
        SecurityContextHolder.getContext().setAuthentication(authToken);
        
        log.debug("Successfully authenticated user: {} with roles: {} (session: {})", 
                 userDetails.username(), userDetails.authorities(), validation.getSessionId());
    }
    
    /**
     * Determine if endpoint is public and doesn't require authentication.
     */
    private boolean isPublicEndpoint(String requestPath) {
        return requestPath.startsWith("/api/chat/health") ||
               requestPath.startsWith("/actuator/health") ||
               requestPath.startsWith("/auth/") ||
               requestPath.equals("/error") ||
               requestPath.startsWith("/swagger-ui") ||
               requestPath.startsWith("/v3/api-docs");
    }
}