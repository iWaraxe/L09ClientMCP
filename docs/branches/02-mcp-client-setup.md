# Branch 02: MCP Client Setup

## Overview

This branch introduces the foundational Model Context Protocol (MCP) client infrastructure to our Spring AI application. The implementation focuses on **configuration and architectural preparation** rather than actual MCP server communication, setting the stage for future MCP tool integration.

## Learning Objectives

1. **Understand MCP Purpose**: Why AI applications need standardized external tool access
2. **Infrastructure Setup**: How to configure MCP client dependencies and profiles  
3. **Architecture Evolution**: Transition from isolated AI to MCP-ready ecosystem
4. **Graceful Degradation**: How to handle MCP-enabled vs MCP-disabled scenarios
5. **Configuration Patterns**: Spring profiles for different MCP transport types

## Architecture Changes

### Before (Branch 1 - Baseline)
```
HTTP Request → ChatController → ChatService → ChatClient → OpenAI API
```

### After (Branch 2 - MCP Setup)
```
HTTP Request → ChatController → ChatService → McpClientService → [MCP Infrastructure]
                                         ↓
                                   ChatClient → OpenAI API
```

### Key Architectural Decisions

#### 1. Service Layer Abstraction
**Decision**: Create `McpClientService` interface with implementation
**WHY**: 
- ✅ **Testability**: Easy to mock for unit testing
- ✅ **Future Flexibility**: Can swap implementations (sync/async, different transports)
- ✅ **Graceful Degradation**: Single point to handle MCP enabled/disabled states
- ✅ **Single Responsibility**: Separates MCP concerns from chat logic

**Alternative Considered**: Direct MCP client injection into ChatService
**Why Rejected**: Tightly couples chat logic to MCP implementation details

#### 2. Configuration-Driven Behavior
**Decision**: Use Spring profiles for different MCP configurations
**WHY**:
- ✅ **Environment Flexibility**: Different setups for dev/test/prod
- ✅ **Transport Options**: Easy switching between STDIO/SSE
- ✅ **Educational Value**: Students can see different MCP scenarios
- ✅ **Backward Compatibility**: Default profile keeps MCP disabled

**Profiles Created**:
- `default`: MCP disabled (backward compatibility)
- `mcp-disabled`: Explicitly disabled MCP
- `mcp-stdio`: STDIO transport for development
- `mcp-sse`: SSE transport for production

#### 3. Health Check Integration
**Decision**: Integrate MCP status into existing health endpoint
**WHY**:
- ✅ **Unified Monitoring**: Single endpoint for all system health
- ✅ **Graceful Degradation**: Returns healthy when MCP is disabled
- ✅ **Debugging Support**: Provides MCP status information
- ✅ **Production Ready**: Essential for monitoring MCP-enabled deployments

## Implementation Details

### Code Changes Summary

| Component | Lines Added | Purpose |
|-----------|-------------|---------|
| `McpClientService.java` | 30 | Interface defining MCP operations |
| `McpClientServiceImpl.java` | 80 | Implementation with config handling |
| `McpConfig.java` | 25 | Conditional MCP configuration |
| Profile configurations | 45 | STDIO/SSE/disabled profiles |
| Test updates | 40 | Modern testing patterns |
| **Total** | **~220 lines** | **Foundation for MCP integration** |

### Key Features Implemented

#### 1. **Configuration Management**
```yaml
spring:
  ai:
    mcp:
      client:
        enabled: false  # Default disabled
        request-timeout: 30s
        type: STDIO     # or SSE
```

#### 2. **Service Interface**
```java
public interface McpClientService {
    boolean isEnabled();     // Configuration check
    boolean isHealthy();     // Connection health
    String getStatus();      // Human-readable status
}
```

#### 3. **Graceful Degradation**
- When MCP disabled: Returns healthy status, clear messaging
- When MCP enabled: Prepares for future server connections
- Error handling: Robust timeout parsing, fallback defaults

## Educational Demonstrations

