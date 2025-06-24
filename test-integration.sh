#!/bin/bash

# Integration test script for Branch 7: SSE Transport Integration
echo "=== Branch 7: MCP SSE Transport Integration Test ==="

# Function to check if server is running
check_server() {
    curl -s http://localhost:3000/health > /dev/null
    return $?
}

# Function to test Spring Boot app
test_spring_app() {
    echo "Testing Spring Boot application..."
    
    # Test health endpoint
    echo "1. Testing health endpoint..."
    response=$(curl -s http://localhost:8080/api/chat/health)
    echo "   Health response: $response"
    
    # Test functions endpoint
    echo "2. Testing functions endpoint..."
    response=$(curl -s http://localhost:8080/api/chat/functions)
    echo "   Functions response: $response"
    
    # Test basic chat
    echo "3. Testing basic chat..."
    response=$(curl -s -X POST http://localhost:8080/api/chat \
        -H "Content-Type: application/json" \
        -d '{"message": "Hello, what can you do?"}')
    echo "   Chat response: $response"
    
    # Test echo functionality (if functions are available)
    echo "4. Testing echo functionality..."
    response=$(curl -s -X POST http://localhost:8080/api/chat \
        -H "Content-Type: application/json" \
        -d '{"message": "Can you echo back 'Hello World' in uppercase?"}')
    echo "   Echo test response: $response"
}

# Main test execution
echo "Starting integration test..."

# Check if external server is running
echo "Checking external MCP server..."
if check_server; then
    echo "✅ External MCP server is running at http://localhost:3000"
else
    echo "❌ External MCP server is not running"
    echo "Please start it with: cd external-server && npm start"
    exit 1
fi

# Test external server directly
echo "Testing external server endpoints..."
echo "Health check:"
curl -s http://localhost:3000/health | jq . 2>/dev/null || curl -s http://localhost:3000/health

echo -e "\nTools discovery:"
curl -s -X POST http://localhost:3000/tools | jq . 2>/dev/null || curl -s -X POST http://localhost:3000/tools

echo -e "\nDirect echo test:"
curl -s -X POST http://localhost:3000/tools/echo \
    -H "Content-Type: application/json" \
    -d '{"message": "Hello World", "format": "uppercase"}' | jq . 2>/dev/null || \
curl -s -X POST http://localhost:3000/tools/echo \
    -H "Content-Type: application/json" \
    -d '{"message": "Hello World", "format": "uppercase"}'

# Check if Spring Boot app is running
echo -e "\n\nChecking Spring Boot application..."
if curl -s http://localhost:8080/api/chat/health > /dev/null; then
    echo "✅ Spring Boot application is running at http://localhost:8080"
    test_spring_app
else
    echo "❌ Spring Boot application is not running"
    echo "Please start it with: ./mvnw spring-boot:run"
    echo "Or test manually after starting the application"
fi

echo -e "\n=== Integration Test Complete ===
To run this test:
1. Start external server: cd external-server && npm start
2. Start Spring Boot app: ./mvnw spring-boot:run
3. Run this script: ./test-integration.sh"