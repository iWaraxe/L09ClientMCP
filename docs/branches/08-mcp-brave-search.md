# Branch 8: Brave Search MCP Integration

## Learning Objectives

By completing this branch, you will understand:

- **Real-world External API Integration**: Move beyond mock servers to actual external services
- **Production-Grade Configuration Management**: Secure credential handling and environment variable integration
- **Advanced Function Design Patterns**: Complex parameter validation, type-safe processing, and structured responses
- **AI-Driven Tool Selection**: Intelligent detection and automatic tool invocation based on user intent
- **Search Result Processing**: Structured data parsing, metadata extraction, and response formatting
- **Comprehensive Testing Strategies**: Unit testing, integration testing with external dependencies, and edge case coverage

## Architecture Evolution

### Previous State (Branch 7)
```
Chat Request → ChatService → MockMcpServer → Simple Echo/Ping Tools
```

### Current State (Branch 8)
```
Chat Request → ChatService → [Search Detection] → SearchFunction → Brave Search API
                     ↓
               [Echo/Ping Detection] → EchoFunction/PingFunction → MockMcpServer
                     ↓
               [Standard AI] → OpenAI ChatClient
```

### Key Architectural Additions

1. **Intelligent Tool Router**: Automatic detection of search-requiring queries
2. **External API Integration**: Real Brave Search API through MCP protocol
3. **Multi-type Search Support**: Web, news, images, and video search capabilities
4. **Advanced Response Formatting**: Structured results with citations and metadata

## Implementation Details

### 1. Search Function Architecture

#### Core Components
```java
@Component
public class SearchFunction implements Function<SearchFunction.Request, SearchFunction.Response> {
    
    public record Request(
        String query,           // Required search query
        Integer count,          // Optional result count (1-20)
        String type,           // Search type: web, news, images, videos
        String country,        // Localization (US, GB, CA, etc.)
        String safesearch     // Safety level: strict, moderate, off
    ) {}
    
    public record Response(
        boolean success,
        String query,
        int resultCount,
        List<SearchResult> results,
        SearchMetadata metadata,
        String error
    ) {}
}
```

#### Advanced Parameter Processing
- **Validation**: Count constraints (1-20), valid country codes, safety levels
- **Normalization**: Country code uppercase conversion, format trimming
- **Defaults**: Intelligent fallbacks for missing parameters
- **Type Safety**: Strong typing with records for compile-time validation

### 2. Configuration Management

#### Type-Safe Configuration
```java
@ConfigurationProperties(prefix = "brave.search")
@Data
public class BraveSearchConfig {
    private String apiKey = "YOUR_API_KEY_HERE";
    private String baseUrl = "https://api.search.brave.com";
    private Duration timeout = Duration.ofSeconds(30);
    private int maxResults = 10;
    private String defaultCountry = "US";
    private String defaultSafeSearch = "moderate";
    
    public boolean isApiKeyConfigured() {
        return apiKey != null && !"YOUR_API_KEY_HERE".equals(apiKey);
    }
}
```

#### Production Features
- **Environment Variable Integration**: `BRAVE_API_KEY` support
- **Validation**: API key presence checking
- **Debugging Support**: Optional request/response logging
- **Flexible Endpoints**: Support for different search types

### 3. AI-Driven Search Detection

#### Smart Query Analysis
```java
private boolean isSearchRequest(String lowerMessage) {
    String[] currentInfoKeywords = {
        "current", "recent", "latest", "new", "today", "breaking news"
    };
    
    String[] searchKeywords = {
        "search for", "find", "tell me about", "what is", "how to"
    };
    
    // Multi-level detection strategy
    for (String keyword : currentInfoKeywords) {
        if (lowerMessage.contains(keyword)) return true;
    }
    
    // Context-aware detection for likely current information needs
    for (String keyword : searchKeywords) {
        if (lowerMessage.contains(keyword) && 
            requiresCurrentInfo(lowerMessage)) {
            return true;
        }
    }
    
    return false;
}
```

#### Intelligent Type Detection
- **News**: Breaking news, headlines, recent events
- **Images**: Photos, pictures, visual content
- **Videos**: Tutorials, demonstrations, video content
- **Web**: Default for general information queries

