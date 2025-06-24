# MCP Echo Server

This is a simple external MCP (Model Context Protocol) server for educational demonstration in Branch 7 of the Spring AI course.

## Purpose

This Node.js server demonstrates:
- Real external MCP server connectivity
- SSE (Server-Sent Events) transport layer
- Tool discovery and invocation protocols
- Educational MCP implementations

## Features

- **SSE Transport**: Real-time communication using Server-Sent Events
- **Tool Discovery**: Dynamic tool listing and capabilities
- **Echo Tool**: Message echoing with optional formatting (uppercase, lowercase, reverse)
- **Ping Tool**: Connectivity testing with response time simulation
- **Health Monitoring**: Server health and status endpoints

## Quick Start

### Prerequisites
- Node.js 16+ installed
- npm package manager

### Installation and Running

1. Install dependencies:
```bash
cd external-server
npm install
```

2. Start the server:
```bash
npm start
```

3. Server will be available at:
- SSE endpoint: `http://localhost:3000/sse`
- Health check: `http://localhost:3000/health`
- Tools discovery: `http://localhost:3000/tools`

## API Endpoints

### SSE Endpoint
- **URL**: `GET /sse`
- **Purpose**: Establish SSE connection for real-time MCP communication
- **Response**: Server-Sent Events stream with connection status and tool updates

### Tools Discovery
- **URL**: `POST /tools`
- **Purpose**: List available tools and their capabilities
- **Response**: JSON with tools array and capabilities

### Tool Invocation
- **URL**: `POST /tools/{toolName}`
- **Purpose**: Invoke specific tools with parameters
- **Supported Tools**:
  - `echo`: Echo messages with optional formatting
  - `ping`: Test connectivity and response time

### Health Check
- **URL**: `GET /health`
- **Purpose**: Server health monitoring
- **Response**: Server status, uptime, and connection statistics

## Integration with Spring AI Client

The Spring AI client (Branch 7) connects to this server using the SSE transport configuration:

```yaml
spring:
  ai:
    mcp:
      client:
        enabled: true
        type: SYNC
        sse:
          connections:
            echo-server:
              url: "http://localhost:3000/sse"
              enabled: true
```

## Development

For development with auto-restart:
```bash
npm run dev
```

## Educational Value

This server demonstrates:
1. **Real MCP Protocol**: Actual implementation of MCP over HTTP/SSE
2. **Tool Architecture**: How external tools are discovered and invoked
3. **Network Communication**: Real network latency and error handling
4. **Production Patterns**: Health monitoring, graceful shutdown, error handling

## Testing

Test the server manually:

1. **Health Check**:
```bash
curl http://localhost:3000/health
```

2. **Tool Discovery**:
```bash
curl -X POST http://localhost:3000/tools \
  -H "Content-Type: application/json"
```

3. **Echo Tool**:
```bash
curl -X POST http://localhost:3000/tools/echo \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello World", "format": "uppercase"}'
```

4. **Ping Tool**:
```bash
curl -X POST http://localhost:3000/tools/ping \
  -H "Content-Type: application/json" \
  -d '{"target": "external-service"}'
```

## Troubleshooting

### Port Conflicts
If port 3000 is in use, modify the `port` variable in `server.js`.

### Connection Issues
- Ensure the server is running before starting the Spring AI client
- Check CORS settings if accessing from different origins
- Verify firewall settings allow connections to port 3000

### Spring AI Integration
- Confirm the SSE URL in `application.yml` matches the server endpoint
- Check that `spring.ai.mcp.client.enabled=true` in configuration
- Review Spring AI logs for connection status and error messages

This server provides a realistic external MCP implementation for learning production-ready integration patterns.