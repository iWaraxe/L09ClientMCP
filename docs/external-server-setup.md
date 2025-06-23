# External MCP Server Setup Guide

This guide explains how to set up and run the external Node.js MCP server for testing SSE transport integration in Branch 5.

## Quick Start

### Prerequisites

- Node.js 18+ installed
- npm package manager
- Terminal/command line access

### Installation and Startup

```bash
# Navigate to the MCP server directory
cd mcp-server

# Install dependencies
npm install

# Start the server
npm start
```

The server will start on `http://localhost:3000` and display:
```
🚀 MCP Demo Server running on http://localhost:3000
📡 SSE endpoint: http://localhost:3000/mcp
🏥 Health check: http://localhost:3000/health
🛠️  Tools endpoint: http://localhost:3000/tools
```

### Verification

Test the server is running:
```bash
# Health check
curl http://localhost:3000/health

# Expected response:
{
  "status": "healthy",
  "server": "node_mcp_server",
  "version": "1.0.0",
  "uptime": 123.45,
  "connections": 0
}
```

## Integration with Spring AI Application

### Using SSE Profile

Start the Spring AI application with the SSE profile:

```bash
# Terminal 1: Start MCP server
cd mcp-server
npm start

# Terminal 2: Start Spring AI app with SSE profile
./mvnw spring-boot:run -Dspring.profiles.active=mcp-sse
```

### Configuration

The SSE profile (`application-mcp-sse.yml`) automatically connects to:
- **Server URL**: `http://localhost:3000/mcp`
- **Timeout**: 30 seconds
- **Retry Attempts**: 3
- **Retry Delay**: 5 seconds

## Testing the Integration

### 1. Health Check

```bash
curl http://localhost:8080/api/chat/health
```

Expected response includes:
```
MCP enabled - Type: SSE, Server: http://localhost:3000/mcp, Status: connected (2 tools)
```

### 2. Chat with Tool Integration

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "echo Hello SSE Transport!"}'
```

Expected response:
```json
{
  "response": "I used the echo tool to process your message:\n\n**Result:** Echo: Hello SSE Transport!\n**Original:** Hello SSE Transport!\n**Format Applied:** none\n**Server:** node_mcp_server"
}
```

### 3. Ping Tool Test

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "ping the server"}'
```

Expected response includes server connectivity information with real timestamp and uptime data.

## Development and Debugging

### Server Logs

The Node.js server provides detailed logging:
```
🚀 MCP Demo Server running on http://localhost:3000
New MCP SSE connection established
Invoking tool: echo with arguments: { message: 'Hello SSE Transport!' }
```

### Spring AI Application Logs

With the SSE profile, you'll see:
```
MCP SSE Client Service initialized - Enabled: true, Type: SSE, Server: http://localhost:3000/mcp
Connecting to MCP server via SSE: http://localhost:3000
MCP SSE client connected successfully
Connected to MCP server: node_mcp_server version 1.0.0
```

### Troubleshooting

#### Connection Issues

**Problem**: `SSE client not connected - cannot invoke tools`
**Solution**: 
1. Verify MCP server is running: `curl http://localhost:3000/health`
2. Check firewall/network settings
3. Ensure correct URL in configuration

**Problem**: `Connection failed after 3 attempts`
**Solution**:
1. Start MCP server before Spring AI application
2. Check server logs for errors
3. Verify Node.js dependencies installed correctly

#### Tool Invocation Issues

**Problem**: Tools return errors or unexpected results
**Solution**:
1. Test tools directly via HTTP: `curl -X POST http://localhost:3000/tools/echo -d '{"message":"test"}'`
2. Check server logs for error details
3. Verify tool parameters match expected schema

#### Performance Issues

**Problem**: Slow response times
**Solution**:
1. Check network latency: `ping localhost`
2. Monitor server resource usage
3. Review timeout configurations in `application-mcp-sse.yml`

## Advanced Configuration

### Custom Server URL

To use a different server URL, update `application-mcp-sse.yml`:

```yaml
spring:
  ai:
    mcp:
      client:
        sse:
          connections:
            demo-server:
              url: "http://your-server:port/mcp"
```

### Connection Tuning

Adjust connection parameters for your environment:

```yaml
spring:
  ai:
    mcp:
      client:
        request-timeout: 60s  # Increase for slow networks
        sse:
          connections:
            demo-server:
              timeout: 45s
              retry-attempts: 5     # More retries
              retry-delay: 10s      # Longer delays
```

### Production Deployment

For production use:

1. **Use HTTPS**: Configure SSL/TLS for the MCP server
2. **Authentication**: Add API keys or OAuth integration
3. **Load Balancing**: Deploy multiple MCP server instances
4. **Monitoring**: Add health checks and metrics collection
5. **Error Handling**: Implement circuit breakers and fallback mechanisms

## Docker Deployment (Optional)

### MCP Server Dockerfile

```dockerfile
FROM node:18-alpine

WORKDIR /app
COPY package*.json ./
RUN npm install

COPY . .
EXPOSE 3000

CMD ["npm", "start"]
```

### Build and Run

```bash
# Build Docker image
docker build -t mcp-demo-server ./mcp-server

# Run container
docker run -p 3000:3000 mcp-demo-server
```

### Docker Compose

```yaml
version: '3.8'
services:
  mcp-server:
    build: ./mcp-server
    ports:
      - "3000:3000"
    environment:
      - NODE_ENV=production
    restart: unless-stopped
```

## Integration Testing

### Automated Tests

Run integration tests with the external server:

```bash
# Start MCP server
cd mcp-server && npm start &

# Set environment variable
export MCP_INTEGRATION_TEST=true

# Run integration tests
./mvnw test -Dtest=McpSseIntegrationTest

# Cleanup
kill %1  # Stop background MCP server
```

### Manual Testing Script

```bash
#!/bin/bash
# test-sse-integration.sh

echo "🚀 Starting MCP SSE Integration Test..."

# Start MCP server in background
cd mcp-server
npm start &
MCP_PID=$!

# Wait for server to start
sleep 3

# Test server health
echo "🏥 Testing server health..."
curl -s http://localhost:3000/health | jq .

# Start Spring AI app with SSE profile
cd ..
echo "🌱 Starting Spring AI application..."
./mvnw spring-boot:run -Dspring.profiles.active=mcp-sse &
SPRING_PID=$!

# Wait for application to start
sleep 10

# Test integration
echo "🧪 Testing chat integration..."
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "echo Hello SSE Integration Test!"}' | jq .

# Cleanup
echo "🧹 Cleaning up..."
kill $SPRING_PID
kill $MCP_PID

echo "✅ Integration test complete!"
```

This comprehensive setup guide ensures successful integration between the Spring AI application and the external MCP server using SSE transport.