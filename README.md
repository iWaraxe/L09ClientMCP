# MCP Client Application - Lecture 9

## Overview

This project demonstrates the evolution of a Spring AI application from a simple chatbot to a sophisticated MCP (Model Context Protocol) client. The application showcases how to integrate distributed AI capabilities using Spring AI's MCP support.

## Why Model Context Protocol (MCP)?

### Problems Solved by MCP

1. **Tool Fragmentation**: Before MCP, each AI application had to implement its own tools and integrations, leading to duplicated effort across teams.

2. **Vendor Lock-in**: Function calling was specific to each AI provider (OpenAI, Anthropic, etc.), making it difficult to switch providers or use multiple providers.

3. **Limited Scalability**: Tools were confined within application boundaries, preventing sharing of capabilities across services.

4. **Protocol Inconsistency**: No standard way for AI applications to discover and interact with external tools and data sources.

### MCP Solution

MCP provides a **transport-agnostic protocol** that standardizes how AI applications:
- Discover available tools and resources
- Invoke remote capabilities
- Handle responses consistently
- Manage connections and sessions

Think of MCP as the "HTTP for AI tools" - a universal protocol that any AI system can use to access capabilities anywhere.

## Project Evolution

This project evolves through three branches, each building upon the previous:

### Branch: `mcp-intro-baseline` (Current)
- Basic Spring Boot chatbot using OpenAI
- No MCP integration yet
- Establishes the foundation for enhancement

### Branch: `mcp-client-stdio`
- Adds Spring AI MCP client starter
- Implements STDIO transport for local tool servers
- Demonstrates basic MCP client configuration

### Branch: `mcp-client-sse-webflux`
- Switches to WebFlux for reactive programming
- Implements SSE (Server-Sent Events) transport
- Shows cloud-ready MCP deployment patterns

## Architecture

### Current Architecture (Baseline)
```
User → REST API → ChatController → ChatService → OpenAI API
```

### Target Architecture (With MCP)
```
User → REST API → ChatController → ChatService → MCP Client → MCP Servers
                                           ↓
                                      OpenAI API
```

## Key Components

### 1. ChatController
- REST endpoint for chat interactions
- Health check endpoint
- Request/response handling

### 2. ChatService
- Core chat logic
- OpenAI integration via Spring AI
- Error handling and logging

### 3. Configuration
- Spring AI ChatClient configuration
- OpenAI API key management
- Future: MCP client configuration

## Getting Started

### Prerequisites
- Java 21
- Maven 3.8+
- OpenAI API key

### Setup
1. Clone the repository
2. Set your OpenAI API key:
   ```bash
   export OPENAI_API_KEY=your-api-key-here
   ```
3. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

### Testing the Chatbot
```bash
# Health check
curl http://localhost:8080/api/chat/health

# Send a chat message
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello, AI!"}'
```

## Spring AI 1.0.0 Updates

With the release of Spring AI 1.0.0, several important changes affect MCP integration:

1. **New Session Layer**: MCP 0.8 introduced breaking changes with a new session management layer (`McpSession`)
2. **Improved Client Types**: Clear distinction between SYNC and ASYNC client types
3. **Enhanced Configuration**: More granular `spring.ai.mcp.client.*` properties
4. **Better Tool Discovery**: Automatic tool registration and discovery mechanisms

## Next Steps

After mastering the baseline chatbot, you'll learn to:
1. Configure MCP clients for different transports
2. Connect to external MCP servers
3. Invoke remote tools seamlessly
4. Handle MCP-specific concerns (timeouts, retries, session management)

## Resources

- [Spring AI MCP Documentation](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html)
- [MCP Specification](https://modelcontextprotocol.io/)
- [Spring AI Examples](https://github.com/spring-projects/spring-ai-examples/tree/main/model-context-protocol)

## Course Context

This application is part of Lecture 9 in the Spring AI Mastery course, focusing on building MCP clients. It serves as a foundation for understanding how modern AI applications evolve from isolated tools to interconnected ecosystems through standardized protocols.