### 4. Advanced Response Processing

#### Structured Result Parsing
```java
private List<SearchResult> parseSearchResults(McpToolResult result) {
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
    
    // Graceful fallback for simple responses
    return createFallbackResult(result);
}
```

#### Rich Response Formatting
```java
private String formatSearchResponse(SearchFunction.Response response, String originalQuery) {
    StringBuilder result = new StringBuilder();
    
    result.append(String.format("I searched for '%s' and found %d relevant results:\n\n", 
                 response.query(), response.resultCount()));
    
    // Format top results with citations
    for (int i = 0; i < Math.min(3, response.results().size()); i++) {
        SearchResult searchResult = response.results().get(i);
        result.append(String.format("**%d. %s**\n", i + 1, searchResult.title()));
        result.append(String.format("*Source: %s*\n", searchResult.displayUrl()));
        result.append(String.format("%s\n", searchResult.description()));
        result.append(String.format("🔗 [Read more](%s)\n\n", searchResult.url()));
    }
    
    // Add search metadata
    result.append(String.format("*Search completed in %dms • %d total results*\n", 
                 response.metadata().searchTime(), response.metadata().totalResults()));
    
    return result.toString();
}
```

## Testing Strategy

### 1. Comprehensive Unit Testing (13 Tests)

#### Parameter Validation Tests
```java
@Test
void testParameterValidationAndDefaults() {
    SearchFunction.Request request = new SearchFunction.Request(
        "test query", 25, null, null, null  // Count too high, nulls
    );
    
    // Expects count capped at 20, type defaulted to web
    when(mcpClientService.invokeTool(eq("brave_web_search"), eq(Map.of(
        "q", "test query",
        "count", 20  // Should be capped
    )))).thenReturn(mcpResult);
    
    SearchFunction.Response response = searchFunction.apply(request);
    
    assertTrue(response.success());
    assertEquals("web", response.metadata().searchType());
}
```

#### Search Type Detection Tests
```java
@Test
void testNewsSearchType() {
    SearchFunction.Request request = new SearchFunction.Request(
        "AI technology news", 5, "news", "GB", "strict"
    );
    
    when(mcpClientService.invokeTool(eq("brave_news_search"), any()))
        .thenReturn(successfulResult);
    
    SearchFunction.Response response = searchFunction.apply(request);
    
    assertEquals("news", response.metadata().searchType());
    assertEquals("GB", response.metadata().country());
}
```

#### Error Handling Tests
```java
@Test
void testSearchException() {
    when(mcpClientService.invokeTool(any(), any()))
        .thenThrow(new RuntimeException("Network connection failed"));
    
    SearchFunction.Response response = searchFunction.apply(request);
    
    assertFalse(response.success());
    assertTrue(response.error().contains("Search function error"));
}
```

### 2. Integration Testing (9 Tests)

#### Real API Testing (Conditional)
```java
@EnabledIfEnvironmentVariable(named = "BRAVE_API_KEY", matches = ".*")
@SpringBootTest(properties = {
    "spring.ai.mcp.client.enabled=true",
    "brave.search.api-key=${BRAVE_API_KEY:test-key}"
})
class BraveSearchIntegrationTest {
    
    @Test
    void testBasicWebSearch() {
        SearchFunction.Request request = new SearchFunction.Request(
            "OpenAI GPT-4", 5, "web", "US", "moderate"
        );
        
        SearchFunction.Response response = searchFunction.apply(request);
        
        assertTrue(response.success());
        assertTrue(response.resultCount() > 0);
        assertNotNull(response.metadata());
    }
}
```

### 3. Chat Integration Testing

#### AI Search Detection Tests
```java
@Test
void testSearchDetectionIntegration() {
    // Test updated ChatService with SearchFunction
    String userMessage = "What are the latest AI developments?";
    
    String response = chatService.chat(userMessage);
    
    // Should trigger search instead of standard AI response
    assertTrue(response.contains("searched"));
    assertTrue(response.contains("found"));
}
```

## Configuration and Setup

### 1. Application Configuration

#### Environment Variables
```bash
# Required for real search functionality
export BRAVE_API_KEY="your_actual_api_key_here"

# Optional: Enable debug logging
export LOGGING_LEVEL_COM_COHERENTSOLUTIONS_L09CLIENTMCP=DEBUG
```

