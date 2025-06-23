# ADR-002: MCP Transport Selection Strategy

## Status
Accepted

## Context

The Model Context Protocol (MCP) supports multiple transport mechanisms for client-server communication. We need to choose the appropriate transport(s) for our educational Spring AI application, considering both learning progression and production readiness.

## Available Transport Options

### 1. STDIO Transport
**Mechanism**: Standard input/output pipes
**Use Case**: Process-based MCP servers, local development

**Pros**:
- ✅ Simple to implement and debug
- ✅ Perfect for educational demonstrations
- ✅ Low latency for local processes
- ✅ Built-in process lifecycle management
- ✅ Great for development and testing

**Cons**:
- ❌ Limited to local processes only
- ❌ Not suitable for distributed systems
- ❌ Process management complexity
- ❌ Limited scalability

### 2. SSE (Server-Sent Events) Transport
**Mechanism**: HTTP-based with Server-Sent Events
**Use Case**: Web-based MCP servers, production deployments

**Pros**:
- ✅ HTTP-based, firewall-friendly
- ✅ Production-ready and scalable
- ✅ Works across network boundaries
- ✅ Standard web technologies
- ✅ Better for microservices architecture

**Cons**:
- ❌ More complex to set up initially
- ❌ Network latency considerations
- ❌ Requires HTTP server infrastructure
- ❌ Less intuitive for beginners

### 3. WebSocket Transport
**Mechanism**: Full-duplex WebSocket connections
**Use Case**: Real-time, bidirectional communication

**Pros**:
- ✅ Full-duplex communication
- ✅ Real-time capabilities
- ✅ Efficient for high-frequency operations

**Cons**:
- ❌ More complex connection management
- ❌ Not yet widely supported in MCP ecosystem
- ❌ Overkill for request-response patterns

## Decision

We will implement **both STDIO and SSE transports** with a **progressive approach**:

### Phase 1: STDIO First (Branches 2-6)
- Start with STDIO transport for educational clarity
- Use for mock servers and development examples
- Demonstrate local MCP server integration

### Phase 2: SSE Second (Branches 7-10)
- Transition to SSE for production-ready examples
- Show network-based MCP server integration
- Demonstrate real-world deployment patterns

### Configuration Strategy
Use Spring profiles to support both transports:
```yaml
# Profile: mcp-stdio (development)
spring.ai.mcp.client.type: STDIO

# Profile: mcp-sse (production)  
spring.ai.mcp.client.type: SSE
```

## Rationale

### Educational Progression
1. **STDIO First**: Easier to understand, debug, and demonstrate
2. **SSE Second**: Shows production evolution and scalability
3. **Both Options**: Students see full spectrum of MCP capabilities

### Technical Benefits
1. **Flexibility**: Support different deployment scenarios
2. **Learning Path**: Clear progression from simple to production-ready
3. **Future-Proof**: Easy to add WebSocket support later

### Implementation Benefits
1. **Profile-Based**: Easy switching between transports
2. **Backward Compatible**: Default remains MCP-disabled
3. **Testing-Friendly**: Can test both transports independently

## Consequences

### Positive
- ✅ **Complete Learning Experience**: Students see both local and network MCP
- ✅ **Production Readiness**: SSE support enables real deployments
- ✅ **Educational Value**: Clear progression from simple to advanced
- ✅ **Flexibility**: Choose transport based on requirements

### Negative
- ❌ **Increased Complexity**: Must maintain two transport implementations
- ❌ **Testing Overhead**: Need test coverage for both transports
- ❌ **Documentation**: Must explain when to use each transport

### Mitigation Strategies
1. **Clear Documentation**: Explain use cases for each transport
2. **Profile Naming**: Use descriptive profile names (mcp-stdio, mcp-sse)
3. **Examples**: Provide working examples for both transports
4. **Testing**: Comprehensive test coverage for both paths

## Implementation Timeline

### Branch 2: MCP Client Setup
- ✅ Configuration structure for both transports
- ✅ Profile definitions (stdio, sse, disabled)
- ✅ Service abstraction ready for both

### Branch 3-6: STDIO Phase
- Implement STDIO-based MCP servers
- Echo server, calculator, mock tools
- Local development patterns

### Branch 7-10: SSE Phase  
- Implement SSE-based MCP servers
- Web search, real APIs, production patterns
- Network deployment examples

## References
- [MCP Transport Specification](https://spec.modelcontextprotocol.io/specification/basic/transports/)
- [Spring AI MCP Client Documentation](https://docs.spring.io/spring-ai/reference/api/mcp.html)
- [Educational Progression Strategy](../branches/01-baseline.md#future-branches)