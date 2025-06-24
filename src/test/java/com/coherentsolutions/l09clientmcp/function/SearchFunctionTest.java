package com.coherentsolutions.l09clientmcp.function;

import com.coherentsolutions.l09clientmcp.mcp.McpToolResult;
import com.coherentsolutions.l09clientmcp.service.McpClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SearchFunction.
 * Branch 8: Tests Spring AI Function integration for Brave Search MCP integration.
 * 
 * Educational Focus:
 * - External API integration testing patterns
 * - Search result processing validation
 * - Parameter validation and transformation
 * - Error handling for network operations
 * - Metadata extraction and formatting
 */
@ExtendWith(MockitoExtension.class)
class SearchFunctionTest {

    @Mock
    private McpClientService mcpClientService;

    private SearchFunction searchFunction;

    @BeforeEach
    void setUp() {
        searchFunction = new SearchFunction(mcpClientService);
    }

    @Test
    void testSuccessfulWebSearch() {
        // Arrange
        SearchFunction.Request request = new SearchFunction.Request(
            "Spring Boot tutorial", 10, "web", "US", "moderate"
        );
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(true)
            .content("Found 10 results for Spring Boot tutorial")
            .metadata(Map.of(
                "search_time_ms", 150L,
                "total_results", 1000,
                "results", List.of(
                    Map.of(
                        "title", "Spring Boot Official Tutorial",
                        "url", "https://spring.io/guides/gs/spring-boot/",
                        "description", "Learn how to build Spring Boot applications",
                        "display_url", "spring.io",
                        "type", "web"
                    ),
                    Map.of(
                        "title", "Spring Boot Tutorial - Baeldung",
                        "url", "https://www.baeldung.com/spring-boot",
                        "description", "Comprehensive Spring Boot guide",
                        "display_url", "baeldung.com",
                        "type", "web"
                    )
                )
            ))
            .build();
        
        when(mcpClientService.invokeTool(eq("brave_web_search"), eq(Map.of(
            "q", "Spring Boot tutorial",
            "count", 10,
            "country", "US",
            "safesearch", "moderate"
        )))).thenReturn(mcpResult);

        // Act
        SearchFunction.Response response = searchFunction.apply(request);

        // Assert
        assertTrue(response.success());
        assertEquals("Spring Boot tutorial", response.query());
        assertEquals(2, response.resultCount());
        assertEquals(2, response.results().size());
        
        SearchFunction.SearchResult firstResult = response.results().get(0);
        assertEquals("Spring Boot Official Tutorial", firstResult.title());
        assertEquals("https://spring.io/guides/gs/spring-boot/", firstResult.url());
        assertEquals("Learn how to build Spring Boot applications", firstResult.description());
        assertEquals("spring.io", firstResult.displayUrl());
        assertEquals("web", firstResult.type());
        
        assertNotNull(response.metadata());
        assertEquals("web", response.metadata().searchType());
        assertEquals("US", response.metadata().country());
        assertEquals("moderate", response.metadata().safeSearch());
        assertEquals(150L, response.metadata().searchTime());
        assertEquals(1000, response.metadata().totalResults());
        
        assertNull(response.error());
    }

    @Test
    void testSuccessfulNewsSearch() {
        // Arrange
        SearchFunction.Request request = new SearchFunction.Request(
            "AI technology news", 5, "news", "GB", "strict"
        );
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(true)
            .content("Latest AI technology news")
            .metadata(Map.of(
                "search_time_ms", 200L,
                "total_results", 500,
                "results", List.of(
                    Map.of(
                        "title", "AI Breakthrough in 2024",
                        "url", "https://techcrunch.com/ai-news",
                        "description", "Major AI developments this year",
                        "display_url", "techcrunch.com",
                        "type", "news"
                    )
                )
            ))
            .build();
        
        when(mcpClientService.invokeTool(eq("brave_news_search"), eq(Map.of(
            "q", "AI technology news",
            "count", 5,
            "country", "GB",
            "safesearch", "strict"
        )))).thenReturn(mcpResult);

        // Act
        SearchFunction.Response response = searchFunction.apply(request);

        // Assert
        assertTrue(response.success());
        assertEquals("AI technology news", response.query());
        assertEquals(1, response.resultCount());
        assertEquals("news", response.metadata().searchType());
        assertEquals("GB", response.metadata().country());
        assertEquals("strict", response.metadata().safeSearch());
    }