#### Application.yml Updates
```yaml
spring:
  ai:
    mcp:
      client:
        enabled: true
        sse:
          connections:
            brave-search:
              url: "http://localhost:3001/sse"
              enabled: true

brave:
  search:
    api-key: ${BRAVE_API_KEY:YOUR_API_KEY_HERE}
    base-url: "https://api.search.brave.com"
    timeout: 30s
    max-results: 10
    default-country: "US"
    default-safe-search: "moderate"
```

### 2. MCP Server Configuration

#### Brave Search MCP Server Setup (External)
```bash
# Install Brave Search MCP server
npm install -g @modelcontextprotocol/server-brave-search

# Run with API key
BRAVE_API_KEY=your_key npx @modelcontextprotocol/server-brave-search
```

#### Alternative: Mock Implementation
For development without API key, the mock MCP server simulates search responses:

```java
// Mock returns structured search results
McpToolResult mockResult = McpToolResult.builder()
    .success(true)
    .content("Search results for: " + query)
    .metadata(Map.of(
        "results", List.of(
            Map.of("title", "Result 1", "url", "https://example.com"),
            Map.of("title", "Result 2", "url", "https://example2.com")
        ),
        "search_time_ms", 150L,
        "total_results", 1000
    ))
    .build();
```

## Architectural Decisions and Trade-offs

### Decision 1: Function-Based Tool Integration

**Choice**: Implement SearchFunction as Spring AI Function rather than direct API calls

**Rationale**:
- **Consistency**: Matches existing EchoFunction and PingFunction patterns
- **Type Safety**: Compile-time validation with record-based requests/responses
- **Testability**: Easy mocking and unit testing
- **AI Integration**: Seamless integration with Spring AI's function calling

**Trade-offs**:
- **Pros**: Type safety, consistent architecture, easy testing
- **Cons**: Additional abstraction layer, function serialization overhead

### Decision 2: Intelligent Search Detection

**Choice**: Implement keyword-based search detection in ChatService

**Rationale**:
- **User Experience**: Automatic tool selection without explicit commands
- **Context Awareness**: Different detection strategies for different query types
- **Fallback Support**: Graceful degradation to standard AI responses

**Trade-offs**:
- **Pros**: Seamless user experience, intelligent automation
- **Cons**: Potential false positives, complex detection logic

### Decision 3: Structured Response Format

**Choice**: Rich response objects with metadata rather than simple strings

**Rationale**:
- **Extensibility**: Easy to add new response fields
- **Debugging**: Comprehensive error information and timing data
- **User Experience**: Rich formatting with citations and source links

**Trade-offs**:
- **Pros**: Rich information, extensible design, better debugging
- **Cons**: Increased complexity, larger response sizes

### Decision 4: Mock vs Real MCP Server

**Choice**: Support both mock (for development) and real Brave Search MCP server

**Rationale**:
- **Development Speed**: No API key required for basic development
- **Testing**: Predictable responses for unit testing
- **Production**: Real search capabilities when properly configured

**Trade-offs**:
- **Pros**: Flexible development environment, comprehensive testing
- **Cons**: Dual implementation complexity, configuration management

## Common Implementation Pitfalls

### 1. Parameter Validation Oversights

**Problem**: Not validating search parameters leads to API errors
```java
// Wrong: No validation
arguments.put("count", request.count()); // Could be null or > 20

// Right: Proper validation
arguments.put("count", request.count() != null ? 
    Math.min(Math.max(request.count(), 1), 20) : 10);
```

### 2. Metadata Handling Errors

**Problem**: Assuming metadata structure without null checks
```java
// Wrong: Potential NullPointerException
String timestamp = (String) result.getMetadata().get("timestamp");

// Right: Safe metadata extraction
String timestamp = null;
if (result.getMetadata() != null) {
    Object timestampObj = result.getMetadata().get("timestamp");
    timestamp = timestampObj != null ? String.valueOf(timestampObj) : null;
}
```

### 3. Search Detection False Positives

