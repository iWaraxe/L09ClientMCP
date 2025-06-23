# Quick Start Guide

## 🚀 Running the Application

### Prerequisites
- Java 21+
- Maven 3.8+
- OpenAI API Key

### 1. Set Environment Variable
```bash
export OPENAI_API_KEY=your-openai-api-key-here
```

### 2. Start the Application
```bash
./mvnw spring-boot:run
```

### 3. Verify It's Running
Wait for this log message:
```
Started L09ClientMcpApplication in X.XXX seconds (JVM running for Y.YYY)
```

### 4. Test the Health Endpoint
```bash
curl http://localhost:8080/api/chat/health
```
Expected response: `Chat service is running`

## 🧪 Testing with curl

### Basic Chat Test
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello! What is Spring AI?"}'
```

### Run All Tests
```bash
./mvnw test
```

## 📮 Visual Testing with Postman

1. **Import Collection**: `postman/L09ClientMCP.postman_collection.json`
2. **Import Environment**: `postman/Local Development.postman_environment.json`
3. **Select Environment**: "Local Development" in Postman
4. **Run Health Check**: Verify connection
5. **Try Chat Requests**: Explore different scenarios

See [Visual Testing Guide](docs/VISUAL_TESTING.md) for detailed instructions.

## 🎯 Demo Script for Trainers

### 1. Health Check (30 seconds)
- Show health endpoint in browser: `http://localhost:8080/api/chat/health`
- Explain importance of health checks in production

### 2. Basic Chat (2 minutes)
- Use Postman to send: `{"message": "Hello! Can you introduce yourself?"}`
- Show JSON request/response structure
- Highlight AI's conversational ability

### 3. Technical Question (2 minutes)
- Ask: `{"message": "What is Spring AI and why should I use it?"}`
- Demonstrate AI's technical knowledge
- Discuss response quality and limitations

### 4. MCP Setup Discussion (1 minute)
- Ask: `{"message": "Explain the Model Context Protocol"}`
- Show current AI limitations (training data only)
- Set up motivation for MCP integration

**Total demo time: ~5 minutes**

## 🔧 Troubleshooting

### Port 8080 Already in Use
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

### OpenAI API Errors
- Verify API key is correct
- Check OpenAI account has credits
- Ensure key has appropriate permissions

### Application Won't Start
1. Check Java version: `java --version`
2. Verify Maven: `./mvnw --version`
3. Clean and rebuild: `./mvnw clean package`

## 📚 Next Steps

1. **Explore Documentation**: Read `docs/branches/01-baseline.md`
2. **Test All Endpoints**: Use the Postman collection
3. **Move to Next Branch**: `git checkout mcp-client-setup`
4. **Continue Learning**: Follow the 10-branch progression