package com.coherentsolutions.l09clientmcp.production.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Set;

/**
 * Authentication Controller for JWT token management.
 * 
 * This controller provides endpoints for user authentication, token generation,
 * and token refresh operations in the production MCP client system.
 * 
 * Educational Focus:
 * - JWT-based authentication flow
 * - Token generation and validation
 * - Secure authentication endpoint design
 * - Integration with RBAC authorization
 */
@RestController
@RequestMapping("/auth")
@ConditionalOnProperty(
    name = "spring.ai.mcp.production.enabled", 
    havingValue = "true"
)
@RequiredArgsConstructor
@Slf4j
public class AuthenticationController {
    
    private final JwtAuthenticationService jwtService;
    private final RbacAuthorizationService rbacService;
    
    /**
     * Authenticate user and generate JWT tokens.
     * 
     * In a real implementation, this would validate against a user store.
     * For demo purposes, we'll use hardcoded credentials.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for user: {}", request.username());
        
        try {
            // Validate credentials (demo implementation)
            UserValidationResult validationResult = validateCredentials(request.username(), request.password());
            
            if (!validationResult.isValid()) {
                log.warn("Invalid login attempt for user: {} - {}", request.username(), validationResult.reason());
                return ResponseEntity.status(401)
                        .body(new LoginResponse(false, "Invalid credentials", null, null, null));
            }
            
            // Create authentication object
            List<SimpleGrantedAuthority> authorities = validationResult.roles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
            
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    request.username(), null, authorities);
            
            // Generate tokens
            String accessToken = jwtService.generateAccessToken(authentication);
            String refreshToken = jwtService.generateRefreshToken(authentication);
            
            log.info("Successful login for user: {} with roles: {}", request.username(), validationResult.roles());
            
            return ResponseEntity.ok(new LoginResponse(
                    true,
                    "Authentication successful",
                    accessToken,
                    refreshToken,
                    validationResult.roles()
            ));
            
        } catch (Exception e) {
            log.error("Error during login for user: {}", request.username(), e);
            return ResponseEntity.status(500)
                    .body(new LoginResponse(false, "Authentication error", null, null, null));
        }
    }
    
    /**
     * Refresh access token using refresh token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponse> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        log.debug("Token refresh attempt");
        
        try {
            // Extract username from refresh token
            String username = jwtService.extractUsername(request.refreshToken());
            
            if (username == null) {
                log.warn("Invalid refresh token - could not extract username");
                return ResponseEntity.status(401)
                        .body(new TokenRefreshResponse(false, "Invalid refresh token", null));
            }
            
            // Validate user still exists and get current roles
            UserValidationResult validationResult = getUserByUsername(username);
            
            if (!validationResult.isValid()) {
                log.warn("User no longer valid for token refresh: {}", username);
                return ResponseEntity.status(401)
                        .body(new TokenRefreshResponse(false, "User no longer valid", null));
            }
            
            // Create authentication for current user
            List<SimpleGrantedAuthority> authorities = validationResult.roles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
            
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    username, null, authorities);
            
            // Attempt to refresh the token
            JwtAuthenticationService.TokenRefreshResult refreshResult = 
                    jwtService.refreshAccessToken(request.refreshToken(), authentication);
            
            if (refreshResult.isSuccess()) {
                log.debug("Token refresh successful for user: {}", username);
                return ResponseEntity.ok(new TokenRefreshResponse(
                        true, "Token refreshed successfully", refreshResult.getNewAccessToken()));
            } else {
                log.warn("Token refresh failed for user: {} - {}", username, refreshResult.getErrorMessage());
                return ResponseEntity.status(401)
                        .body(new TokenRefreshResponse(false, refreshResult.getErrorMessage(), null));
            }
            
        } catch (Exception e) {
            log.error("Error during token refresh", e);
            return ResponseEntity.status(500)
                    .body(new TokenRefreshResponse(false, "Token refresh error", null));
        }
    }
    
    /**
     * Validate JWT token and return user information.
     */
    @PostMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validateToken(@Valid @RequestBody TokenValidationRequest request) {
        log.debug("Token validation request");
        
        try {
            JwtAuthenticationService.JwtValidationResult validation = jwtService.validateToken(request.token());
            
            if (validation.isValid()) {
                JwtAuthenticationService.UserDetails userDetails = validation.getUserDetails();
                
                return ResponseEntity.ok(new TokenValidationResponse(
                        true,
                        "Token is valid",
                        userDetails.username(),
                        userDetails.authorities(),
                        validation.getSessionId()
                ));
            } else {
                return ResponseEntity.status(401)
                        .body(new TokenValidationResponse(
                                false, validation.getErrorMessage(), null, null, null));
            }
            
        } catch (Exception e) {
            log.error("Error during token validation", e);
            return ResponseEntity.status(500)
                    .body(new TokenValidationResponse(false, "Token validation error", null, null, null));
        }
    }
    
