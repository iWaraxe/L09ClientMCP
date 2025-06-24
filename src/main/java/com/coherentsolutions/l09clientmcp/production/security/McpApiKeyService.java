package com.coherentsolutions.l09clientmcp.production.security;

import com.coherentsolutions.l09clientmcp.production.config.ProductionConfig;
import com.coherentsolutions.l09clientmcp.production.monitoring.McpMetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Secure API Key Management Service for MCP Servers.
 * 
 * This service provides secure storage, encryption, and management of API keys
 * used to authenticate with external MCP servers, with rotation capabilities
 * and audit logging.
 * 
 * Educational Focus:
 * - Secure key storage and encryption patterns
 * - API key rotation and lifecycle management
 * - Environment-specific key management
 * - Security audit logging for key operations
 */
@Service
@ConditionalOnProperty(
    name = "spring.ai.mcp.production.enabled", 
    havingValue = "true"
)
@Slf4j
public class McpApiKeyService {
    
    private final ProductionConfig config;
    private final McpMetricsCollector metricsCollector;
    
    // In-memory encrypted key storage (in production, use external key management)
    private final Map<String, EncryptedApiKey> encryptedKeys = new ConcurrentHashMap<>();
    private final SecretKey encryptionKey;
    private final SecureRandom secureRandom = new SecureRandom();
    
    // AES-GCM encryption parameters
    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    
    public McpApiKeyService(ProductionConfig config, McpMetricsCollector metricsCollector) {
        this.config = config;
        this.metricsCollector = metricsCollector;
        this.encryptionKey = initializeEncryptionKey();
        
        // Initialize with demo keys for development
        if (config.isDevelopment()) {
            initializeDemoKeys();
        }
        
        log.info("MCP API Key Service initialized with encryption enabled");
    }
    
    /**
     * Store encrypted API key for a server.
     */
    public ApiKeyResult storeApiKey(String serverId, String apiKey, String keyType, String description) {
        try {
            validateInput(serverId, apiKey, keyType);
            
            // Encrypt the API key
            EncryptedData encryptedData = encryptApiKey(apiKey);
            
            // Create encrypted API key record
            EncryptedApiKey encryptedApiKey = new EncryptedApiKey(
                    serverId,
                    keyType,
                    description,
                    encryptedData,
                    System.currentTimeMillis(),
                    calculateExpirationTime(keyType)
            );
            
            // Store encrypted key
            encryptedKeys.put(serverId, encryptedApiKey);
            
            // Record metrics
            metricsCollector.recordFailure("api_key_management", "store", serverId, "success");
            
            log.info("API key stored for server: {} (type: {})", serverId, keyType);
            
            return ApiKeyResult.success("API key stored successfully");
            
        } catch (Exception e) {
            log.error("Failed to store API key for server: {}", serverId, e);
            metricsCollector.recordFailure("api_key_management", "store", serverId, "encryption_failed");
            return ApiKeyResult.failure("Failed to store API key: " + e.getMessage());
        }
    }
    
    /**
     * Retrieve and decrypt API key for a server.
     */
    public ApiKeyResult retrieveApiKey(String serverId) {
        try {
            EncryptedApiKey encryptedApiKey = encryptedKeys.get(serverId);
            
            if (encryptedApiKey == null) {
                log.warn("No API key found for server: {}", serverId);
                return ApiKeyResult.failure("No API key found for server: " + serverId);
            }
            
            // Check if key is expired
            if (isKeyExpired(encryptedApiKey)) {
                log.warn("API key expired for server: {}", serverId);
                encryptedKeys.remove(serverId); // Remove expired key
                return ApiKeyResult.failure("API key expired for server: " + serverId);
            }
            
            // Decrypt the API key
            String decryptedKey = decryptApiKey(encryptedApiKey.encryptedData());
            
            // Record metrics
            metricsCollector.recordFailure("api_key_management", "retrieve", serverId, "success");
            
            log.debug("API key retrieved for server: {}", serverId);
            
            return ApiKeyResult.success("API key retrieved successfully", decryptedKey);
            
        } catch (Exception e) {
            log.error("Failed to retrieve API key for server: {}", serverId, e);
            metricsCollector.recordFailure("api_key_management", "retrieve", serverId, "decryption_failed");
            return ApiKeyResult.failure("Failed to retrieve API key: " + e.getMessage());
        }
    }
    
    /**
     * Rotate API key for a server.
     */
    public ApiKeyResult rotateApiKey(String serverId, String newApiKey) {
        try {
            EncryptedApiKey existingKey = encryptedKeys.get(serverId);
            
            if (existingKey == null) {
                return ApiKeyResult.failure("No existing API key found for server: " + serverId);
            }
            
            // Store the new key with same metadata
            ApiKeyResult storeResult = storeApiKey(serverId, newApiKey, 
                                                 existingKey.keyType(), 
                                                 existingKey.description() + " (rotated)");
            
            if (storeResult.isSuccess()) {
                log.info("API key rotated for server: {}", serverId);
                metricsCollector.recordFailure("api_key_management", "rotate", serverId, "success");
            }
            
            return storeResult;
            
        } catch (Exception e) {
            log.error("Failed to rotate API key for server: {}", serverId, e);
            metricsCollector.recordFailure("api_key_management", "rotate", serverId, "failed");
            return ApiKeyResult.failure("Failed to rotate API key: " + e.getMessage());
        }
    }
    
