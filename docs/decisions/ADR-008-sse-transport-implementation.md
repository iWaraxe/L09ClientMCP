# ADR-008: SSE Transport Implementation for External MCP Servers

## Status
Accepted

## Context

Branch 7 requires transitioning from local mock server simulation to real external MCP server communication. The Model Context Protocol specification supports multiple transport layers including STDIO, SSE (Server-Sent Events), and WebSocket. We need to select and implement a transport mechanism that:

1. **Educational Requirements**:
   - Demonstrates real network communication patterns
   - Shows production-ready external service integration
   - Teaches distributed system concepts progressively
   - Maintains compatibility with existing Spring AI function integration

2. **Technical Requirements**:
   - Real-time communication capability for MCP protocol
   - HTTP-based for web accessibility and debugging
   - Support for tool discovery and invocation patterns
   - Graceful error handling and connection management

3. **Implementation Constraints**:
   - Must integrate cleanly with Spring Boot application lifecycle
   - Should not disrupt existing ChatService and Function patterns
   - Needs to support configuration-driven server connections
   - Must provide clear migration path from mock implementation

## Decision

We will implement **SSE (Server-Sent Events) transport** as the communication layer for external MCP server integration.

### Architecture Decision

```
Spring Application                    External MCP Server
      │                                     │
      ├─ McpSseClientService               ├─ Node.js Express Server
      │  ├─ Configuration management       │  ├─ SSE endpoint (/sse)
      │  ├─ Connection lifecycle           │  ├─ Tool discovery (/tools)
      │  └─ Health monitoring              │  └─ Tool invocation (/tools/{name})
      │                                    │
      ├─ McpSseClient                      ├─ HTTP API Endpoints
      │  ├─ SSE connection handling        │  ├─ JSON request/response
      │  ├─ HTTP tool invocation           │  ├─ CORS support
      │  └─ Async request correlation      │  └─ Error handling
      │                                    │
      └─ SseConnection                     └─ SSE Event Stream
         ├─ WebFlux reactive client            ├─ Connection events
         ├─ Retry logic                        ├─ Response correlation
         └─ Error handling                     └─ Health updates
```

## Options Considered

### Option 1: SSE (Server-Sent Events) Transport ✅ **SELECTED**

**Implementation Approach**:
- HTTP-based SSE connection for real-time updates
- Standard HTTP endpoints for tool discovery and invocation
- Spring WebFlux reactive client for SSE handling
- External Node.js server providing MCP protocol implementation

**PROS**:
- **Educational Clarity**: HTTP-based protocol easy to understand and debug
- **Real-time Capability**: SSE provides real-time updates while maintaining HTTP semantics
- **Web Accessibility**: Standard HTTP makes server accessible from browsers and tools
- **Debugging Friendly**: HTTP requests visible in network tools and logs
- **Spring Integration**: Excellent Spring WebFlux support for reactive SSE clients
- **Production Relevance**: SSE commonly used in production web applications

**CONS**:
- **Unidirectional**: SSE is client-to-server for requests, server-to-client for events
- **Browser Limitations**: Connection limits in browser environments (not relevant for our use case)
- **Complexity**: More complex than pure REST, simpler than WebSocket

### Option 2: WebSocket Transport

**Implementation Approach**:
- Full bidirectional WebSocket communication
- Custom protocol for tool discovery and invocation
- WebSocket connection lifecycle management

**PROS**:
- **Full Bidirectional**: True real-time bidirectional communication
- **Lower Latency**: More efficient for frequent message exchange
- **Protocol Flexibility**: Custom message format possible

**CONS**:
- **Educational Complexity**: WebSocket lifecycle more complex to understand
- **Debugging Difficulty**: Binary/custom protocols harder to debug
- **Overkill**: Request/response pattern doesn't require full bidirectional communication
- **Spring Integration**: More complex Spring WebSocket configuration
- **Network Issues**: More sensitive to network proxy and firewall configurations

### Option 3: Pure REST/HTTP

**Implementation Approach**:
- Standard REST endpoints for all operations
- Polling for status updates if needed
- No real-time communication

**PROS**:
- **Simplicity**: Easiest to implement and understand
- **Standard Patterns**: Well-known REST semantics
- **Debugging**: Simple HTTP requests and responses
- **Tooling**: Excellent tooling support (Postman, curl, etc.)

**CONS**:
- **No Real-time**: Missing real-time capabilities of MCP protocol
- **Educational Gap**: Doesn't demonstrate real-time communication patterns
- **Protocol Mismatch**: MCP specification expects real-time transport layer
- **Polling Overhead**: Status updates require inefficient polling

### Option 4: gRPC Transport

