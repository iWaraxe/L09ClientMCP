package com.coherentsolutions.l09clientmcp.function;

import com.coherentsolutions.l09clientmcp.mcp.McpToolResult;
import com.coherentsolutions.l09clientmcp.service.McpClientService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Spring AI Function for MCP Brave Search integration.
 * 
 * This function enables the AI model to perform web searches through the Brave Search API
 * when users request information about current events, technical documentation, or any
 * topic that requires up-to-date web content.
 * 
 * Educational Focus:
 * - Real-world external API integration through MCP
 * - Search result processing and formatting for AI consumption
 * - Dynamic query handling with parameters
 * - Production-grade error handling and fallbacks
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Description("Search the web using Brave Search API for current information, news, and research")
public class SearchFunction implements Function<SearchFunction.Request, SearchFunction.Response> {
    
    private final McpClientService mcpClientService;
    
    /**
     * Request record for web search parameters.
     */
    public record Request(
        @JsonProperty(required = true)
        @JsonPropertyDescription("The search query or keywords to search for")
        String query,
        
        @JsonProperty(required = false)
        @JsonPropertyDescription("Maximum number of results to return (1-20, default: 10)")
        Integer count,
        
        @JsonProperty(required = false)
        @JsonPropertyDescription("Search result type filter: 'web', 'news', 'images', 'videos' (default: 'web')")
        String type,
        
        @JsonProperty(required = false)
        @JsonPropertyDescription("Country code for localized search (e.g., 'US', 'GB', 'CA')")
        String country,
        
        @JsonProperty(required = false)
        @JsonPropertyDescription("Safe search level: 'strict', 'moderate', 'off' (default: 'moderate')")
        String safesearch
    ) {}
    
    /**
     * Response record with search results.
     */
    public record Response(
        boolean success,
        String query,
        int resultCount,
        List<SearchResult> results,
        SearchMetadata metadata,
        String error
    ) {}
    
    /**
     * Individual search result structure.
     */
    public record SearchResult(
        String title,
        String url,
        String description,
        String displayUrl,
        String type
    ) {}
    
    /**
     * Search metadata information.
     */
    public record SearchMetadata(
        String searchType,
        String country,
        String safeSearch,
        long searchTime,
        int totalResults
    ) {}
    
    @Override
    public Response apply(Request request) {
        log.info("AI requested search function: query='{}', count={}, type='{}'", 
                request.query(), request.count(), request.type());
        
        try {
            // Validate and prepare search parameters
            Map<String, Object> arguments = buildSearchArguments(request);
            
            // Determine the appropriate search tool based on type
            String toolName = determineSearchTool(request.type());
            
            // Invoke MCP search tool
            McpToolResult result = mcpClientService.invokeTool(toolName, arguments);
            
            if (result.isSuccess()) {
                return processSuccessfulSearch(request, result);
            } else {
                log.warn("Search tool failed: {}", result.getContent());
                return createErrorResponse(request, result.getContent());
            }
            
        } catch (Exception e) {
            log.error("Error in search function execution", e);
            return createErrorResponse(request, "Search function error: " + e.getMessage());
        }
    }
    
    /**
     * Build search arguments from request parameters.
     */
    private Map<String, Object> buildSearchArguments(Request request) {
        Map<String, Object> arguments = new HashMap<>();
        
        // Required parameter
        arguments.put("q", request.query());
        
        // Optional parameters with defaults
        arguments.put("count", request.count() != null ? 
            Math.min(Math.max(request.count(), 1), 20) : 10);
        
        if (request.country() != null && !request.country().trim().isEmpty()) {
            arguments.put("country", request.country().trim().toUpperCase());
        }
        
        if (request.safesearch() != null && !request.safesearch().trim().isEmpty()) {
            String safeSearch = request.safesearch().trim().toLowerCase();
            if (List.of("strict", "moderate", "off").contains(safeSearch)) {
                arguments.put("safesearch", safeSearch);
            }
        }
        
        return arguments;
    }
    
    /**
     * Determine which MCP tool to use based on search type.
     */
    private String determineSearchTool(String type) {
        if (type == null || type.trim().isEmpty()) {
            return "brave_web_search";
        }
        
        return switch (type.trim().toLowerCase()) {
            case "news" -> "brave_news_search";
            case "images" -> "brave_image_search";
            case "videos" -> "brave_video_search";
            case "web" -> "brave_web_search";
            default -> "brave_web_search";
        };
    }
    