    /**
     * Remove API key for a server.
     */
    public ApiKeyResult removeApiKey(String serverId) {
        try {
            EncryptedApiKey removed = encryptedKeys.remove(serverId);
            
            if (removed == null) {
                return ApiKeyResult.failure("No API key found for server: " + serverId);
            }
            
            log.info("API key removed for server: {}", serverId);
            metricsCollector.recordFailure("api_key_management", "remove", serverId, "success");
            
            return ApiKeyResult.success("API key removed successfully");
            
        } catch (Exception e) {
            log.error("Failed to remove API key for server: {}", serverId, e);
            metricsCollector.recordFailure("api_key_management", "remove", serverId, "failed");
            return ApiKeyResult.failure("Failed to remove API key: " + e.getMessage());
        }
    }
    
    /**
     * List servers with stored API keys (without revealing the keys).
     */
    public Map<String, ApiKeyInfo> listApiKeys() {
        Map<String, ApiKeyInfo> keyInfos = new ConcurrentHashMap<>();
        
        encryptedKeys.forEach((serverId, encryptedKey) -> {
            keyInfos.put(serverId, new ApiKeyInfo(
                    serverId,
                    encryptedKey.keyType(),
                    encryptedKey.description(),
                    encryptedKey.createdAt(),
                    encryptedKey.expiresAt(),
                    !isKeyExpired(encryptedKey)
            ));
        });
        
        log.debug("Listed {} API keys", keyInfos.size());
        return keyInfos;
    }
    
    /**
     * Check if API key exists for a server.
     */
    public boolean hasValidApiKey(String serverId) {
        EncryptedApiKey encryptedKey = encryptedKeys.get(serverId);
        return encryptedKey != null && !isKeyExpired(encryptedKey);
    }
    
    // Private helper methods
    
    private void validateInput(String serverId, String apiKey, String keyType) {
        if (serverId == null || serverId.trim().isEmpty()) {
            throw new IllegalArgumentException("Server ID cannot be null or empty");
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        if (keyType == null || keyType.trim().isEmpty()) {
            throw new IllegalArgumentException("Key type cannot be null or empty");
        }
    }
    
    private EncryptedData encryptApiKey(String apiKey) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);
        
        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, gcmSpec);
        
        byte[] encryptedBytes = cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));
        
        return new EncryptedData(
                Base64.getEncoder().encodeToString(encryptedBytes),
                Base64.getEncoder().encodeToString(iv)
        );
    }
    
    private String decryptApiKey(EncryptedData encryptedData) throws Exception {
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedData.encryptedValue());
        byte[] iv = Base64.getDecoder().decode(encryptedData.iv());
        
        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, gcmSpec);
        
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }
    
    private SecretKey initializeEncryptionKey() {
        try {
            // In production, this should come from a secure key management service
            String configuredKey = System.getenv("MCP_ENCRYPTION_KEY");
            
            if (configuredKey != null && !configuredKey.isEmpty()) {
                byte[] keyBytes = Base64.getDecoder().decode(configuredKey);
                return new SecretKeySpec(keyBytes, "AES");
            } else {
                // Generate a new key for development (this will not persist across restarts)
                KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                keyGen.init(256);
                SecretKey generatedKey = keyGen.generateKey();
                
                if (config.isDevelopment()) {
                    String keyB64 = Base64.getEncoder().encodeToString(generatedKey.getEncoded());
                    log.warn("Generated new encryption key for development: {}", keyB64);
                    log.warn("Set MCP_ENCRYPTION_KEY environment variable to persist keys across restarts");
                }
                
                return generatedKey;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize encryption key", e);
        }
    }
    
    private void initializeDemoKeys() {
        // Demo keys for development and testing
        storeApiKey("echo-server", "demo-echo-key-12345", "bearer", "Demo echo server API key");
        storeApiKey("brave-search", "demo-brave-key-67890", "api_key", "Demo Brave search API key");
        storeApiKey("calculator-server", "demo-calc-key-abcde", "bearer", "Demo calculator server API key");
        
        log.info("Initialized demo API keys for development");
    }
    
    private long calculateExpirationTime(String keyType) {
        // Different key types have different expiration policies
        return switch (keyType.toLowerCase()) {
            case "temporary", "demo" -> System.currentTimeMillis() + (24 * 60 * 60 * 1000L); // 1 day
            case "bearer", "oauth" -> System.currentTimeMillis() + (30 * 24 * 60 * 60 * 1000L); // 30 days
            case "api_key" -> System.currentTimeMillis() + (90 * 24 * 60 * 60 * 1000L); // 90 days
            default -> System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L); // 7 days default
        };
    }
    
    private boolean isKeyExpired(EncryptedApiKey encryptedKey) {
        return System.currentTimeMillis() > encryptedKey.expiresAt();
    }
    
    // Data classes
    
    public record EncryptedData(String encryptedValue, String iv) {}
    
    public record EncryptedApiKey(
            String serverId,
            String keyType,
            String description,
            EncryptedData encryptedData,
            long createdAt,
            long expiresAt
    ) {}
    
    public record ApiKeyInfo(
            String serverId,
            String keyType,
            String description,
            long createdAt,
            long expiresAt,
            boolean isValid
    ) {}
    
    public static class ApiKeyResult {
        private final boolean success;
        private final String message;
        private final String apiKey;
        
        private ApiKeyResult(boolean success, String message, String apiKey) {
            this.success = success;
            this.message = message;
            this.apiKey = apiKey;
        }
        
        public static ApiKeyResult success(String message) {
            return new ApiKeyResult(true, message, null);
        }
        
        public static ApiKeyResult success(String message, String apiKey) {
            return new ApiKeyResult(true, message, apiKey);
        }
        
        public static ApiKeyResult failure(String message) {
            return new ApiKeyResult(false, message, null);
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getApiKey() { return apiKey; }
    }
}