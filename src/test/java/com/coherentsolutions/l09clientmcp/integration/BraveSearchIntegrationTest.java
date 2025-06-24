package com.coherentsolutions.l09clientmcp.integration;

import com.coherentsolutions.l09clientmcp.config.BraveSearchConfig;
import com.coherentsolutions.l09clientmcp.function.SearchFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Brave Search MCP functionality.
 * 
 * These tests require:
 * 1. BRAVE_API_KEY environment variable to be set
 * 2. Running Brave Search MCP server at configured URL
 * 3. Network connectivity to Brave Search API
 * 
 * Educational Focus:
 * - Real-world external API integration testing
 * - Environment-based test configuration
 * - Integration test patterns for MCP servers
 * - Production connectivity validation
 * 
 * Note: These tests are disabled by default and only run when BRAVE_API_KEY is set.
 */
@SpringBootTest(properties = {
    "spring.ai.mcp.client.enabled=true",
    "brave.search.api-key=${BRAVE_API_KEY:test-key}",
    "logging.level.com.coherentsolutions.l09clientmcp=DEBUG"
})
@ActiveProfiles("integration")
@EnabledIfEnvironmentVariable(named = "BRAVE_API_KEY", matches = ".*")
class BraveSearchIntegrationTest {

    @Autowired
    private SearchFunction searchFunction;

    @Autowired
    private BraveSearchConfig braveSearchConfig;

    @BeforeEach
    void setUp() {
        // Verify configuration is properly loaded
        assertNotNull(searchFunction, "SearchFunction should be available");
        assertNotNull(braveSearchConfig, "BraveSearchConfig should be available");
        
        // Skip tests if API key is not properly configured
        if (!braveSearchConfig.isApiKeyConfigured()) {
            throw new IllegalStateException(
                "Brave Search API key not configured. Set BRAVE_API_KEY environment variable."
            );
        }
    }

    @Test
    void testBasicWebSearch() {
        // Arrange
        SearchFunction.Request request = new SearchFunction.Request(
            "OpenAI GPT-4", 5, "web", "US", "moderate"
        );

        // Act
        SearchFunction.Response response = searchFunction.apply(request);

        // Assert
        assertTrue(response.success(), "Search should succeed with valid API key");
        assertEquals("OpenAI GPT-4", response.query());
        assertTrue(response.resultCount() > 0, "Should return at least one result");
        assertFalse(response.results().isEmpty(), "Results list should not be empty");
        assertNotNull(response.metadata(), "Metadata should be present");
        assertNull(response.error(), "No error should be present on success");

        // Validate first result structure
        SearchFunction.SearchResult firstResult = response.results().get(0);
        assertNotNull(firstResult.title(), "Result title should not be null");
        assertNotNull(firstResult.url(), "Result URL should not be null");
        assertNotNull(firstResult.description(), "Result description should not be null");
        assertTrue(firstResult.url().startsWith("http"), "URL should be valid HTTP(S) URL");
    }

    @Test
    void testNewsSearch() {
        // Arrange
        SearchFunction.Request request = new SearchFunction.Request(
            "artificial intelligence news", 3, "news", "US", "moderate"
        );

        // Act
        SearchFunction.Response response = searchFunction.apply(request);

        // Assert
        assertTrue(response.success(), "News search should succeed");
        assertEquals("artificial intelligence news", response.query());
        assertNotNull(response.metadata());
        assertEquals("news", response.metadata().searchType());
        
        // News results should have appropriate structure
        if (!response.results().isEmpty()) {
            SearchFunction.SearchResult firstResult = response.results().get(0);
            assertNotNull(firstResult.title());
            assertNotNull(firstResult.url());
            // News articles typically have substantial descriptions
            assertNotNull(firstResult.description());
        }
    }

    @Test
    void testSearchWithDifferentCountries() {
        // Arrange
        SearchFunction.Request usRequest = new SearchFunction.Request(
            "weather forecast", 3, "web", "US", "moderate"
        );
        
        SearchFunction.Request gbRequest = new SearchFunction.Request(
            "weather forecast", 3, "web", "GB", "moderate"
        );

        // Act
        SearchFunction.Response usResponse = searchFunction.apply(usRequest);
        SearchFunction.Response gbResponse = searchFunction.apply(gbRequest);

        // Assert
        assertTrue(usResponse.success(), "US search should succeed");
        assertTrue(gbResponse.success(), "GB search should succeed");
        assertEquals("US", usResponse.metadata().country());
        assertEquals("GB", gbResponse.metadata().country());
        
        // Results may differ based on country localization
        assertNotNull(usResponse.results());
        assertNotNull(gbResponse.results());
    }

