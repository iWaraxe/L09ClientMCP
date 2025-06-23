# ADR-006: Function Registry Pattern for Spring AI Integration

## Status
**Accepted** - Branch 6 (2025-06-24)

## Context

Branch 6 requires transitioning from manual tool detection patterns (Branch 5) to Spring AI's native function calling capabilities. The system needs a way to dynamically discover, register, and manage Spring AI functions while maintaining health-based availability and operational visibility.

### Requirements
- Dynamic function discovery using Spring's component scanning
- Health-based function availability with graceful degradation
- Integration with Spring AI's function calling mechanism
- Operational visibility for monitoring and debugging
- Educational preservation of architectural evolution

### Constraints
- Must work with existing Spring Boot architecture
- Should leverage Spring AI framework capabilities
- Need to maintain backwards compatibility in API structure
- Must support function health monitoring and status reporting

## Decision

Implement a **Function Registry Service Pattern** that uses Spring ApplicationContext for automatic function discovery, combined with health-based availability checking and comprehensive status reporting.

### Core Components

1. **FunctionRegistry Service**: Central service for function discovery and management
2. **Annotation-based Discovery**: Use Spring's @Description annotation to identify MCP functions
3. **Health-based Availability**: Functions only available when MCP services are healthy
4. **Enhanced System Messages**: Dynamic prompt generation based on available functions
5. **Function Introspection API**: REST endpoints for operational visibility

## Options Considered

### Option 1: Hardcoded Function Lists
```java
@Configuration
public class FunctionConfig {
    @Bean
    public List<Function<?, ?>> mcpFunctions() {
        return List.of(echoFunction, pingFunction);
    }
}
```

**Pros**: Simple, explicit, compile-time safety
**Cons**: Violates DRY, manual maintenance, no health checking, inflexible

### Option 2: Manual Function Registration
```java
@Service
public class FunctionRegistry {
    private final Map<String, Function<?, ?>> functions = new HashMap<>();
    
    public void register(String name, Function<?, ?> function) {
        functions.put(name, function);
    }
}
```

**Pros**: Explicit control, flexible registration timing
**Cons**: Requires boilerplate, prone to configuration errors, no automatic discovery

### Option 3: Function Registry with Spring Context Discovery (CHOSEN)
```java
@Service
public class FunctionRegistry {
    public Map<String, Function<?, ?>> discoverFunctions() {
        return applicationContext.getBeansOfType(Function.class)
            .entrySet().stream()
            .filter(entry -> isMcpFunction(entry.getValue()))
            .collect(toMap(/*...*/));
    }
}
```

**Pros**: Automatic discovery, leverages Spring, health integration, operational visibility
**Cons**: Runtime discovery complexity, requires Spring knowledge

### Option 4: Event-Driven Function Management
```java
@Service
public class FunctionRegistry {
    @EventListener
    public void onMcpHealthChange(McpHealthChangeEvent event) {
        if (event.isHealthy()) enableFunctions();
        else disableFunctions();
    }
}
```

**Pros**: Reactive to health changes, decoupled
**Cons**: Event timing complexity, race conditions, overkill for requirements

## Decision Rationale

### Technical Factors
1. **Framework Alignment**: Leverages Spring's dependency injection and component scanning
2. **Inversion of Control**: Functions register themselves via annotations
3. **Single Responsibility**: Registry handles discovery, not execution logic
4. **Open/Closed Principle**: New functions added without modifying existing code

### Educational Factors
1. **Architectural Evolution**: Clear progression from manual to declarative patterns
2. **Best Practices**: Demonstrates proper Spring AI integration techniques
3. **Production Readiness**: Includes health monitoring and operational visibility
4. **Scalability**: Pattern scales from 2 functions to dozens across multiple servers

### Operational Factors
1. **Health Monitoring**: Functions disabled when dependencies unhealthy
2. **Graceful Degradation**: System continues operating without functions
3. **Observability**: Clear status reporting and logging
4. **API Discoverability**: Clients can query available functions

