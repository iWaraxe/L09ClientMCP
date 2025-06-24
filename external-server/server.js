const express = require('express');
const cors = require('cors');

const app = express();
const port = 3000;

// Middleware
app.use(cors());
app.use(express.json());

// Store SSE connections
const sseConnections = new Set();

// SSE endpoint for MCP protocol
app.get('/sse', (req, res) => {
    console.log('New SSE connection established');
    
    // Set SSE headers
    res.writeHead(200, {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive',
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Headers': 'Cache-Control'
    });

    // Send connection established message
    const connectionMessage = {
        type: 'connection',
        data: {
            server: 'MCP Echo Server',
            version: '1.0.0',
            capabilities: {
                tools: true,
                echo: true,
                ping: true
            }
        }
    };
    
    res.write(`data: ${JSON.stringify(connectionMessage)}\n\n`);
    
    // Store connection
    sseConnections.add(res);
    
    // Handle client disconnect
    req.on('close', () => {
        console.log('SSE connection closed');
        sseConnections.delete(res);
    });
    
    // Keep connection alive
    const keepAlive = setInterval(() => {
        if (sseConnections.has(res)) {
            res.write(`data: {"type": "ping", "timestamp": "${new Date().toISOString()}"}\n\n`);
        } else {
            clearInterval(keepAlive);
        }
    }, 30000); // Send ping every 30 seconds
});

// Tools endpoint - list available tools
app.post('/tools', (req, res) => {
    console.log('Tools list requested');
    
    const tools = [
        {
            name: 'echo',
            description: 'Echo back the provided message with optional formatting',
            parameters: {
                type: 'object',
                properties: {
                    message: {
                        type: 'string',
                        description: 'The message to echo back'
                    },
                    format: {
                        type: 'string',
                        enum: ['uppercase', 'lowercase', 'reverse'],
                        description: 'Optional formatting to apply to the message'
                    }
                },
                required: ['message']
            }
        },
        {
            name: 'ping',
            description: 'Test server connectivity and response time',
            parameters: {
                type: 'object',
                properties: {
                    target: {
                        type: 'string',
                        description: 'Target to ping (optional, defaults to server)',
                        default: 'server'
                    }
                }
            }
        }
    ];
    
    res.json({
        success: true,
        tools: tools
    });
});

// Tool invocation endpoint
app.post('/tools/:toolName', (req, res) => {
    const { toolName } = req.params;
    const arguments = req.body;
    
    console.log(`Tool '${toolName}' invoked with arguments:`, arguments);
    
    try {
        let result;
        
        switch (toolName) {
            case 'echo':
                result = handleEcho(arguments);
                break;
            case 'ping':
                result = handlePing(arguments);
                break;
            default:
                return res.status(404).json({
                    success: false,
                    error: `Tool '${toolName}' not found`
                });
        }
        
        res.json(result);
        
    } catch (error) {
        console.error(`Error executing tool '${toolName}':`, error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Echo tool implementation
function handleEcho(arguments) {
    const { message, format } = arguments;
    
    if (!message) {
        throw new Error('Message parameter is required');
    }
    
    let result = message;
    
    if (format) {
        switch (format.toLowerCase()) {
            case 'uppercase':
                result = message.toUpperCase();
                break;
            case 'lowercase':
                result = message.toLowerCase();
                break;
            case 'reverse':
                result = message.split('').reverse().join('');
                break;
            default:
                // No formatting applied
                break;
        }
    }
    
    return {
        success: true,
        content: result,
        metadata: {
            originalMessage: message,
            format: format || 'none',
            timestamp: new Date().toISOString()
        }
    };
}

// Ping tool implementation
function handlePing(arguments) {
    const { target = 'server' } = arguments;
    const startTime = Date.now();
    
    // Simulate ping operation
    const responseTime = Math.floor(Math.random() * 10) + 1; // 1-10ms
    
    return {
        success: true,
        content: `Ping to ${target} successful`,
        metadata: {
            target: target,
            responseTime: responseTime,
            timestamp: new Date().toISOString(),
            status: 'healthy'
        }
    };
}

// Health check endpoint
app.get('/health', (req, res) => {
    res.json({
        status: 'healthy',
        server: 'MCP Echo Server',
        version: '1.0.0',
        uptime: process.uptime(),
        activeConnections: sseConnections.size,
        timestamp: new Date().toISOString()
    });
});

// Start server
app.listen(port, () => {
    console.log(`=== MCP Echo Server ===`);
    console.log(`Server running on http://localhost:${port}`);
    console.log(`SSE endpoint: http://localhost:${port}/sse`);
    console.log(`Health check: http://localhost:${port}/health`);
    console.log(`Tools endpoint: http://localhost:${port}/tools`);
    console.log(`========================`);
});

// Graceful shutdown
process.on('SIGTERM', () => {
    console.log('Shutting down MCP Echo Server...');
    sseConnections.forEach(connection => {
        connection.end();
    });
    process.exit(0);
});

process.on('SIGINT', () => {
    console.log('Shutting down MCP Echo Server...');
    sseConnections.forEach(connection => {
        connection.end();
    });
    process.exit(0);
});