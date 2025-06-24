# L09ClientMCP - Production Ready

## Overview

This project demonstrates a production-ready Spring AI MCP (Model Context Protocol) client implementation. Starting from a basic chatbot, it has evolved into a comprehensive enterprise-grade application with security, monitoring, resilience patterns, and deployment capabilities.

## 🚀 Production Features

### Core Architecture
- **Spring AI 1.0.0** with MCP client integration
- **Multi-server MCP support** with intelligent load balancing
- **Reactive and traditional web stack** options
- **Comprehensive error handling** and circuit breaker patterns

### 🔐 Security & Authentication
- **JWT Authentication** with configurable expiration
- **Role-Based Access Control (RBAC)** for fine-grained permissions
- **API Key Management** for service-to-service communication
- **Request rate limiting** and abuse protection

### 📊 Monitoring & Observability
- **Micrometer metrics** with Prometheus export
- **Distributed tracing** with OpenTelemetry
- **Custom health indicators** for MCP connections
- **Structured audit logging** for compliance

### 🛡️ Resilience Patterns
- **Circuit breaker** for external MCP servers
- **Retry logic** with exponential backoff
- **Connection pooling** and timeout management
- **Graceful degradation** when services are unavailable

### 🐳 Deployment Ready
- **Docker containerization** with multi-stage builds
- **Kubernetes manifests** with security contexts
- **Helm charts** for scalable deployment
- **Production configuration** profiles

## Getting Started

### Quick Start
```bash
# Clone and setup
git clone <repository-url>
cd L09ClientMCP

# Set required environment variables
export OPENAI_API_KEY=your-api-key-here

# Run the application
./mvnw spring-boot:run
```

### Docker Deployment
```bash
# Build the image
docker build -t l09clientmcp:latest .

# Run with docker-compose
docker-compose up -d
```

### Kubernetes Deployment
```bash
# Apply all manifests
kubectl apply -f k8s/

# Or use Kustomize
kubectl apply -k k8s/
```

## 🧪 Testing

### Unit & Integration Tests
```bash
# Run all tests
./mvnw test

# Run with coverage
./mvnw test jacoco:report
```

**Current Status**: ✅ 143 passing, ⚠️ 23 skipped (complex integration tests)

### API Testing with Postman

#### Quick Setup
1. Import: `postman/L09ClientMCP-Production-Ready.postman_collection.json`
2. Environment: `postman/Local Development.postman_environment.json`
3. Start app: `./mvnw spring-boot:run`
4. Run collection

#### Test Categories
- ✅ **Health & Status** - Service availability and MCP status
- ✅ **Chat Operations** - Core chatbot functionality
- ✅ **MCP Operations** - Tool discovery and invocation
- ✅ **Production Monitoring** - Metrics and observability
- ✅ **Error Handling** - Edge cases and failure scenarios
- ✅ **Performance Tests** - Load testing and rate limiting

#### Command Line Testing
```bash
# Install Newman
npm install -g newman

# Run full test suite
newman run postman/L09ClientMCP-Production-Ready.postman_collection.json \
  --environment "postman/Local Development.postman_environment.json" \
  --reporters cli,html
```

## 🔧 Configuration

### Environment Profiles
- **test** - Unit testing with minimal dependencies
- **dev** - Development with debug logging
- **staging** - Pre-production environment
- **production** - Full production features

### Key Configuration Properties
```yaml
spring:
  ai:
    mcp:
      multi-server:
        enabled: true
        load-balancing-strategy: ROUND_ROBIN
      production:
        enabled: true
  
  security:
    jwt:
      secret: ${JWT_SECRET}
      expiration: 86400
```

### Environment Variables
```bash
# Required
OPENAI_API_KEY=sk-...

# Production
JWT_SECRET=your-jwt-secret
API_KEY_ADMIN=admin-access-key
API_KEY_USER=user-access-key

# Optional
SPRING_PROFILES_ACTIVE=production
SERVER_PORT=8080
```

## 📚 API Documentation

### Core Endpoints
- `GET /api/chat/health` - Health check
- `POST /api/chat` - Chat interaction
- `GET /api/mcp/status` - MCP status
- `GET /api/mcp/tools` - List available tools
- `POST /api/mcp/tools/{tool}/invoke` - Invoke tool

