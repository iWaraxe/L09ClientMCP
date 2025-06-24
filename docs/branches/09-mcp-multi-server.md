# Branch 9: Multi-Server MCP Connections

## Learning Objectives

By the end of this branch, students will understand:

- **Multi-server architecture patterns** for scalable MCP implementations
- **Load balancing strategies** including round-robin and weighted distribution
- **Circuit breaker pattern** for resilient service communication
- **Health monitoring and automatic failover** mechanisms
- **Configuration-driven server management** with type-safe properties
- **Centralized server registry** for connection lifecycle management

## Overview

Branch 9 represents a significant architectural evolution from single-server MCP connections to a sophisticated multi-server ecosystem. This branch introduces enterprise-grade patterns for managing multiple MCP servers with intelligent load balancing, health monitoring, and failure recovery.

### Architectural Evolution

**Before (Single Server):**
```
ChatService → McpSseClientService → Single MCP Server
```

**After (Multi-Server):**
```
ChatService → MultiServerMcpClientService → ServerSelector → Multiple MCP Servers
                                        → McpServerRegistry
                                        → McpHealthMonitor
                                        → McpCircuitBreaker
```

## Key Components Implemented

### 1. McpServerRegistry

**Purpose:** Centralized registry for managing multiple MCP server connections.

**Key Features:**
- Thread-safe server registration and discovery
- Tool-to-server mapping for intelligent routing
- Health status tracking per server
- Concurrent access patterns with ConcurrentHashMap
- Automatic cleanup and disconnection

```java
@Component
public class McpServerRegistry {
    private final Map<String, McpServerConnection> servers = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> toolToServers = new ConcurrentHashMap<>();
    private final Map<String, ServerHealth> serverHealth = new ConcurrentHashMap<>();
    
    public void registerServer(String serverId, McpServerConnection connection) {
        // Thread-safe registration with tool mapping
    }
    
    public List<McpServerConnection> getHealthyServersForTool(String toolName) {
        // Returns only healthy servers that provide the requested tool
    }
}
```

**Educational Value:**
- Demonstrates registry pattern for service discovery
- Shows thread-safe collection management
- Illustrates health tracking integration

### 2. ServerSelector Interface & Implementations

**Purpose:** Strategy pattern for different load balancing algorithms.

#### RoundRobinServerSelector
- **Algorithm:** Distributes requests evenly across available servers
- **State Management:** Per-tool counters using AtomicInteger
- **Thread Safety:** Concurrent access with atomic operations
- **Statistics:** Tracks selection counts and timing

```java
@Component
public class RoundRobinServerSelector implements ServerSelector {
    private final Map<String, AtomicInteger> toolCounters = new ConcurrentHashMap<>();
    
    public Optional<McpServerConnection> selectServer(String toolName, 
            List<McpServerConnection> candidates, SelectionContext context) {
        AtomicInteger counter = toolCounters.computeIfAbsent(toolName, k -> new AtomicInteger(0));
        int index = Math.abs(counter.getAndIncrement()) % candidates.size();
        return Optional.of(candidates.get(index));
    }
}
```

#### WeightedServerSelector
- **Algorithm:** Selects servers based on configured weights and performance metrics
- **Dynamic Weighting:** Considers response times and failure rates
- **Performance Optimization:** Prefers faster, more reliable servers
- **Adaptive Behavior:** Adjusts weights based on real-time performance

**Educational Value:**
- Strategy pattern implementation
- Load balancing algorithm comparison
- Performance-based server selection
- Atomic operations for thread safety

### 3. McpCircuitBreaker

**Purpose:** Implements circuit breaker pattern for fault tolerance.

**States:**
- **CLOSED:** Normal operation, requests pass through
- **OPEN:** Failure threshold exceeded, requests blocked
- **HALF_OPEN:** Testing if service has recovered

```java
public class McpCircuitBreaker {
    public enum State { CLOSED, OPEN, HALF_OPEN }
    
    public <T> T executeWithCircuitBreaker(String serverId, Supplier<T> operation) 
            throws CircuitBreakerOpenException {
        CircuitBreakerState state = getOrCreateState(serverId);
        
        if (shouldRejectCall(state, config)) {
            throw new CircuitBreakerOpenException("Circuit breaker is open for server: " + serverId);
        }
        
        try {
            T result = operation.get();
            recordSuccess(state);
            return result;
        } catch (Exception e) {
            recordFailure(state, e);
            throw e;
        }
    }
}
```

**Configuration:**
```yaml
spring:
  ai:
    mcp:
      multi-server:
        circuit-breaker:
          failure-threshold: 5
          timeout: 60s
          failure-rate-threshold: 0.5
```

