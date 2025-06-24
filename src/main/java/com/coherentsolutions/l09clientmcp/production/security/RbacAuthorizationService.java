package com.coherentsolutions.l09clientmcp.production.security;

import com.coherentsolutions.l09clientmcp.production.config.ProductionConfig;
import com.coherentsolutions.l09clientmcp.production.monitoring.McpMetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Role-Based Access Control (RBAC) Authorization Service.
 * 
 * This service provides fine-grained authorization control for MCP operations
 * using a flexible role-based permission system with resource-level access control.
 * 
 * Educational Focus:
 * - RBAC principles and implementation patterns
 * - Resource-based authorization (servers, tools, operations)
 * - Permission hierarchies and inheritance
 * - Integration with Spring Security and custom authorization
 */
@Service
@ConditionalOnProperty(
    name = "spring.ai.mcp.production.enabled", 
    havingValue = "true"
)
@RequiredArgsConstructor
@Slf4j
public class RbacAuthorizationService {
    
    private final ProductionConfig config;
    private final McpMetricsCollector metricsCollector;
    
    // Cache for performance optimization
    private final Map<String, Set<Permission>> rolePermissionsCache = new ConcurrentHashMap<>();
    private final Map<String, AuthorizationResult> authorizationCache = new ConcurrentHashMap<>();
    
    /**
     * Check if user has permission to access a specific MCP server.
     */
    public AuthorizationResult authorizeServerAccess(Authentication authentication, String serverId) {
        return authorizeResource(authentication, ResourceType.SERVER, serverId, Operation.ACCESS);
    }
    
    /**
     * Check if user has permission to invoke a specific tool on a server.
     */
    public AuthorizationResult authorizeToolInvocation(Authentication authentication, 
                                                     String serverId, String toolName) {
        // Check server access first
        AuthorizationResult serverAuth = authorizeServerAccess(authentication, serverId);
        if (!serverAuth.isAuthorized()) {
            return serverAuth;
        }
        
        // Check tool-specific permission
        String toolResource = serverId + ":" + toolName;
        return authorizeResource(authentication, ResourceType.TOOL, toolResource, Operation.INVOKE);
    }
    
    /**
     * Check if user has permission to access production monitoring endpoints.
     */
    public AuthorizationResult authorizeMonitoringAccess(Authentication authentication, String endpoint) {
        return authorizeResource(authentication, ResourceType.MONITORING, endpoint, Operation.READ);
    }
    
    /**
     * Check if user has permission to perform administrative operations.
     */
    public AuthorizationResult authorizeAdminOperation(Authentication authentication, 
                                                     String operation, String resource) {
        return authorizeResource(authentication, ResourceType.ADMIN, resource, 
                               Operation.valueOf(operation.toUpperCase()));
    }
    
    /**
     * Core authorization method with caching and comprehensive logging.
     */
    public AuthorizationResult authorizeResource(Authentication authentication, 
                                               ResourceType resourceType, 
                                               String resourceId, 
                                               Operation operation) {
        String username = authentication.getName();
        String cacheKey = buildCacheKey(username, resourceType, resourceId, operation);
        
        // Check cache first
        AuthorizationResult cachedResult = authorizationCache.get(cacheKey);
        if (cachedResult != null && !cachedResult.isExpired()) {
            log.debug("Authorization cache hit for user={}, resource={}:{}, operation={}", 
                     username, resourceType, resourceId, operation);
            return cachedResult;
        }
        
        // Perform authorization check
        AuthorizationResult result = performAuthorizationCheck(authentication, resourceType, resourceId, operation);
        
        // Cache the result
        authorizationCache.put(cacheKey, result);
        
        // Record metrics
        metricsCollector.recordFailure("authorization", 
                                     resourceType.name().toLowerCase(), 
                                     resourceId, 
                                     result.isAuthorized() ? "authorized" : "denied");
        
        log.info("Authorization {} for user={}, resource={}:{}, operation={}, reason={}", 
                result.isAuthorized() ? "GRANTED" : "DENIED",
                username, resourceType, resourceId, operation, result.getReason());
        
        return result;
    }
    