### Production Endpoints
- `GET /production/monitoring/health` - Production health
- `GET /production/monitoring/metrics` - System metrics
- `GET /production/monitoring/status` - Detailed status

### Monitoring Endpoints
- `GET /actuator/health` - Spring Boot health
- `GET /actuator/metrics` - Micrometer metrics
- `GET /actuator/prometheus` - Prometheus metrics

## 🏗️ Architecture

### Current Production Architecture
```
Internet → Load Balancer → Ingress Controller
    ↓
Kubernetes Pods (3 replicas) → MCP Servers
    ↓                            ↓
ChatService → SpringAI → OpenAI API
    ↓
Monitoring Stack (Prometheus, Grafana)
```

### MCP Integration
```
ChatController → ChatService → MultiServerMcpClient
                                    ↓
                Circuit Breaker → Server Selection → MCP Servers
                                    ↓                    ↓
                Health Monitor → Load Balancer → [Echo, Search, Calculator]
```

## 🔍 Monitoring

### Metrics Available
- HTTP request rates and latencies
- MCP tool invocation metrics
- Circuit breaker states
- JVM memory and CPU usage
- Custom business metrics

### Health Checks
- Application startup health
- MCP server connectivity
- Database connections (if applicable)
- External service availability

### Logging
- Structured JSON logging
- Audit trail for compliance
- Configurable log levels
- Error tracking and alerting

## 🚢 Deployment Strategies

### Local Development
```bash
./mvnw spring-boot:run -Dspring.profiles.active=dev
```

### Docker Compose
```bash
docker-compose up -d
# Includes: app, postgres, redis, prometheus, grafana
```

### Kubernetes
```bash
# Development
kubectl apply -f k8s/ -n development

# Production
kubectl apply -k k8s/ -n production
```

### Production Checklist
- [ ] Environment variables configured
- [ ] TLS certificates installed
- [ ] Monitoring stack deployed
- [ ] Log aggregation configured
- [ ] Backup strategy implemented
- [ ] Security scanning completed

## 🔧 Troubleshooting

### Common Issues

1. **MCP Connection Failures**
   ```bash
   # Check MCP server status
   curl http://localhost:8080/api/mcp/status
   
   # Verify server configuration
   kubectl logs deployment/l09clientmcp
   ```

2. **Authentication Issues**
   ```bash
   # Verify JWT configuration
   echo $JWT_SECRET | base64
   
   # Check API key setup
   curl -H "X-API-Key: $API_KEY_USER" http://localhost:8080/api/chat/health
   ```

3. **Performance Issues**
   ```bash
   # Check metrics
   curl http://localhost:8080/actuator/metrics
   
   # Review circuit breaker status
   curl http://localhost:8080/production/monitoring/status
   ```

### Debug Mode
```bash
# Enable debug logging
export LOGGING_LEVEL_COM_COHERENTSOLUTIONS=DEBUG
./mvnw spring-boot:run
```

## 📖 Documentation

- [Testing Guide](docs/TESTING_GUIDE.md) - Comprehensive testing approach
- [Production Readiness Guide](docs/PRODUCTION_READINESS.md) - Production deployment guide
- [Architecture Decisions](docs/decisions/) - ADR documentation
- [Branch Documentation](docs/branches/) - Feature evolution guide

## 🎯 Performance Benchmarks

### Expected Performance
- **Throughput**: 1000+ requests/second
- **Latency**: P95 < 2 seconds
- **Memory**: < 512MB heap usage
- **Availability**: 99.9% uptime target

### Load Testing Results
- ✅ 200 concurrent users supported
- ✅ 95%+ success rate under load
- ✅ Sub-second response times maintained
- ✅ Graceful degradation under stress

## 🛠️ Development

### Building
```bash
./mvnw clean package
```

### Testing
```bash
./mvnw test
```

### Code Quality
```bash
./mvnw spotbugs:check
./mvnw jacoco:report
```

## 🤝 Contributing

1. Follow the existing code style
2. Add tests for new features
3. Update documentation
4. Ensure all tests pass
5. Submit pull request

## 📄 License

[Add your license information here]

---

This production-ready implementation showcases how a simple Spring AI chatbot can evolve into a comprehensive, enterprise-grade MCP client suitable for production deployment with proper security, monitoring, and operational excellence.