**Educational Value:**
- Circuit breaker pattern implementation
- Fault tolerance and resilience patterns
- State machine design
- Configuration-driven behavior

### 4. McpHealthMonitor

**Purpose:** Scheduled health checking with automatic failover.

**Features:**
- Configurable health check intervals
- Automatic server marking (healthy/unhealthy)
- Integration with circuit breaker
- Performance metrics collection

```java
@Component
public class McpHealthMonitor {
    @Scheduled(fixedRateString = "${spring.ai.mcp.multi-server.health-check.interval:30000}")
    public void performHealthChecks() {
        Set<String> serverIds = serverRegistry.getAllServerIds();
        
        for (String serverId : serverIds) {
            CompletableFuture.runAsync(() -> checkServerHealth(serverId), healthCheckExecutor)
                .orTimeout(healthCheckTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(throwable -> {
                    handleHealthCheckFailure(serverId, throwable);
                    return null;
                });
        }
    }
}
```

**Educational Value:**
- Scheduled task implementation with Spring
- Asynchronous health checking
- Timeout handling and error recovery
- Integration with service registry

### 5. MultiServerMcpClientService

**Purpose:** Main orchestrator for multi-server MCP operations.

**Responsibilities:**
- Server initialization and configuration
- Load balancing strategy selection
- Tool routing and execution
- Error handling and failover
- Integration with all supporting components

```java
@Service
@ConditionalOnProperty(name = "spring.ai.mcp.multi-server.enabled", havingValue = "true")
public class MultiServerMcpClientService implements McpClientService {
    
    @PostConstruct
    public void initialize() {
        activeSelector = selectLoadBalancingStrategy();
        initializeServers();
        configureCircuitBreakers();
    }
    
    @Override
    public McpToolResult invokeTool(String toolName, Map<String, Object> arguments) {
        List<McpServerConnection> candidates = serverRegistry.getHealthyServersForTool(toolName);
        
        if (candidates.isEmpty()) {
            throw new RuntimeException("No healthy servers available for tool: " + toolName);
        }
        
        Optional<McpServerConnection> selectedServer = activeSelector.selectServer(
            toolName, candidates, SelectionContext.create(generateRequestId())
        );
        
        return selectedServer.map(server -> {
            try {
                return circuitBreaker.executeWithCircuitBreaker(
                    server.getServerId(),
                    () -> server.invokeTool(toolName, arguments)
                );
            } catch (CircuitBreakerOpenException e) {
                // Attempt fallback to another server
                return attemptFallback(toolName, arguments, candidates, server);
            }
        }).orElseThrow(() -> new RuntimeException("No server selected for tool: " + toolName));
    }
}
```

**Educational Value:**
- Service orchestration patterns
- Integration of multiple architectural patterns
- Error handling and recovery strategies
- Conditional bean creation

## Configuration Management

### Type-Safe Configuration

```java
@ConfigurationProperties(prefix = "spring.ai.mcp.multi-server")
@Data
@Validated
public class MultiServerConfig {
    
    @NotNull
    private Boolean enabled = false;
    
    @NotNull
    private LoadBalancingStrategy loadBalancingStrategy = LoadBalancingStrategy.ROUND_ROBIN;
    
    @NotEmpty
    @Valid
    private Map<String, ServerConfig> servers = new HashMap<>();
    
    @Valid
    private HealthCheckConfig healthCheck = new HealthCheckConfig();
    
    @Valid
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();
    
    public enum LoadBalancingStrategy {
        ROUND_ROBIN, WEIGHTED, LEAST_CONNECTIONS
    }
}
```

### Application Configuration

```yaml
spring:
  ai:
    mcp:
      multi-server:
        enabled: true
        load-balancing-strategy: ROUND_ROBIN
        servers:
          mock-server-1:
            url: "mock://server1"
            type: MOCK
            tools: ["echo", "ping"]
            weight: 1
            enabled: true
          mock-server-2:
            url: "mock://server2"
            type: MOCK
            tools: ["echo", "ping"]
            weight: 2
            enabled: true
          brave-search-server:
            url: "http://localhost:3000/sse"
            type: SSE
            tools: ["search"]
            weight: 1
            enabled: true
        health-check:
          interval: 30s
          timeout: 5s
          retry-attempts: 3
        circuit-breaker:
          failure-threshold: 5
          timeout: 60s
          failure-rate-threshold: 0.5
```

## Testing Strategy

### Unit Tests

**McpServerRegistryTest:**
- Server registration and unregistration
- Tool-to-server mapping
- Health status management
- Concurrent access patterns
- Statistics collection