    @Test
    void testSearchParameterValidation() {
        // Arrange - test with extreme parameters
        SearchFunction.Request request = new SearchFunction.Request(
            "test query", 25, "web", "INVALID", "invalid_safe"  // Invalid parameters
        );

        // Act
        SearchFunction.Response response = searchFunction.apply(request);

        // Assert - Should handle invalid parameters gracefully
        assertNotNull(response);
        assertEquals("test query", response.query());
        
        if (response.success()) {
            // Parameters should be normalized/validated by the function
            assertNotNull(response.metadata());
            assertTrue(response.resultCount() <= 20, "Result count should be capped at 20");
        }
    }

    @Test
    void testEmptyQueryHandling() {
        // Arrange
        SearchFunction.Request request = new SearchFunction.Request(
            "", 5, "web", "US", "moderate"
        );

        // Act
        SearchFunction.Response response = searchFunction.apply(request);

        // Assert - Should handle empty query appropriately
        assertNotNull(response);
        assertEquals("", response.query());
        
        // Empty query might fail or return limited results
        if (!response.success()) {
            assertNotNull(response.error());
            assertTrue(response.error().length() > 0);
        }
    }

    @Test
    void testLargeQueryHandling() {
        // Arrange - test with a very long query
        String longQuery = "artificial intelligence machine learning deep learning neural networks " +
                          "natural language processing computer vision robotics automation " +
                          "data science big data analytics cloud computing edge computing " +
                          "quantum computing blockchain cryptocurrency fintech cybersecurity";
        
        SearchFunction.Request request = new SearchFunction.Request(
            longQuery, 5, "web", "US", "moderate"
        );

        // Act
        SearchFunction.Response response = searchFunction.apply(request);

        // Assert
        assertNotNull(response);
        assertEquals(longQuery, response.query());
        
        // Long queries should be handled gracefully
        if (response.success()) {
            assertTrue(response.resultCount() >= 0);
        } else {
            assertNotNull(response.error());
        }
    }

    @Test
    void testSearchResponseTiming() {
        // Arrange
        SearchFunction.Request request = new SearchFunction.Request(
            "Spring Boot", 5, "web", "US", "moderate"
        );

        // Act
        long startTime = System.currentTimeMillis();
        SearchFunction.Response response = searchFunction.apply(request);
        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;

        // Assert
        assertTrue(response.success(), "Search should complete successfully");
        assertTrue(responseTime < 10000, "Search should complete within 10 seconds");
        
        if (response.metadata() != null && response.metadata().searchTime() > 0) {
            // API search time should be reasonable
            assertTrue(response.metadata().searchTime() < 5000, 
                      "API search time should be under 5 seconds");
        }
    }

    @Test
    void testMultipleSearchTypes() {
        // Arrange - test different search types with the same query
        String query = "Java programming";
        
        SearchFunction.Request webRequest = new SearchFunction.Request(query, 3, "web", "US", "moderate");
        SearchFunction.Request newsRequest = new SearchFunction.Request(query, 3, "news", "US", "moderate");

        // Act
        SearchFunction.Response webResponse = searchFunction.apply(webRequest);
        SearchFunction.Response newsResponse = searchFunction.apply(newsRequest);

        // Assert
        assertTrue(webResponse.success(), "Web search should succeed");
        assertEquals("web", webResponse.metadata().searchType());
        
        assertTrue(newsResponse.success(), "News search should succeed");
        assertEquals("news", newsResponse.metadata().searchType());
        
        // Results should be appropriately different
        assertNotNull(webResponse.results());
        assertNotNull(newsResponse.results());
    }

    @Test
    void testSearchResultDataIntegrity() {
        // Arrange
        SearchFunction.Request request = new SearchFunction.Request(
            "Spring Framework documentation", 5, "web", "US", "moderate"
        );

        // Act
        SearchFunction.Response response = searchFunction.apply(request);

        // Assert
        assertTrue(response.success(), "Search should succeed");
        
        // Validate each result has required fields
        for (SearchFunction.SearchResult result : response.results()) {
            assertNotNull(result.title(), "Title should not be null");
            assertNotNull(result.url(), "URL should not be null");
            assertNotNull(result.description(), "Description should not be null");
            assertNotNull(result.displayUrl(), "Display URL should not be null");
            assertNotNull(result.type(), "Type should not be null");
            
            assertTrue(result.title().length() > 0, "Title should not be empty");
            assertTrue(result.url().length() > 0, "URL should not be empty");
            assertTrue(result.url().startsWith("http"), "URL should be properly formatted");
            
            // Display URL should be a reasonable domain
            assertTrue(result.displayUrl().length() > 0, "Display URL should not be empty");
            assertFalse(result.displayUrl().startsWith("http"), "Display URL should not include protocol");
        }
        
        // Metadata validation
        SearchFunction.SearchMetadata metadata = response.metadata();
        assertNotNull(metadata.searchType());
        assertNotNull(metadata.country());
        assertNotNull(metadata.safeSearch());
        assertTrue(metadata.totalResults() >= 0);
        assertTrue(metadata.searchTime() >= 0);
    }
}