    @Test
    void testParameterValidationAndDefaults() {
        // Arrange - test parameter validation and defaults
        SearchFunction.Request request = new SearchFunction.Request(
            "test query", 25, null, null, null  // count too high, other params null
        );
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(true)
            .content("Search results")
            .metadata(Map.of("total_results", 1))
            .build();
        
        // Expect count to be capped at 20, type to default to web search
        when(mcpClientService.invokeTool(eq("brave_web_search"), eq(Map.of(
            "q", "test query",
            "count", 20  // Should be capped at 20
        )))).thenReturn(mcpResult);

        // Act
        SearchFunction.Response response = searchFunction.apply(request);

        // Assert
        assertTrue(response.success());
        assertEquals("web", response.metadata().searchType());
        assertEquals("US", response.metadata().country());  // Default
        assertEquals("moderate", response.metadata().safeSearch());  // Default
    }

    @Test
    void testMinimumCountParameterValidation() {
        // Arrange - test minimum count validation
        SearchFunction.Request request = new SearchFunction.Request(
            "test query", 0, "web", "US", "moderate"  // count too low
        );
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(true)
            .content("Search results")
            .metadata(Map.of("total_results", 1))
            .build();
        
        // Expect count to be set to minimum of 1
        when(mcpClientService.invokeTool(eq("brave_web_search"), eq(Map.of(
            "q", "test query",
            "count", 1,  // Should be set to minimum of 1
            "country", "US",
            "safesearch", "moderate"
        )))).thenReturn(mcpResult);

        // Act
        SearchFunction.Response response = searchFunction.apply(request);

        // Assert
        assertTrue(response.success());
    }

    @Test
    void testImageSearchType() {
        // Arrange
        SearchFunction.Request request = new SearchFunction.Request(
            "cat photos", 3, "images", "CA", "off"
        );
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(true)
            .content("Found cat images")
            .metadata(Map.of("total_results", 100))
            .build();
        
        when(mcpClientService.invokeTool(eq("brave_image_search"), eq(Map.of(
            "q", "cat photos",
            "count", 3,
            "country", "CA",
            "safesearch", "off"
        )))).thenReturn(mcpResult);

        // Act
        SearchFunction.Response response = searchFunction.apply(request);

        // Assert
        assertTrue(response.success());
        assertEquals("images", response.metadata().searchType());
        assertEquals("CA", response.metadata().country());
        assertEquals("off", response.metadata().safeSearch());
    }

    @Test
    void testVideoSearchType() {
        // Arrange
        SearchFunction.Request request = new SearchFunction.Request(
            "tutorial videos", 5, "videos", null, null
        );
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(true)
            .content("Found tutorial videos")
            .metadata(Map.of("total_results", 50))
            .build();
        
        when(mcpClientService.invokeTool(eq("brave_video_search"), eq(Map.of(
            "q", "tutorial videos",
            "count", 5
        )))).thenReturn(mcpResult);

        // Act
        SearchFunction.Response response = searchFunction.apply(request);

        // Assert
        assertTrue(response.success());
        assertEquals("videos", response.metadata().searchType());
    }

    @Test
    void testInvalidSafeSearchIgnored() {
        // Arrange - test invalid safesearch value is ignored
        SearchFunction.Request request = new SearchFunction.Request(
            "test query", 5, "web", "US", "invalid_value"
        );
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(true)
            .content("Search results")
            .metadata(Map.of("total_results", 1))
            .build();
        
        // Expect invalid safesearch to be ignored (not included in arguments)
        when(mcpClientService.invokeTool(eq("brave_web_search"), eq(Map.of(
            "q", "test query",
            "count", 5,
            "country", "US"
            // safesearch should be omitted due to invalid value
        )))).thenReturn(mcpResult);

        // Act
        SearchFunction.Response response = searchFunction.apply(request);

        // Assert
        assertTrue(response.success());
    }

    @Test
    void testFailedSearch() {
        // Arrange
        SearchFunction.Request request = new SearchFunction.Request(
            "test query", null, null, null, null
        );
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(false)
            .content("API quota exceeded")
            .build();
        
        when(mcpClientService.invokeTool(eq("brave_web_search"), eq(Map.of(
            "q", "test query",
            "count", 10
        )))).thenReturn(mcpResult);

        // Act
        SearchFunction.Response response = searchFunction.apply(request);

        // Assert
        assertFalse(response.success());
        assertEquals("test query", response.query());
        assertEquals(0, response.resultCount());
        assertTrue(response.results().isEmpty());
        assertNull(response.metadata());
        assertEquals("API quota exceeded", response.error());
    }