    /**
     * Perform the actual authorization check.
     */
    private AuthorizationResult performAuthorizationCheck(Authentication authentication,
                                                        ResourceType resourceType,
                                                        String resourceId,
                                                        Operation operation) {
        String username = authentication.getName();
        Set<String> userRoles = extractUserRoles(authentication);
        
        // Check if user has any roles
        if (userRoles.isEmpty()) {
            return AuthorizationResult.denied("User has no assigned roles");
        }
        
        // Get all permissions for user's roles
        Set<Permission> userPermissions = getUserPermissions(userRoles);
        
        // Check for specific permission
        Permission requiredPermission = new Permission(resourceType, resourceId, operation);
        
        // Check exact permission match
        if (userPermissions.contains(requiredPermission)) {
            return AuthorizationResult.granted("Exact permission match");
        }
        
        // Check wildcard permissions
        Permission wildcardResourcePermission = new Permission(resourceType, "*", operation);
        if (userPermissions.contains(wildcardResourcePermission)) {
            return AuthorizationResult.granted("Wildcard resource permission");
        }
        
        // Check operation wildcard
        Permission wildcardOperationPermission = new Permission(resourceType, resourceId, Operation.ALL);
        if (userPermissions.contains(wildcardOperationPermission)) {
            return AuthorizationResult.granted("Wildcard operation permission");
        }
        
        // Check super admin role
        if (userRoles.contains("SUPER_ADMIN")) {
            return AuthorizationResult.granted("Super admin override");
        }
        
        // Check environment-specific permissions
        if (config.isDevelopment() && userRoles.contains("DEVELOPER")) {
            // Developers have broader access in development environment
            if (resourceType == ResourceType.MONITORING || resourceType == ResourceType.TOOL) {
                return AuthorizationResult.granted("Developer access in development environment");
            }
        }
        
        return AuthorizationResult.denied("No matching permissions found");
    }
    