### 1. **Configuration Flexibility** (2 minutes)
```bash
# Default mode (MCP disabled)
./mvnw spring-boot:run

# STDIO development mode
./mvnw spring-boot:run -Dspring.profiles.active=mcp-stdio

# SSE production mode  
./mvnw spring-boot:run -Dspring.profiles.active=mcp-sse
```

**Teaching Point**: Show how same codebase adapts to different environments

### 2. **Health Monitoring** (1 minute)
```bash
curl http://localhost:8080/api/chat/health
```

**Expected Output**: 
- Default: `"Chat service is running. MCP: healthy (MCP disabled - running in baseline mode)"`
- MCP-enabled: `"Chat service is running. MCP: healthy (MCP enabled - Type: STDIO, Timeout: 30s, Servers: none configured)"`

**Teaching Point**: Demonstrates infrastructure awareness and monitoring

### 3. **AI Limitation Motivation** (2 minutes)
Ask the AI: *"What's the current weather in New York?"*
**Expected Response**: AI acknowledges it cannot access real-time data
**Teaching Point**: Sets up the need for MCP tool integration in future branches

## Testing Strategy

### 1. **Unit Testing**
- **McpClientServiceTest**: Tests all configuration scenarios
- **Timeout parsing**: Various format handling (30s, 2m, invalid)
- **State management**: Enabled/disabled behavior

### 2. **Integration Testing**
- **ChatControllerTest**: Updated with MCP service mocking
- **Health endpoint**: Verifies MCP status integration
- **Modern patterns**: Uses `@TestConfiguration` instead of deprecated `@MockBean`

### 3. **Configuration Testing**
- **Profile validation**: Each profile loads correctly
- **Conditional beans**: McpConfig only loads when enabled
- **Graceful fallback**: Default configuration works

## Common Pitfalls & Solutions

### ❌ **Pitfall 1**: Tight Coupling
**Mistake**: Injecting MCP client directly into ChatService
**Solution**: Use service layer abstraction for flexibility

### ❌ **Pitfall 2**: Hard-Coded Configuration
**Mistake**: Fixed MCP settings in code
**Solution**: Use Spring profiles and property-driven configuration

### ❌ **Pitfall 3**: All-or-Nothing Approach
**Mistake**: Application fails when MCP is unavailable
**Solution**: Graceful degradation with health checks

### ❌ **Pitfall 4**: Testing Complexity
**Mistake**: Using deprecated `@MockBean`
**Solution**: Modern `@TestConfiguration` with `@Primary` mocks

## Preparation for Future Branches

This branch establishes the foundation for:

### Branch 3: Echo Server
- **Ready**: Service interface for tool invocation
- **Ready**: Configuration profiles for STDIO transport
- **Ready**: Health monitoring for connection status

### Branch 4: Tool Discovery
- **Ready**: Service abstraction for tool management
- **Ready**: Error handling patterns
- **Ready**: Testing infrastructure

### Branch 5: First Tool Invocation
- **Ready**: Configuration-driven behavior
- **Ready**: Integration points in ChatService
- **Ready**: Health monitoring and debugging

## Production Considerations

### **Performance**
- Minimal overhead when MCP disabled
- Configurable timeouts for different environments
- Async-ready architecture (for future branches)

### **Monitoring**
- Health endpoint integration
- Structured logging for debugging
- Status reporting for operations teams

### **Security**
- No external connections in this branch
- Configuration validation and defaults
- Error handling without information leakage

## Summary

Branch 2 successfully establishes MCP client infrastructure with:
- ✅ **Zero Breaking Changes**: Backward compatible with branch 1
- ✅ **Configuration Flexibility**: Multiple deployment scenarios
- ✅ **Testing Foundation**: Modern patterns and comprehensive coverage
- ✅ **Production Ready**: Health monitoring and error handling
- ✅ **Educational Value**: Clear demonstration of architecture evolution

**Next**: Branch 3 will implement a mock echo server to demonstrate actual MCP communication patterns.