    @Test
    void testSearchException() {
        // Arrange
        SearchFunction.Request request = new SearchFunction.Request(
            "test query", null, null, null, null
        );
        
        when(mcpClientService.invokeTool(eq("brave_web_search"), eq(Map.of(
            "q", "test query",
            "count", 10
        )))).thenThrow(new RuntimeException("Network connection failed"));

        // Act
        SearchFunction.Response response = searchFunction.apply(request);

        // Assert
        assertFalse(response.success());
        assertEquals("test query", response.query());
        assertEquals(0, response.resultCount());
        assertTrue(response.results().isEmpty());
        assertNull(response.metadata());
        assertTrue(response.error().contains("Search function error"));
        assertTrue(response.error().contains("Network connection failed"));
    }

    @Test
    void testSearchWithNoResults() {
        // Arrange
        SearchFunction.Request request = new SearchFunction.Request(
            "very specific query with no results", 10, "web", "US", "moderate"
        );
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(true)
            .content("No results found")
            .metadata(Map.of(
                "search_time_ms", 100L,
                "total_results", 0,
                "results", List.of()  // Empty results
            ))
            .build();
        
        when(mcpClientService.invokeTool(eq("brave_web_search"), eq(Map.of(
            "q", "very specific query with no results",
            "count", 10,
            "country", "US",
            "safesearch", "moderate"
        )))).thenReturn(mcpResult);

        // Act
        SearchFunction.Response response = searchFunction.apply(request);

        // Assert
        assertTrue(response.success());
        assertEquals("very specific query with no results", response.query());
        assertEquals(0, response.resultCount());
        assertTrue(response.results().isEmpty());
        assertNotNull(response.metadata());
        assertEquals(0, response.metadata().totalResults());
        assertNull(response.error());
    }

    @Test
    void testSearchWithFallbackResult() {
        // Arrange - test fallback when metadata doesn't contain structured results
        SearchFunction.Request request = new SearchFunction.Request(
            "fallback test", 5, "web", null, null
        );
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(true)
            .content("Search completed: Found results at https://example.com/results")
            .metadata(Map.of("search_time_ms", 200L))  // No results array
            .build();
        
        when(mcpClientService.invokeTool(eq("brave_web_search"), eq(Map.of(
            "q", "fallback test",
            "count", 5
        )))).thenReturn(mcpResult);

        // Act
        SearchFunction.Response response = searchFunction.apply(request);

        // Assert
        assertTrue(response.success());
        assertEquals("fallback test", response.query());
        assertEquals(1, response.resultCount());
        assertEquals(1, response.results().size());
        
        SearchFunction.SearchResult result = response.results().get(0);
        assertEquals("Search Result", result.title());
        assertEquals("https://example.com/results", result.url());
        assertEquals("Search completed: Found results at https://example.com/results", result.description());
        assertEquals("web", result.type());
    }

    @Test
    void testCountryCodeNormalization() {
        // Arrange - test country code is normalized to uppercase
        SearchFunction.Request request = new SearchFunction.Request(
            "test query", 5, "web", "gb", "moderate"  // lowercase country code
        );
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(true)
            .content("Search results")
            .metadata(Map.of("total_results", 1))
            .build();
        
        when(mcpClientService.invokeTool(eq("brave_web_search"), eq(Map.of(
            "q", "test query",
            "count", 5,
            "country", "GB",  // Should be normalized to uppercase
            "safesearch", "moderate"
        )))).thenReturn(mcpResult);

        // Act
        SearchFunction.Response response = searchFunction.apply(request);

        // Assert
        assertTrue(response.success());
        assertEquals("gb", response.metadata().country());
    }

    @Test
    void testEmptyAndWhitespaceParametersIgnored() {
        // Arrange - test empty/whitespace parameters are ignored
        SearchFunction.Request request = new SearchFunction.Request(
            "test query", 5, "web", "  ", ""  // whitespace and empty string
        );
        
        McpToolResult mcpResult = McpToolResult.builder()
            .success(true)
            .content("Search results")
            .metadata(Map.of("total_results", 1))
            .build();
        
        when(mcpClientService.invokeTool(eq("brave_web_search"), eq(Map.of(
            "q", "test query",
            "count", 5
            // country and safesearch should be omitted
        )))).thenReturn(mcpResult);

        // Act
        SearchFunction.Response response = searchFunction.apply(request);

        // Assert
        assertTrue(response.success());
        assertEquals("  ", response.metadata().country());  // Should reflect actual parameter passed
        assertEquals("", response.metadata().safeSearch());  // Should reflect actual parameter passed
    }
}