    /**
     * Extract user roles from Spring Security authentication.
     */
    private Set<String> extractUserRoles(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5)) // Remove "ROLE_" prefix
                .collect(Collectors.toSet());
    }
    
    /**
     * Get all permissions for a set of roles with caching.
     */
    private Set<Permission> getUserPermissions(Set<String> userRoles) {
        Set<Permission> allPermissions = ConcurrentHashMap.newKeySet();
        
        for (String role : userRoles) {
            Set<Permission> rolePermissions = rolePermissionsCache.computeIfAbsent(role, 
                                                                                  this::loadRolePermissions);
            allPermissions.addAll(rolePermissions);
        }
        
        return allPermissions;
    }
    
    /**
     * Load permissions for a specific role.
     * In a real implementation, this would load from database or configuration.
     */
    private Set<Permission> loadRolePermissions(String role) {
        Set<Permission> permissions = ConcurrentHashMap.newKeySet();
        
        switch (role) {
            case "SUPER_ADMIN" -> {
                // Super admin has all permissions
                permissions.add(new Permission(ResourceType.ADMIN, "*", Operation.ALL));
                permissions.add(new Permission(ResourceType.MONITORING, "*", Operation.ALL));
                permissions.add(new Permission(ResourceType.SERVER, "*", Operation.ALL));
                permissions.add(new Permission(ResourceType.TOOL, "*", Operation.ALL));
            }
            case "ADMIN" -> {
                // Admin has management permissions
                permissions.add(new Permission(ResourceType.MONITORING, "*", Operation.READ));
                permissions.add(new Permission(ResourceType.MONITORING, "*", Operation.WRITE));
                permissions.add(new Permission(ResourceType.SERVER, "*", Operation.ACCESS));
                permissions.add(new Permission(ResourceType.TOOL, "*", Operation.INVOKE));
                permissions.add(new Permission(ResourceType.ADMIN, "config", Operation.READ));
            }
            case "OPERATOR" -> {
                // Operator has operational permissions
                permissions.add(new Permission(ResourceType.MONITORING, "*", Operation.READ));
                permissions.add(new Permission(ResourceType.SERVER, "*", Operation.ACCESS));
                permissions.add(new Permission(ResourceType.TOOL, "*", Operation.INVOKE));
            }
            case "DEVELOPER" -> {
                // Developer has development-specific permissions
                permissions.add(new Permission(ResourceType.MONITORING, "metrics", Operation.READ));
                permissions.add(new Permission(ResourceType.MONITORING, "tracing", Operation.READ));
                permissions.add(new Permission(ResourceType.SERVER, "echo-server", Operation.ACCESS));
                permissions.add(new Permission(ResourceType.TOOL, "*:calculator", Operation.INVOKE));
                permissions.add(new Permission(ResourceType.TOOL, "*:echo", Operation.INVOKE));
            }
            case "USER" -> {
                // Regular user has basic permissions
                permissions.add(new Permission(ResourceType.SERVER, "public-server", Operation.ACCESS));
                permissions.add(new Permission(ResourceType.TOOL, "public-server:search", Operation.INVOKE));
                permissions.add(new Permission(ResourceType.TOOL, "public-server:calculator", Operation.INVOKE));
            }
            case "READONLY" -> {
                // Read-only access
                permissions.add(new Permission(ResourceType.MONITORING, "status", Operation.READ));
                permissions.add(new Permission(ResourceType.MONITORING, "health", Operation.READ));
            }
        }
        
        log.debug("Loaded {} permissions for role: {}", permissions.size(), role);
        return permissions;
    }
    
    /**
     * Build cache key for authorization results.
     */
    private String buildCacheKey(String username, ResourceType resourceType, 
                                String resourceId, Operation operation) {
        return String.format("%s:%s:%s:%s", username, resourceType, resourceId, operation);
    }
    
    /**
     * Clear authorization cache (useful for role changes).
     */
    public void clearAuthorizationCache() {
        authorizationCache.clear();
        rolePermissionsCache.clear();
        log.info("Authorization cache cleared");
    }
    
    /**
     * Clear cache for specific user.
     */
    public void clearUserAuthorizationCache(String username) {
        authorizationCache.entrySet().removeIf(entry -> entry.getKey().startsWith(username + ":"));
        log.info("Authorization cache cleared for user: {}", username);
    }
    
    /**
     * Resource types for authorization.
     */
    public enum ResourceType {
        SERVER,     // MCP servers
        TOOL,       // MCP tools
        MONITORING, // Monitoring endpoints
        ADMIN       // Administrative operations
    }
    
    /**
     * Operations that can be performed on resources.
     */
    public enum Operation {
        READ,       // Read/view access
        WRITE,      // Write/modify access
        ACCESS,     // General access
        INVOKE,     // Invoke/execute
        DELETE,     // Delete operations
        ALL         // All operations (wildcard)
    }
    
    /**
     * Permission definition.
     */
    public record Permission(
            ResourceType resourceType,
            String resourceId,  // Can be "*" for wildcard
            Operation operation
    ) {}
    
    /**
     * Authorization result with caching support.
     */
    public static class AuthorizationResult {
        private final boolean authorized;
        private final String reason;
        private final long timestamp;
        private final long ttlMs;
        
        private AuthorizationResult(boolean authorized, String reason, long ttlMs) {
            this.authorized = authorized;
            this.reason = reason;
            this.timestamp = System.currentTimeMillis();
            this.ttlMs = ttlMs;
        }
        
        public static AuthorizationResult granted(String reason) {
            return new AuthorizationResult(true, reason, 300_000); // 5 minutes TTL
        }
        
        public static AuthorizationResult denied(String reason) {
            return new AuthorizationResult(false, reason, 60_000); // 1 minute TTL for denials
        }
        
        public boolean isAuthorized() { return authorized; }
        public String getReason() { return reason; }
        
        public boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > ttlMs;
        }
    }
}