**RoundRobinServerSelectorTest:**
- Load balancing distribution
- Per-tool counter management
- Selection statistics
- Thread safety validation

**McpCircuitBreakerTest:**
- State transitions (CLOSED → OPEN → HALF_OPEN)
- Failure threshold behavior
- Recovery timing
- Configuration validation

### Integration Tests

**MultiServerIntegrationTest:**
- End-to-end multi-server workflow
- Load balancing verification
- Health monitoring integration
- Circuit breaker behavior
- Configuration property binding

## Key Architectural Decisions

### 1. Registry Pattern for Server Management

**Decision:** Implement a centralized `McpServerRegistry` for managing all server connections.

**Rationale:**
- **Single Source of Truth:** All server information centralized
- **Thread Safety:** Concurrent access patterns built-in
- **Tool Discovery:** Efficient tool-to-server mapping
- **Health Integration:** Unified health status management

**Alternatives Considered:**
- **Distributed Registry:** Too complex for this scope
- **Direct Service Injection:** Doesn't scale with server count
- **Static Configuration:** No runtime flexibility

**Trade-offs:**
- **PROS:** Centralized management, efficient lookup, extensible
- **CONS:** Single point of failure, memory overhead for large deployments

### 2. Strategy Pattern for Load Balancing

**Decision:** Use Strategy pattern with pluggable load balancing algorithms.

**Rationale:**
- **Flexibility:** Easy to add new load balancing strategies
- **Testability:** Each strategy can be tested independently
- **Runtime Selection:** Strategy can be chosen via configuration
- **Performance Optimization:** Different strategies for different use cases

**Alternatives Considered:**
- **Hard-coded Strategy:** Not flexible enough
- **Enum-based Selection:** Less extensible
- **External Load Balancer:** Adds infrastructure complexity

**Trade-offs:**
- **PROS:** Flexible, extensible, testable, performant
- **CONS:** Increased complexity, more classes to maintain

### 3. Circuit Breaker for Fault Tolerance

**Decision:** Implement circuit breaker pattern for each server connection.

**Rationale:**
- **Fault Isolation:** Prevents cascade failures
- **Fast Failure:** Avoids hanging requests to failed servers
- **Automatic Recovery:** Tests service recovery automatically
- **Resource Protection:** Prevents resource exhaustion

**Alternatives Considered:**
- **Simple Retry:** Doesn't prevent cascade failures
- **Timeout Only:** Doesn't provide fast failure
- **Manual Failover:** Not scalable

**Trade-offs:**
- **PROS:** Excellent fault tolerance, automatic recovery, protects resources
- **CONS:** Added complexity, potential for false positives

### 4. Scheduled Health Monitoring

**Decision:** Use Spring's `@Scheduled` for automatic health checking.

**Rationale:**
- **Proactive Detection:** Finds failures before they affect users
- **Automatic Failover:** Integrates with circuit breaker and registry
- **Configurable Frequency:** Balance between detection speed and overhead
- **Asynchronous Execution:** Doesn't block main application threads

**Alternatives Considered:**
- **On-demand Health Checks:** Only during requests
- **External Health Monitor:** Adds infrastructure dependency
- **Passive Health Checks:** Only detects failures during usage

**Trade-offs:**
- **PROS:** Proactive, configurable, integrates well
- **CONS:** Background resource usage, potential false positives

## Performance Considerations

### Memory Usage

- **Registry Size:** O(servers + tools) memory overhead
- **Statistics Collection:** Bounded by configured retention period
- **Health Check Data:** Small per-server overhead
- **Circuit Breaker State:** Minimal per-server state

### Network Efficiency

- **Connection Reuse:** Servers maintain persistent connections
- **Health Check Optimization:** Lightweight ping operations
- **Load Balancing:** Distributes load to prevent hot spots
- **Circuit Breaker:** Avoids unnecessary network calls to failed servers

### Scalability

- **Horizontal Scaling:** Add more servers via configuration
- **Load Distribution:** Multiple strategies available
- **Fault Tolerance:** System remains operational with partial failures
- **Resource Management:** Circuit breakers prevent resource exhaustion

## Common Pitfalls and Solutions

### 1. Thread Safety Issues

**Problem:** Concurrent access to shared state without proper synchronization.

**Solution:** Use `ConcurrentHashMap` and atomic operations throughout.

```java
// WRONG: Not thread-safe
private int counter = 0;
public void increment() { counter++; }

// RIGHT: Thread-safe with atomic operations
private final AtomicInteger counter = new AtomicInteger(0);
public void increment() { counter.incrementAndGet(); }
```

