# MCP Demo Server

A simple Node.js server implementing the Model Context Protocol (MCP) with Server-Sent Events (SSE) transport for educational purposes.

## Features

- **SSE Transport**: Real-time communication using Server-Sent Events
- **Echo Tool**: Text echoing with formatting options (uppercase, lowercase, reverse)
- **Ping Tool**: Connectivity testing and server information
- **Health Monitoring**: Server status and connection tracking
- **CORS Support**: Cross-origin requests for web-based clients

## Quick Start

### Prerequisites

- Node.js 18+ installed
- npm or yarn package manager

### Installation

```bash
# Navigate to the MCP server directory
cd mcp-server

# Install dependencies
npm install

# Start the server
npm start
```

### Development Mode

```bash
# Start with auto-reload
npm run dev
```

## API Endpoints

### SSE Connection
- **GET** `/mcp` - Main MCP SSE endpoint for real-time communication

### HTTP Endpoints (for testing)
- **GET** `/health` - Server health check
- **POST** `/tools` - List available tools
- **POST** `/tools/:toolName` - Invoke a specific tool

## Tool Specifications

### Echo Tool
**Purpose**: Echo back messages with optional formatting

**Parameters**:
- `message` (required): The text to echo
- `format` (optional): Formatting option
  - `uppercase` - Convert to uppercase
  - `lowercase` - Convert to lowercase  
  - `reverse` - Reverse the text

**Example**:
```json
{
  "message": "Hello World",
  "format": "uppercase"
}
```

**Response**:
```json
{
  "success": true,
  "content": "Echo: HELLO WORLD",
  "metadata": {
    "original_message": "Hello World",
    "format_applied": "uppercase",
    "timestamp": "1750704859249",
    "server_type": "node_mcp_server"
  }
}
```

### Ping Tool
**Purpose**: Test connectivity and get server information

**Parameters**: None required

**Response**:
```json
{
  "success": true,
  "content": "pong",
  "metadata": {
    "timestamp": "1750704859249",
    "server_type": "node_mcp_server", 
    "uptime": 123.45,
    "memory": {
      "rss": 50331648,
      "heapTotal": 20971520,
      "heapUsed": 15728640,
      "external": 1441792
    }
  }
}
```

## Testing

### Manual Testing with curl

```bash
# Health check
curl http://localhost:3000/health

# List tools
curl -X POST http://localhost:3000/tools

# Test echo tool
curl -X POST http://localhost:3000/tools/echo \\
  -H "Content-Type: application/json" \\
  -d '{"message": "Hello World", "format": "uppercase"}'

# Test ping tool
curl -X POST http://localhost:3000/tools/ping \\
  -H "Content-Type: application/json" \\
  -d '{}'
```

### SSE Testing

```bash
# Connect to SSE stream
curl -N http://localhost:3000/mcp
```

## Integration with Spring AI

This server is designed to work with the L09ClientMCP Spring AI application using the `mcp-sse` profile:

```bash
# Start Spring AI application with SSE profile
./mvnw spring-boot:run -Dspring.profiles.active=mcp-sse
```

The Spring AI application will connect to this server automatically when using the SSE transport configuration.

## Architecture

```
┌─────────────────┐    SSE/HTTP    ┌─────────────────┐
│  Spring AI App  │ ──────────────→ │  Node.js MCP    │
│  (Java Client)  │ ←────────────── │  Server         │
└─────────────────┘                └─────────────────┘
```

## Educational Purpose

This server demonstrates:
- Real MCP protocol implementation
- SSE transport for real-time communication
- Tool registration and invocation patterns
- Error handling and response formatting
- Server lifecycle management

Perfect for learning how MCP works in practice with actual network communication rather than mock implementations.