**Problem**: Overly broad search detection triggers inappropriately
```java
// Wrong: Too broad
if (message.contains("what")) {
    return handleSearchRequest(message); // Triggers on "what is OOP?"
}

// Right: Context-aware detection
if (message.contains("what") && requiresCurrentInfo(message)) {
    return handleSearchRequest(message);
}
```

### 4. Configuration Security Issues

**Problem**: Hardcoding API keys or exposing them in logs
```java
// Wrong: Hardcoded key
private String apiKey = "sk-actual-api-key-here";

// Wrong: Logging sensitive data
log.info("Using API key: {}", apiKey);

// Right: Environment variable with validation
@Value("${BRAVE_API_KEY:YOUR_API_KEY_HERE}")
private String apiKey;

public boolean isApiKeyConfigured() {
    return !"YOUR_API_KEY_HERE".equals(apiKey);
}
```

## Testing and Validation

### 1. Manual Testing Commands

#### Basic Functionality
```bash
# Start application
./mvnw spring-boot:run

# Test search detection
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What are the latest AI developments?"}'

# Test specific search types
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Find recent news about Spring Boot"}'
```

#### With Real API Key
```bash
# Set environment variable
export BRAVE_API_KEY="your_actual_api_key"

# Run application
./mvnw spring-boot:run

# Test real search
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Current weather forecast"}'
```

### 2. Postman Testing

Import the provided collection: `postman/L09ClientMCP-Branch8.postman_collection.json`

**Key Test Scenarios**:
- AI-driven search detection
- Multiple search types (web, news, images, videos)
- Legacy tool integration (echo, ping)
- Error handling and edge cases
- Performance validation

### 3. Integration Testing

Run integration tests (requires API key):
```bash
# With API key
BRAVE_API_KEY=your_key ./mvnw test -Dtest=BraveSearchIntegrationTest

# Without API key (tests skipped)
./mvnw test -Dtest=BraveSearchIntegrationTest
```

## Performance Considerations

### 1. Search Response Times

**Typical Performance**:
- Mock server: < 50ms
- Real API: 200-1000ms depending on query complexity
- Timeout configuration: 30s default

**Optimization Strategies**:
- Result count limitation (default: 5 for chat responses)
- Efficient response parsing
- Proper timeout configuration

### 2. Memory Usage

**Response Size Management**:
- Structured but compact response format
- Limited result count for chat responses
- Efficient string processing

### 3. API Rate Limiting

**Brave Search API Limits**:
- Free tier: 2,000 queries/month, 1 query/second
- Paid plans: Higher limits available

**Mitigation Strategies**:
- Intelligent search detection to avoid unnecessary calls
- Graceful error handling for rate limit responses
- User feedback for quota exceeded scenarios

## Future Enhancements

### 1. Advanced Search Features

**Planned Improvements**:
- Search result caching
- Query suggestion and expansion
- Multi-language search support
- Advanced filtering options

### 2. Enhanced AI Integration

**Potential Features**:
- Context-aware search refinement
- Automatic follow-up questions
- Search result summarization
- Cross-reference validation

### 3. Monitoring and Analytics

**Production Features**:
- Search usage analytics
- Performance monitoring
- Error rate tracking
- User interaction patterns

## Next Steps: Branch 9 Preview

Branch 9 will build on this foundation by implementing:

- **Multi-Server MCP Connections**: Connect to multiple MCP servers simultaneously
- **Server Load Balancing**: Distribute requests across available servers
- **Fallback Mechanisms**: Automatic failover between search providers
- **Connection Pooling**: Efficient resource management for multiple connections

The search functionality implemented in Branch 8 provides the foundation for these advanced multi-server patterns while demonstrating real-world external API integration through the MCP protocol.

## Summary

Branch 8 represents a significant milestone in the MCP learning journey:

- **Real-world Integration**: Beyond mock servers to actual external APIs
- **Production Patterns**: Secure configuration, comprehensive testing, error handling
- **Advanced Functionality**: Intelligent tool selection, rich response formatting
- **Scalable Architecture**: Foundation for multi-server and production features

The implementation demonstrates how MCP enables seamless integration of external services while maintaining clean architecture, comprehensive testing, and excellent user experience. This prepares learners for advanced topics in subsequent branches while providing immediately useful search capabilities.