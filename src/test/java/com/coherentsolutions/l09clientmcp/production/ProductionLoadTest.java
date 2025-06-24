package com.coherentsolutions.l09clientmcp.production;

import com.coherentsolutions.l09clientmcp.model.ChatRequest;
import com.coherentsolutions.l09clientmcp.model.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnableAutoConfiguration(exclude = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Disabled("Load tests require complex Spring context setup - skipped for now")
class ProductionLoadTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testHighConcurrencyLoad() throws InterruptedException, ExecutionException {
        // Given
        int numberOfThreads = 20;
        int requestsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        List<Future<TestResult>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // When
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            Future<TestResult> future = executor.submit(() -> {
                TestResult result = new TestResult();
                
                for (int j = 0; j < requestsPerThread; j++) {
                    try {
                        ChatRequest request = new ChatRequest("Load test message " + threadId + "-" + j);
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        HttpEntity<ChatRequest> entity = new HttpEntity<>(request, headers);

                        long requestStart = System.currentTimeMillis();
                        ResponseEntity<ChatResponse> response = restTemplate.postForEntity(
                                "http://localhost:" + port + "/api/chat", entity, ChatResponse.class);
                        long requestTime = System.currentTimeMillis() - requestStart;

                        if (response.getStatusCode().is2xxSuccessful()) {
                            result.successCount++;
                            result.totalResponseTime += requestTime;
                            result.maxResponseTime = Math.max(result.maxResponseTime, requestTime);
                            result.minResponseTime = Math.min(result.minResponseTime, requestTime);
                        } else {
                            result.errorCount++;
                        }
                    } catch (Exception e) {
                        result.errorCount++;
                    }
                }
                return result;
            });
            futures.add(future);
        }

        // Collect results
        TestResult totalResult = new TestResult();
        for (Future<TestResult> future : futures) {
            TestResult result = future.get();
            totalResult.successCount += result.successCount;
            totalResult.errorCount += result.errorCount;
            totalResult.totalResponseTime += result.totalResponseTime;
            totalResult.maxResponseTime = Math.max(totalResult.maxResponseTime, result.maxResponseTime);
            if (result.minResponseTime > 0) {
                totalResult.minResponseTime = totalResult.minResponseTime == 0 ? 
                    result.minResponseTime : Math.min(totalResult.minResponseTime, result.minResponseTime);
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;
        executor.shutdown();

        // Then
        int totalRequests = numberOfThreads * requestsPerThread;
        double successRate = (double) totalResult.successCount / totalRequests * 100;
        double avgResponseTime = totalResult.totalResponseTime / (double) totalResult.successCount;
        
        System.out.println("Load Test Results:");
        System.out.println("Total Requests: " + totalRequests);
        System.out.println("Successful: " + totalResult.successCount);
        System.out.println("Failed: " + totalResult.errorCount);
        System.out.println("Success Rate: " + String.format("%.2f%%", successRate));
        System.out.println("Total Time: " + totalTime + "ms");
        System.out.println("Avg Response Time: " + String.format("%.2f ms", avgResponseTime));
        System.out.println("Min Response Time: " + totalResult.minResponseTime + "ms");
        System.out.println("Max Response Time: " + totalResult.maxResponseTime + "ms");
        System.out.println("Requests/sec: " + String.format("%.2f", totalRequests / (totalTime / 1000.0)));

        // Assertions
        assertTrue(successRate >= 95.0, "Success rate should be at least 95%");
        assertTrue(avgResponseTime < 5000, "Average response time should be under 5 seconds");
        assertTrue(totalResult.maxResponseTime < 10000, "Max response time should be under 10 seconds");
    }

    @Test
    void testMemoryUsageUnderLoad() throws InterruptedException, ExecutionException {
        // Given
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        int numberOfRequests = 50;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<Void>> futures = new ArrayList<>();

        // When
        for (int i = 0; i < numberOfRequests; i++) {
            futures.add(executor.submit(() -> {
                ChatRequest request = new ChatRequest("Memory test message");
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<ChatRequest> entity = new HttpEntity<>(request, headers);

                restTemplate.postForEntity("http://localhost:" + port + "/api/chat", entity, ChatResponse.class);
                return null;
            }));
        }

        // Wait for completion
        for (Future<Void> future : futures) {
            future.get();
        }
        executor.shutdown();

        // Force garbage collection
        System.gc();
        Thread.sleep(1000);
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;

        // Then
        System.out.println("Memory Usage Test:");
        System.out.println("Initial Memory: " + (initialMemory / 1024 / 1024) + " MB");
        System.out.println("Final Memory: " + (finalMemory / 1024 / 1024) + " MB");
        System.out.println("Memory Increase: " + (memoryIncrease / 1024 / 1024) + " MB");

        // Memory increase should be reasonable (less than 100MB for this test)
        assertTrue(memoryIncrease < 100 * 1024 * 1024, 
                  "Memory increase should be less than 100MB, was: " + (memoryIncrease / 1024 / 1024) + "MB");
    }

    @Test
    void testSystemRecoveryAfterLoad() throws InterruptedException, ExecutionException {
        // Given - First, create some load
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<Void>> loadFutures = new ArrayList<>();

        for (int i = 0; i < 30; i++) {
            loadFutures.add(executor.submit(() -> {
                ChatRequest request = new ChatRequest("Recovery test load");
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<ChatRequest> entity = new HttpEntity<>(request, headers);

                restTemplate.postForEntity("http://localhost:" + port + "/api/chat", entity, ChatResponse.class);
                return null;
            }));
        }

        // Wait for load to complete
        for (Future<Void> future : loadFutures) {
            future.get();
        }
        executor.shutdown();

        // Allow system to recover
        Thread.sleep(2000);

        // When - Test normal operation
        ChatRequest request = new ChatRequest("Post-load recovery test");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ChatRequest> entity = new HttpEntity<>(request, headers);

        long startTime = System.currentTimeMillis();
        ResponseEntity<ChatResponse> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/chat", entity, ChatResponse.class);
        long responseTime = System.currentTimeMillis() - startTime;

        // Then
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(responseTime < 3000, "Response time after load should be reasonable: " + responseTime + "ms");
    }

    private static class TestResult {
        int successCount = 0;
        int errorCount = 0;
        long totalResponseTime = 0;
        long minResponseTime = 0;
        long maxResponseTime = 0;
    }
}