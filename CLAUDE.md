# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring AI demonstration project for Lecture 9 of the Spring AI Mastery course, focusing on Model Context Protocol (MCP) client implementation. The project evolves through multiple git branches, each introducing new MCP concepts incrementally.

## Build and Test Commands

```bash
# Build the project
./mvnw clean package

# Run all tests
./mvnw test

# Run a specific test class
./mvnw test -Dtest=ChatControllerTest

# Run the application
./mvnw spring-boot:run

# Run with specific profile
./mvnw spring-boot:run -Dspring.profiles.active=dev

# Skip tests during build
./mvnw clean package -DskipTests

# Manual testing
./test-examples.sh  # Requires running application
```

## Architecture

### Current Architecture (Baseline)
```
HTTP Request → ChatController → ChatService → Spring AI ChatClient → OpenAI API
```

### Target Architecture (With MCP)
```
HTTP Request → ChatController → ChatService → MCP Client → MCP Servers
                                         ↓
                                   Spring AI ChatClient → OpenAI API
```

### Key Components

- **ChatController**: REST endpoints at `/api/chat` (POST) and `/api/chat/health` (GET)
- **ChatService**: Business logic using Spring AI's ChatClient with system/user message handling
- **ChatClientConfig**: Bean configuration for Spring AI ChatClient
- **Models**: ChatRequest/ChatResponse DTOs using Lombok

## Branch Progression Strategy

The project teaches MCP concepts through 10 incremental branches:

1. **mcp-intro-baseline** - Basic chatbot without MCP (current)
2. **mcp-client-setup** - Add MCP dependencies and configuration
3. **mcp-echo-server** - Create mock server with STDIO transport
4. **mcp-tool-discovery** - Implement tool listing capability
5. **mcp-first-tool** - Basic tool invocation
6. **mcp-chat-tools** - Integrate MCP tools into chat flow
7. **mcp-sse-transport** - Switch to SSE HTTP transport
8. **mcp-brave-search** - Real Brave Search integration
9. **mcp-multi-server** - Multiple MCP server connections
10. **mcp-production-ready** - Production features (retry, monitoring)

Each branch adds 1-2 concepts with 50-150 lines of code change.

## Configuration

### Required Environment Variables
- `OPENAI_API_KEY` - Required for OpenAI API access

### Application Properties
```properties
spring.application.name=L09ClientMCP
spring.ai.openai.api-key=${OPENAI_API_KEY}
logging.level.com.coherentsolutions.l09clientmcp=DEBUG
logging.level.org.springframework.ai=DEBUG
server.port=8080
```

### Future MCP Configuration (for reference)
```yaml
spring:
  ai:
    mcp:
      client:
        enabled: true
        type: SYNC  # or ASYNC
        request-timeout: 30s
        sse:
          connections:
            server-name:
              url: http://localhost:8080
```

## Development Guidelines

### Spring AI Version Compatibility
- Using Spring AI 1.0.0 (released version)
- MCP implementation follows the new session layer (McpSession) introduced in MCP 0.8
- Use `spring-ai-starter-mcp-client` for STDIO/SSE transports
- Use `spring-ai-starter-mcp-client-webflux` for reactive implementation

### Code Style Preferences
- Constructor injection with Lombok's @RequiredArgsConstructor
- Use @Slf4j for logging
- DTOs with @Data annotation
- Service layer for business logic
- Proper error handling with try-catch blocks

### Testing Approach
- WebMvcTest for controllers with @TestConfiguration for mocks
- Use @TestConfiguration + @Primary + mock() instead of deprecated @MockBean
- Mock external dependencies (services, not Spring AI directly)
- Test both success and error scenarios
- Integration tests in later branches for MCP connections

### Modern Testing Pattern
```java
@WebMvcTest(ControllerClass.class)
class ControllerTest {
    @TestConfiguration
    static class TestConfig {
        @Bean @Primary
        public ServiceClass serviceClass() {
            return mock(ServiceClass.class);
        }
    }
}
```

