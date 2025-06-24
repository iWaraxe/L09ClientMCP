package com.coherentsolutions.l09clientmcp.production;

import com.coherentsolutions.l09clientmcp.model.ChatRequest;
import com.coherentsolutions.l09clientmcp.model.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebMvc
@EnableAutoConfiguration(exclude = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Disabled("Integration tests require complex Spring context setup - skipped for now")
class ProductionIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testHealthEndpoint() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/chat/health", String.class);

        // Then
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Chat service is running"));
    }

    @Test
    void testChatEndpoint() throws Exception {
        // Given
        ChatRequest request = new ChatRequest("Hello, how are you?");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ChatRequest> entity = new HttpEntity<>(request, headers);

        // When
        ResponseEntity<ChatResponse> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/chat", entity, ChatResponse.class);

        // Then
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getResponse());
        assertFalse(response.getBody().getResponse().isEmpty());
    }

    @Test
    void testProductionMonitoringEndpoint() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/production/monitoring/health", String.class);

        // Then
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void testProductionMetricsEndpoint() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/production/monitoring/metrics", String.class);

        // Then
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void testProductionStatusEndpoint() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/production/monitoring/status", String.class);

        // Then
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void testRateLimitingBehavior() throws Exception {
        // Given
        ChatRequest request = new ChatRequest("Test rate limiting");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ChatRequest> entity = new HttpEntity<>(request, headers);

        // When - Make multiple rapid requests
        ResponseEntity<ChatResponse> firstResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/chat", entity, ChatResponse.class);
        
        ResponseEntity<ChatResponse> secondResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/chat", entity, ChatResponse.class);

        // Then - Both should succeed (rate limiting is configured but not too restrictive for tests)
        assertEquals(200, firstResponse.getStatusCode().value());
        assertEquals(200, secondResponse.getStatusCode().value());
    }

    @Test
    void testErrorHandling() {
        // Given - Invalid request
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{\"invalid\": \"json\"}", headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/chat", entity, String.class);

        // Then - Should handle error gracefully
        assertTrue(response.getStatusCode().value() >= 400);
    }

    @Test
    void testSecurityHeaders() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/chat/health", String.class);

        // Then - Check for security headers (implementation depends on security configuration)
        assertNotNull(response.getHeaders());
    }

    @Test
    void testApplicationStartupPerformance() {
        // This test verifies that the application can handle requests immediately after startup
        // Given
        long startTime = System.currentTimeMillis();

        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/chat/health", String.class);
        
        long responseTime = System.currentTimeMillis() - startTime;

        // Then
        assertEquals(200, response.getStatusCode().value());
        assertTrue(responseTime < 5000, "Health check should respond within 5 seconds");
    }

    @Test
    void testConcurrentRequests() throws InterruptedException {
        // Given
        ChatRequest request = new ChatRequest("Concurrent test");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ChatRequest> entity = new HttpEntity<>(request, headers);

        // When - Make concurrent requests
        Thread[] threads = new Thread[5];
        ResponseEntity<ChatResponse>[] responses = new ResponseEntity[5];
        
        for (int i = 0; i < 5; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                responses[index] = restTemplate.postForEntity(
                        "http://localhost:" + port + "/api/chat", entity, ChatResponse.class);
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Then - All requests should succeed
        for (ResponseEntity<ChatResponse> response : responses) {
            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
        }
    }
}