    /**
     * Process successful search results from MCP.
     */
    private Response processSuccessfulSearch(Request request, McpToolResult result) {
        try {
            // Parse search results from MCP response
            List<SearchResult> searchResults = parseSearchResults(result);
            
            // Extract metadata
            SearchMetadata metadata = extractSearchMetadata(request, result);
            
            log.info("Search succeeded: found {} results for query '{}'", 
                    searchResults.size(), request.query());
            
            return new Response(
                true,
                request.query(),
                searchResults.size(),
                searchResults,
                metadata,
                null
            );
            
        } catch (Exception e) {
            log.error("Error processing search results", e);
            return createErrorResponse(request, "Failed to process search results: " + e.getMessage());
        }
    }
    
    /**
     * Parse search results from MCP tool response.
     */
    @SuppressWarnings("unchecked")
    private List<SearchResult> parseSearchResults(McpToolResult result) {
        try {
            // In a real implementation, the MCP server would return structured JSON
            // with search results from Brave Search API. For now, we simulate this
            // with metadata processing and content parsing.
            
            if (result.getMetadata() != null && result.getMetadata().containsKey("results")) {
                Object resultsObj = result.getMetadata().get("results");
                if (resultsObj instanceof List<?> resultsList) {
                    return resultsList.stream()
                        .filter(item -> item instanceof Map)
                        .map(item -> (Map<String, Object>) item)
                        .map(this::mapToSearchResult)
                        .toList();
                }
            }
            
            // Fallback: create single result from content
            return List.of(
                new SearchResult(
                    "Search Result",
                    extractUrlFromContent(result.getContent()),
                    result.getContent(),
                    "brave.com",
                    "web"
                )
            );
            
        } catch (Exception e) {
            log.warn("Failed to parse search results, using fallback", e);
            return List.of(
                new SearchResult(
                    "Search Result",
                    "https://search.brave.com",
                    result.getContent(),
                    "search.brave.com",
                    "web"
                )
            );
        }
    }
    
    /**
     * Map metadata object to SearchResult record.
     */
    private SearchResult mapToSearchResult(Map<String, Object> resultMap) {
        String title = getStringValue(resultMap, "title", "Untitled");
        String url = getStringValue(resultMap, "url", "");
        String description = getStringValue(resultMap, "description", "");
        String displayUrl = getStringValue(resultMap, "display_url", extractDomainFromUrl(url));
        String type = getStringValue(resultMap, "type", "web");
        
        return new SearchResult(title, url, description, displayUrl, type);
    }
    
    /**
     * Safely extract string value from map with default fallback.
     */
    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }
    
    /**
     * Extract URL from content string (basic implementation).
     */
    private String extractUrlFromContent(String content) {
        if (content != null && content.contains("http")) {
            String[] parts = content.split("\\s+");
            for (String part : parts) {
                if (part.startsWith("http://") || part.startsWith("https://")) {
                    return part;
                }
            }
        }
        return "https://search.brave.com";
    }
    
    /**
     * Extract domain from URL for display purposes.
     */
    private String extractDomainFromUrl(String url) {
        try {
            if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                java.net.URL urlObj = new java.net.URL(url);
                return urlObj.getHost();
            }
        } catch (Exception e) {
            log.debug("Failed to extract domain from URL: {}", url);
        }
        return "unknown";
    }
    
    /**
     * Extract search metadata from MCP response.
     */
    private SearchMetadata extractSearchMetadata(Request request, McpToolResult result) {
        String searchType = request.type() != null ? request.type() : "web";
        String country = request.country() != null ? request.country() : "US";
        String safeSearch = request.safesearch() != null ? request.safesearch() : "moderate";
        
        // Extract timing and result count from metadata if available
        long searchTime = 0;
        int totalResults = 1;
        
        if (result.getMetadata() != null) {
            Object timeObj = result.getMetadata().get("search_time_ms");
            if (timeObj instanceof Number) {
                searchTime = ((Number) timeObj).longValue();
            }
            
            Object countObj = result.getMetadata().get("total_results");
            if (countObj instanceof Number) {
                totalResults = ((Number) countObj).intValue();
            }
        }
        
        return new SearchMetadata(searchType, country, safeSearch, searchTime, totalResults);
    }
    
    /**
     * Create standardized error response.
     */
    private Response createErrorResponse(Request request, String errorMessage) {
        return new Response(
            false,
            request.query(),
            0,
            List.of(),
            null,
            errorMessage
        );
    }
}