    /**
     * Logout endpoint (for completeness - JWT is stateless).
     */
    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        // In a stateless JWT system, logout is primarily client-side
        // However, we can log the event for audit purposes
        
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            String username = jwtService.extractUsername(token);
            
            if (username != null) {
                log.info("Logout request for user: {}", username);
                // In a real implementation, you might want to maintain a token blacklist
            }
        }
        
        return ResponseEntity.ok(new LogoutResponse(true, "Logout successful"));
    }
    
    // Private helper methods
    
    /**
     * Validate user credentials (demo implementation).
     * In production, this would check against a secure user store.
     */
    private UserValidationResult validateCredentials(String username, String password) {
        // Demo credentials for different roles
        return switch (username) {
            case "admin" -> password.equals("admin123") ? 
                    UserValidationResult.valid(Set.of("ADMIN")) : UserValidationResult.invalid("Invalid password");
            case "operator" -> password.equals("operator123") ? 
                    UserValidationResult.valid(Set.of("OPERATOR")) : UserValidationResult.invalid("Invalid password");
            case "developer" -> password.equals("dev123") ? 
                    UserValidationResult.valid(Set.of("DEVELOPER")) : UserValidationResult.invalid("Invalid password");
            case "user" -> password.equals("user123") ? 
                    UserValidationResult.valid(Set.of("USER")) : UserValidationResult.invalid("Invalid password");
            case "readonly" -> password.equals("readonly123") ? 
                    UserValidationResult.valid(Set.of("READONLY")) : UserValidationResult.invalid("Invalid password");
            case "superadmin" -> password.equals("superadmin123") ? 
                    UserValidationResult.valid(Set.of("SUPER_ADMIN")) : UserValidationResult.invalid("Invalid password");
            default -> UserValidationResult.invalid("Unknown user");
        };
    }
    
    /**
     * Get user information by username.
     */
    private UserValidationResult getUserByUsername(String username) {
        // In a real implementation, this would query a user store
        return switch (username) {
            case "admin" -> UserValidationResult.valid(Set.of("ADMIN"));
            case "operator" -> UserValidationResult.valid(Set.of("OPERATOR"));
            case "developer" -> UserValidationResult.valid(Set.of("DEVELOPER"));
            case "user" -> UserValidationResult.valid(Set.of("USER"));
            case "readonly" -> UserValidationResult.valid(Set.of("READONLY"));
            case "superadmin" -> UserValidationResult.valid(Set.of("SUPER_ADMIN"));
            default -> UserValidationResult.invalid("Unknown user");
        };
    }
    
    // Data classes
    
    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {}
    
    public record LoginResponse(
            boolean success,
            String message,
            String accessToken,
            String refreshToken,
            Set<String> roles
    ) {}
    
    public record TokenRefreshRequest(
            @NotBlank String refreshToken
    ) {}
    
    public record TokenRefreshResponse(
            boolean success,
            String message,
            String accessToken
    ) {}
    
    public record TokenValidationRequest(
            @NotBlank String token
    ) {}
    
    public record TokenValidationResponse(
            boolean valid,
            String message,
            String username,
            Set<String> authorities,
            String sessionId
    ) {}
    
    public record LogoutResponse(
            boolean success,
            String message
    ) {}
    
    private record UserValidationResult(
            boolean valid,
            String reason,
            Set<String> roles
    ) {
        public static UserValidationResult valid(Set<String> roles) {
            return new UserValidationResult(true, null, roles);
        }
        
        public static UserValidationResult invalid(String reason) {
            return new UserValidationResult(false, reason, Set.of());
        }
        
        public boolean isValid() { return valid; }
    }
}