## Implementation Details

### Function Discovery Mechanism
```java
private boolean isMcpFunction(Function<?, ?> function) {
    return function.getClass().isAnnotationPresent(
        org.springframework.context.annotation.Description.class
    );
}

private String getFunctionName(String beanName) {
    // Convert EchoFunction -> echo, PingFunction -> ping
    if (beanName.endsWith("Function")) {
        return beanName.substring(0, beanName.length() - 8).toLowerCase();
    }
    return beanName.toLowerCase();
}
```

### Health-Based Availability
```java
public boolean areFunctionsAvailable() {
    return mcpClientService.isEnabled() && mcpClientService.isHealthy();
}

public String getFunctionStatus() {
    if (!mcpClientService.isEnabled()) {
        return "Functions disabled (MCP disabled)";
    }
    if (!mcpClientService.isHealthy()) {
        return "Functions unavailable (MCP unhealthy)";
    }
    int functionCount = getAvailableFunctionNames().size();
    return String.format("Functions available (%d registered)", functionCount);
}
```

### Spring AI Integration
```java
// Enhanced system messages with function awareness
if (functionRegistry.areFunctionsAvailable()) {
    List<String> functionNames = functionRegistry.getAvailableFunctionNames();
    systemMessage.append("\n\nYou have access to the following functions:");
    // Dynamic function descriptions...
}
```

## Consequences

### Positive Consequences
- ✅ **Automatic Discovery**: Functions self-register via Spring annotations
- ✅ **Health Integration**: Functions only available when dependencies healthy
- ✅ **Operational Visibility**: Clear status reporting and monitoring endpoints
- ✅ **Framework Alignment**: Proper use of Spring AI capabilities
- ✅ **Educational Value**: Clear architectural evolution demonstration
- ✅ **Scalability**: Pattern scales to multiple functions and servers

### Negative Consequences
- ❌ **Complexity Increase**: More components and interactions to understand
- ❌ **Runtime Discovery**: Function availability determined at runtime
- ❌ **Spring Dependency**: Requires understanding of Spring ApplicationContext
- ❌ **Testing Complexity**: Need real function instances for annotation detection

### Neutral Consequences
- 🔄 **Breaking Change**: Different approach from Branch 5 manual detection
- 🔄 **Framework Lock-in**: Tightly coupled to Spring AI patterns
- 🔄 **Performance Overhead**: Function discovery and health checking costs

## Monitoring and Success Criteria

### Success Metrics
- Function discovery works correctly with @Description annotations
- Health-based availability prevents calls to unhealthy services
- API endpoints provide accurate function status information
- Educational logs help understand function integration
- Tests demonstrate function registry patterns

### Monitoring Points
- Function discovery success/failure rates
- Health check performance and accuracy
- Function call success rates when available
- API endpoint response times
- Educational log clarity and usefulness

## Future Considerations

### Branch 7+: External Tool Integration
- Function registry will automatically discover external tool functions
- Health checking will extend to external service dependencies
- Status reporting will include external service health

### Production Deployment
- Consider caching function discovery results for performance
- Add metrics collection for function usage patterns
- Implement circuit breaker patterns for function health
- Monitor function discovery timing and reliability

### Alternative Implementations
- Could evolve to use Spring Cloud Function for serverless functions
- Might integrate with Spring Boot Actuator for enhanced monitoring
- Could add function versioning and compatibility checking

## Related Decisions
- [ADR-003: MCP Client Service Architecture](ADR-003-mcp-client-service-architecture.md) - Foundation for health checking
- [ADR-004: Tool Discovery and Invocation](ADR-004-tool-discovery-invocation.md) - Basic tool patterns
- [ADR-005: SSE Transport Integration](ADR-005-sse-transport-integration.md) - External server support

This decision establishes the architectural foundation for scalable function integration while maintaining educational value and operational visibility.