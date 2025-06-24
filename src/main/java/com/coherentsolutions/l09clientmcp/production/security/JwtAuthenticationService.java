package com.coherentsolutions.l09clientmcp.production.security;

import com.coherentsolutions.l09clientmcp.production.config.ProductionConfig;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JWT Authentication Service for secure API access.
 * 
 * This service provides comprehensive JWT token management including generation,
 * validation, refresh capabilities, and integration with Spring Security.
 * 
 * Educational Focus:
 * - JWT token structure and security considerations
 * - Token lifecycle management (generation, validation, refresh)
 * - Integration with Spring Security authentication
 * - Secure key management and rotation
 */
@Service
@ConditionalOnProperty(
    name = "spring.ai.mcp.production.enabled", 
    havingValue = "true"
)
@Slf4j
public class JwtAuthenticationService {
    
    private final ProductionConfig config;
    private final SecretKey jwtSigningKey;
    
    // JWT configuration constants
    private static final String AUTHORITIES_CLAIM = "authorities";
    private static final String USER_ID_CLAIM = "userId";
    private static final String SESSION_ID_CLAIM = "sessionId";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";
    
    public JwtAuthenticationService(ProductionConfig config) {
        this.config = config;
        this.jwtSigningKey = generateSigningKey();
        
        log.info("JWT Authentication Service initialized with key algorithm: {}", 
                jwtSigningKey.getAlgorithm());
    }
    
    /**
     * Generate JWT access token for authenticated user.
     */
    public String generateAccessToken(Authentication authentication) {
        return generateToken(authentication, ACCESS_TOKEN_TYPE, 
                           config.getSecurity().getJwt().getAccessTokenExpiry());
    }
    
    /**
     * Generate JWT refresh token for token renewal.
     */
    public String generateRefreshToken(Authentication authentication) {
        return generateToken(authentication, REFRESH_TOKEN_TYPE, 
                           config.getSecurity().getJwt().getRefreshTokenExpiry());
    }
    
    /**
     * Generate JWT token with specified type and expiration.
     */
    private String generateToken(Authentication authentication, String tokenType, Duration expiry) {
        Instant now = Instant.now();
        Instant expiration = now.plus(expiry);
        
        // Extract user details
        String username = authentication.getName();
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        
        // Generate unique session ID for this token
        String sessionId = generateSessionId();
        
        // Build JWT claims
        JwtBuilder jwtBuilder = Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .issuer(config.getSecurity().getJwt().getIssuer())
                .audience().add(config.getSecurity().getJwt().getAudience())
                .and()
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .claim(SESSION_ID_CLAIM, sessionId)
                .claim(AUTHORITIES_CLAIM, authorities);
        
        // Add user ID if available (using username as ID for demo)
        jwtBuilder.claim(USER_ID_CLAIM, username);
        
        String token = jwtBuilder.signWith(jwtSigningKey).compact();
        
        log.debug("Generated {} token for user: {} (session: {}, expires: {})", 
                 tokenType, username, sessionId, expiration);
        
        return token;
    }
    
    /**
     * Validate JWT token and extract claims.
     */
    public JwtValidationResult validateToken(String token) {
        try {
            if (token == null || token.trim().isEmpty()) {
                return JwtValidationResult.invalid("Token is null or empty");
            }
            
            // Remove Bearer prefix if present
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            
            Claims claims = Jwts.parser()
                    .verifyWith(jwtSigningKey)
                    .requireIssuer(config.getSecurity().getJwt().getIssuer())
                    .requireAudience(config.getSecurity().getJwt().getAudience())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            // Verify token type
            String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
            if (!ACCESS_TOKEN_TYPE.equals(tokenType)) {
                return JwtValidationResult.invalid("Invalid token type: " + tokenType);
            }
            
            // Check if token is expired
            if (claims.getExpiration().before(new Date())) {
                return JwtValidationResult.expired("Token has expired");
            }
            
            // Extract authorities
            @SuppressWarnings("unchecked")
            List<String> authoritiesList = claims.get(AUTHORITIES_CLAIM, List.class);
            Set<String> authorities = authoritiesList != null ? 
                    Set.copyOf(authoritiesList) : Set.of();
            
            String username = claims.getSubject();
            String sessionId = claims.get(SESSION_ID_CLAIM, String.class);
            String userId = claims.get(USER_ID_CLAIM, String.class);
            
            UserDetails userDetails = new UserDetails(userId, username, authorities);
            
            log.debug("Successfully validated token for user: {} (session: {})", username, sessionId);
            
            return JwtValidationResult.valid(userDetails, sessionId, claims);
            
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
            return JwtValidationResult.expired("Token expired: " + e.getMessage());
            
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return JwtValidationResult.invalid("Invalid token: " + e.getMessage());
            
        } catch (Exception e) {
            log.error("Unexpected error validating JWT token", e);
            return JwtValidationResult.invalid("Token validation error: " + e.getMessage());
        }
    }
    