## API Endpoints

### Current Endpoints
- `POST /api/chat` - Send chat message
  - Request: `{"message": "Your question here"}`
  - Response: `{"response": "AI response"}`
- `GET /api/chat/health` - Health check

### Future MCP Endpoints (planned)
- `POST /mcp/ping` - Test MCP connection
- `GET /mcp/tools` - List available MCP tools
- `POST /mcp/calculate` - Invoke calculator tool
- `POST /mcp/search` - Web search via MCP

## Testing and Demonstration

### Command Line Testing
```bash
# Quick API test suite
./test-examples.sh

# Manual health check
curl http://localhost:8080/api/chat/health

# Manual chat test
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello, AI!"}'
```

### Visual Testing with Postman
For trainers and visual demonstrations:

1. **Import Collection**: `postman/L09ClientMCP.postman_collection.json`
2. **Import Environment**: `postman/Local Development.postman_environment.json`
3. **Select Environment**: "Local Development"
4. **Run Collection**: Test all endpoints visually

**Collection includes**:
- Health check validation
- Basic chat functionality
- Technical question testing
- MCP concept exploration
- Error handling verification
- Complex scenario testing

See [Visual Testing Guide](docs/VISUAL_TESTING.md) for detailed trainer instructions.

## Common Tasks

### Adding New MCP Server Connection
1. Add server configuration to application.yml
2. Create service class for tool invocation
3. Update ChatService to use MCP tools
4. Add error handling for connection failures
5. Write integration tests

### Debugging MCP Issues
- Check DEBUG logs for MCP session initialization
- Verify server URL and transport configuration
- Test with simple ping/echo before complex tools
- Monitor WebSocket/SSE connections in browser DevTools

## Documentation Philosophy

This is an **educational project** with specific documentation requirements:

### Core Principles
- **WHY over HOW**: Always explain the reasoning behind architectural decisions
- **Trade-offs**: Document PROS and CONS of different approaches  
- **Alternatives**: Discuss what other options were considered and why they were rejected
- **Learning Objectives**: Clear goals for each implementation phase
- **Pitfalls**: Common mistakes and how to avoid them

### Documentation Requirements

**When implementing any new feature or branch:**

1. **Create branch documentation** in `docs/branches/XX-branch-name.md`:
   - Learning objectives for the branch
   - Architectural decisions made and WHY
   - Code patterns introduced with explanations
   - PROS/CONS of the chosen approach
   - Alternative solutions considered and rejected
   - Preparation for future enhancements

2. **Create ADRs** in `docs/decisions/` for significant architectural decisions:
   - Follow the standard ADR format
   - Include context, options considered, and rationale
   - Document consequences and trade-offs

3. **Update documentation index** in `docs/README.md`

### Educational Focus Areas
- **Architecture Evolution**: How each branch builds on previous concepts
- **Pattern Teaching**: Reusable design patterns and when to use them
- **Decision Rationale**: Why specific technologies/approaches were chosen
- **Common Pitfalls**: What mistakes learners typically make and how to avoid them
- **Future Evolution**: How current decisions prepare for upcoming features

### Code Documentation Standards
- Comments should explain WHY, not WHAT
- Include decision rationale in code when non-obvious
- Demonstrate patterns that students can reuse
- Show evolution path in comments when preparing for future features

## Course Context

This project is part of a teaching progression:
- Lectures 1-8: Basic Spring AI concepts (completed)
- Lecture 9: MCP Client implementation (this project)
- Lecture 10: MCP Server implementation
- Lecture 11: Final project combining voice, RAG, and MCP

The goal is to demonstrate how AI applications evolve from isolated tools to interconnected ecosystems using standardized protocols.

## Postman Collection Management

- **Workflow Recommendation**: Do not update L09ClientMCP.postman_collection.json rather create new document for each new branch, and I'll move from collection to collection in Postman while I move from branch to branch in the project