/**
 * Simple MCP Server with SSE Transport
 * Educational demonstration for L09ClientMCP Branch 5
 * 
 * This server implements a basic Model Context Protocol server that communicates
 * via Server-Sent Events (SSE) over HTTP. It provides echo and ping tools
 * for integration testing with the Spring AI MCP client.
 */

const express = require('express');
const cors = require('cors');

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(express.json());

// MCP Protocol Implementation
class McpServer {
    constructor() {
        this.clients = new Map();
        this.tools = [
            {
                name: 'echo',
                description: 'Echo back a message with optional text formatting',
                inputSchema: {
                    type: 'object',
                    properties: {
                        message: {
                            type: 'string',
                            description: 'The message to echo back'
                        },
                        format: {
                            type: 'string',
                            description: 'Optional formatting: uppercase, lowercase, reverse',
                            enum: ['uppercase', 'lowercase', 'reverse']
                        }
                    },
                    required: ['message']
                }
            },
            {
                name: 'ping',
                description: 'Test connectivity and get server information',
                inputSchema: {
                    type: 'object',
                    properties: {},
                    required: []
                }
            }
        ];
    }

    handleConnection(req, res) {
        console.log('New MCP SSE connection established');
        
        // Set up SSE headers
        res.writeHead(200, {
            'Content-Type': 'text/event-stream',
            'Cache-Control': 'no-cache',
            'Connection': 'keep-alive',
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Headers': 'Cache-Control'
        });

        const clientId = Date.now().toString();
        this.clients.set(clientId, res);

        // Send initial connection message
        this.sendMessage(res, {
            type: 'connection',
            data: {
                server: 'node_mcp_server',
                version: '1.0.0',
                capabilities: {
                    tools: true,
                    ping: true
                }
            }
        });

        // Handle client disconnect
        req.on('close', () => {
            console.log('MCP SSE connection closed');
            this.clients.delete(clientId);
        });

        req.on('error', (err) => {
            console.error('MCP SSE connection error:', err);
            this.clients.delete(clientId);
        });
    }

    sendMessage(res, message) {
        try {
            res.write(`data: ${JSON.stringify(message)}\\n\\n`);
        } catch (error) {
            console.error('Error sending SSE message:', error);
        }
    }

    listTools() {
        return {
            success: true,
            tools: this.tools
        };
    }

    invokeTool(toolName, arguments) {
        console.log(`Invoking tool: ${toolName} with arguments:`, arguments);

        switch (toolName) {
            case 'echo':
                return this.handleEcho(arguments);
            case 'ping':
                return this.handlePing(arguments);
            default:
                return {
                    success: false,
                    error: `Unknown tool: ${toolName}`
                };
        }
    }

    handleEcho(args) {
        const { message, format } = args;

        if (!message || message.trim() === '') {
            return {
                success: false,
                error: 'Message cannot be empty'
            };
        }

        let result = message;
        let formatApplied = 'none';

        if (format) {
            switch (format.toLowerCase()) {
                case 'uppercase':
                    result = message.toUpperCase();
                    formatApplied = 'uppercase';
                    break;
                case 'lowercase':
                    result = message.toLowerCase();
                    formatApplied = 'lowercase';
                    break;
                case 'reverse':
                    result = message.split('').reverse().join('');
                    formatApplied = 'reverse';
                    break;
                default:
                    formatApplied = 'none';
            }
        }

        return {
            success: true,
            content: `Echo: ${result}`,
            metadata: {
                original_message: message,
                format_applied: formatApplied,
                timestamp: Date.now().toString(),
                server_type: 'node_mcp_server'
            }
        };
    }

    handlePing(args) {
        return {
            success: true,
            content: 'pong',
            metadata: {
                timestamp: Date.now().toString(),
                server_type: 'node_mcp_server',
                uptime: process.uptime(),
                memory: process.memoryUsage()
            }
        };
    }
}

// Initialize MCP server
const mcpServer = new McpServer();

// Routes
app.get('/mcp', (req, res) => {
    mcpServer.handleConnection(req, res);
});

app.get('/health', (req, res) => {
    res.json({
        status: 'healthy',
        server: 'node_mcp_server',
        version: '1.0.0',
        uptime: process.uptime(),
        connections: mcpServer.clients.size
    });
});

app.post('/tools', (req, res) => {
    const result = mcpServer.listTools();
    res.json(result);
});

app.post('/tools/:toolName', (req, res) => {
    const { toolName } = req.params;
    const arguments = req.body;
    
    const result = mcpServer.invokeTool(toolName, arguments);
    res.json(result);
});

// Start server
app.listen(PORT, () => {
    console.log(`🚀 MCP Demo Server running on http://localhost:${PORT}`);
    console.log(`📡 SSE endpoint: http://localhost:${PORT}/mcp`);
    console.log(`🏥 Health check: http://localhost:${PORT}/health`);
    console.log(`🛠️  Tools endpoint: http://localhost:${PORT}/tools`);
});

// Graceful shutdown
process.on('SIGINT', () => {
    console.log('\\n🛑 Shutting down MCP server...');
    process.exit(0);
});

process.on('SIGTERM', () => {
    console.log('\\n🛑 Shutting down MCP server...');
    process.exit(0);
});