    /**
     * Refresh access token using valid refresh token.
     */
    public TokenRefreshResult refreshAccessToken(String refreshToken, Authentication currentAuth) {
        try {
            JwtValidationResult validation = validateRefreshToken(refreshToken);
            
            if (!validation.isValid()) {
                return TokenRefreshResult.failed(validation.getErrorMessage());
            }
            
            // Generate new access token
            String newAccessToken = generateAccessToken(currentAuth);
            
            log.info("Successfully refreshed access token for user: {}", currentAuth.getName());
            
            return TokenRefreshResult.success(newAccessToken);
            
        } catch (Exception e) {
            log.error("Error refreshing access token", e);
            return TokenRefreshResult.failed("Token refresh failed: " + e.getMessage());
        }
    }
    
    /**
     * Validate refresh token.
     */
    private JwtValidationResult validateRefreshToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(jwtSigningKey)
                    .requireIssuer(config.getSecurity().getJwt().getIssuer())
                    .requireAudience(config.getSecurity().getJwt().getAudience())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            // Verify this is a refresh token
            String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
            if (!REFRESH_TOKEN_TYPE.equals(tokenType)) {
                return JwtValidationResult.invalid("Not a refresh token");
            }
            
            // Check expiration
            if (claims.getExpiration().before(new Date())) {
                return JwtValidationResult.expired("Refresh token expired");
            }
            
            return JwtValidationResult.valid(null, null, claims);
            
        } catch (Exception e) {
            return JwtValidationResult.invalid("Invalid refresh token: " + e.getMessage());
        }
    }
    
    /**
     * Extract username from JWT token without full validation.
     */
    public String extractUsername(String token) {
        try {
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            
            Claims claims = Jwts.parser()
                    .verifyWith(jwtSigningKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            return claims.getSubject();
            
        } catch (Exception e) {
            log.debug("Could not extract username from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if token is expired without full validation.
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(jwtSigningKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            return claims.getExpiration().before(new Date());
            
        } catch (Exception e) {
            return true; // Consider invalid tokens as expired
        }
    }
    
    /**
     * Generate signing key for JWT tokens.
     */
    private SecretKey generateSigningKey() {
        String configuredSecret = config.getSecurity().getJwt().getSecret();
        
        if (configuredSecret != null && !configuredSecret.isEmpty()) {
            // Use configured secret (ensure it's properly base64 encoded)
            return Keys.hmacShaKeyFor(configuredSecret.getBytes());
        } else {
            // Generate secure random key for development
            SecretKey generatedKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);
            log.warn("No JWT secret configured, generated random key. This will not work across restarts!");
            return generatedKey;
        }
    }
    
    /**
     * Generate unique session ID for token tracking.
     */
    private String generateSessionId() {
        return java.util.UUID.randomUUID().toString();
    }
    
    /**
     * User details extracted from JWT token.
     */
    public record UserDetails(
            String userId,
            String username,
            Set<String> authorities
    ) {}
    
    /**
     * JWT validation result.
     */
    public static class JwtValidationResult {
        private final boolean valid;
        private final boolean expired;
        private final String errorMessage;
        private final UserDetails userDetails;
        private final String sessionId;
        private final Claims claims;
        
        private JwtValidationResult(boolean valid, boolean expired, String errorMessage,
                                  UserDetails userDetails, String sessionId, Claims claims) {
            this.valid = valid;
            this.expired = expired;
            this.errorMessage = errorMessage;
            this.userDetails = userDetails;
            this.sessionId = sessionId;
            this.claims = claims;
        }
        
        public static JwtValidationResult valid(UserDetails userDetails, String sessionId, Claims claims) {
            return new JwtValidationResult(true, false, null, userDetails, sessionId, claims);
        }
        
        public static JwtValidationResult invalid(String errorMessage) {
            return new JwtValidationResult(false, false, errorMessage, null, null, null);
        }
        
        public static JwtValidationResult expired(String errorMessage) {
            return new JwtValidationResult(false, true, errorMessage, null, null, null);
        }
        
        // Getters
        public boolean isValid() { return valid; }
        public boolean isExpired() { return expired; }
        public String getErrorMessage() { return errorMessage; }
        public UserDetails getUserDetails() { return userDetails; }
        public String getSessionId() { return sessionId; }
        public Claims getClaims() { return claims; }
    }
    
    /**
     * Token refresh result.
     */
    public static class TokenRefreshResult {
        private final boolean success;
        private final String newAccessToken;
        private final String errorMessage;
        
        private TokenRefreshResult(boolean success, String newAccessToken, String errorMessage) {
            this.success = success;
            this.newAccessToken = newAccessToken;
            this.errorMessage = errorMessage;
        }
        
        public static TokenRefreshResult success(String newAccessToken) {
            return new TokenRefreshResult(true, newAccessToken, null);
        }
        
        public static TokenRefreshResult failed(String errorMessage) {
            return new TokenRefreshResult(false, null, errorMessage);
        }
        
        public boolean isSuccess() { return success; }
        public String getNewAccessToken() { return newAccessToken; }
        public String getErrorMessage() { return errorMessage; }
    }
}