**Implementation Approach**:
- gRPC service definition for MCP protocol
- HTTP/2-based binary communication
- Strong typing with Protocol Buffers

**PROS**:
- **Performance**: High-performance binary protocol
- **Type Safety**: Strong schema validation
- **Tooling**: Excellent development tooling

**CONS**:
- **Educational Complexity**: gRPC adds significant complexity for educational project
- **Dependencies**: Additional build tooling and dependencies required
- **Debugging Difficulty**: Binary protocol harder to debug and understand
- **Web Accessibility**: Limited browser support without gRPC-Web proxy

## Implementation Strategy

### Phase 1: SSE Client Implementation

1. **Create McpSseClientService** with conditional loading based on configuration
2. **Implement McpSseClient** for low-level SSE and HTTP communication
3. **Add SseConnection** for reactive SSE connection management
4. **Configure WebFlux dependency** for reactive HTTP client capabilities

### Phase 2: External Server Development

1. **Create Node.js Express server** with SSE endpoint
2. **Implement tool discovery API** with JSON responses
3. **Add tool invocation endpoints** for echo and ping tools
4. **Include health monitoring** and CORS support

### Phase 3: Integration and Testing

1. **Configure Spring application** to use SSE transport
2. **Test end-to-end tool integration** with external server
3. **Validate error handling** and connection management
4. **Document setup and troubleshooting** procedures

### Configuration Design

```yaml
spring:
  ai:
    mcp:
      client:
        enabled: true
        type: SYNC  # Selects SSE transport implementation
        sse:
          connections:
            echo-server:
              url: "http://localhost:3000/sse"
              enabled: true
              max-retries: 3
              retry-delay: 5s
```

### Service Architecture

```java
// Conditional service loading
@Service
@ConditionalOnProperty(value = "spring.ai.mcp.client.type", havingValue = "SYNC")
public class McpSseClientService implements McpClientService {
    // SSE-based implementation
}

@Service  
@ConditionalOnProperty(value = "spring.ai.mcp.client.type", havingValue = "STDIO", matchIfMissing = true)
public class McpClientServiceImpl implements McpClientService {
    // Mock-based implementation (Branch 6 and earlier)
}
```

## Consequences

### Positive Consequences

1. **Real Network Communication**: Students learn actual distributed system patterns with network latency, failures, and retry logic
2. **Production Readiness**: SSE is commonly used in production applications, providing relevant experience
3. **Educational Progression**: Natural evolution from mock to real external service integration
4. **Transport Abstraction**: Demonstrates how proper abstraction allows transport layer changes without affecting business logic
5. **Real-time Learning**: Students experience real-time communication patterns relevant to modern applications
6. **Debugging Skills**: HTTP-based protocol teaches network debugging and monitoring techniques

### Negative Consequences

1. **Increased Complexity**: More moving parts requiring coordination between Spring app and external server
2. **Development Environment**: Requires running external Node.js server alongside Spring application
3. **Error Surface**: Network communication introduces new failure modes and error handling requirements
4. **Testing Complexity**: Integration tests require external server or sophisticated mocking
5. **Configuration Management**: More complex configuration with server URLs, retry policies, and timeouts

### Mitigation Strategies

1. **Clear Documentation**: Comprehensive setup instructions and troubleshooting guides
2. **Graceful Degradation**: Fallback to mock implementation if external server unavailable
3. **Health Monitoring**: Robust health checks and status reporting for connection management
4. **Error Handling**: Comprehensive error handling with meaningful error messages
5. **Testing Strategy**: Maintain ability to test with mock implementation while adding integration tests

### Future Implications

1. **Multiple Server Support**: Architecture prepares for connecting to multiple external MCP servers
2. **Transport Flexibility**: Clean abstraction allows future addition of WebSocket or other transports
3. **Production Patterns**: Establishes patterns needed for real production MCP deployments
4. **Monitoring Foundation**: Connection management patterns support future observability features

## Implementation Notes

### Key Components

- **McpSseClientService**: Main service implementation with configuration management
- **McpSseClient**: Core SSE client with HTTP communication
- **SseConnection**: Low-level SSE connection handling with retry logic
- **External MCP Server**: Node.js server providing realistic external service

### Dependencies Added

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

### Configuration Properties

- `spring.ai.mcp.client.type`: Selects transport implementation
- `spring.ai.mcp.client.sse.connections.*`: Server-specific connection configuration
- Retry policies, timeouts, and health check intervals

This SSE transport implementation provides the optimal balance of educational value, technical relevance, and implementation complexity for demonstrating real external MCP server integration while maintaining clean architecture patterns established in previous branches.