### 2. Circuit Breaker False Positives

**Problem:** Circuit breaker opens due to temporary network issues.

**Solution:** Configure appropriate thresholds and implement proper health checks.

```yaml
circuit-breaker:
  failure-threshold: 5          # Allow some failures
  failure-rate-threshold: 0.5   # 50% failure rate threshold
  timeout: 60s                  # Reasonable recovery time
```

### 3. Configuration Validation

**Problem:** Invalid configuration causes runtime failures.

**Solution:** Use `@Validated` and proper validation annotations.

```java
@ConfigurationProperties(prefix = "spring.ai.mcp.multi-server")
@Validated
public class MultiServerConfig {
    @NotEmpty(message = "At least one server must be configured")
    private Map<String, ServerConfig> servers;
    
    @Min(value = 1, message = "Health check interval must be positive")
    private Duration healthCheckInterval;
}
```

### 4. Memory Leaks in Statistics

**Problem:** Unbounded statistics collection leads to memory leaks.

**Solution:** Implement bounded collections with TTL.

```java
// Bounded statistics with automatic cleanup
private final Map<String, TimestampedStats> stats = new ConcurrentHashMap<>();

@Scheduled(fixedRate = 300000) // Every 5 minutes
public void cleanupOldStats() {
    long cutoff = System.currentTimeMillis() - statsRetentionPeriod;
    stats.entrySet().removeIf(entry -> entry.getValue().getTimestamp() < cutoff);
}
```

## Production Readiness Checklist

### Configuration
- [ ] Proper health check intervals configured
- [ ] Circuit breaker thresholds tuned for your environment
- [ ] Load balancing strategy appropriate for your use case
- [ ] Server weights configured based on capacity

### Monitoring
- [ ] Health check metrics exposed
- [ ] Circuit breaker state monitoring
- [ ] Load balancing distribution metrics
- [ ] Error rate and response time tracking

### Resilience
- [ ] Circuit breaker thresholds tested
- [ ] Failover scenarios validated
- [ ] Health check false positive rate acceptable
- [ ] Resource limits configured

### Security
- [ ] Server authentication configured
- [ ] SSL/TLS enabled for remote connections
- [ ] Timeouts configured to prevent DoS
- [ ] Input validation on all tool parameters

## Integration with Existing Systems

### Spring AI Integration

The multi-server implementation integrates seamlessly with Spring AI:

- **Conditional Configuration:** Only activates when multi-server is enabled
- **Service Interface:** Implements existing `McpClientService` interface
- **Function Integration:** Works with existing Spring AI function calling
- **Chat Client:** Integrates with existing ChatClient configuration

### Migration Path

**From Single Server:**
1. Add multi-server configuration to `application.yml`
2. Set `spring.ai.mcp.multi-server.enabled=true`
3. Configure your existing server as first multi-server entry
4. Add additional servers incrementally
5. Test load balancing and failover
6. Disable single-server configuration

**Configuration Migration:**
```yaml
# OLD: Single server configuration
spring:
  ai:
    mcp:
      client:
        enabled: true
        type: SSE
        sse:
          connections:
            echo-server:
              url: "http://localhost:3000/sse"

# NEW: Multi-server configuration
spring:
  ai:
    mcp:
      client:
        enabled: false  # Disable single-server
      multi-server:
        enabled: true
        servers:
          echo-server:
            url: "http://localhost:3000/sse"
            type: SSE
            tools: ["echo", "ping"]
```

## Future Enhancements

### Advanced Load Balancing
- **Least Connections:** Route to server with fewest active connections
- **Response Time Based:** Route to fastest responding server
- **Geographic Routing:** Route based on server location
- **Custom Metrics:** Use application-specific metrics for routing

### Enhanced Health Checking
- **Custom Health Checks:** Application-specific health validation
- **Dependency Checking:** Verify server dependencies
- **Performance Thresholds:** Mark servers unhealthy based on performance
- **Graceful Degradation:** Partial functionality during failures

### Advanced Monitoring
- **Metrics Export:** Prometheus/Micrometer integration
- **Distributed Tracing:** OpenTelemetry integration
- **Real-time Dashboards:** Grafana integration
- **Alerting:** Integration with monitoring systems

### Security Enhancements
- **mTLS Authentication:** Mutual TLS for server connections
- **JWT Token Management:** Automatic token refresh
- **Rate Limiting:** Per-server rate limiting
- **Audit Logging:** Comprehensive operation logging

This multi-server implementation provides a solid foundation for enterprise-grade MCP applications while maintaining the simplicity and educational value that makes it an excellent learning tool.