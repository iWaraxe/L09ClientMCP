# ADR-001: Adopt Spring AI Framework for AI Integration

## Status
Accepted

## Context

We need to integrate with AI services (initially OpenAI) to build a chatbot that will evolve to support Model Context Protocol (MCP). The application must be educational, demonstrating best practices for AI integration in Spring Boot applications.

## Decision Drivers

- **Educational Value**: Code should demonstrate best practices and be easy to understand
- **Extensibility**: Must support future integration with MCP and multiple AI providers
- **Maintainability**: Clear separation of concerns and testable architecture
- **Spring Ecosystem Integration**: Leverage existing Spring Boot knowledge
- **Future-Proofing**: Architecture should evolve as AI integration patterns mature

## Options Considered

### Option 1: Direct OpenAI Java Client
```java
@Service
public class ChatService {
    private final OpenAiService openAiService;
    
    public String chat(String message) {
        CompletionRequest request = CompletionRequest.builder()
            .model("gpt-3.5-turbo")
            .prompt(message)
            .build();
        return openAiService.createCompletion(request).getChoices().get(0).getText();
    }
}
```

**PROS**:
- Direct control over API calls
- No additional abstraction layer to learn
- Full access to OpenAI-specific features

**CONS**:
- Tight coupling to OpenAI
- Manual prompt engineering
- No Spring Boot integration patterns
- Difficult to switch providers
- No built-in error handling patterns

### Option 2: Custom HTTP Client with RestTemplate
```java
@Service
public class ChatService {
    private final RestTemplate restTemplate;
    
    public String chat(String message) {
        OpenAIRequest request = new OpenAIRequest(message);
        OpenAIResponse response = restTemplate.postForObject(
            "https://api.openai.com/v1/completions", 
            request, 
            OpenAIResponse.class
        );
        return response.getChoices().get(0).getText();
    }
}
```

**PROS**:
- Full control over HTTP requests
- Spring RestTemplate integration
- Custom error handling

**CONS**:
- Manual JSON mapping and error handling
- No prompt engineering utilities
- Reinventing the wheel
- No provider abstraction

### Option 3: Spring AI Framework
```java
@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatClient chatClient;
    
    public String chat(String message) {
        var systemMessage = new SystemMessage("You are a helpful assistant");
        var userMessage = new UserMessage(message);
        var prompt = new Prompt(List.of(systemMessage, userMessage));
        
        return chatClient.prompt(prompt).call().content();
    }
}
```

**PROS**:
- Provider abstraction (OpenAI, Anthropic, Azure, etc.)
- Built-in Spring Boot integration
- Prompt engineering utilities
- Structured message handling
- MCP support planned/available
- Spring ecosystem consistency
- Built-in error handling patterns

**CONS**:
- Additional abstraction layer to learn
- Potential overhead
- Less direct control over API specifics

## Decision

**Chosen Option: Spring AI Framework (Option 3)**

## Rationale

### Provider Flexibility
Spring AI provides a clean abstraction that allows switching between AI providers without changing business logic:

```java
// Same code works with OpenAI, Anthropic, or Azure OpenAI
ChatClient chatClient = ChatClient.builder()
    .chatModel(openAiChatModel)  // or anthropicChatModel, or azureChatModel
    .build();
```

### Educational Value
The framework demonstrates Spring Boot best practices:
- Dependency injection patterns
- Configuration management
- Auto-configuration usage
- Clean separation of concerns

### MCP Integration Readiness
Spring AI 1.0.0 includes MCP support, making future integration seamless:
```java
// Future MCP integration will look like:
@Bean
public McpClient mcpClient() {
    return McpClient.builder()
        .withTransport(stdioTransport())
        .build();
}
```

### Prompt Engineering Support
Built-in utilities for complex prompt management:
```java
PromptTemplate template = new PromptTemplate("""
    You are a {role} assistant.
    Context: {context}
    Question: {question}
    """);
    
Map<String, Object> variables = Map.of(
    "role", "helpful",
    "context", retrievedContext,
    "question", userQuestion
);

Prompt prompt = template.create(variables);
```

## Consequences

### Positive
- **Future-Proof Architecture**: Easy MCP integration path
- **Provider Independence**: Can switch from OpenAI to Claude/Anthropic easily
- **Spring Consistency**: Follows Spring Boot patterns developers expect
- **Rich Ecosystem**: Access to Spring AI's growing feature set
- **Testing Support**: Built-in test utilities and mocking support

### Negative
- **Learning Curve**: Developers must learn Spring AI concepts
- **Abstraction Overhead**: Additional layer between application and AI API
- **Framework Dependency**: Tied to Spring AI evolution and release cycle

### Neutral
- **Community**: Spring AI is officially supported by VMware/Spring team
- **Documentation**: Good documentation and examples available
- **Performance**: Abstraction overhead is minimal for typical use cases

## Implementation Notes

### Configuration Pattern
```java
@Configuration
public class ChatClientConfig {
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
            .defaultSystem("You are a helpful AI assistant")
            .build();
    }
}
```

### Error Handling Pattern
```java
try {
    return chatClient.prompt(prompt).call().content();
} catch (Exception e) {
    log.error("AI call failed: {}", e.getMessage());
    return "I apologize, but I'm having trouble processing your request right now.";
}
```

### Testing Pattern
```java
@WebMvcTest
class ChatControllerTest {
    @MockBean
    private ChatService chatService;  // Mock the service, not Spring AI
}
```

## Related Decisions
- [ADR-002: Layered Architecture for AI Applications](./002-layered-architecture.md) (future)
- [ADR-003: MCP Integration Strategy](./003-mcp-integration-strategy.md) (future)

## References
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [Spring AI Examples](https://github.com/spring-projects/spring-ai-examples)
- [Model Context Protocol Specification](https://modelcontextprotocol.io/)